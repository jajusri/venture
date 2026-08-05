package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParseResult
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParser
import com.budcom.android.core.pairing.domain.model.SecurePairingQrValidationPolicy
import com.budcom.android.core.util.TimeProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

private const val NOW = 1_800_000_000_000L

class ValidateSecurePairingPayloadTest {

    private val timeProvider = TimeProvider { NOW }
    private val useCase = ValidateSecurePairingPayload(SecurePairingQrPayloadParser(timeProvider))

    private fun fingerprint(): String {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
        return "sha256/" + Base64.getEncoder().encodeToString(digest)
    }

    @Test
    fun `validates a well-formed payload without redeeming anything`() {
        val json = """
            {
              "schemaVersion": "1", "pairingSessionId": "id", "secret": "s",
              "connectorId": "c", "connectorName": "Front Desk", "host": "10.0.0.5",
              "port": 8080, "securePort": 8443, "transportProtocol": "https",
              "transportFingerprint": "${fingerprint()}", "fingerprintAlgorithm": "sha256",
              "transportIdentityVersion": 1, "expiresAt": "${Instant.ofEpochMilli(NOW + 60_000L)}"
            }
        """.trimIndent()

        val result = useCase(json)

        assertTrue(result is SecurePairingQrPayloadParseResult.Valid)
    }

    @Test
    fun `rejects a malformed payload`() {
        val result = useCase("not json")
        assertTrue(result is SecurePairingQrPayloadParseResult.Invalid)
    }

    @Test
    fun `respects an explicitly injected validation policy`() {
        val json = """
            {
              "schemaVersion": "1", "pairingSessionId": "id", "secret": "s",
              "connectorId": "c", "connectorName": "Front Desk", "host": "127.0.0.1",
              "port": 8080, "securePort": 8443, "transportProtocol": "https",
              "transportFingerprint": "${fingerprint()}", "fingerprintAlgorithm": "sha256",
              "transportIdentityVersion": 1, "expiresAt": "${Instant.ofEpochMilli(NOW + 60_000L)}"
            }
        """.trimIndent()

        val defaultPolicy = useCase(json)
        val loopbackAllowed = useCase(json, SecurePairingQrValidationPolicy(allowLoopbackHost = true))

        assertTrue(defaultPolicy is SecurePairingQrPayloadParseResult.Invalid)
        assertTrue(loopbackAllowed is SecurePairingQrPayloadParseResult.Valid)
    }
}
