package com.budcom.android.feature.settings.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.settings.domain.model.SettingsSnapshot
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.port.ApplicationIdentityPort
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import com.budcom.android.feature.settings.domain.repository.ThemePreferencesRepository
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class ObserveThemePreferenceUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    operator fun invoke(): Flow<ThemeObservation> = repository.observeTheme()
}

class SetThemePreferenceUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(preference: ThemePreference): AppResult<Unit> =
        repository.setTheme(preference)
}

/**
 * Aggregates confirmed settings sources. Does not invent preferences or Connector metadata.
 */
class ObserveSettingsSnapshotUseCase @Inject constructor(
    private val applicationIdentity: ApplicationIdentityPort,
    private val themePreferences: ThemePreferencesRepository,
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val syncStatus: ObserveSyncStatusPort,
    private val connectivity: NetworkConnectivityObserver,
) {
    operator fun invoke(): Flow<SettingsSnapshot> = combine(
        themePreferences.observeTheme(),
        connectorStatus.observeBaseUrl(),
        companySession.observeSelectedCompanyId(),
        syncStatus.summary,
        connectivity.isOnline,
    ) { themeObs, baseUrl, companyId, syncSummary, isOnline ->
        val (theme, themeError) = when (themeObs) {
            is ThemeObservation.Available -> themeObs.preference to null
            is ThemeObservation.Invalid -> ThemePreference.System to
                "Stored theme value \"${themeObs.rawValue}\" is invalid. Choose System, Light, or Dark."
        }
        val syncLabel = when {
            syncSummary.isAnySyncActive -> "Sync in progress"
            !syncSummary.latestSuccessfulAt.isNullOrBlank() ->
                "Last sync completed at ${syncSummary.latestSuccessfulAt}"
            !syncSummary.latestFailedMessage.isNullOrBlank() -> "Last sync failed"
            else -> "Never synced"
        }
        SettingsSnapshot(
            themePreference = theme,
            themeConfigurationError = themeError,
            baseUrl = baseUrl,
            isOnline = isOnline,
            companyId = companyId,
            companyName = null,
            syncStatusLabel = syncLabel,
            connectorVersion = null,
            application = applicationIdentity.read(),
        )
    }.distinctUntilChanged()
}

class RefreshSettingsConnectorFactsUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
) {
    data class ConnectorFacts(
        val connectorVersion: String?,
        val companyName: String?,
        val companyId: String?,
        val probeError: AppError? = null,
    )

    suspend operator fun invoke(): ConnectorFacts {
        val company = when (val selected = companySession.readSelectedCompany()) {
            is AppResult.Success -> selected.value
            is AppResult.Failure -> null
        }
        return when (val probe = connectorStatus.probeConnection()) {
            is AppResult.Success -> ConnectorFacts(
                connectorVersion = probe.value.health.connectorVersion,
                companyName = company?.companyName,
                companyId = company?.companyId,
            )
            is AppResult.Failure -> ConnectorFacts(
                connectorVersion = null,
                companyName = company?.companyName,
                companyId = company?.companyId,
                probeError = probe.error,
            )
        }
    }
}
