package com.budcom.android.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferences
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferencesStore
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.usecase.ObserveSettingsSnapshotUseCase
import com.budcom.android.feature.settings.domain.usecase.RefreshSettingsConnectorFactsUseCase
import com.budcom.android.feature.settings.domain.usecase.SetThemePreferenceUseCase
import com.budcom.android.navigation.ResolveStartupRoutingState
import dagger.hilt.android.lifecycle.HiltViewModel
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
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsSnapshotUseCase,
    private val setThemePreference: SetThemePreferenceUseCase,
    private val refreshConnectorFacts: RefreshSettingsConnectorFactsUseCase,
    private val resolveStartupRoutingState: ResolveStartupRoutingState,
    private val ledgerSharingPreferencesStore: LedgerSharingPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<SettingsNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<SettingsNavigation> = _navigation.asSharedFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { snapshot ->
                _uiState.update { prior ->
                    prior.copy(
                        isInitialLoading = false,
                        isOnline = snapshot.isOnline,
                        themePreference = snapshot.themePreference,
                        themeConfigurationError = snapshot.themeConfigurationError,
                        baseUrl = snapshot.baseUrl,
                        companyId = snapshot.companyId ?: prior.companyId,
                        companyName = snapshot.companyName ?: prior.companyName,
                        syncStatusLabel = snapshot.syncStatusLabel,
                        connectorVersion = snapshot.connectorVersion ?: prior.connectorVersion,
                        application = snapshot.application,
                    )
                }
            }
        }
        viewModelScope.launch {
            ledgerSharingPreferencesStore.observation.collect { prefs ->
                _uiState.update {
                    it.copy(
                        ledgerSharingStatementMode = prefs.statementMode,
                        ledgerSharingDefaultPeriod = prefs.defaultPeriod,
                        ledgerSharingDefaultDestination = prefs.defaultDestination,
                    )
                }
            }
        }
        refreshFacts()
        refreshSecureConnectionState()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Refresh,
            SettingsEvent.Retry,
            -> {
                refreshFacts()
                refreshSecureConnectionState()
            }
            is SettingsEvent.SelectTheme -> selectTheme(event.preference)
            SettingsEvent.OpenServerConfig ->
                viewModelScope.launch { _navigation.emit(SettingsNavigation.ServerConfig) }
            SettingsEvent.OpenCompanySelection ->
                viewModelScope.launch { _navigation.emit(SettingsNavigation.CompanySelection) }
            SettingsEvent.OpenSync ->
                viewModelScope.launch { _navigation.emit(SettingsNavigation.Sync) }
            SettingsEvent.OpenDiagnostics ->
                viewModelScope.launch { _navigation.emit(SettingsNavigation.Diagnostics) }
            SettingsEvent.OpenSecurePairing ->
                viewModelScope.launch { _navigation.emit(SettingsNavigation.SecurePairing) }
            is SettingsEvent.SelectLedgerSharingStatementMode -> saveLedgerSharingPreferences { it.copy(statementMode = event.mode) }
            is SettingsEvent.SelectLedgerSharingDefaultPeriod -> saveLedgerSharingPreferences { it.copy(defaultPeriod = event.period) }
            is SettingsEvent.SelectLedgerSharingDefaultDestination ->
                saveLedgerSharingPreferences { it.copy(defaultDestination = event.destination) }
        }
    }

    private fun saveLedgerSharingPreferences(transform: (LedgerSharingPreferences) -> LedgerSharingPreferences) {
        viewModelScope.launch {
            val current = LedgerSharingPreferences(
                statementMode = _uiState.value.ledgerSharingStatementMode,
                defaultPeriod = _uiState.value.ledgerSharingDefaultPeriod,
                defaultDestination = _uiState.value.ledgerSharingDefaultDestination,
            )
            ledgerSharingPreferencesStore.save(transform(current))
        }
    }

    /**
     * Re-resolved every time Settings is opened/refreshed (never cached from app-startup
     * routing) — the user may open Settings, tap "Secure this connection," come back, and expects
     * the migration action to reflect whatever the vault holds right now, not a stale snapshot.
     */
    private fun refreshSecureConnectionState() {
        viewModelScope.launch {
            val state = resolveStartupRoutingState()
            _uiState.update { it.copy(secureConnectionState = state) }
        }
    }

    private fun selectTheme(preference: ThemePreference) {
        viewModelScope.launch {
            _uiState.update { it.copy(themeSaveError = null) }
            when (val result = setThemePreference(preference)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            themePreference = preference,
                            themeConfigurationError = null,
                            themeSaveError = null,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(themeSaveError = result.error.toMasterDataUiError())
                    }
                }
            }
        }
    }

    private fun refreshFacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, connectorFactsError = null) }
            val facts = refreshConnectorFacts()
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isInitialLoading = false,
                    baseUrl = facts.endpointDisplay,
                    connectorVersion = facts.connectorVersion ?: it.connectorVersion,
                    companyId = facts.companyId ?: it.companyId,
                    companyName = facts.companyName ?: it.companyName,
                    connectorFactsError = facts.probeError?.toMasterDataUiError(),
                )
            }
        }
    }
}
