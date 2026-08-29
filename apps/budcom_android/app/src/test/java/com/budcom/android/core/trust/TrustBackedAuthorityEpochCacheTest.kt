package com.budcom.android.core.trust

import com.budcom.android.core.trust.data.TrustBackedAuthorityEpochCache
import com.budcom.android.core.trust.data.remote.TrustApi
import com.budcom.android.core.trust.data.remote.TrustAuthorityEpochResponseDto
import com.budcom.android.core.trust.data.remote.TrustEnrollmentRequestDto
import com.budcom.android.core.trust.data.remote.TrustEnrollmentResponseDto
import com.budcom.android.core.trust.data.remote.TrustVerificationKeysResponseDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.io.IOException

private class FakeEpochApi(private val result: () -> Response<TrustAuthorityEpochResponseDto>) : TrustApi {
    var callCount = 0
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> = error("not used")
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> = error("not used")
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> { callCount += 1; return result() }
}

private class ThrowingEpochApi(private val error: Throwable) : TrustApi {
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> = error("not used")
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> = error("not used")
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> = throw error
}

class TrustBackedAuthorityEpochCacheTest {
    @Test
    fun `returns the current epoch on a successful lookup`() = runTest {
        val api = FakeEpochApi { Response.success(TrustAuthorityEpochResponseDto("business-1", "membership-1", "device-1", 4L)) }
        assertEquals(4L, TrustBackedAuthorityEpochCache(api).currentEpoch("business-1", "membership-1", "device-1"))
    }

    @Test
    fun `returns null when Trust reports no active authority (404)`() = runTest {
        val api = FakeEpochApi { Response.error(404, "{}".toResponseBody("application/json".toMediaType())) }
        assertNull(TrustBackedAuthorityEpochCache(api).currentEpoch("business-1", "membership-1", "device-1"))
    }

    @Test
    fun `returns null (fail closed) when Trust is unreachable`() = runTest {
        assertNull(TrustBackedAuthorityEpochCache(ThrowingEpochApi(IOException("unreachable"))).currentEpoch("business-1", "membership-1", "device-1"))
    }

    @Test
    fun `ROUND 5 SECURITY FIX -- never caches a positive answer -- two calls always mean two live Trust fetches`() = runTest {
        val api = FakeEpochApi { Response.success(TrustAuthorityEpochResponseDto("business-1", "membership-1", "device-1", 4L)) }
        val cache = TrustBackedAuthorityEpochCache(api)
        cache.currentEpoch("business-1", "membership-1", "device-1")
        cache.currentEpoch("business-1", "membership-1", "device-1")
        assertEquals(2, api.callCount)
    }

    @Test
    fun `ROUND 5 SECURITY FIX -- an epoch advanced between two calls is reflected immediately, not after a TTL expires`() = runTest {
        var epoch = 3L
        val api = FakeEpochApi { Response.success(TrustAuthorityEpochResponseDto("business-1", "membership-1", "device-1", epoch)) }
        val cache = TrustBackedAuthorityEpochCache(api)
        assertEquals(3L, cache.currentEpoch("business-1", "membership-1", "device-1"))
        epoch = 4L
        assertEquals(4L, cache.currentEpoch("business-1", "membership-1", "device-1"))
    }
}
