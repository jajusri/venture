package com.budcom.android.core.connectorauth.data.remote

import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextResolution
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.connectorauth.domain.model.ConnectorHttpMethod
import com.budcom.android.core.connectorauth.domain.model.ConnectorTimeoutProfile
import com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dedicated, authenticated Connector business-data transport. Distinct from
 * [com.budcom.android.core.pairing.data.remote.SecurePairingApiPort] (bootstrap/self-service only)
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
) : AuthenticatedConnectorApiPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        return when (val resolution = contextProvider.resolve()) {
            AuthenticatedConnectorContextResolution.Unpaired -> AuthenticatedConnectorResult.Unpaired
            AuthenticatedConnectorContextResolution.PendingVerification -> AuthenticatedConnectorResult.PendingVerification
            AuthenticatedConnectorContextResolution.RePairRequired -> AuthenticatedConnectorResult.RePairRequired
            AuthenticatedConnectorContextResolution.CredentialUnavailable -> AuthenticatedConnectorResult.CredentialUnavailable
            is AuthenticatedConnectorContextResolution.Ready -> {
                val request = buildRequest(resolution.context.endpoint, resolution.context.bearerHeaderValue(), operation)
                val client = applyTimeoutProfile(pinnedHttpClientFactory.create(resolution.context.endpoint), operation.timeoutProfile)
                executeRequest(client, request, resolution.context.credentialId)
            }
        }
    }

    private fun buildRequest(endpoint: TrustedConnectorEndpoint, bearerHeaderValue: String, operation: AuthenticatedConnectorOperation): Request {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host(endpoint.host)
            .port(endpoint.securePort)
        operation.pathSegments.forEach { urlBuilder.addPathSegment(it) }
        operation.queryParams.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            // Never logged: NetworkDiagnosticsInterceptor/HttpLoggingInterceptor belong only to the
            // separate shared OkHttpClient in core/network/NetworkModule.kt, never attached here.
            .header("Authorization", bearerHeaderValue)

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
     * Runs the blocking OkHttp call on [Dispatchers.IO] via [runInterruptible], so cancelling the
     * calling coroutine interrupts the underlying socket wait rather than leaking it. A resulting
     * [CancellationException] is caught here (not rethrown) specifically to satisfy this port's
     * contract of returning a typed [AuthenticatedConnectorResult.Cancelled] value rather than
     * propagating cancellation past this boundary.
     */
    private suspend fun executeRequest(client: OkHttpClient, request: Request, credentialId: String): AuthenticatedConnectorResult = try {
        runInterruptible(Dispatchers.IO) { client.newCall(request).execute() }.use { response -> mapResponse(response, credentialId) }
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
            AuthenticatedConnectorResult.TransportFailure
        }
    }

    private fun mapResponse(response: Response, credentialId: String): AuthenticatedConnectorResult {
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
            400 -> AuthenticatedConnectorResult.ValidationFailure(sanitizedErrorCode(bodyText))
            401 -> AuthenticatedConnectorResult.Unauthorized(credentialId)
            403 -> AuthenticatedConnectorResult.Forbidden
            404 -> AuthenticatedConnectorResult.NotFound
            409 -> AuthenticatedConnectorResult.Conflict
            429 -> AuthenticatedConnectorResult.RateLimited
            in 500..599 -> AuthenticatedConnectorResult.ServerFailure(response.code)
            else -> AuthenticatedConnectorResult.ServerFailure(response.code)
        }
    }

    private fun isWellFormedJson(text: String): Boolean = runCatching { json.parseToJsonElement(text) }.isSuccess

    /** Extracts only the bounded `code` field — never the full body, which may carry internal detail. */
    private fun sanitizedErrorCode(bodyText: String?): String? {
        if (bodyText.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val code = (obj["code"] as? JsonPrimitive)?.contentOrNull ?: return null
        return code.take(MAX_SANITIZED_CODE_LENGTH)
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
