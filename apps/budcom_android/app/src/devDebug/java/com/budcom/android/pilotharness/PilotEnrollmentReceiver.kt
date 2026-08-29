package com.budcom.android.pilotharness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.budcom.android.core.relay.data.local.RelayEndpointLocalStore
import com.budcom.android.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.budcom.android.core.trust.data.TrustEnrollmentRepository
import com.budcom.android.core.trust.data.local.TrustEndpointLocalStore
import com.budcom.android.core.trust.data.remote.TrustEndpointProvider
import com.budcom.android.core.trust.domain.EnrollmentGrantProof
import com.budcom.android.core.trust.domain.TrustEnrollmentOutcome
import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/**
 * DEV-FLAVOR + DEBUG-BUILD-TYPE ONLY (compiled only for the devDebug variant -- see this source
 * set's own AndroidManifest.xml doc comment). Lets `scripts/budcom-controlled-pilot.ps1` trigger one
 * real enrollment attempt over adb, for controlled-pilot physical-device setup, without a manual UI
 * tap and without ever putting the one-time grant secret on a command line, in a log, or in an intent
 * extra.
 *
 * Exact sequence this receiver performs -- nothing more:
 *  1. Read [PAYLOAD_FILE] from this app's own private files directory (already placed there by the
 *     orchestration script via `adb shell run-as <package> sh -c 'cat > .../files/pilot_enrollment.json'`,
 *     which pipes the JSON through stdin -- never through argv, never logged by adb itself).
 *  2. DELETE that file immediately after reading it, before attempting anything else -- so grant
 *     material is never retained past this one use, even if enrollment subsequently fails.
 *  3. If present, persist `trustBaseUrl`/`relayBaseUrl` through the SAME real, already-existing
 *     runtime-configuration stores a settings screen would use ([TrustEndpointLocalStore],
 *     [RelayEndpointLocalStore]) and hydrate the in-memory providers so the change takes effect on
 *     this process immediately, with no restart.
 *  4. Call the REAL, unmodified [TrustEnrollmentRepository.enroll] -- the exact same production
 *     enrollment path any real device uses: Android Keystore generates/loads this installation's own
 *     device key pair, only the public half + fingerprint ever leaves the device, and Trust's real
 *     `POST /v1/trust/enrollment/consume` endpoint validates and consumes the grant server-side. This
 *     receiver never constructs a credential itself and never bypasses that call.
 *  5. Write only NON-SECRET outcome fields (outcome tag, businessId, deviceId on success) to
 *     [RESULT_FILE] for the orchestration script to poll and read back via `run-as ... cat`. The
 *     grant secret is never written to this file, never logged (no `Log.*`/`println` of either
 *     payload field anywhere in this class), and this receiver is `android:exported="false"` --
 *     reachable only by an adb shell explicitly naming this component, never by another app.
 */
@AndroidEntryPoint
class PilotEnrollmentReceiver : BroadcastReceiver() {

    @Inject lateinit var trustEndpointLocalStore: TrustEndpointLocalStore
    @Inject lateinit var trustEndpointProvider: TrustEndpointProvider
    @Inject lateinit var relayEndpointLocalStore: RelayEndpointLocalStore
    @Inject lateinit var relayEndpointProvider: RelayRuntimeEndpointProvider
    @Inject lateinit var enrollmentRepository: TrustEnrollmentRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PILOT_ENROLL) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runEnrollment(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runEnrollment(context: Context) {
        val payloadFile = File(context.filesDir, PAYLOAD_FILE)
        val resultFile = File(context.filesDir, RESULT_FILE)
        if (!payloadFile.exists()) {
            resultFile.writeText(Json.encodeToString(PilotEnrollmentResult.serializer(), PilotEnrollmentResult(outcome = "no_payload_present")))
            return
        }
        val raw = payloadFile.readText()
        // Grant material must not outlive this one read, regardless of what happens next.
        payloadFile.delete()

        val payload = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(PilotEnrollmentPayload.serializer(), raw)
        } catch (e: Exception) {
            resultFile.writeText(Json.encodeToString(PilotEnrollmentResult.serializer(), PilotEnrollmentResult(outcome = "malformed_payload")))
            return
        }

        payload.trustBaseUrl?.let { raw ->
            val normalized = ConnectorUrlValidator.normalizeOrNull(raw)
            if (normalized != null) {
                trustEndpointLocalStore.save(normalized)
                trustEndpointProvider.updateInMemory(normalized)
            }
        }
        payload.relayBaseUrl?.let { raw ->
            if (relayEndpointLocalStore.configure(raw)) {
                relayEndpointProvider.updateInMemory(relayEndpointLocalStore.read())
            }
        }

        val outcome = try {
            enrollmentRepository.enroll(EnrollmentGrantProof(payload.grantId, payload.grantSecret))
        } catch (e: Exception) {
            resultFile.writeText(Json.encodeToString(PilotEnrollmentResult.serializer(), PilotEnrollmentResult(outcome = "exception:${e::class.simpleName}")))
            return
        }

        val result = when (outcome) {
            is TrustEnrollmentOutcome.Success -> PilotEnrollmentResult(
                outcome = "success", businessId = outcome.credential.businessId, deviceId = outcome.credential.deviceId,
            )
            is TrustEnrollmentOutcome.Rejected -> PilotEnrollmentResult(outcome = "rejected:${outcome.code}")
            TrustEnrollmentOutcome.TrustUnavailable -> PilotEnrollmentResult(outcome = "trust_unavailable")
            TrustEnrollmentOutcome.MalformedResponse -> PilotEnrollmentResult(outcome = "malformed_response")
            is TrustEnrollmentOutcome.Unexpected -> PilotEnrollmentResult(outcome = "unexpected")
        }
        resultFile.writeText(Json.encodeToString(PilotEnrollmentResult.serializer(), result))
    }

    companion object {
        const val ACTION_PILOT_ENROLL = "com.budcom.android.dev.debug.action.PILOT_ENROLL"
        const val PAYLOAD_FILE = "pilot_enrollment.json"
        const val RESULT_FILE = "pilot_enrollment_result.json"
    }
}

@Serializable
data class PilotEnrollmentPayload(
    val grantId: String,
    val grantSecret: String,
    val trustBaseUrl: String? = null,
    val relayBaseUrl: String? = null,
)

@Serializable
data class PilotEnrollmentResult(
    val outcome: String,
    val businessId: String? = null,
    val deviceId: String? = null,
)
