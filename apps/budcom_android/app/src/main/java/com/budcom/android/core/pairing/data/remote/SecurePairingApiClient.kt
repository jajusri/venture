package com.budcom.android.core.pairing.data.remote

import com.budcom.android.core.pairing.domain.model.PairingDeviceIdentity
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayload
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class PairingRedeemOutcome {
    data class Success(val connectorId: String, val connectorName: String, val credentialId: String, val token: String) : PairingRedeemOutcome() {
        override fun toString(): String = "PairingRedeemOutcome.Success(connectorId=$connectorId, credentialId=$credentialId, token=<redacted>)"
    }
    data class Rejected(val reasonCode: String?, val httpStatus: Int) : PairingRedeemOutcome()
    data object TransportFailure : PairingRedeemOutcome()
    data object MalformedResponse : PairingRedeemOutcome()
}

sealed class PairingSelfStatusOutcome {
    data class Active(
        val credentialId: String,
        val deviceId: String?,
        val deviceLabel: String?,
        val connectorId: String,
        val createdAt: String,
        val lastUsedAt: String?,
        val status: String,
    ) : PairingSelfStatusOutcome()
    data object Unauthorized : PairingSelfStatusOutcome()
    data object TransportFailure : PairingSelfStatusOutcome()
    data object MalformedResponse : PairingSelfStatusOutcome()
}

sealed class PairingSelfRevokeOutcome {
    data object Revoked : PairingSelfRevokeOutcome()
    data object Unauthorized : PairingSelfRevokeOutcome()
    data object TransportFailure : PairingSelfRevokeOutcome()
    data object MalformedResponse : PairingSelfRevokeOutcome()
}

/**
 * The narrow, Android-facing pairing API — exactly the four operations this phase needs, over
 * the dedicated pinned client from [PinnedHttpClientFactory]. No business API (companies,
 * ledgers, stock, vouchers, sync) is reachable through this client at all.
 */
interface SecurePairingApiPort {
    /** Redeems a QR-path payload. Sends `deviceIdentity`'s id/label; never a business identifier. */
    suspend fun redeemQr(payload: SecurePairingQrPayload, deviceIdentity: PairingDeviceIdentity): PairingRedeemOutcome

    /**
     * Redeems a manually-entered short code. [endpoint] must already be established (from a
     * prior QR pairing or a persisted trust record) — a short code alone never locates or trusts
     * a Connector; it only ever identifies a session at an endpoint the caller already trusts.
     */
    suspend fun redeemShortCode(shortCode: String, endpoint: TrustedConnectorEndpoint, deviceIdentity: PairingDeviceIdentity): PairingRedeemOutcome

    /** `GET /device/pairing-credential/self` — canonical `Authorization: Bearer {credential}` only. */
    suspend fun getCredentialSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfStatusOutcome

    /** `POST /device/pairing-credential/self/revoke` — revokes only the credential presented. */
    suspend fun revokeSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfRevokeOutcome
}

@Serializable
private data class RedeemSuccessDto(val connectorId: String, val connectorName: String, val credentialId: String, val token: String)

@Serializable
private data class RedeemErrorDto(val code: String? = null, val message: String? = null)

