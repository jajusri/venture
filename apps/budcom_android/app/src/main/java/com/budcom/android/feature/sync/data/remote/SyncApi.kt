package com.budcom.android.feature.sync.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Confirmed Connector sync endpoints for ledgers and stock items.
 */
interface SyncApi {
    @POST("sync/ledgers")
    suspend fun startLedgerSync(@Body body: SyncStartRequestDto): LedgerSyncResultDto

    @POST("sync/ledgers/cancel")
    suspend fun cancelLedgerSync(): SyncCancelResponseDto

    @GET("sync/ledgers/status")
    suspend fun ledgerSyncStatus(): SyncStatusResponseDto

    @GET("sync/ledgers/statistics")
    suspend fun ledgerStatistics(): LedgerStatisticsResponseDto

    @GET("sync/ledgers/runs")
    suspend fun ledgerSyncRuns(@Query("limit") limit: Int = 5): SyncRunsResponseDto

    @POST("sync/stock-items")
    suspend fun startStockItemSync(@Body body: SyncStartRequestDto): StockSyncResultDto

    @POST("sync/stock-items/cancel")
    suspend fun cancelStockItemSync(): SyncCancelResponseDto

    @GET("sync/stock-items/status")
    suspend fun stockItemSyncStatus(): SyncStatusResponseDto

    @GET("sync/stock-items/statistics")
    suspend fun stockItemStatistics(): StockStatisticsResponseDto

    @GET("sync/stock-items/runs")
    suspend fun stockItemSyncRuns(@Query("limit") limit: Int = 5): SyncRunsResponseDto

    @POST("sync/vouchers")
    suspend fun startVoucherSync(@Body body: VoucherSyncStartRequestDto): VoucherSyncResultDto
}
