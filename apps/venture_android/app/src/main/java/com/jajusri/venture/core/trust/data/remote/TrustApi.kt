package com.jajusri.venture.core.trust.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Typed Retrofit client for Trust's HTTP surface this app calls: device enrollment, real
 * signature-verification material (issuer verification keys), and local authority-freshness
 * checks (current authority epoch) -- see `backend/services/trust/src/app.ts` for the exact
 * server-side contract each of these mirrors. */
interface TrustApi {
    @POST("v1/trust/enrollment/consume")
    suspend fun consumeEnrollmentGrant(@Body body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto>

    @GET("v1/trust/issuers/{issuerId}/verification-keys")
    suspend fun getVerificationKeys(@Path("issuerId") issuerId: String): Response<TrustVerificationKeysResponseDto>

    @GET("v1/trust/authority/epoch")
    suspend fun getCurrentAuthorityEpoch(
        @Query("businessId") businessId: String,
        @Query("membershipId") membershipId: String,
        @Query("deviceId") deviceId: String,
    ): Response<TrustAuthorityEpochResponseDto>
}
