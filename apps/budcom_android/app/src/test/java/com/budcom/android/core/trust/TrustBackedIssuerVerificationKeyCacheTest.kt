package com.budcom.android.core.trust

import com.budcom.android.core.trust.data.TrustBackedIssuerVerificationKeyCache
import com.budcom.android.core.trust.data.decodePemPublicKey
import com.budcom.android.core.trust.data.remote.TrustApi
import com.budcom.android.core.trust.data.remote.TrustAuthorityEpochResponseDto
import com.budcom.android.core.trust.data.remote.TrustEnrollmentRequestDto
import com.budcom.android.core.trust.data.remote.TrustEnrollmentResponseDto
import com.budcom.android.core.trust.data.remote.TrustVerificationKeyDto
import com.budcom.android.core.trust.data.remote.TrustVerificationKeysResponseDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

private class FakeVerificationKeysApi(private val result: () -> Response<TrustVerificationKeysResponseDto>) : TrustApi {
    var callCount = 0
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> = error("not used")
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> { callCount += 1; return result() }
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> = error("not used")
}

private class ThrowingVerificationKeysApi(private val error: Throwable) : TrustApi {
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> = error("not used")
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> = throw error
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> = error("not used")
}

private fun realEcPublicKeyPem(): Pair<ByteArray, String> {
    val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val der = keyPair.public.encoded
    val base64 = java.util.Base64.getEncoder().encodeToString(der)
    val pem = "-----BEGIN PUBLIC KEY-----\n" + base64.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----\n"
    return der to pem
}

class TrustBackedIssuerVerificationKeyCacheTest {
    @Test
    fun `decodePemPublicKey round-trips a real EC public key back to its exact DER bytes`() {
        val (der, pem) = realEcPublicKeyPem()
        assertArrayEquals(der, decodePemPublicKey(pem))
    }

    @Test
    fun `fetches and returns the matching key when found`() = runTest {
        val (der, pem) = realEcPublicKeyPem()
        val api = FakeVerificationKeysApi { Response.success(TrustVerificationKeysResponseDto(1, "issuer-1", listOf(TrustVerificationKeyDto("issuer-1", "key-1", "P256-SHA256-v1", pem, "2026-01-01T00:00:00Z", null, "active")))) }
        val cache = TrustBackedIssuerVerificationKeyCache(api)
        val result = cache.get("issuer-1", "key-1")
        assertArrayEquals(der, result?.publicKey)
        assertEquals(false, result?.revoked)
    }

    @Test
    fun `maps a revoked key status to revoked = true`() = runTest {
        val (_, pem) = realEcPublicKeyPem()
        val api = FakeVerificationKeysApi { Response.success(TrustVerificationKeysResponseDto(1, "issuer-1", listOf(TrustVerificationKeyDto("issuer-1", "key-1", "P256-SHA256-v1", pem, "2026-01-01T00:00:00Z", null, "revoked")))) }
        val cache = TrustBackedIssuerVerificationKeyCache(api)
        assertTrue(cache.get("issuer-1", "key-1")?.revoked == true)
    }

    @Test
    fun `returns null when the requested keyId is not present in the response`() = runTest {
        val (_, pem) = realEcPublicKeyPem()
        val api = FakeVerificationKeysApi { Response.success(TrustVerificationKeysResponseDto(1, "issuer-1", listOf(TrustVerificationKeyDto("issuer-1", "some-other-key", "P256-SHA256-v1", pem, "2026-01-01T00:00:00Z", null, "active")))) }
        assertNull(TrustBackedIssuerVerificationKeyCache(api).get("issuer-1", "key-1"))
    }

    @Test
    fun `returns null on a non-2xx response`() = runTest {
        val api = FakeVerificationKeysApi { Response.error(404, "{}".toResponseBody("application/json".toMediaType())) }
        assertNull(TrustBackedIssuerVerificationKeyCache(api).get("issuer-1", "key-1"))
    }

    @Test
    fun `returns null (fail closed) when Trust is unreachable`() = runTest {
        assertNull(TrustBackedIssuerVerificationKeyCache(ThrowingVerificationKeysApi(IOException("unreachable"))).get("issuer-1", "key-1"))
    }

    @Test
    fun `caches a successful lookup and does not re-fetch within the TTL window`() = runTest {
        val (_, pem) = realEcPublicKeyPem()
        val api = FakeVerificationKeysApi { Response.success(TrustVerificationKeysResponseDto(1, "issuer-1", listOf(TrustVerificationKeyDto("issuer-1", "key-1", "P256-SHA256-v1", pem, "2026-01-01T00:00:00Z", null, "active")))) }
        var clock = 0L
        val cache = TrustBackedIssuerVerificationKeyCache(api, now = { clock })
        cache.get("issuer-1", "key-1")
        clock += 60_000L
        cache.get("issuer-1", "key-1")
        assertEquals(1, api.callCount)
    }

    @Test
    fun `re-fetches once the TTL window has elapsed`() = runTest {
        val (_, pem) = realEcPublicKeyPem()
        val api = FakeVerificationKeysApi { Response.success(TrustVerificationKeysResponseDto(1, "issuer-1", listOf(TrustVerificationKeyDto("issuer-1", "key-1", "P256-SHA256-v1", pem, "2026-01-01T00:00:00Z", null, "active")))) }
        var clock = 0L
        val cache = TrustBackedIssuerVerificationKeyCache(api, now = { clock })
        cache.get("issuer-1", "key-1")
        clock += 6 * 60_000L
        cache.get("issuer-1", "key-1")
        assertEquals(2, api.callCount)
    }
}
