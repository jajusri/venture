package com.budcom.android.feature.masterdata.ledger.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
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

    /** Confirmed Connector Ledger statement endpoint: `GET /ledgers/{id}/statement`. */
    @GET("ledgers/{id}/statement")
    suspend fun getLedgerStatement(
        @Path("id") ledgerId: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): LedgerStatementEnvelopeDto
}
