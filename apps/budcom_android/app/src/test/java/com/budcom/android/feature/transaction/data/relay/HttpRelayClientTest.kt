package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.model.CommercialReturnEvent
import com.budcom.android.feature.transaction.domain.model.COMMERCIAL_EVENT_CONTENT_TYPE
import com.budcom.android.feature.transaction.domain.port.AuthenticatedRelayRequest
import com.budcom.android.feature.transaction.domain.port.AuthenticatedTransportEnvelope
import com.budcom.android.feature.transaction.domain.port.ConfiguredRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.EmptyRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.EnvelopeSubmission
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.TransportResult
import com.budcom.android.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class HttpRelayClientTest {
    private val canonicalContent = File("../../../shared/fixtures/relay/canonical-order-snapshot-v2.json")
        .readText(Charsets.UTF_8)
        .removeSuffix("\n")
        .removeSuffix("\r")
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `unconfigured endpoint leaves local intent queued`() = runTest(dispatcher) {
        val result = HttpRelayClient(EmptyRelayEndpointProvider).submit(authenticated())
        assertTrue(result is TransportResult.TemporarilyUnavailable)
    }

    @Test
    fun `accepted response is relay_accepted evidence and retries stay idempotent`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.start()
        try {
            val client = testClient(server)
            val first = client.submit(authenticated())
            val retry = client.submit(authenticated())
            assertEquals("accept-1", (first as TransportResult.Accepted).evidence.acceptanceId)
            assertEquals("order-1", first.relayAcceptance?.objectId)
            assertEquals("buyer-1", first.relayAcceptance?.recipientBusinessId)
            assertEquals("accept-1", (retry as TransportResult.Accepted).evidence.acceptanceId)
            assertEquals("relay_accepted", retry.relayAcceptance?.status)
            assertEquals(2, server.requestCount)
            val firstRequest = server.takeRequest()
            assertEquals(firstRequest.getHeader("Idempotency-Key"), "order:order-1:v1")
            val submitted = Json.parseToJsonElement(firstRequest.body.readUtf8()).jsonObject
            assertEquals(canonicalContent, submitted.getValue("commercialContent").jsonPrimitive.content)
            assertEquals("application/vnd.budcom.order-snapshot+json", submitted.getValue("commercialContentType").jsonPrimitive.content)
            assertEquals("3", submitted.getValue("commercialContentVersion").jsonPrimitive.content)
            assertEquals(server.takeRequest().getHeader("Idempotency-Key"), "order:order-1:v1")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `temporary failure retries with jittered backoff then accepts`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.start()
        try {
            val client = testClient(server, RetryPolicy(maxAttempts = 3, initialDelayMillis = 10, jitterRatio = 0.0))
            val job = client.submit(authenticated())
            advanceUntilIdle()
            assertTrue(job is TransportResult.Accepted)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `forbidden credential is a permanent rejection`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()
        try {
            val result = testClient(server).submit(authenticated())
            assertTrue(result is TransportResult.PermanentRejection)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `retry after is bounded`() {
        assertEquals(1_000L, HttpRelayClient.parseRetryAfterMs("1"))
        assertNull(HttpRelayClient.parseRetryAfterMs("9999"))
        assertNull(HttpRelayClient.parseRetryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT"))
    }

    @Test
    fun `structured transport authenticates off the calling thread contract`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.start()
        try {
            val transport = HttpRelayStructuredTransport(testClient(server), FakeSubmitOnlyAuthenticator { authenticated() }, dispatchers)
            val result = transport.submit(envelope())
            assertTrue(result is TransportResult.Accepted)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `mailbox fetch returns bounded relay accepted items`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"recipientBusinessId":"co-1","mailboxId":"orders","nextCursor":null,"items":[{"envelopeId":"envelope-1","mailboxSequence":1,"objectType":"CANONICAL_ORDER","objectId":"order-1","objectVersion":1,"senderBusinessId":"co-sender","senderActorId":"actor-s","senderDeviceId":"device-s","status":"relay_accepted","acceptedAt":"1970-01-01T00:00:00.010Z","acceptanceId":"accept-1","authenticatedEnvelope":"AQI=","commercialContent":${JsonPrimitive(canonicalContent)},"commercialContentType":"application/vnd.budcom.order-snapshot+json","commercialContentVersion":2}]}""",
            ).setResponseCode(200),
        )
        server.start()
        try {
            val page = testClient(server).fetchMailbox(authenticatedRequest(), "co-1", "orders", null)
            assertEquals(1, page?.items?.size)
            assertEquals("relay_accepted", page?.items?.single()?.status)
            assertEquals(canonicalContent, page?.items?.single()?.commercialSnapshotCanonical)
            assertEquals("application/vnd.budcom.order-snapshot+json", page?.items?.single()?.commercialContentType)
            assertEquals(2, page?.items?.single()?.commercialContentVersion)
            val request = server.takeRequest()
            assertEquals("/v1/relay/mailboxes/fetch", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("actor-1", body.getValue("recipientActorId").jsonPrimitive.content)
            assertEquals("device-1", body.getValue("recipientDeviceId").jsonPrimitive.content)
            val wire = Json.parseToJsonElement(
                String(java.util.Base64.getDecoder().decode(body.getValue("authenticatedRequest").jsonPrimitive.content)),
            ).jsonObject
            assertEquals("req-1", wire.getValue("requestId").jsonPrimitive.content)
            assertEquals("co-1", wire.getValue("credentialClaims").jsonObject.getValue("businessId").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `acknowledgement request carries the authenticated request wire and recipient identity from the credential`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val accepted = testClient(server).acknowledgeDelivery(authenticatedRequest(), "envelope-1", "co-1", 10L)
            assertTrue(accepted)
            val request = server.takeRequest()
            assertEquals("/v1/relay/acknowledgements", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("actor-1", body.getValue("recipientActorId").jsonPrimitive.content)
            assertEquals("device-1", body.getValue("recipientDeviceId").jsonPrimitive.content)
            val wire = Json.parseToJsonElement(
                String(java.util.Base64.getDecoder().decode(body.getValue("authenticatedRequest").jsonPrimitive.content)),
            ).jsonObject
            assertEquals("req-1", wire.getValue("requestId").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `submit request wire carries credential claims, Trust signature and device signature verbatim`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.start()
        try {
            testClient(server).submit(authenticated())
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("actor-1", body.getValue("senderActorId").jsonPrimitive.content)
            val wire = Json.parseToJsonElement(
                String(java.util.Base64.getDecoder().decode(body.getValue("authenticatedEnvelope").jsonPrimitive.content)),
            ).jsonObject
            val claims = wire.getValue("credentialClaims").jsonObject
            assertEquals("co-1", claims.getValue("businessId").jsonPrimitive.content)
            assertEquals("membership-1", claims.getValue("membershipId").jsonPrimitive.content)
            assertEquals(java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), wire.getValue("credentialSignature").jsonPrimitive.content)
            assertEquals(java.util.Base64.getEncoder().encodeToString(byteArrayOf(7)), wire.getValue("deviceSignature").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }

    private fun testClient(server: MockWebServer, policy: RetryPolicy = RetryPolicy(maxAttempts = 2, initialDelayMillis = 1, jitterRatio = 0.0)) =
        HttpRelayClient(
            endpoint = ConfiguredRelayEndpointProvider(server.url("/").toString()),
            retryPolicy = policy,
            random = Random(1),
            httpClient = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build(),
        )

    private fun envelope() = OrderDeliveryEnvelope(
        companyId = "co-1", envelopeId = "envelope-1", idempotencyKey = "order:order-1:v1",
        objectType = "CANONICAL_ORDER", orderId = "order-1", orderVersion = 1,
        senderCompanyId = "co-1", recipientPartyId = "buyer-1",
        createdAt = TransactionTimestamp(10L, TransactionTimestampSource.DeviceLocalProvisional),
        state = OrderTransportState.Queued, attemptCount = 0, lastAttemptAt = null, lastError = null,
    )

    private fun credential() = TrustedBusinessDeviceCredential(
        credentialVersion = 1, credentialId = "cred-1", businessId = "co-1", actorId = "actor-1", membershipId = "membership-1",
        deviceId = "device-1", deviceKeyId = "key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fp",
        authorityScope = setOf("send_orders", "receive_orders"), authorityEpoch = 1L, issuedAtEpochMillis = 1L, notBeforeEpochMillis = 1L,
        expiresAtEpochMillis = 999_999_999_999L, issuerId = "issuer-1", issuerKeyId = "issuer-key-1", signatureProfile = "P256-SHA256-v1",
        signature = byteArrayOf(1, 2, 3),
    )

    private fun authenticated() = AuthenticatedTransportEnvelope(
        envelope = EnvelopeSubmission.fromEnvelope(envelope(), "device-1", "buyer-1"),
        recipient = RecipientBinding("buyer-1", "buyer-1", "orders"),
        credential = credential(),
        deviceSignature = byteArrayOf(7),
        commercialSnapshotCanonical = canonicalContent,
    )

    private fun authenticatedRequest() = AuthenticatedRelayRequest(
        credential = credential(),
        requestId = "req-1",
        timestampIso = "2026-08-29T00:00:00Z",
        requestSignature = byteArrayOf(8),
    )

    private fun acceptedJson() =
        """{"status":"relay_accepted","acceptanceId":"accept-1","envelopeId":"envelope-1","objectType":"CANONICAL_ORDER","objectId":"order-1","objectVersion":1,"senderBusinessId":"co-1","recipientBusinessId":"buyer-1","acceptedAt":"1970-01-01T00:00:00.010Z"}"""

    @Test
    fun `missing or unsupported authenticated commercial content fails before HTTP`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.start()
        try {
            val client = testClient(server)
            assertTrue(client.submit(authenticated().copy(commercialSnapshotCanonical = "")) is TransportResult.PermanentRejection)
            assertTrue(client.submit(authenticated().copy(commercialContentVersion = 4)) is TransportResult.PermanentRejection)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `commercial return event uses the same opaque exact content relay submission`() = runTest(dispatcher) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(acceptedJson()).setResponseCode(200))
        server.start()
        try {
            val event = CommercialReturnEvent(
                1, CommercialReturnEvent.TYPE_ORDER_CONFIRMED, "buyer-1", "co-1", "order-1", 1,
                "event-1", "confirm:key", 10, TransactionTimestampSource.DeviceLocalProvisional.name,
            ).deterministicEncoding()
            val result = testClient(server).submit(
                authenticated().copy(
                    commercialSnapshotCanonical = event,
                    commercialContentType = COMMERCIAL_EVENT_CONTENT_TYPE,
                    commercialContentVersion = 1,
                ),
            )
            assertTrue(result is TransportResult.Accepted)
            val submitted = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals(event, submitted.getValue("commercialContent").jsonPrimitive.content)
            assertEquals(COMMERCIAL_EVENT_CONTENT_TYPE, submitted.getValue("commercialContentType").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}

/** Submit-only fake: [RelayEnvelopeAuthenticator] is no longer a fun interface (it now covers
 * Fetch/Acknowledge too), so a plain SAM lambda can't stand in for it here. */
private class FakeSubmitOnlyAuthenticator(private val submitResult: () -> AuthenticatedTransportEnvelope?) : RelayEnvelopeAuthenticator {
    override suspend fun authenticate(envelope: OrderDeliveryEnvelope) = submitResult()
    override suspend fun authenticateMailboxFetch(businessId: String, mailboxId: String, cursor: String?, limit: Int): AuthenticatedRelayRequest? =
        throw UnsupportedOperationException("not used by this test")
    override suspend fun authenticateAcknowledgement(businessId: String, envelopeId: String, receivedAtEpochMillis: Long): AuthenticatedRelayRequest? =
        throw UnsupportedOperationException("not used by this test")
}
