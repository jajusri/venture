package com.budcom.android.feature.masterdata.ledger.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Confirmed Connector ledger list endpoint: `GET /ledgers`.
 */
interface LedgerApi {
    @GET("ledgers")
    suspend fun getLedgers(
        @Query("query") query: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sortBy") sortBy: String = "name",
        @Query("sortDirection") sortDirection: String = "asc",
    ): LedgerListResponseDto
}
