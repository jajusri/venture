package com.budcom.android.feature.settings.presentation

import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerShareDefaultDestination
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingDefaultPeriod
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.settings.domain.model.ApplicationInformation
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.navigation.StartupRoutingState

data class SettingsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.System,
    val themeConfigurationError: String? = null,
    val themeSaveError: MasterDataUiError? = null,
    val baseUrl: String = "",
    val companyId: String? = null,
    val companyName: String? = null,
    val syncStatusLabel: String = "Never synced",
    val connectorVersion: String? = null,
    val connectorFactsError: MasterDataUiError? = null,
    val application: ApplicationInformation? = null,
    /** Freshly re-resolved whenever Settings opens — never cached from app-startup routing. */
    val secureConnectionState: StartupRoutingState? = null,
    val ledgerSharingStatementMode: LedgerStatementMode = LedgerStatementMode.Summary,
    val ledgerSharingDefaultPeriod: LedgerSharingDefaultPeriod = LedgerSharingDefaultPeriod.Last7Sales,
    val ledgerSharingDefaultDestination: LedgerShareDefaultDestination = LedgerShareDefaultDestination.AndroidShare,
) {
    val isBusy: Boolean
        get() = isInitialLoading || isRefreshing
}

sealed interface SettingsEvent {
    data object Refresh : SettingsEvent
    data object Retry : SettingsEvent
    data class SelectTheme(val preference: ThemePreference) : SettingsEvent
    data object OpenServerConfig : SettingsEvent
    data object OpenCompanySelection : SettingsEvent
    data object OpenSync : SettingsEvent
    data object OpenDiagnostics : SettingsEvent
    data object OpenSecurePairing : SettingsEvent
    data class SelectLedgerSharingStatementMode(val mode: LedgerStatementMode) : SettingsEvent
    data class SelectLedgerSharingDefaultPeriod(val period: LedgerSharingDefaultPeriod) : SettingsEvent
    data class SelectLedgerSharingDefaultDestination(val destination: LedgerShareDefaultDestination) : SettingsEvent
}

enum class SettingsNavigation {
    ServerConfig,
    CompanySelection,
    Sync,
    Diagnostics,
    SecurePairing,
}
