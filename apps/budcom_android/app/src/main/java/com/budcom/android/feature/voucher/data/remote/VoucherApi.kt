package com.budcom.android.feature.voucher.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Confirmed Connector voucher endpoints under `/api/v1/vouchers`.
 */
interface VoucherApi {
    @GET("api/v1/vouchers")
    suspend fun listVouchers(
        @Query("company") company: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sort") sort: String? = null,
        @Query("q") query: String? = null,
        @Query("voucherType") voucherType: String? = null,
        @Query("voucherNumber") voucherNumber: String? = null,
        @Query("partyName") partyName: String? = null,
    ): VoucherListEnvelopeDto

    @GET("api/v1/vouchers/{id}")
    suspend fun getVoucher(
        @Path("id") id: String,
        @Query("company") company: String,
    ): VoucherDetailsEnvelopeDto
}