@Serializable
private data class SelfStatusDto(
    val credentialId: String,
    val deviceId: String? = null,
    val deviceLabel: String? = null,
    val connectorId: String,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val status: String,
)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Singleton
class OkHttpSecurePairingApiClient @Inject constructor(
    private val pinnedHttpClientFactory: PinnedHttpClientFactory,
) : SecurePairingApiPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun redeemQr(payload: SecurePairingQrPayload, deviceIdentity: PairingDeviceIdentity): PairingRedeemOutcome =
        withContext(Dispatchers.IO) {
            val endpoint = TrustedConnectorEndpoint.fromValidatedQrPayload(payload)
            val client = pinnedHttpClientFactory.create(endpoint)
            val body = buildJsonObject {
                put("schemaVersion", payload.schemaVersion)
                put("pairingSessionId", payload.pairingSessionId)
                put("secret", payload.secret)
                put("connectorId", payload.connectorId)
                put("host", payload.host)
                put("port", payload.port)
                put("deviceId", deviceIdentity.logicalDeviceId)
                put("deviceLabel", deviceIdentity.deviceLabel)
            }.toString()
            executeRedeem(client, endpoint, body)
        }

    override suspend fun redeemShortCode(
        shortCode: String,
        endpoint: TrustedConnectorEndpoint,
        deviceIdentity: PairingDeviceIdentity,
    ): PairingRedeemOutcome = withContext(Dispatchers.IO) {
        val client = pinnedHttpClientFactory.create(endpoint)
        val body = buildJsonObject {
            put("shortCode", shortCode)
            put("deviceId", deviceIdentity.logicalDeviceId)
            put("deviceLabel", deviceIdentity.deviceLabel)
        }.toString()
        executeRedeem(client, endpoint, body)
    }

    private fun executeRedeem(client: OkHttpClient, endpoint: TrustedConnectorEndpoint, bodyJson: String): PairingRedeemOutcome {
        val request = Request.Builder()
            .url(pairingUrl(endpoint, "/device/pairing-session/redeem"))
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.code == 201) {
                    val dto = runCatching { json.decodeFromString(RedeemSuccessDto.serializer(), responseBody) }.getOrNull()
                        ?: return@use PairingRedeemOutcome.MalformedResponse
                    PairingRedeemOutcome.Success(dto.connectorId, dto.connectorName, dto.credentialId, dto.token)
                } else {
                    val errorDto = runCatching { json.decodeFromString(RedeemErrorDto.serializer(), responseBody) }.getOrNull()
                    PairingRedeemOutcome.Rejected(errorDto?.code, response.code)
                }
            }
        } catch (e: IOException) {
            // Includes SSLPeerUnverifiedException/CertificateException thrown by the pinned
            // trust manager on a fingerprint mismatch — a security failure never falls back to
            // an unpinned retry, it just surfaces here as an ordinary transport failure.
            PairingRedeemOutcome.TransportFailure
        }
    }

    override suspend fun getCredentialSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfStatusOutcome =
        withContext(Dispatchers.IO) {
            val client = pinnedHttpClientFactory.create(endpoint)
            val request = Request.Builder()
                .url(pairingUrl(endpoint, "/device/pairing-credential/self"))
                .header("Authorization", "Bearer $bearerCredential")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    when (response.code) {
                        200 -> {
                            val dto = runCatching { json.decodeFromString(SelfStatusDto.serializer(), responseBody) }.getOrNull()
                                ?: return@use PairingSelfStatusOutcome.MalformedResponse
                            PairingSelfStatusOutcome.Active(
                                credentialId = dto.credentialId,
                                deviceId = dto.deviceId,
                                deviceLabel = dto.deviceLabel,
                                connectorId = dto.connectorId,
                                createdAt = dto.createdAt,
                                lastUsedAt = dto.lastUsedAt,
                                status = dto.status,
                            )
                        }
                        401 -> PairingSelfStatusOutcome.Unauthorized
                        else -> PairingSelfStatusOutcome.MalformedResponse
                    }
                }
            } catch (e: IOException) {
                PairingSelfStatusOutcome.TransportFailure
            }
        }

    override suspend fun revokeSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfRevokeOutcome =
        withContext(Dispatchers.IO) {
            val client = pinnedHttpClientFactory.create(endpoint)
            val request = Request.Builder()
                .url(pairingUrl(endpoint, "/device/pairing-credential/self/revoke"))
                .header("Authorization", "Bearer $bearerCredential")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> PairingSelfRevokeOutcome.Revoked
                        401 -> PairingSelfRevokeOutcome.Unauthorized
                        else -> PairingSelfRevokeOutcome.MalformedResponse
                    }
                }
            } catch (e: IOException) {
                PairingSelfRevokeOutcome.TransportFailure
            }
        }

    private fun pairingUrl(endpoint: TrustedConnectorEndpoint, path: String): String =
        "https://${endpoint.host}:${endpoint.securePort}$path"
}
