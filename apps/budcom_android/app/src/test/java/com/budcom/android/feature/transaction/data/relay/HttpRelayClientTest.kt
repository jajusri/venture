package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.AuthenticatedTransportEnvelope
import com.budcom.android.feature.transaction.domain.port.ConfiguredRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.EmptyRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.EnvelopeSubmission
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.TransportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class HttpRelayClientTest {
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
            assertEquals(server.takeRequest().getHeader("Idempotency-Key"), "order:order-1:v1")
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
            val transport = HttpRelayStructuredTransport(testClient(server), { authenticated() }, dispatchers)
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
                """{"recipientBusinessId":"co-1","mailboxId":"orders","nextCursor":null,"items":[{"envelopeId":"envelope-1","mailboxSequence":1,"objectType":"CANONICAL_ORDER","objectId":"order-1","objectVersion":1,"senderBusinessId":"co-sender","senderActorId":"actor-s","senderDeviceId":"device-s","status":"relay_accepted","acceptedAt":"1970-01-01T00:00:00.010Z","acceptanceId":"accept-1","authenticatedEnvelope":"AQI="}]}""",
            ).setResponseCode(200),
        )
        server.start()
        try {
            val page = testClient(server).fetchMailbox("co-1", "actor-b", "device-b", "orders", null)
            assertEquals(1, page?.items?.size)
            assertEquals("relay_accepted", page?.items?.single()?.status)
            assertEquals("/v1/relay/mailboxes/fetch", server.takeRequest().path)
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

    private fun authenticated() = AuthenticatedTransportEnvelope(
        envelope = EnvelopeSubmission.fromEnvelope(envelope(), "device-1", "buyer-1"),
        senderActorId = "actor-1",
        deviceKeyId = "key-1",
        deviceKeyVersion = 1,
        deviceFingerprint = "fp",
        credentialId = "cred-1",
        credentialVersion = 1,
        credentialEpoch = 1,
        recipient = RecipientBinding("buyer-1", "buyer-1", "orders"),
        signatureAlgorithm = "SHA256withECDSA",
        signature = byteArrayOf(7),
    )

    private fun acceptedJson() =
        """{"status":"relay_accepted","acceptanceId":"accept-1","envelopeId":"envelope-1","objectType":"CANONICAL_ORDER","objectId":"order-1","objectVersion":1,"senderBusinessId":"co-1","recipientBusinessId":"buyer-1","acceptedAt":"1970-01-01T00:00:00.010Z"}"""
}
