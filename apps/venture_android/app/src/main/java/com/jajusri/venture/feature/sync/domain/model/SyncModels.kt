package com.jajusri.venture.feature.sync.domain.model

/**
 * Sync targets known to the Android companion.
 *
 * [Vouchers] has a blocking start route but no cancel, status, statistics, or recent-runs route.
 */
enum class SyncTarget {
    Ledgers,
    StockItems,
    Vouchers,
}

enum class SyncMode {
    Full,
    Incremental,
}

/**
 * Connector-reported sync lifecycle status values.
 */
enum class SyncRunStatus {
    Idle,
    Running,
    Completed,
    Failed,
    Cancelled,
    Interrupted,
    Recovering,
    Cancelling,
    Unknown,
}

fun SyncRunStatus.isActive(): Boolean = when (this) {
    SyncRunStatus.Running,
    SyncRunStatus.Cancelling,
    SyncRunStatus.Recovering,
    -> true
    else -> false
}

data class SyncCounts(
    val itemsProcessed: Int,
    val itemsAdded: Int,
    val itemsUpdated: Int,
    val itemsSkipped: Int,
    val itemsFailed: Int,
    val totalExpected: Int?,
) {
    /** Determinate progress only when Connector reported a positive total. */
    val hasDeterminateProgress: Boolean
        get() = totalExpected != null && totalExpected > 0
}

/**
 * The Connector's own adaptive-sync scheduler stage for one target -- raw internal state, never
 * shown to the user directly. See [checkingFrequencyLabel], which maps this to the two honest,
 * non-technical phrases the UI actually displays: "Checking regularly" / "Checking occasionally".
 */
enum class SchedulerStage {
    ActiveWindow,
    Backoff15,
    Backoff30,
    Backoff60,
    Unknown,
}

fun String.toSchedulerStage(): SchedulerStage = when (this) {
    "active_window" -> SchedulerStage.ActiveWindow
    "backoff_15" -> SchedulerStage.Backoff15
    "backoff_30" -> SchedulerStage.Backoff30
    "backoff_60" -> SchedulerStage.Backoff60
    else -> SchedulerStage.Unknown
}

data class SchedulerState(
    val stage: SchedulerStage,
    val nextCheckDueAt: String?,
)

/**
 * Combines whichever targets' scheduler state is currently known into the one two-word phrase
 * the architecture's UX section allows -- "Checking regularly" while any of them is inside the
 * active window, "Checking occasionally" once all known ones have backed off. `null` when nothing
 * is known yet (older Connector, nothing synced yet, or a transient failure) -- the caller must
 * leave this blank rather than guessing, exactly like Desktop's equivalent
 * `deriveCheckingFrequencyLabel()`.
 */
fun checkingFrequencyLabel(vararg states: SchedulerState?): String? {
    val known = states.filterNotNull()
    if (known.isEmpty()) return null
    return if (known.any { it.stage == SchedulerStage.ActiveWindow }) {
        "Checking regularly"
    } else {
        "Checking occasionally"
    }
}

data class SyncProgress(
    val syncRunId: String?,
    val status: SyncRunStatus,
    val counts: SyncCounts,
    val startedAt: String?,
    val completedAt: String?,
    val durationMs: Long?,
    val lastError: String?,
    val cancelRequested: Boolean,
    val schedulerState: SchedulerState? = null,
)

data class SyncStatisticsSummary(
    val lastSyncedAt: String?,
    val totalItems: Int?,
)

data class SyncRunSummary(
    val syncRunId: String,
    val target: SyncTarget,
    val mode: SyncMode,
    val status: SyncRunStatus,
    val startedAt: String,
    val completedAt: String?,
    val processed: Int,
    val failed: Int,
    val failureCode: String?,
    val failureSummary: String?,
)

/**
 * Terminal outcome of a sync start attempt or observed run.
 */
sealed interface SyncOutcome {
    val target: SyncTarget

    data class Succeeded(
        override val target: SyncTarget,
        val syncRunId: String,
        val status: SyncRunStatus,
        val progress: SyncProgress,
        val validationIssueCount: Int,
        val extractionCompleteness: String?,
        val statistics: SyncStatisticsSummary?,
        val warningMessage: String?,
    ) : SyncOutcome

    data class PartiallySucceeded(
        override val target: SyncTarget,
        val syncRunId: String,
        val status: SyncRunStatus,
        val progress: SyncProgress,
        val extractionCompleteness: String,
        val validationIssueCount: Int,
        val statistics: SyncStatisticsSummary?,
    ) : SyncOutcome

    data class Failed(
        override val target: SyncTarget,
        val error: com.jajusri.venture.core.common.AppError,
        val progress: SyncProgress?,
    ) : SyncOutcome

    data class Cancelled(
        override val target: SyncTarget,
        val syncRunId: String?,
        val progress: SyncProgress,
    ) : SyncOutcome

    data class Conflict(
        override val target: SyncTarget,
        val message: String,
        val existingSyncRunId: String?,
    ) : SyncOutcome
}

data class SyncTargetSnapshot(
    val target: SyncTarget,
    val available: Boolean,
    val unavailableReason: String? = null,
    val liveProgress: SyncProgress? = null,
    val lastOutcome: SyncOutcome? = null,
    val lastSuccessfulAt: String? = null,
    val recentRuns: List<SyncRunSummary> = emptyList(),
    val statistics: SyncStatisticsSummary? = null,
)

/**
 * Canonical in-process sync summary for Dashboard and Sync UI.
 * Not durable across process death unless refreshed from Connector.
 */
data class SyncStatusSummary(
    val companyId: String?,
    val isAnySyncActive: Boolean,
    val activeTarget: SyncTarget?,
    val activeStatus: SyncRunStatus?,
    val latestSuccessfulAt: String?,
    val latestFailedMessage: String?,
    val targets: List<SyncTargetSnapshot>,
    val lastUpdatedEpochMillis: Long,
)
