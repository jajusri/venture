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
    fun `caches within the short TTL window, then re-fetches once elapsed`() = runTest {
        val api = FakeEpochApi { Response.success(TrustAuthorityEpochResponseDto("business-1", "membership-1", "device-1", 4L)) }
        var clock = 0L
        val cache = TrustBackedAuthorityEpochCache(api, now = { clock })
        cache.currentEpoch("business-1", "membership-1", "device-1")
        clock += 5_000L
        cache.currentEpoch("business-1", "membership-1", "device-1")
        assertEquals(1, api.callCount)
        clock += 6_000L
        cache.currentEpoch("business-1", "membership-1", "device-1")
        assertEquals(2, api.callCount)
    }
}
