package com.budcom.android.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.dashboard.domain.usecase.ObserveDashboardContextUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ProbeConnectorConnectionUseCase
import com.budcom.android.feature.dashboard.domain.usecase.RefreshDashboardUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ValidateDashboardSessionUseCase
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
class DashboardViewModel @Inject constructor(
    private val refreshDashboard: RefreshDashboardUseCase,
    private val probeConnectorConnection: ProbeConnectorConnectionUseCase,
    private val validateDashboardSession: ValidateDashboardSessionUseCase,
    private val observeDashboardContext: ObserveDashboardContextUseCase,
    private val observeSyncStatus: ObserveSyncStatusPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<DashboardNavigation>(extraBufferCapacity = 8)
    val navigation: SharedFlow<DashboardNavigation> = _navigation.asSharedFlow()

    @Volatile
    private var refreshInFlight = false

    /**
     * Foreground/lifecycle reconciliation backstop. Call from a `repeatOnLifecycle(RESUMED)`
     * block in the Compose layer: cancellation when the screen leaves the active state
     * (backgrounded, locked, navigated away) stops this via ordinary structured-concurrency
     * cancellation of the enclosing coroutine — no separate start/stop bookkeeping is needed.
     *
     * Physical iQOO testing showed the OS network-loss callback that drives [refresh]'s
     * automatic reconnect (see the `wentOffline`/`cameBackOnline` handling below) fires
     * reliably only ~1 in 10 times, which can leave "Fully operational" stale indefinitely with
     * no other trigger. This reuses the exact same bounded [refresh] path (and its
     * [refreshInFlight] guard) that manual Refresh and the network-restore auto-refresh already
     * use — it is a backstop for a missed/delayed OS callback, not a second reconnect
     * implementation. [NetworkConnectivityObserver.current] is a fresh synchronous OS query
     * (not a cached callback value), so each tick re-derives the true state regardless of
     * whether the network callback ever fired.
     */
    suspend fun reconcileWhileActive() {
        reconcileOnce()
        while (true) {
            delay(FOREGROUND_RECONCILE_INTERVAL_MS)
            reconcileOnce()
        }
    }

    /**
     * A known "no usable network" fact is authoritative and local — checking it first and
     * short-circuiting straight to an immediate offline state when it is false means a
     * foreground reconciliation tick never spends the Connector health probe's connect timeout
     * (or any network I/O at all) on a probe that cannot possibly succeed. This bypasses
     * [mapSnapshotToUiState]'s `keepStaleHealth` anti-flicker debounce via [withAuthoritativeMode]
     * — the same bypass the `wentOffline` transition below already uses — precisely because that
     * debounce exists for *ambiguous* transient probe failures while a network route exists, not
     * for an authoritative local fact that no network exists at all. When a network route does
     * exist, this falls through to the unchanged [refresh] path (and its debounce) exactly as
     * before.
     */
    private fun reconcileOnce() {
        if (connectivityObserver.current()) {
            refresh(isInitial = false)
        } else {
            _uiState.update { state ->
                state.copy(
                    isOnline = false,
                    connectorConnected = false,
                    connectorError = DashboardUiError.Offline("Device is offline."),
                ).withAuthoritativeMode()
            }
        }
    }

    init {
        viewModelScope.launch {
            // Tracks isOnline transitions so a network drop/restore refreshes Connector
            // reachability automatically, without waiting for a manual Refresh tap. `null`
            // means "no prior emission yet" (app just started), which must never itself count
            // as a transition.
            var previousIsOnline: Boolean? = null
            observeDashboardContext().collect { context ->
                val wentOffline = previousIsOnline == true && !context.isOnline
                val cameBackOnline = previousIsOnline == false && context.isOnline
                previousIsOnline = context.isOnline

                _uiState.update { state ->
                    val next = state.copy(
                        isOnline = context.isOnline,
                        baseUrl = context.baseUrl.ifBlank { state.baseUrl },
                        selectedCompanyId = context.selectedCompanyId,
                        sessionValidity = if (context.selectedCompanyId.isNullOrBlank()) {
                            DashboardSessionValidity.NoCompany
                        } else if (state.sessionValidity == DashboardSessionValidity.NoCompany) {
                            DashboardSessionValidity.Unknown
                        } else {
                            state.sessionValidity
                        },
                        selectedCompanyName = context.selectedCompanyName,
                    )
                    if (wentOffline) {
                        // Invalidate stale reachability immediately (no network round-trip —
                        // we already know locally there is no route). Pairing, selected
                        // company, and synced data are untouched.
                        next.copy(
                            connectorConnected = false,
                            connectorError = DashboardUiError.Offline("Device is offline."),
                        ).withAuthoritativeMode()
                    } else {
                        next.withAuthoritativeMode()
                    }
                }

                if (cameBackOnline) {
                    // Reuse the exact same bounded refresh path manual Refresh uses — no
                    // second reconnect implementation. refreshInFlight already guards against
                    // overlapping this with a concurrent manual/initial refresh.
                    refresh(isInitial = false)
                }
            }
        }
        viewModelScope.launch {
            observeSyncStatus.summary.collect { summary ->
                val label = when {
                    summary.isAnySyncActive -> "Sync in progress"
                    !summary.latestSuccessfulAt.isNullOrBlank() ->
                        "Last sync completed at ${summary.latestSuccessfulAt}"
                    !summary.latestFailedMessage.isNullOrBlank() -> "Last sync failed"
                    else -> "Never synced"
                }
                _uiState.update { it.copy(syncStatusLabel = label) }
            }
        }
        refresh(isInitial = true)
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Refresh -> refresh(isInitial = false)
            DashboardEvent.TestConnection -> testConnectionOnly()
            DashboardEvent.ValidateSession -> validateSessionOnly()
            DashboardEvent.OpenServerConfig -> emitNav(DashboardNavigation.ServerConfig)
            DashboardEvent.OpenCompanySelection -> emitNav(DashboardNavigation.CompanySelection)
            DashboardEvent.OpenMasterData -> emitNav(DashboardNavigation.MasterData)
            DashboardEvent.OpenVouchers -> emitNav(DashboardNavigation.Vouchers)
            DashboardEvent.OpenLedgers -> emitNav(DashboardNavigation.Ledgers)
            DashboardEvent.OpenConnect -> emitNav(DashboardNavigation.Connect)
            DashboardEvent.OpenSearch -> emitNav(DashboardNavigation.Search)
            DashboardEvent.OpenSync -> emitNav(DashboardNavigation.Sync)
            DashboardEvent.OpenDiagnostics -> emitNav(DashboardNavigation.Diagnostics)
            DashboardEvent.OpenSettings -> emitNav(DashboardNavigation.Settings)
        }
    }

    private fun emitNav(target: DashboardNavigation) {
        viewModelScope.launch { _navigation.emit(target) }
    }

    private fun refresh(isInitial: Boolean) {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            try {
                _uiState.update {
                    if (isInitial && !it.hasContent) {
                        it.copy(isInitialLoading = true)
                    } else {
                        it.copy(isRefreshing = true)
                    }
                }
                _uiState.update { mapSnapshotToUiState(refreshDashboard(), it) }
            } catch (_: Throwable) {
                // Never leave the user on a permanent loading dead-end.
                _uiState.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                    ).withAuthoritativeMode()
                }
            } finally {
                refreshInFlight = false
                _uiState.update { state ->
                    if (state.isInitialLoading || state.isRefreshing) {
                        state.copy(isInitialLoading = false, isRefreshing = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun testConnectionOnly() {
        if (_uiState.value.isTestingConnection || _uiState.value.isValidatingSession) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            when (val result = probeConnectorConnection()) {
                is AppResult.Success -> {
                    val probe = result.value
                    _uiState.update { state ->
                        state.copy(
                            isTestingConnection = false,
                            isInitialLoading = false,
                            connectorConnected = true,
                            baseUrl = probe.endpointDisplay,
                            readinessLabel = readinessLabelFromStatus(probe.readinessStatus),
                            lastSuccessfulHealthCheckEpochMillis = probe.checkedAtEpochMillis,
                            connectorError = null,
                        ).withAuthoritativeMode()
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isTestingConnection = false,
                            isInitialLoading = false,
                            connectorConnected = false,
                            connectorError = result.error.toDashboardUiError(),
                        ).withAuthoritativeMode()
                    }
                }
            }
        }
    }

    private fun validateSessionOnly() {
        if (_uiState.value.isValidatingSession || _uiState.value.isTestingConnection) return
        if (_uiState.value.selectedCompanyId.isNullOrBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingSession = true) }
            val result = validateDashboardSession()
            _uiState.update { state ->
                state.copy(
                    isValidatingSession = false,
                    selectedCompanyId = result.companyId ?: state.selectedCompanyId,
                    selectedCompanyName = result.companyName ?: state.selectedCompanyName,
                    sessionValidity = result.validity,
                    lastSuccessfulSessionValidationEpochMillis =
                        result.validatedAtEpochMillis
                            ?: state.lastSuccessfulSessionValidationEpochMillis,
                    sessionError = result.error?.toDashboardUiError(),
                ).withAuthoritativeMode()
            }
        }
    }

    companion object {
        /**
         * [reconcileWhileActive] tick interval. Chosen as roughly double the Connector
         * health-probe's own connect timeout (`NetworkConstants.CONNECT_TIMEOUT_SECONDS` = 15s),
         * so a missed OS network callback is corrected well within one screen-viewing session
         * while never being so frequent that a slow/failing probe could still be in flight when
         * the next tick is due — though [refreshInFlight] makes overlap impossible either way.
         */
        const val FOREGROUND_RECONCILE_INTERVAL_MS = 30_000L
    }
}

enum class DashboardNavigation {
    ServerConfig,
    CompanySelection,
    MasterData,
    Vouchers,
    Ledgers,
    Connect,
    Search,
    Sync,
    Diagnostics,
    Settings,
}
