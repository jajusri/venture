package com.jajusri.venture.feature.sync.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.masterdata.presentation.toMasterDataUiError
import com.jajusri.venture.feature.sync.domain.model.SyncOutcome
import com.jajusri.venture.feature.sync.domain.model.SyncProgress
import com.jajusri.venture.feature.sync.domain.model.SyncRunStatus
import com.jajusri.venture.feature.sync.domain.model.SyncTarget
import com.jajusri.venture.feature.sync.domain.model.isActive

data class SyncUiState(
    val companyId: String? = null,
    val isOnline: Boolean = true,
    val isRefreshingOverview: Boolean = false,
    val isBusy: Boolean = false,
    val activeTarget: SyncTarget? = null,
    val activeProgress: SyncProgressUi? = null,
    val phase: SyncPhase = SyncPhase.Idle,
    val targets: List<SyncTargetCardUi> = defaultCards(),
    val bannerError: MasterDataUiError? = null,
    val aggregateMessage: String? = null,
) {
    val hasCompany: Boolean get() = !companyId.isNullOrBlank()
    val canStart: Boolean get() = hasCompany && isOnline && !isBusy
}

enum class SyncPhase {
    Idle,
    Starting,
    Running,
    Observing,
    Success,
    PartialSuccess,
    Failed,
    Cancelled,
    Conflict,
}

internal fun SyncPhase.toLabel(): String = when (this) {
    SyncPhase.Idle -> "Idle"
    SyncPhase.Starting -> "Starting"
    SyncPhase.Running -> "Running"
    SyncPhase.Observing -> "Observing"
    SyncPhase.Success -> "Completed"
    SyncPhase.PartialSuccess -> "Partially completed"
    SyncPhase.Failed -> "Failed"
    SyncPhase.Cancelled -> "Cancelled"
    SyncPhase.Conflict -> "Conflict"
}

data class SyncProgressUi(
    val statusLabel: String,
    val processedLabel: String?,
    val determinateFraction: Float?,
    val lastError: String?,
    val cancelRequested: Boolean,
)

data class SyncTargetCardUi(
    val target: SyncTarget,
    val title: String,
    val available: Boolean,
    val unavailableReason: String? = null,
    val statusLine: String,
    val lastSyncedLine: String?,
    val canSync: Boolean,
    val canCancel: Boolean,
)

sealed interface SyncEvent {
    data object RefreshOverview : SyncEvent
    data object Retry : SyncEvent
    data object RunAvailableSyncs : SyncEvent
    data class StartTarget(val target: SyncTarget) : SyncEvent
    data class CancelTarget(val target: SyncTarget) : SyncEvent
    data object OpenCompanySelection : SyncEvent
    data object OpenServerConfig : SyncEvent
}

sealed interface SyncNavigation {
    data object CompanySelection : SyncNavigation
    data object ServerConfig : SyncNavigation
}

internal fun defaultCards(): List<SyncTargetCardUi> = listOf(
    SyncTargetCardUi(
        target = SyncTarget.Ledgers,
        title = "Ledgers",
        available = true,
        statusLine = "Idle",
        lastSyncedLine = null,
        canSync = false,
        canCancel = false,
    ),
    SyncTargetCardUi(
        target = SyncTarget.StockItems,
        title = "Stock items",
        available = true,
        statusLine = "Idle",
        lastSyncedLine = null,
        canSync = false,
        canCancel = false,
    ),
    SyncTargetCardUi(
        target = SyncTarget.Vouchers,
        title = "Vouchers",
        available = true,
        statusLine = "Idle",
        lastSyncedLine = null,
        canSync = false,
        canCancel = false,
    ),
)

internal fun SyncProgress.toUi(): SyncProgressUi {
    val total = counts.totalExpected
    val fraction = if (counts.hasDeterminateProgress && total != null) {
        (counts.itemsProcessed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val processed = buildString {
        append("Processed ${counts.itemsProcessed}")
        if (total != null && total > 0) append(" of $total")
        append(" · added ${counts.itemsAdded}, updated ${counts.itemsUpdated}, skipped ${counts.itemsSkipped}, failed ${counts.itemsFailed}")
    }
    return SyncProgressUi(
        statusLabel = status.toLabel(),
        processedLabel = processed,
        determinateFraction = fraction,
        lastError = lastError,
        cancelRequested = cancelRequested,
    )
}

internal fun SyncRunStatus.toLabel(): String = when (this) {
    SyncRunStatus.Idle -> "Idle"
    SyncRunStatus.Running -> "Running"
    SyncRunStatus.Completed -> "Completed"
    SyncRunStatus.Failed -> "Failed"
    SyncRunStatus.Cancelled -> "Cancelled"
    SyncRunStatus.Interrupted -> "Interrupted"
    SyncRunStatus.Recovering -> "Recovering"
    SyncRunStatus.Cancelling -> "Cancelling"
    SyncRunStatus.Unknown -> "Unknown"
}

internal fun SyncOutcome.toPhase(): SyncPhase = when (this) {
    is SyncOutcome.Succeeded -> if (warningMessage != null) SyncPhase.PartialSuccess else SyncPhase.Success
    is SyncOutcome.PartiallySucceeded -> SyncPhase.PartialSuccess
    is SyncOutcome.Failed -> SyncPhase.Failed
    is SyncOutcome.Cancelled -> SyncPhase.Cancelled
    is SyncOutcome.Conflict -> SyncPhase.Conflict
}

internal fun AppError.toUi(): MasterDataUiError = toMasterDataUiError()

internal fun SyncProgress?.statusLineOrIdle(): String =
    this?.status?.toLabel() ?: "Idle"
