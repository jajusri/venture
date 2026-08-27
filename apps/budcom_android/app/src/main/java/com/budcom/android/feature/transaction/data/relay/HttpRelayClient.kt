package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence
import com.budcom.android.feature.transaction.domain.port.AuthenticatedTransportEnvelope
import com.budcom.android.feature.transaction.domain.port.RelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.StructuredBusinessTransport
import com.budcom.android.feature.transaction.domain.port.StructuredBusinessTransportRouter
import com.budcom.android.feature.transaction.domain.port.TransportEvidence
import com.budcom.android.feature.transaction.domain.port.TransportResult
import com.budcom.android.feature.transaction.domain.port.TransportRouterResult
import com.budcom.android.feature.transaction.domain.port.RelayMailboxDeliveryItem
import com.budcom.android.feature.transaction.domain.port.RelayMailboxPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Serializable
internal data class RelaySubmissionJson(
    val protocolVersion: Int,
    val envelopeId: String,
    val idempotencyKey: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderActorId: String,
    val senderDeviceId: String,
    val recipientBusinessId: String,
    val mailboxId: String,
    val authenticatedEnvelope: String,
    val submittedAt: String,
)

@Serializable
internal data class RelayAcceptanceJson(
    val status: String,
    val acceptanceId: String,
    val envelopeId: String,
    val objectType: String? = null,
    val objectId: String? = null,
    val objectVersion: Int? = null,
    val senderBusinessId: String? = null,
    val recipientBusinessId: String? = null,
    val acceptedAt: String? = null,
)

@Serializable
internal data class RelayMailboxFetchJson(
    val recipientBusinessId: String,
    val mailboxId: String,
    val recipientActorId: String,
    val recipientDeviceId: String,
    val cursor: String? = null,
    val limit: Int = 25,
)

@Serializable
internal data class RelayMailboxItemJson(
    val envelopeId: String,
    val mailboxSequence: Long,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderActorId: String,
    val senderDeviceId: String,
    val status: String,
    val acceptedAt: String,
    val acceptanceId: String,
    val authenticatedEnvelope: String,
)

@Serializable
internal data class RelayMailboxPageJson(
    val recipientBusinessId: String,
    val mailboxId: String,
    val nextCursor: String? = null,
    val items: List<RelayMailboxItemJson> = emptyList(),
)

