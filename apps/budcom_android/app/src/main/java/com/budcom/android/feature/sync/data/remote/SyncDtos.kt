package com.budcom.android.feature.sync.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SyncStartRequestDto(
    val incremental: Boolean = false,
)

@Serializable
data class VoucherSyncStartRequestDto(
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

@Serializable
data class SyncProgressDto(
    val syncRunId: String? = null,
    val status: String,
    val totalExpected: Int? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val itemsProcessed: Int = 0,
    val itemsAdded: Int = 0,
    val itemsUpdated: Int = 0,
    val itemsSkipped: Int = 0,
    val itemsFailed: Int = 0,
    val lastError: String? = null,
    val cancelRequested: Boolean = false,
    val storageBackend: String? = null,
    val migrationStatus: String? = null,
)

@Serializable
data class SyncStorageDto(
    val backend: String? = null,
    val schemaVersion: Int? = null,
    val databaseHealthy: Boolean? = null,
    val migrationStatus: String? = null,
    val message: String? = null,
)

@Serializable
data class SyncStatusResponseDto(
    val schemaVersion: String? = null,
    val progress: SyncProgressDto,
    val storage: SyncStorageDto? = null,
)

@Serializable
data class SyncCancelResponseDto(
    val schemaVersion: String? = null,
    val progress: SyncProgressDto,
)

@Serializable
data class LedgerSyncStatisticsDto(
    val totalLedgers: Int = 0,
    val activeLedgers: Int = 0,
    val inactiveLedgers: Int = 0,
    val reservedLedgers: Int = 0,
    val deletedLedgers: Int = 0,
    val withGst: Int = 0,
    val withOpeningBalance: Int = 0,
    val lastSyncedAt: String? = null,
)

@Serializable
data class StockSyncStatisticsDto(
    val totalStockItems: Int = 0,
    val withBaseUnit: Int = 0,
    val incompleteData: Int = 0,
    val withHsn: Int = 0,
    val withGst: Int = 0,
    val withOpeningBalance: Int = 0,
    val deletedStockItems: Int = 0,
    val lastSyncedAt: String? = null,
)

@Serializable
data class LedgerStatisticsResponseDto(
    val schemaVersion: String? = null,
    val statistics: LedgerSyncStatisticsDto,
)

@Serializable
data class StockStatisticsResponseDto(
    val schemaVersion: String? = null,
    val statistics: StockSyncStatisticsDto,
)

@Serializable
data class SyncChangeDto(
    val ledgerId: String? = null,
    val stockItemId: String? = null,
    val changeType: String,
    val reason: String? = null,
)

@Serializable
data class LedgerSyncResultDto(
    val schemaVersion: String? = null,
    val syncRunId: String,
    val status: String,
    val statistics: LedgerSyncStatisticsDto,
    val progress: SyncProgressDto,
    val changes: List<SyncChangeDto> = emptyList(),
    val validationIssueCount: Int = 0,
)

@Serializable
data class StockSyncResultDto(
    val schemaVersion: String? = null,
    val syncRunId: String,
    val status: String,
    val extractionCompleteness: String,
    val deletionReconciliation: String? = null,
    val statistics: StockSyncStatisticsDto,
    val progress: SyncProgressDto,
    val changes: List<SyncChangeDto> = emptyList(),
    val validationIssueCount: Int = 0,
)

@Serializable
data class VoucherSyncStatisticsDto(
    val totalVouchers: Int = 0,
    val lastSyncedAt: String? = null,
)

@Serializable
data class VoucherSyncResultDto(
    val schemaVersion: String? = null,
    val syncRunId: String,
    val status: String,
    val extractionCompleteness: String? = null,
    val statistics: VoucherSyncStatisticsDto,
    val progress: SyncProgressDto,
    val validationIssueCount: Int = 0,
)

@Serializable
data class SyncRunRecordDto(
    val syncRunId: String,
    val companyId: String,
    val resourceKind: String,
    val syncType: String,
    val status: String,
    val startedAt: String,
    val updatedAt: String? = null,
    val completedAt: String? = null,
    val totalExpected: Int? = null,
    val processed: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val lastProcessedId: String? = null,
    val predecessorSyncRunId: String? = null,
    val retryCount: Int = 0,
    val cancelRequested: Boolean = false,
    val failureCode: String? = null,
    val failureSummary: String? = null,
    val connectorVersion: String? = null,
    val schemaVersion: String? = null,
)

@Serializable
data class SyncRunsResponseDto(
    val schemaVersion: String? = null,
    val runs: List<SyncRunRecordDto> = emptyList(),
)
