package com.jajusri.venture.core.connectorauth.data.remote

import com.jajusri.venture.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.jajusri.venture.core.connectorauth.domain.AuthenticatedConnectorContextResolution
import com.jajusri.venture.core.connectorauth.domain.AuthenticatedConnectorEndpointResolver
import com.jajusri.venture.core.connectorauth.domain.VerifiedEndpointResolution
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.core.connectorauth.domain.model.ConnectorHttpMethod
import com.jajusri.venture.core.connectorauth.domain.model.ConnectorTimeoutProfile
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVaultEndpointUpdateResult
import com.jajusri.venture.core.pairing.data.remote.PinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.connectorCertificateVerificationResult
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.SpkiFingerprintVerificationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * The dedicated, authenticated Connector business-data transport. Distinct from
 * [com.jajusri.venture.core.pairing.data.remote.SecurePairingApiPort] (bootstrap/self-service only)
 * and never falls back to the shared legacy Retrofit stack in `core/network/NetworkModule.kt` on
 * any failure — a device with an ACTIVE credential stays on this pinned-HTTPS path permanently.
 */
interface AuthenticatedConnectorApiPort {
    /**
     * Resolves a fresh [AuthenticatedConnectorContextProvider] result for this call alone and, if
     * ready, executes [operation] against it. At most one HTTP request is made per invocation.
     */
    suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private const val TIMBER_TAG = "AuthenticatedConnectorApi"

/** Bounds every response read regardless of status code — an oversized body is never buffered whole. */
internal const val MAX_RESPONSE_BODY_BYTES = 4L * 1024 * 1024

private const val SYNC_CONNECT_TIMEOUT_MS = 15_000L
private const val SYNC_READ_TIMEOUT_MS = 600_000L
private const val SYNC_WRITE_TIMEOUT_MS = 60_000L
/** 0 disables OkHttp's overall call timeout so the read timeout governs long syncs, mirroring `@SyncHttp`. */
private const val SYNC_CALL_TIMEOUT_MS = 0L

@Singleton
class OkHttpAuthenticatedConnectorApiClient @Inject constructor(
    private val contextProvider: AuthenticatedConnectorContextProvider,
    private val pinnedHttpClientFactory: PinnedHttpClientFactory,
    private val endpointResolver: AuthenticatedConnectorEndpointResolver,
    private val vault: SecureCredentialVault,
) : AuthenticatedConnectorApiPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        return when (val resolution = contextProvider.resolve()) {
            AuthenticatedConnectorContextResolution.Unpaired -> AuthenticatedConnectorResult.Unpaired
            AuthenticatedConnectorContextResolution.PendingVerification -> AuthenticatedConnectorResult.PendingVerification
            AuthenticatedConnectorContextResolution.RePairRequired -> AuthenticatedConnectorResult.RePairRequired
            AuthenticatedConnectorContextResolution.CredentialUnavailable -> AuthenticatedConnectorResult.CredentialUnavailable
            is AuthenticatedConnectorContextResolution.Ready -> executeWithRediscovery(
                resolution.context.endpoint,
                resolution.context.bearerHeaderValue(),
                resolution.context.credentialId,
                operation,
            )
        }
    }

    /**
     * TD-017: the shared rediscovery boundary every authenticated operation goes through — a
     * single bounded rediscovery cycle and at most one retry, never for a caller-visible failure
     * other than [AuthenticatedConnectorResult.TransportFailure] (a 400/401/403/etc. is a real
     * answer from the real Connector, not evidence the endpoint moved).
     */
    private suspend fun executeWithRediscovery(
        endpoint: TrustedConnectorEndpoint,
        bearerHeaderValue: String,
        credentialId: String,
        operation: AuthenticatedConnectorOperation,
    ): AuthenticatedConnectorResult {
        val firstAttempt = executeOnce(endpoint, bearerHeaderValue, credentialId, operation)
        if (firstAttempt != AuthenticatedConnectorResult.TransportFailure &&
            firstAttempt != AuthenticatedConnectorResult.IdentityMismatch &&
            firstAttempt != AuthenticatedConnectorResult.CertificateInvalid
        ) {
            return firstAttempt
        }

        val verified = when (val resolution = endpointResolver.resolveVerifiedEndpoint(endpoint)) {
            is VerifiedEndpointResolution.Verified -> resolution.endpoint
            VerifiedEndpointResolution.Unavailable -> return firstAttempt
            VerifiedEndpointResolution.IdentityMismatch -> return AuthenticatedConnectorResult.IdentityMismatch
            VerifiedEndpointResolution.CertificateInvalid -> return AuthenticatedConnectorResult.CertificateInvalid
        }

        // Best-effort persistence: this operation's retry proceeds against the just-verified
        // endpoint regardless of whether the write below succeeds — the endpoint was already
        // cryptographically verified moments ago for this exact operation. A write failure only
        // means the *next* process/request won't yet benefit from it, never that this retry is
        // treated as untrusted (see SecureCredentialVault.updateVerifiedEndpoint's doc comment).
        val updateResult = runCatching {
            vault.updateVerifiedEndpoint(verified.connectorId, verified.host, verified.securePort)
        }.getOrElse { error ->
            Timber.tag(TIMBER_TAG).w(error, "verified endpoint persistence threw; retrying in-memory only")
            null
        }
        if (updateResult != null && updateResult !is SecureCredentialVaultEndpointUpdateResult.Updated) {
            Timber.tag(TIMBER_TAG).w("verified endpoint not persisted: %s", updateResult)
        }

        return executeOnce(verified, bearerHeaderValue, credentialId, operation)
    }

    private suspend fun executeOnce(
        endpoint: TrustedConnectorEndpoint,
        bearerHeaderValue: String,
        credentialId: String,
        operation: AuthenticatedConnectorOperation,
    ): AuthenticatedConnectorResult {
        val request = buildRequest(endpoint, bearerHeaderValue, operation)
        val client = applyTimeoutProfile(pinnedHttpClientFactory.create(endpoint), operation.timeoutProfile)
        return executeRequest(client, request, credentialId, operation)
    }

    private fun buildRequest(endpoint: TrustedConnectorEndpoint, bearerHeaderValue: String, operation: AuthenticatedConnectorOperation): Request {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host(endpoint.host)
            .port(endpoint.securePort)
        operation.pathSegments.forEach { urlBuilder.addPathSegment(it) }
        operation.queryParams.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }

        val requestBuilder = Request.Builder().url(urlBuilder.build())
        if (operation.requiresCredential) {
            // Never logged: NetworkDiagnosticsInterceptor/HttpLoggingInterceptor belong only to the
            // separate shared OkHttpClient in core/network/NetworkModule.kt, never attached here.
            requestBuilder.header("Authorization", bearerHeaderValue)
        }

        when (operation.method) {
            ConnectorHttpMethod.GET -> requestBuilder.get()
            ConnectorHttpMethod.POST -> requestBuilder.post((operation.jsonBody ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
            ConnectorHttpMethod.DELETE -> requestBuilder.delete()
        }
        return requestBuilder.build()
    }

    private fun applyTimeoutProfile(client: OkHttpClient, profile: ConnectorTimeoutProfile): OkHttpClient =
        applyConnectorTimeoutProfile(client, profile)

    /**
     * Runs the blocking OkHttp call **and** the response-body read on [Dispatchers.IO] via
     * [runInterruptible], so cancelling the calling coroutine interrupts the underlying socket
     * wait rather than leaking it. The entire [Response] lifetime — [Call.execute], [mapResponse],
     * and [readBoundedBody]'s body consumption — must stay inside this single [runInterruptible]
     * block: [mapResponse] returns only a fully-materialized [AuthenticatedConnectorResult] (plain
     * String/Int/Boolean fields, never a live [Response]/[ResponseBody]/[okio.Source]), so nothing
     * network-backed escapes the IO boundary. Splitting body consumption out of this block was the
     * historical cause of `NetworkOnMainThreadException` crashes: [Call.execute] alone does not
     * guarantee the response body is already fully buffered, and callers of this port's [execute]
     * commonly run on `Dispatchers.Main.immediate` (e.g. `viewModelScope.launch`). A resulting
     * [CancellationException] is caught here (not rethrown) specifically to satisfy this port's
     * contract of returning a typed [AuthenticatedConnectorResult.Cancelled] value rather than
     * propagating cancellation past this boundary.
     */
    private suspend fun executeRequest(
        client: OkHttpClient,
        request: Request,
        credentialId: String,
        operation: AuthenticatedConnectorOperation,
    ): AuthenticatedConnectorResult = try {
        runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                mapResponse(response, credentialId, operation)
            }
        }
    } catch (e: CancellationException) {
        AuthenticatedConnectorResult.Cancelled
    } catch (e: IOException) {
        // runInterruptible interrupts the blocking thread on cancellation, but OkHttp's blocking
        // execute() surfaces that as a plain java.io.InterruptedIOException — a different type
        // from java.lang.InterruptedException, so runInterruptible cannot itself recognize and
        // convert it into a CancellationException. Disambiguate explicitly: if this coroutine's
        // own Job is no longer active, the IOException was caused by our own cancellation:
        if (!currentCoroutineContext().isActive) {
            AuthenticatedConnectorResult.Cancelled
        } else {
            // Covers TLS/pin/hostname failures (SSLPeerUnverifiedException/CertificateException
            // from the pinned trust manager), socket errors, and timeouts — a security failure
            // never falls back to an unpinned retry, it surfaces here as a transport failure.
            // TD-017: classified only for an internal diagnostic log line — never carried in the
            // returned result (see the sealed AuthenticatedConnectorResult.TransportFailure
            // doc comment: no message/cause field exists, deliberately). A fingerprint-mismatch
            // classification does not skip or weaken the rediscovery that may follow this
            // result — the exact same pinned-fingerprint check applies to every candidate
            // rediscovery considers, so classifying it here never masks an identity attack; it
            // only makes the log line legible instead of a generic "could not connect".
            val result = when (e.connectorCertificateVerificationResult()) {
                SpkiFingerprintVerificationResult.FingerprintMismatch -> AuthenticatedConnectorResult.IdentityMismatch
                null -> AuthenticatedConnectorResult.TransportFailure
                else -> AuthenticatedConnectorResult.CertificateInvalid
            }
            Timber.tag(TIMBER_TAG).d("transport failure classified as %s", classifyTransportFailure(e))
            result
        }
    }

    private fun classifyTransportFailure(e: IOException): String = when (e) {
        is SSLException -> "TLS_OR_FINGERPRINT_MISMATCH"
        is UnknownHostException -> "DNS_RESOLUTION_FAILURE"
        is ConnectException -> "CONNECTION_REFUSED"
        is SocketTimeoutException -> "TIMEOUT"
        else -> "OTHER_IO"
    }

    private fun mapResponse(
        response: Response,
        credentialId: String,
        operation: AuthenticatedConnectorOperation,
    ): AuthenticatedConnectorResult {
        val bodyRead = readBoundedBody(response.body)
        if (bodyRead is BoundedBodyReadResult.Oversized) {
            return AuthenticatedConnectorResult.MalformedResponse
        }
        val bodyText = (bodyRead as? BoundedBodyReadResult.Ok)?.text

        return when (response.code) {
            in 200..299 -> {
                val text = bodyText.orEmpty()
                if (text.isNotEmpty() && !isWellFormedJson(text)) {
                    AuthenticatedConnectorResult.MalformedResponse
                } else {
                    AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(text))
                }
            }
            400 -> AuthenticatedConnectorResult.ValidationFailure(
                sanitizedErrorCode(bodyText),
                isNoCompanySelected = matchesNoCompanySelected(bodyText),
            )
            401 -> AuthenticatedConnectorResult.Unauthorized(credentialId)
            403 -> AuthenticatedConnectorResult.Forbidden
            404 -> AuthenticatedConnectorResult.NotFound
            409 -> AuthenticatedConnectorResult.Conflict
            410 -> if (
                operation == AuthenticatedConnectorOperation.ValidateSession &&
                matchesSessionExpired(bodyText)
            ) {
                AuthenticatedConnectorResult.SessionExpired
            } else {
                AuthenticatedConnectorResult.ServerFailure(response.code)
            }
            429 -> AuthenticatedConnectorResult.RateLimited
            503 -> if (
                operation == AuthenticatedConnectorOperation.PublicReadiness &&
                matchesReadinessNotReady(bodyText)
            ) {
                AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(bodyText.orEmpty()))
            } else {
                AuthenticatedConnectorResult.ServerFailure(response.code)
            }
            in 500..599 -> AuthenticatedConnectorResult.ServerFailure(response.code)
            else -> AuthenticatedConnectorResult.ServerFailure(response.code)
        }
    }

    private fun isWellFormedJson(text: String): Boolean = runCatching { json.parseToJsonElement(text) }.isSuccess

    /** Exact allowlist match only; no Connector reason/message text crosses this boundary. */
    private fun matchesSessionExpired(bodyText: String?): Boolean {
        if (bodyText.isNullOrBlank()) return false
        val element = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return false
        return (element as? JsonObject)
            ?.get("status")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull == "SESSION_EXPIRED"
    }

    /** `/ready` legitimately returns 503 with this exact bounded status body when not ready. */
    private fun matchesReadinessNotReady(bodyText: String?): Boolean {
        if (bodyText.isNullOrBlank()) return false
        val element = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return false
        return (element as? JsonObject)
            ?.get("status")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull == "not_ready"
    }

    /** Extracts only the bounded `code` field — never the full body, which may carry internal detail. */
    private fun sanitizedErrorCode(bodyText: String?): String? {
        if (bodyText.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val code = (obj["code"] as? JsonPrimitive)?.contentOrNull ?: return null
        return code.take(MAX_SANITIZED_CODE_LENGTH)
    }

    /**
     * True only if [bodyText] is well-formed JSON whose `status` field is exactly the literal
     * `"NO_COMPANY_SELECTED"` (TD-013) — an allowlist check against one known Connector session
     * value, never a generic extraction of the `status` field's contents.
     */
    private fun matchesNoCompanySelected(bodyText: String?): Boolean {
        if (bodyText.isNullOrBlank()) return false
        val element = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return false
        val obj = element as? JsonObject ?: return false
        return (obj["status"] as? JsonPrimitive)?.contentOrNull == "NO_COMPANY_SELECTED"
    }

    private fun readBoundedBody(body: ResponseBody?): BoundedBodyReadResult {
        if (body == null) return BoundedBodyReadResult.Ok("")
        return body.use {
            val source = it.source()
            source.request(MAX_RESPONSE_BODY_BYTES + 1)
            if (source.buffer.size > MAX_RESPONSE_BODY_BYTES) {
                BoundedBodyReadResult.Oversized
            } else {
                BoundedBodyReadResult.Ok(source.buffer.readUtf8())
            }
        }
    }

    private sealed class BoundedBodyReadResult {
        data class Ok(val text: String) : BoundedBodyReadResult()
        data object Oversized : BoundedBodyReadResult()
    }

    private companion object {
        const val MAX_SANITIZED_CODE_LENGTH = 64
    }
}

/**
 * Derives a bounded-timeout client for [profile] via [OkHttpClient.newBuilder], which copies every
 * other existing setting unchanged — the pinned [javax.net.ssl.SSLSocketFactory]/`X509TrustManager`,
 * the exact-host `HostnameVerifier`, the TLS-only `ConnectionSpec`, and the disabled
 * redirect/retry flags all carry over untouched; only the four timeout fields are ever overridden.
 * Exposed at file scope (not a private class member) so a JVM test can assert this preservation
 * directly against a real [PinnedHttpClientFactory]-produced client without needing to drive a
 * full request.
 */
internal fun applyConnectorTimeoutProfile(client: OkHttpClient, profile: ConnectorTimeoutProfile): OkHttpClient = when (profile) {
    ConnectorTimeoutProfile.STANDARD -> client
    ConnectorTimeoutProfile.SYNC -> client.newBuilder()
        .connectTimeout(SYNC_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SYNC_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(SYNC_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(SYNC_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
}
