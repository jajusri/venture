package com.jajusri.venture.core.pairing.data.remote

import com.jajusri.venture.core.security.DefaultSpkiFingerprintVerifier
import com.jajusri.venture.core.util.TimeProvider
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSessionContext

class PinnedHttpClientFactoryTest {

    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
    }

    private fun newServer(): Pair<MockWebServer, okhttp3.tls.HeldCertificate> {
        val heldCertificate = testHeldCertificate()
        val server = startTlsMockWebServer(heldCertificate)
        servers += server
        return server to heldCertificate
    }

    @Test
    fun `a client successfully connects to the server whose certificate matches the pinned fingerprint`() {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setBody("ok"))
        val endpoint = endpointFor(server, heldCertificate)
        val client = factory.create(endpoint)

        val response = client.newCall(Request.Builder().url("https://${endpoint.host}:${endpoint.securePort}/").build()).execute()

        assertEquals(200, response.code)
        response.close()
    }

    @Test
    fun `a client rejects a server certificate that does not match the pinned fingerprint`() {
        val (server, _) = newServer() // server presents a DIFFERENT certificate than pinnedCert below
        server.enqueue(MockResponse().setBody("ok"))
        val pinnedCert = testHeldCertificate() // never served by anything; only its fingerprint is used
        val endpoint = endpointFor(server, pinnedCert) // host:port from `server`, fingerprint from a DIFFERENT cert
        val client = factory.create(endpoint)

        var threw = false
        try {
            client.newCall(Request.Builder().url("https://${endpoint.host}:${endpoint.securePort}/").build()).execute()
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("expected the mismatched-fingerprint connection to fail", threw)
    }

    // 25. configured host mismatch rejected — tested directly against the produced
    // HostnameVerifier, since the client always dials the URL built from endpoint.host itself
    // (a real handshake can't organically present a "different" hostname to verify against).
    @Test
    fun `the produced hostname verifier accepts only the exact configured host`() {
        val (server, heldCertificate) = newServer()
        val endpoint = endpointFor(server, heldCertificate)
        val client = factory.create(endpoint)
        val verifier = client.hostnameVerifier

        assertTrue(verifier.verify(endpoint.host, stubSslSession))
        assertFalse(verifier.verify("some-other-host.example", stubSslSession))
        assertFalse(verifier.verify(endpoint.host + ".evil", stubSslSession))
    }

    // 26. redirects prohibited
    @Test
    fun `redirects are never followed`() {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://${server.hostName}:${server.port}/elsewhere"))
        server.enqueue(MockResponse().setBody("should never be reached"))
        val endpoint = endpointFor(server, heldCertificate)
        val client = factory.create(endpoint)

        val response = client.newCall(Request.Builder().url("https://${endpoint.host}:${endpoint.securePort}/").build()).execute()

        assertEquals(302, response.code)
        assertEquals(1, server.requestCount)
        response.close()
    }

    // 27. HTTP fallback prohibited
    @Test
    fun `a plain-HTTP URL is refused outright — no cleartext connection spec is configured`() {
        val (server, heldCertificate) = newServer()
        val endpoint = endpointFor(server, heldCertificate)
        val client = factory.create(endpoint)

        var threw = false
        try {
            client.newCall(Request.Builder().url("http://${endpoint.host}:${endpoint.securePort}/").build()).execute()
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("expected a plain-HTTP request through the pinned client to fail", threw)
    }

    // 28. dedicated client cannot be reused with another pin
    @Test
    fun `a client pinned to one endpoint refuses a different endpoint's certificate`() {
        val (serverA, certA) = newServer()
        val (serverB, certB) = newServer()
        serverB.enqueue(MockResponse().setBody("server B"))
        val endpointA = endpointFor(serverA, certA)
        val clientA = factory.create(endpointA)

        var threw = false
        try {
            // Dial server B's real address, but through client A — pinned to server A's fingerprint.
            clientA.newCall(Request.Builder().url("https://${serverB.hostName}:${serverB.port}/").build()).execute()
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("client A must not trust server B's certificate", threw)
    }

    @Test
    fun `no automatic retry on connection failure`() {
        val (server, heldCertificate) = newServer()
        val endpoint = endpointFor(server, heldCertificate)
        val client = factory.create(endpoint)

        assertFalse(client.retryOnConnectionFailure)
    }
}

private val stubSslSession = object : SSLSession {
    override fun getId(): ByteArray = ByteArray(0)
    override fun getSessionContext(): SSLSessionContext? = null
    override fun getCreationTime(): Long = 0
    override fun getLastAccessedTime(): Long = 0
    override fun invalidate() {}
    override fun isValid(): Boolean = false
    override fun putValue(name: String?, value: Any?) {}
    override fun getValue(name: String?): Any? = null
    override fun removeValue(name: String?) {}
    override fun getValueNames(): Array<String> = emptyArray()
    override fun getPeerCertificates(): Array<Certificate> = emptyArray()
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate> = emptyArray()
    override fun getPeerPrincipal(): Principal? = null
    override fun getLocalPrincipal(): Principal? = null
    override fun getCipherSuite(): String = ""
    override fun getProtocol(): String = ""
    override fun getPeerHost(): String? = null
    override fun getPeerPort(): Int = 0
    override fun getPacketBufferSize(): Int = 0
    override fun getApplicationBufferSize(): Int = 0
}
