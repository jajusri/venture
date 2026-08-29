package com.budcom.android.core.trust.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Typed Retrofit client for Trust's HTTP surface this app calls. Only the one narrow enrollment
 * endpoint exists today (`backend/services/trust/src/app.ts`); verification-key retrieval is a
 * separate, later concern once real signature verification is wired up (Gate 3B explicitly scopes
 * "any existing verification metadata required by Android" -- not built in this pass, since nothing
 * on the Android side verifies a Trust signature yet; see the final report). */
interface TrustApi {
    @POST("v1/trust/enrollment/consume")
    suspend fun consumeEnrollmentGrant(@Body body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto>
}
