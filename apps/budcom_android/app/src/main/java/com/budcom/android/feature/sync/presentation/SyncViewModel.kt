package com.budcom.android.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.presentation.displayMessage
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.isActive
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.usecase.CancelTargetSyncUseCase
import com.budcom.android.feature.sync.domain.usecase.ObserveSyncProgressUseCase
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import com.budcom.android.feature.sync.domain.usecase.RunAvailableSyncsUseCase
import com.budcom.android.feature.sync.domain.usecase.StartTargetSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val refreshOverview: RefreshSyncOverviewUseCase,
    private val startTargetSync: StartTargetSyncUseCase,
    private val cancelTargetSync: CancelTargetSyncUseCase,
    private val runAvailableSyncs: RunAvailableSyncsUseCase,
    private val observeProgress: ObserveSyncProgressUseCase,
    private val syncStatusPort: ObserveSyncStatusPort,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<SyncNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<SyncNavigation> = _navigation.asSharedFlow()

    private var pollJob: Job? = null
    private var startJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().collect { companyId ->
                val previous = _uiState.value.companyId
                _uiState.update { it.copy(companyId = companyId) }
                if (previous != null && previous != companyId) {
                    pollJob?.cancel()
                    startJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            activeTarget = null,
                            activeProgress = null,
                            phase = SyncPhase.Idle,
                            aggregateMessage = "Company changed. Sync state was reset for this screen.",
                        )
                    }
                }
                onEvent(SyncEvent.RefreshOverview)
            }
        }
        viewModelScope.launch {
            syncStatusPort.summary.collect { summary ->
                applySummary(summary)
            }
        }
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.RefreshOverview -> refresh()
            SyncEvent.Retry -> {
                val target = _uiState.value.activeTarget
                if (target != null) start(target) else refresh()
            }
            SyncEvent.RunAvailableSyncs -> runAll()
            is SyncEvent.StartTarget -> start(event.target)
            is SyncEvent.CancelTarget -> cancel(event.target)
            SyncEvent.OpenCompanySelection ->
                _navigation.tryEmit(SyncNavigation.CompanySelection)
            SyncEvent.OpenServerConfig ->
                _navigation.tryEmit(SyncNavigation.ServerConfig)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingOverview = true, bannerError = null) }
            refreshOverview()
            _uiState.update { it.copy(isRefreshingOverview = false) }
            recoverActiveObservation()
        }
    }

    private fun recoverActiveObservation() {
        val summary = syncStatusPort.summary.value
        val active = summary.targets.firstOrNull { it.liveProgress?.status?.isActive() == true }
            ?: return
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                phase = SyncPhase.Observing,
                isBusy = true,
                activeTarget = active.target,
                activeProgress = active.liveProgress?.toUi(),
                aggregateMessage = "Observing an in-progress Connector sync. This screen did not start it.",
            )
        }
        beginPolling(active.target)
    }

    private fun start(target: SyncTarget) {
        if (!_uiState.value.canStart) return
        if (startJob?.isActive == true) return

        startJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    phase = SyncPhase.Starting,
                    activeTarget = target,
                    bannerError = null,
                    aggregateMessage = null,
                )
            }
            // Ledger and stock-item syncs expose status endpoints that can be polled while their
            // blocking start calls are in flight. Voucher sync intentionally exposes only its
            // blocking start endpoint, so its final response is the authoritative outcome.
            if (target != SyncTarget.Vouchers) beginPolling(target)
            when (val result = startTargetSync(target)) {
                is AppResult.Success -> applyOutcome(result.value)
                is AppResult.Failure -> {
                    pollJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            phase = SyncPhase.Failed,
                            bannerError = result.error.toUi(),
                            activeProgress = null,
                        )
                    }
                }
            }
        }
    }

    private fun runAll() {
        if (!_uiState.value.canStart) return
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    phase = SyncPhase.Starting,
                    bannerError = null,
                    aggregateMessage = "Running available syncs sequentially (Ledgers, Stock items, then Vouchers). Not atomic.",
                )
            }
            when (val result = runAvailableSyncs()) {
                is AppResult.Success -> {
                    val agg = result.value
                    val last = agg.outcomes.lastOrNull()
                    if (last != null) applyOutcome(last)
                    val message = buildString {
                        append("Finished available syncs. ")
                        append("${agg.outcomes.size} target(s) attempted. ")
                        if (agg.hasFailure && agg.hasSuccess) {
                            append("Some targets failed — results are not an all-or-nothing transaction.")
                        } else if (agg.hasFailure) {
                            append("All attempted targets failed.")
                        } else {
                            append("All attempted targets finished without hard failure.")
                        }
                    }
                    _uiState.update { it.copy(aggregateMessage = message, isBusy = false) }
                    refreshOverview()
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            phase = SyncPhase.Failed,
                            bannerError = result.error.toUi(),
                        )
                    }
                }
            }
        }
    }

    private fun cancel(target: SyncTarget) {
        viewModelScope.launch {
            when (val result = cancelTargetSync(target)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            activeProgress = result.value.toUi(),
                            phase = SyncPhase.Cancelled,
                            aggregateMessage = "Cancel requested on the Connector.",
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(bannerError = result.error.toUi()) }
                }
            }
        }
    }

    private fun beginPolling(target: SyncTarget) {
        pollJob?.cancel()
        pollJob = observeProgress.start(
            scope = viewModelScope,
            target = target,
            onProgress = { progress ->
                _uiState.update {
                    it.copy(
                        phase = if (progress.status.isActive()) SyncPhase.Running else it.phase,
                        activeProgress = progress.toUi(),
                        activeTarget = target,
                    )
                }
            },
            onPollFailure = { error ->
                _uiState.update {
                    it.copy(bannerError = error.toUi())
                }
            },
            shouldContinue = { _uiState.value.isBusy || startJob?.isActive == true },
        )
    }

    private fun applyOutcome(outcome: SyncOutcome) {
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                isBusy = false,
                phase = outcome.toPhase(),
                activeTarget = outcome.target,
                activeProgress = when (outcome) {
                    is SyncOutcome.Succeeded -> outcome.progress.toUi()
                    is SyncOutcome.PartiallySucceeded -> outcome.progress.toUi()
                    is SyncOutcome.Cancelled -> outcome.progress.toUi()
                    is SyncOutcome.Failed -> outcome.progress?.toUi()
                    is SyncOutcome.Conflict -> null
                },
                bannerError = when (outcome) {
                    is SyncOutcome.Failed -> outcome.error.toUi()
                    is SyncOutcome.Conflict ->
                        com.budcom.android.feature.masterdata.presentation.MasterDataUiError.Message(outcome.message)
                    else -> null
                },
                aggregateMessage = when (outcome) {
                    is SyncOutcome.Succeeded -> outcome.warningMessage
                    is SyncOutcome.PartiallySucceeded ->
                        "Partial extraction completeness: ${outcome.extractionCompleteness}."
                    is SyncOutcome.Conflict -> outcome.message
                    else -> it.aggregateMessage
                },
            )
        }
    }

    private fun applySummary(summary: SyncStatusSummary) {
        val canStart = !summary.companyId.isNullOrBlank() &&
            _uiState.value.isOnline &&
            !_uiState.value.isBusy
        val cards = summary.targets.map { snap ->
            val title = when (snap.target) {
                SyncTarget.Ledgers -> "Ledgers"
                SyncTarget.StockItems -> "Stock items"
                SyncTarget.Vouchers -> "Vouchers"
            }
            val active = snap.liveProgress?.status?.isActive() == true
            SyncTargetCardUi(
                target = snap.target,
                title = title,
                available = snap.available,
                unavailableReason = snap.unavailableReason,
                statusLine = if (!snap.available) {
                    "Unavailable"
                } else {
                    snap.liveProgress.statusLineOrIdle()
                },
                lastSyncedLine = snap.lastSuccessfulAt?.let { "Last synced at $it" }
                    ?: snap.statistics?.lastSyncedAt?.let { "Last synced at $it" },
                canSync = snap.available && canStart && !active,
                canCancel = snap.available && active,
            )
        }
        _uiState.update {
            it.copy(
                targets = cards,
            )
        }
    }
}
