package com.budcom.android.feature.dashboard.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.dashboard.domain.model.DashboardSnapshot
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Aggregates stable public ports into a dashboard snapshot.
 */
class RefreshDashboardUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val restoreCompanySelection: RestoreCompanySelectionUseCase,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        validateSessionWhenCompanySelected: Boolean = true,
    ): DashboardSnapshot {
        val baseUrl = connectorStatus.currentBaseUrl()
        val isOnline = connectivityObserver.current()
        // Local DataStore may be empty while Connector already has a Desktop-selected company.
        if (companySession.observeSelectedCompanyId().first().isNullOrBlank()) {
            restoreCompanySelection()
        }
        val selectedCompanyId = companySession.observeSelectedCompanyId().first()

        var healthPresent = false
        var readinessStatus: String? = null
        var lastHealthAt: Long? = null
        var connectorError: AppError? = null

        when (val probe = connectorStatus.probeConnection()) {
            is AppResult.Success -> {
                healthPresent = true
                readinessStatus = probe.value.readiness?.status
                lastHealthAt = probe.value.checkedAtEpochMillis
            }
            is AppResult.Failure -> {
                connectorError = probe.error
            }
        }

        var selectedCompanyName: String? = null
        var sessionValidity = DashboardSessionValidity.NoCompany
        var lastSessionAt: Long? = null
        var sessionError: AppError? = null

        if (selectedCompanyId.isNullOrBlank()) {
            sessionValidity = DashboardSessionValidity.NoCompany
        } else {
            when (val selected = companySession.readSelectedCompany()) {
                is AppResult.Success -> {
                    selectedCompanyName = selected.value.companyName
                }
                is AppResult.Failure -> Unit
            }

            // Only re-validate against Connector when it is reachable; otherwise keep
            // the cached company and treat session as unknown (last-known UI retained).
            if (validateSessionWhenCompanySelected && healthPresent) {
                when (val validation = companySession.validateSessionStatus()) {
                    is AppResult.Success -> {
                        sessionValidity = validation.value.validity.toDashboard()
                        lastSessionAt = if (validation.value.validity == SessionValidity.Valid) {
                            timeProvider.nowEpochMillis()
                        } else {
                            null
                        }
                        selectedCompanyName = validation.value.companyName ?: selectedCompanyName
                        sessionError = validation.value.error
                    }
                    is AppResult.Failure -> {
                        sessionError = validation.error
                        sessionValidity = DashboardSessionValidity.Unknown
                    }
                }
            } else {
                sessionValidity = DashboardSessionValidity.Unknown
            }
        }

        return DashboardSnapshot(
            baseUrl = baseUrl,
            isOnline = isOnline,
            healthPresent = healthPresent,
            readinessStatus = readinessStatus,
            lastSuccessfulHealthCheckEpochMillis = lastHealthAt,
            connectorError = connectorError,
            selectedCompanyId = selectedCompanyId,
            selectedCompanyName = selectedCompanyName,
            sessionValidity = sessionValidity,
            lastSuccessfulSessionValidationEpochMillis = lastSessionAt,
            sessionError = sessionError,
        )
    }
}

/**
 * Probes Connector health/readiness only (dashboard Test Connection action).
 */
class ProbeConnectorConnectionUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
) {
    suspend operator fun invoke(): AppResult<ConnectorConnectionProbe> =
        connectorStatus.probeConnection()
}

/**
 * Re-validates the Connector session only (dashboard Validate Session action).
 */
class ValidateDashboardSessionUseCase @Inject constructor(
    private val companySession: CompanySessionPort,
    private val timeProvider: TimeProvider,
) {
    data class Result(
        val validity: DashboardSessionValidity,
        val companyId: String?,
        val companyName: String?,
        val validatedAtEpochMillis: Long?,
        val error: AppError?,
    )

    suspend operator fun invoke(): Result {
        val selectedId = companySession.observeSelectedCompanyId().first()
        if (selectedId.isNullOrBlank()) {
            return Result(
                validity = DashboardSessionValidity.NoCompany,
                companyId = null,
                companyName = null,
                validatedAtEpochMillis = null,
                error = null,
            )
        }
        return when (val validation = companySession.validateSessionStatus()) {
            is AppResult.Success -> Result(
                validity = validation.value.validity.toDashboard(),
                companyId = validation.value.companyId ?: selectedId,
                companyName = validation.value.companyName,
                validatedAtEpochMillis = if (validation.value.validity == SessionValidity.Valid) {
                    timeProvider.nowEpochMillis()
                } else {
                    null
                },
                error = validation.value.error,
            )
            is AppResult.Failure -> Result(
                validity = DashboardSessionValidity.Unknown,
                companyId = selectedId,
                companyName = null,
                validatedAtEpochMillis = null,
                error = validation.error,
            )
        }
    }
}

/**
 * Observes ambient dashboard context (connectivity, URL, selected company id).
 */
class ObserveDashboardContextUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) {
    data class Context(
        val isOnline: Boolean,
        val baseUrl: String,
        val selectedCompanyId: String?,
    )

    operator fun invoke(): Flow<Context> = combine(
        connectivityObserver.isOnline,
        connectorStatus.observeBaseUrl(),
        companySession.observeSelectedCompanyId(),
    ) { online, url, companyId ->
        Context(isOnline = online, baseUrl = url, selectedCompanyId = companyId)
    }
}

private fun SessionValidity.toDashboard(): DashboardSessionValidity = when (this) {
    SessionValidity.NoCompany -> DashboardSessionValidity.NoCompany
    SessionValidity.Valid -> DashboardSessionValidity.Valid
    SessionValidity.Invalid -> DashboardSessionValidity.Invalid
    SessionValidity.Unknown -> DashboardSessionValidity.Unknown
}
