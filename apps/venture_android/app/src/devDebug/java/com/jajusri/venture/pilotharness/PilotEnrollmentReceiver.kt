package com.jajusri.venture.pilotharness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jajusri.venture.core.relay.data.local.RelayEndpointLocalStore
import com.jajusri.venture.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.jajusri.venture.core.trust.data.TrustEnrollmentRepository
import com.jajusri.venture.core.trust.data.local.TrustEndpointLocalStore
import com.jajusri.venture.core.trust.data.remote.TrustEndpointProvider
import com.jajusri.venture.core.trust.domain.EnrollmentGrantProof
import com.jajusri.venture.core.trust.domain.TrustEnrollmentOutcome
import com.jajusri.venture.feature.serverconfig.domain.validation.ConnectorUrlValidator
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
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
 * set's own AndroidManifest.xml doc comment). Lets `scripts/venture-controlled-pilot.ps1` trigger one
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
 *     this process immediately, with no restart. If `rotateDeviceKey` is set, rotate this
 *     installation's own device key ([VartalapDeviceKeyStore.rotate]) BEFORE enrolling -- the same
 *     production-designed recovery a real device uses when its existing registration row no longer
 *     matches the membership's current authority epoch (e.g. after a scope grant); never a fabricated
 *     or bypassed credential, and never touches Business/device state beyond this one installation's
 *     own key.
 *  4. Call the REAL, unmodified [TrustEnrollmentRepository.enroll] -- the exact same production
 *     enrollment path any real device uses: Android Keystore generates/loads this installation's own
 *     device key pair, only the public half + fingerprint ever leaves the device, and Trust's real
 *     `POST /v1/trust/enrollment/consume` endpoint validates and consumes the grant server-side. This
 *     receiver never constructs a credential itself and never bypasses that call.
 *  5. Write only NON-SECRET outcome fields (outcome tag, businessId, deviceId on success) to
 *     [RESULT_FILE] for the orchestration script to poll and read back via `run-as ... cat`. The
 *     grant secret is never written to this file and never logged (no `Log.*`/`println` of either
 *     payload field anywhere in this class). `exported="true"` (see this source set's own
 *     AndroidManifest.xml doc comment for why) does not widen the attack surface: there is no
 *     intent-filter (not discoverable via implicit broadcast), the Intent carries no extras/secret
 *     material, and the only action any sender can trigger is "re-read whatever is already sitting
 *     in this app's own sandboxed private storage" -- which no other app can have written.
 */
@AndroidEntryPoint
class PilotEnrollmentReceiver : BroadcastReceiver() {

    @Inject lateinit var trustEndpointLocalStore: TrustEndpointLocalStore
    @Inject lateinit var trustEndpointProvider: TrustEndpointProvider
    @Inject lateinit var relayEndpointLocalStore: RelayEndpointLocalStore
    @Inject lateinit var relayEndpointProvider: RelayRuntimeEndpointProvider
    @Inject lateinit var enrollmentRepository: TrustEnrollmentRepository
    @Inject lateinit var keyStore: VartalapDeviceKeyStore

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

        // Opt-in only: a re-enrollment attempt that reuses the SAME device key version against a
        // membership whose authorityEpoch has since moved (e.g. a scope grant) is correctly rejected
        // by Trust's own RegisterBusinessDevice ("Conflicting device key registration" -- an existing
        // registration row is epoch-scoped, not a re-enrollment worth silently overwriting). rotate()
        // is the same production-designed recovery a real device would use: a genuinely NEW key
        // version under the SAME logical deviceId, never a fabricated/bypassed credential.
        if (payload.rotateDeviceKey) {
            keyStore.getCurrentIdentity()?.let { keyStore.rotate(it.deviceId) }
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
        const val ACTION_PILOT_ENROLL = "com.jajusri.venture.dev.debug.action.PILOT_ENROLL"
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
    val rotateDeviceKey: Boolean = false,
)

@Serializable
data class PilotEnrollmentResult(
    val outcome: String,
    val businessId: String? = null,
    val deviceId: String? = null,
)
