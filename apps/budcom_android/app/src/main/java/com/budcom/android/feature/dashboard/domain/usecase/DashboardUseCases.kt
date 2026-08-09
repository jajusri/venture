package com.budcom.android.feature.dashboard.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.dashboard.domain.model.DashboardSnapshot
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

/**
 * Aggregates stable public ports into a dashboard snapshot.
 */
class RefreshDashboardUseCase @Inject constructor(
    private val operationalStatus: ConnectorOperationalStatusPort,
    private val companySession: CompanySessionPort,
    private val restoreCompanySelection: RestoreCompanySelectionUseCase,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        validateSessionWhenCompanySelected: Boolean = true,
    ): DashboardSnapshot {
        val isOnline = connectivityObserver.current()
        // A null complete object can mean either an empty cache or a legacy ID-only cache.
        // Restoration resolves and atomically persists the matching name in both cases.
        if (companySession.observeSelectedCompany().first() == null) {
            restoreCompanySelection()
        }
        val selectedCompanyId = companySession.observeSelectedCompanyId().first()

        val baseUrl: String
        var healthPresent = false
        var readinessStatus: String? = null
        var lastHealthAt: Long? = null
        var connectorError: AppError? = null

        when (val status = operationalStatus.currentStatus()) {
            is ConnectorOperationalStatus.Legacy -> {
                baseUrl = status.baseUrl
                when (val probe = status.healthProbe) {
                    is AppResult.Success -> {
                        healthPresent = true
                        readinessStatus = probe.value.readiness?.status
                        lastHealthAt = probe.value.checkedAtEpochMillis
                    }
                    is AppResult.Failure -> {
                        connectorError = probe.error
                    }
                }
            }

            is ConnectorOperationalStatus.AuthenticatedHealthy -> {
                // healthPresent=true here is exactly what unblocks the session-validation
                // attempt below for a securely paired device — previously that attempt was
                // gated behind the always-LEGACY probe and so never ran for these devices
                // (TD-016). No authenticated /ready equivalent exists, so readinessStatus stays
                // unset rather than fabricated.
                baseUrl = status.endpointDisplay
                healthPresent = true
                lastHealthAt = status.checkedAtEpochMillis
            }

            is ConnectorOperationalStatus.AuthenticatedUnavailable -> {
                baseUrl = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER
                connectorError = status.error
            }

            is ConnectorOperationalStatus.AuthenticatedPreparing -> {
                baseUrl = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER
                connectorError = AppError.Message(status.message)
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
data class ConnectorConnectionCheck(
    val endpointDisplay: String,
    val readinessStatus: String?,
    val checkedAtEpochMillis: Long,
)

class ProbeConnectorConnectionUseCase @Inject constructor(
    private val operationalStatus: ConnectorOperationalStatusPort,
) {
    suspend operator fun invoke(): AppResult<ConnectorConnectionCheck> = when (val status = operationalStatus.currentStatus()) {
        is ConnectorOperationalStatus.Legacy -> when (val probe = status.healthProbe) {
            is AppResult.Success -> AppResult.Success(
                ConnectorConnectionCheck(
                    endpointDisplay = status.baseUrl,
                    readinessStatus = probe.value.readiness?.status,
                    checkedAtEpochMillis = probe.value.checkedAtEpochMillis,
                ),
            )
            is AppResult.Failure -> probe
        }
        is ConnectorOperationalStatus.AuthenticatedHealthy -> AppResult.Success(
            ConnectorConnectionCheck(
                endpointDisplay = status.endpointDisplay,
                readinessStatus = null,
                checkedAtEpochMillis = status.checkedAtEpochMillis,
            ),
        )
        is ConnectorOperationalStatus.AuthenticatedUnavailable -> AppResult.Failure(status.error)
        is ConnectorOperationalStatus.AuthenticatedPreparing -> AppResult.Failure(AppError.Message(status.message))
    }
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
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardContextUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val transportGate: ConnectorTransportSelectionGate,
) {
    data class Context(
        val isOnline: Boolean,
        val baseUrl: String,
        val selectedCompanyId: String?,
        val selectedCompanyName: String?,
    )

    operator fun invoke(): Flow<Context> = combine(
        connectivityObserver.isOnline,
        connectorStatus.observeBaseUrl(),
        companySession.observeSelectedCompany(),
    ) { online, url, company ->
        Context(
            isOnline = online,
            baseUrl = url,
            selectedCompanyId = company?.id,
            selectedCompanyName = company?.name,
        )
    }.mapLatest { context ->
        when (transportGate.resolve()) {
            ConnectorTransportSelection.LEGACY -> context
            ConnectorTransportSelection.AUTHENTICATED -> context.copy(
                baseUrl = ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER,
            )
        }
    }
}

private fun SessionValidity.toDashboard(): DashboardSessionValidity = when (this) {
    SessionValidity.NoCompany -> DashboardSessionValidity.NoCompany
    SessionValidity.Valid -> DashboardSessionValidity.Valid
    SessionValidity.Invalid -> DashboardSessionValidity.Invalid
    SessionValidity.Unknown -> DashboardSessionValidity.Unknown
}
