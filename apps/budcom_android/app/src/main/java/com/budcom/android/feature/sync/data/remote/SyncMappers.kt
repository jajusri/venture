package com.budcom.android.feature.sync.data.remote

import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget

internal fun SyncProgressDto.toDomain(): SyncProgress = SyncProgress(
    syncRunId = syncRunId,
    status = status.toSyncRunStatus(),
    counts = SyncCounts(
        itemsProcessed = itemsProcessed,
        itemsAdded = itemsAdded,
        itemsUpdated = itemsUpdated,
        itemsSkipped = itemsSkipped,
        itemsFailed = itemsFailed,
        totalExpected = totalExpected,
    ),
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    lastError = lastError,
    cancelRequested = cancelRequested,
)

internal fun String.toSyncRunStatus(): SyncRunStatus = when (this) {
    "idle" -> SyncRunStatus.Idle
    "running" -> SyncRunStatus.Running
    "completed" -> SyncRunStatus.Completed
    "failed" -> SyncRunStatus.Failed
    "cancelled" -> SyncRunStatus.Cancelled
    "interrupted" -> SyncRunStatus.Interrupted
    "recovering" -> SyncRunStatus.Recovering
    "cancelling" -> SyncRunStatus.Cancelling
    else -> SyncRunStatus.Unknown
}

internal fun String.toSyncMode(): SyncMode = when (this) {
    "incremental" -> SyncMode.Incremental
    else -> SyncMode.Full
}

internal fun SyncRunRecordDto.toDomain(fallbackTarget: SyncTarget): SyncRunSummary = SyncRunSummary(
    syncRunId = syncRunId,
    target = when (resourceKind) {
        "ledgers" -> SyncTarget.Ledgers
        "stock-items" -> SyncTarget.StockItems
        else -> fallbackTarget
    },
    mode = syncType.toSyncMode(),
    status = status.toSyncRunStatus(),
    startedAt = startedAt,
    completedAt = completedAt,
    processed = processed,
    failed = failed,
    failureCode = failureCode,
    failureSummary = failureSummary,
)

internal fun LedgerSyncStatisticsDto.toSummary(): SyncStatisticsSummary = SyncStatisticsSummary(
    lastSyncedAt = lastSyncedAt,
    totalItems = totalLedgers,
)

internal fun StockSyncStatisticsDto.toSummary(): SyncStatisticsSummary = SyncStatisticsSummary(
    lastSyncedAt = lastSyncedAt,
    totalItems = totalStockItems,
)

internal fun LedgerSyncResultDto.toOutcome(): SyncOutcome {
    val progressDomain = progress.toDomain()
    return when (status.toSyncRunStatus()) {
        SyncRunStatus.Cancelled -> SyncOutcome.Cancelled(
            target = SyncTarget.Ledgers,
            syncRunId = syncRunId,
            progress = progressDomain,
        )
        SyncRunStatus.Failed, SyncRunStatus.Interrupted -> SyncOutcome.Failed(
            target = SyncTarget.Ledgers,
            error = com.budcom.android.core.common.AppError.Remote(
                httpStatus = 200,
                code = status,
                message = progress.lastError ?: "Ledger sync ended with status $status.",
            ),
            progress = progressDomain,
        )
        else -> {
            val warning = if (validationIssueCount > 0) {
                "Completed with $validationIssueCount validation issue(s)."
            } else {
                null
            }
            SyncOutcome.Succeeded(
                target = SyncTarget.Ledgers,
                syncRunId = syncRunId,
                status = status.toSyncRunStatus(),
                progress = progressDomain,
                validationIssueCount = validationIssueCount,
                extractionCompleteness = null,
                statistics = statistics.toSummary(),
                warningMessage = warning,
            )
        }
    }
}

internal fun StockSyncResultDto.toOutcome(): SyncOutcome {
    val progressDomain = progress.toDomain()
    return when {
        status.toSyncRunStatus() == SyncRunStatus.Cancelled ||
            extractionCompleteness == "cancelled" -> SyncOutcome.Cancelled(
            target = SyncTarget.StockItems,
            syncRunId = syncRunId,
            progress = progressDomain,
        )
        extractionCompleteness == "partial" -> SyncOutcome.PartiallySucceeded(
            target = SyncTarget.StockItems,
            syncRunId = syncRunId,
            status = status.toSyncRunStatus(),
            progress = progressDomain,
            extractionCompleteness = extractionCompleteness,
            validationIssueCount = validationIssueCount,
            statistics = statistics.toSummary(),
        )
        status.toSyncRunStatus() == SyncRunStatus.Failed ||
            status.toSyncRunStatus() == SyncRunStatus.Interrupted ||
            extractionCompleteness == "failed" -> SyncOutcome.Failed(
            target = SyncTarget.StockItems,
            error = com.budcom.android.core.common.AppError.Remote(
                httpStatus = 200,
                code = status,
                message = progress.lastError
                    ?: "Stock item sync ended with status $status ($extractionCompleteness).",
            ),
            progress = progressDomain,
        )
        else -> {
            val warning = if (validationIssueCount > 0) {
                "Completed with $validationIssueCount validation issue(s)."
            } else {
                null
            }
            SyncOutcome.Succeeded(
                target = SyncTarget.StockItems,
                syncRunId = syncRunId,
                status = status.toSyncRunStatus(),
                progress = progressDomain,
                validationIssueCount = validationIssueCount,
                extractionCompleteness = extractionCompleteness,
                statistics = statistics.toSummary(),
                warningMessage = warning,
            )
        }
    }
}
