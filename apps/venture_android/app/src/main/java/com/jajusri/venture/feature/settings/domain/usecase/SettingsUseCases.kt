package com.jajusri.venture.feature.settings.domain.usecase

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort
import com.jajusri.venture.feature.settings.domain.model.SettingsSnapshot
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.port.ApplicationIdentityPort
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
import com.jajusri.venture.feature.sync.domain.port.ObserveSyncStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSettingsSnapshotUseCase @Inject constructor(
    private val applicationIdentity: ApplicationIdentityPort,
    private val themePreferences: ThemePreferencesRepository,
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val syncStatus: ObserveSyncStatusPort,
    private val connectivity: NetworkConnectivityObserver,
    private val transportGate: ConnectorTransportSelectionGate,
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
    }.mapLatest { snapshot ->
        when (transportGate.resolve()) {
            ConnectorTransportSelection.LEGACY -> snapshot
            ConnectorTransportSelection.AUTHENTICATED -> snapshot.copy(
                baseUrl = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER,
            )
        }
    }.distinctUntilChanged()
}

class RefreshSettingsConnectorFactsUseCase @Inject constructor(
    private val operationalStatus: ConnectorOperationalStatusPort,
    private val companySession: CompanySessionPort,
) {
    data class ConnectorFacts(
        val endpointDisplay: String,
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
        return when (val status = operationalStatus.currentStatus()) {
            is ConnectorOperationalStatus.Legacy -> when (val probe = status.healthProbe) {
                is AppResult.Success -> ConnectorFacts(
                    endpointDisplay = status.baseUrl,
                    connectorVersion = probe.value.health.connectorVersion,
                    companyName = company?.companyName,
                    companyId = company?.companyId,
                )
                is AppResult.Failure -> ConnectorFacts(
                    endpointDisplay = status.baseUrl,
                    connectorVersion = null,
                    companyName = company?.companyName,
                    companyId = company?.companyId,
                    probeError = probe.error,
                )
            }
            is ConnectorOperationalStatus.AuthenticatedHealthy -> ConnectorFacts(
                endpointDisplay = status.endpointDisplay,
                connectorVersion = null,
                companyName = company?.companyName,
                companyId = company?.companyId,
            )
            is ConnectorOperationalStatus.AuthenticatedUnavailable -> ConnectorFacts(
                endpointDisplay = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER,
                connectorVersion = null,
                companyName = company?.companyName,
                companyId = company?.companyId,
                probeError = status.error,
            )
            is ConnectorOperationalStatus.AuthenticatedPreparing -> ConnectorFacts(
                endpointDisplay = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER,
                connectorVersion = null,
                companyName = company?.companyName,
                companyId = company?.companyId,
                probeError = AppError.Message(status.message),
            )
        }
    }
}