class HttpRelayClient(
    private val endpoint: RelayEndpointProvider,
    private val retryPolicy: RetryPolicy = RelayRetryPolicy,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val random: Random = Random.Default,
    httpClient: OkHttpClient? = null,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun submit(authenticated: AuthenticatedTransportEnvelope): TransportResult {
        val baseUrl = endpoint.snapshot() ?: return TransportResult.TemporarilyUnavailable("relay endpoint unconfigured")
        val mailbox = authenticated.recipient.mailboxReference
            ?: return TransportResult.PermanentRejection("recipient mailbox is required")
        val recipientBusiness = authenticated.recipient.businessId
            ?: return TransportResult.PermanentRejection("recipient business is required")
        val payload = json.encodeToString(
            RelaySubmissionJson.serializer(),
            RelaySubmissionJson(
                protocolVersion = authenticated.envelope.contractVersion,
                envelopeId = authenticated.envelope.envelopeId,
                idempotencyKey = authenticated.envelope.idempotencyKey,
                objectType = authenticated.envelope.objectType,
                objectId = authenticated.envelope.objectId,
                objectVersion = authenticated.envelope.objectVersion,
                senderBusinessId = authenticated.envelope.senderBusinessId,
                senderActorId = authenticated.senderActorId,
                senderDeviceId = authenticated.envelope.senderDeviceId,
                recipientBusinessId = recipientBusiness,
                mailboxId = mailbox,
                authenticatedEnvelope = Base64.getEncoder().encodeToString(authenticated.signingBytes() + authenticated.signature),
                submittedAt = java.time.Instant.ofEpochMilli(authenticated.envelope.createdAtEpochMillis).toString(),
            ),
        )
        var attempt = 0
        var last: TransportResult = TransportResult.RetryableFailure("relay unavailable")
        while (true) {
            val outcome = postOnce(baseUrl, authenticated.envelope.envelopeId, authenticated.envelope.idempotencyKey, payload)
            last = outcome.result
            if (last is TransportResult.Accepted || last is TransportResult.PermanentRejection) return last
            if (last is TransportResult.Delivered) return TransportResult.PermanentRejection("relay must not claim delivery")
            val waitMs = outcome.retryAfterMs ?: retryPolicy.delayBeforeRetryMillis(attempt, random)
            if (waitMs == null) return last
            delay(waitMs)
            attempt += 1
        }
    }

    suspend fun fetchMailbox(
        recipientBusinessId: String,
        recipientActorId: String,
        recipientDeviceId: String,
        mailboxId: String,
        cursor: String?,
        limit: Int = 25,
    ): RelayMailboxPage? {
        val baseUrl = endpoint.snapshot() ?: return null
        val payload = json.encodeToString(
            RelayMailboxFetchJson.serializer(),
            RelayMailboxFetchJson(recipientBusinessId, mailboxId, recipientActorId, recipientDeviceId, cursor, limit),
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + MAILBOX_FETCH_PATH)
            .post(payload.toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val parsed = json.decodeFromString(RelayMailboxPageJson.serializer(), response.body?.string().orEmpty())
                RelayMailboxPage(
                    items = parsed.items.map { item ->
                        RelayMailboxDeliveryItem(
                            envelopeId = item.envelopeId,
                            mailboxSequence = item.mailboxSequence,
                            objectType = item.objectType,
                            objectId = item.objectId,
                            objectVersion = item.objectVersion,
                            senderBusinessId = item.senderBusinessId,
                            senderActorId = item.senderActorId,
                            senderDeviceId = item.senderDeviceId,
                            recipientBusinessId = parsed.recipientBusinessId,
                            mailboxId = parsed.mailboxId,
                            status = item.status,
                            acceptedAtEpochMillis = runCatching { java.time.Instant.parse(item.acceptedAt).toEpochMilli() }.getOrDefault(nowMillis()),
                            acceptanceId = item.acceptanceId,
                            authenticatedEnvelope = Base64.getDecoder().decode(item.authenticatedEnvelope),
                        )
                    },
                    nextCursor = parsed.nextCursor,
                )
            }
        } catch (_: IOException) {
            null
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun postOnce(baseUrl: String, envelopeId: String, idempotencyKey: String, payload: String): Attempt {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + SUBMIT_PATH)
            .header("Idempotency-Key", idempotencyKey)
            .post(payload.toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                when {
                    response.isSuccessful -> parseAccepted(envelopeId, response.body?.string().orEmpty())
                    response.code in PERMANENT_HTTP -> Attempt(TransportResult.PermanentRejection("relay rejected submission"))
                    else -> Attempt(TransportResult.RetryableFailure("relay temporarily unavailable"), retryAfterMs)
                }
            }
        } catch (_: SocketTimeoutException) {
            Attempt(TransportResult.RetryableFailure("relay timeout"))
        } catch (_: IOException) {
            Attempt(TransportResult.RetryableFailure("relay unreachable"))
        }
    }

    private fun parseAccepted(envelopeId: String, body: String): Attempt {
        val parsed = runCatching { json.decodeFromString(RelayAcceptanceJson.serializer(), body) }.getOrNull()
            ?: return Attempt(TransportResult.RetryableFailure("relay acceptance unreadable"))
        if (parsed.status != "relay_accepted" || parsed.envelopeId != envelopeId) {
            return Attempt(TransportResult.PermanentRejection("relay acceptance mismatched"))
        }
        val acceptedAt = parsed.acceptedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: nowMillis()
        return Attempt(
            TransportResult.Accepted(
                TransportEvidence(parsed.envelopeId, acceptedAt, parsed.acceptanceId),
                relayAcceptance = if (
                    parsed.objectType != null && parsed.objectId != null && parsed.objectVersion != null &&
                    parsed.senderBusinessId != null && parsed.recipientBusinessId != null
                ) {
                    RelayAcceptanceEvidence(
                        acceptanceId = parsed.acceptanceId,
                        envelopeId = parsed.envelopeId,
                        objectType = parsed.objectType,
                        objectId = parsed.objectId,
                        objectVersion = parsed.objectVersion,
                        senderBusinessId = parsed.senderBusinessId,
                        recipientBusinessId = parsed.recipientBusinessId,
                        acceptedAtEpochMillis = acceptedAt,
                        status = parsed.status,
                    )
                } else {
                    null
                },
            ),
        )
    }

    private data class Attempt(val result: TransportResult, val retryAfterMs: Long? = null)

    companion object {
        const val SUBMIT_PATH = "/v1/relay/envelopes"
        const val MAILBOX_FETCH_PATH = "/v1/relay/mailboxes/fetch"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 15L
        const val WRITE_TIMEOUT_SECONDS = 15L
        const val CALL_TIMEOUT_SECONDS = 20L
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val PERMANENT_HTTP = setOf(400, 401, 403, 404, 409, 410, 413, 422)
        val RelayRetryPolicy = RetryPolicy(maxAttempts = 4, initialDelayMillis = 400, maxDelayMillis = 8_000, jitterRatio = 0.2)
        fun parseRetryAfterMs(raw: String?): Long? {
            val seconds = raw?.trim()?.toLongOrNull() ?: return null
            if (seconds < 0 || seconds > 120) return null
            return seconds * 1_000
        }
    }
}

@Singleton
class HttpRelayStructuredTransport @Inject constructor(
    private val client: HttpRelayClient,
    private val authenticator: RelayEnvelopeAuthenticator,
    private val dispatchers: DispatcherProvider,
) : StructuredBusinessTransport {
    override suspend fun submit(envelope: OrderDeliveryEnvelope): TransportResult = withContext(dispatchers.io) {
        val authenticated = authenticator.authenticate(envelope)
            ?: return@withContext TransportResult.TemporarilyUnavailable("authenticated envelope unavailable")
        client.submit(authenticated)
    }
}

@Singleton
class RelayAwareTransportRouter @Inject constructor(
    private val endpoint: RelayEndpointProvider,
    private val transport: StructuredBusinessTransport,
) {
    private val router = StructuredBusinessTransportRouter(listOf(transport))
    suspend fun submit(envelope: OrderDeliveryEnvelope): TransportRouterResult {
        if (endpoint.snapshot() == null) return TransportRouterResult.NoAvailableTransport
        return router.submit(envelope)
    }
}
