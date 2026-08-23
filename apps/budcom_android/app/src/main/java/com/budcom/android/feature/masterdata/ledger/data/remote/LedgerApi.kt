package com.budcom.android.feature.masterdata.ledger.data.remote

import retrofit2.http.GET
import retrofit2.http.POST
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

    /**
     * Confirmed Connector single-ledger detail endpoint: `GET /ledgers/{id}` — returns the fuller
     * `LedgerDetails` shape (mailing/contact/gst), unlike the list endpoint above which only ever
     * returns `LedgerSummary`. A genuine HTTP 404 (`{code: "NOT_FOUND", message}`) is returned when
     * the ledger id is unknown — Retrofit surfaces this as an `HttpException`, handled the same way
     * as any other transport error (see `ErrorMapper`). Reads the Connector's already-synced local
     * SQLite snapshot (not a live Tally query) — see `LedgerSyncServiceImpl.getLedgerById`.
     */
    @GET("ledgers/{id}")
    suspend fun getLedgerDetail(@Path("id") ledgerId: String): LedgerDetailEnvelopeDto

    /**
     * Confirmed Connector bulk contact-details endpoint: `POST /sync/ledgers/contact-details` —
     * one Tally round-trip for every ledger's mailing/contact/GST fields at once. Manually
     * triggered only (Connect's own action) — never part of `SyncApi`'s routine sync cycle, and
     * never invoked automatically.
     */
    @POST("sync/ledgers/contact-details")
    suspend fun postLedgerContactDetailsSync(): LedgerContactDetailsBulkResponseDto
}
