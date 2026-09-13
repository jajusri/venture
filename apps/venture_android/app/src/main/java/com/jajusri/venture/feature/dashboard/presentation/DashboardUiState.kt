package com.jajusri.venture.feature.dashboard.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.UserVisibleErrorText
import com.jajusri.venture.feature.dashboard.domain.model.DashboardOperationalInputs
import com.jajusri.venture.feature.dashboard.domain.model.DashboardOperationalMode
import com.jajusri.venture.feature.dashboard.domain.model.DashboardSessionValidity
import com.jajusri.venture.feature.dashboard.domain.model.DashboardSnapshot
import com.jajusri.venture.feature.dashboard.domain.model.deriveOperationalMode

/**
 * Immutable presentation state for the operational dashboard.
 * Contains no Connector domain health/readiness models.
 */
data class DashboardUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isValidatingSession: Boolean = false,
    val isOnline: Boolean = true,
    val baseUrl: String = "",
    val connectorConnected: Boolean? = null,
    val readinessLabel: ReadinessLabel = ReadinessLabel.Unknown,
    val lastSuccessfulHealthCheckEpochMillis: Long? = null,
    val connectorError: DashboardUiError? = null,
    val selectedCompanyId: String? = null,
    val selectedCompanyName: String? = null,
    val sessionValidity: DashboardSessionValidity = DashboardSessionValidity.Unknown,
    val lastSuccessfulSessionValidationEpochMillis: Long? = null,
    val sessionError: DashboardUiError? = null,
    val operationalMode: DashboardOperationalMode = DashboardOperationalMode.PartiallyAvailable,
    val syncStatusLabel: String = "Never synced",
) {
    val isBusy: Boolean
        get() = isInitialLoading || isRefreshing || isTestingConnection || isValidatingSession

    /**
     * True only while a user-initiated probe/validate is running.
     * Dashboard refresh/initial load must not disable recovery actions (Settings, Test connection).
     */
    val isProbeBusy: Boolean
        get() = isTestingConnection || isValidatingSession

    val hasContent: Boolean
        get() = baseUrl.isNotBlank() ||
            connectorConnected != null ||
            selectedCompanyId != null ||
            connectorError != null
}

enum class ReadinessLabel {
    Ready,
    NotReady,
    Unknown,
}

sealed interface DashboardUiError {
    data class Offline(val message: String) : DashboardUiError
    data class Timeout(val message: String) : DashboardUiError
    data class Remote(val message: String, val httpStatus: Int?) : DashboardUiError
    data class Serialization(val message: String) : DashboardUiError
    data class Message(val message: String) : DashboardUiError
    data class Unexpected(val message: String) : DashboardUiError
}

sealed interface DashboardEvent {
    data object Refresh : DashboardEvent
    data object TestConnection : DashboardEvent
    data object ValidateSession : DashboardEvent
    data object OpenServerConfig : DashboardEvent
    data object OpenCompanySelection : DashboardEvent
    data object OpenMasterData : DashboardEvent
    data object OpenVouchers : DashboardEvent
    data object OpenLedgers : DashboardEvent
    data object OpenConnect : DashboardEvent
    data object OpenDincharya : DashboardEvent
    data object OpenBusinessProfile : DashboardEvent
    data object OpenCatalogue : DashboardEvent
    data object OpenSearch : DashboardEvent
    data object OpenSync : DashboardEvent
    data object OpenDiagnostics : DashboardEvent
    data object OpenSettings : DashboardEvent
}

internal fun AppError.toDashboardUiError(): DashboardUiError = when (this) {
    is AppError.Offline -> DashboardUiError.Offline(UserVisibleErrorText.OFFLINE)
    is AppError.Timeout -> DashboardUiError.Timeout(UserVisibleErrorText.TIMEOUT)
    is AppError.Remote -> DashboardUiError.Remote(UserVisibleErrorText.fromRemote(httpStatus, message), httpStatus)
    is AppError.Serialization -> DashboardUiError.Serialization(
        UserVisibleErrorText.sanitizeOr(message, UserVisibleErrorText.UNREADABLE),
    )
    is AppError.Message -> DashboardUiError.Message(UserVisibleErrorText.sanitizeOr(message, UserVisibleErrorText.UNEXPECTED))
    is AppError.Unexpected -> DashboardUiError.Unexpected(UserVisibleErrorText.fromThrowable(cause))
}

internal fun readinessLabelFromStatus(status: String?): ReadinessLabel = when (status) {
    null -> ReadinessLabel.Unknown
    "ready" -> ReadinessLabel.Ready
    else -> ReadinessLabel.NotReady
}

/**
 * Maps a domain snapshot into presentation state.
 * Operational mode always comes from [deriveOperationalMode].
 */
internal fun mapSnapshotToUiState(
    snapshot: DashboardSnapshot,
    prior: DashboardUiState = DashboardUiState(isInitialLoading = false),
): DashboardUiState {
    val keepStaleHealth = !snapshot.healthPresent && snapshot.connectorError != null
    val healthPresent = snapshot.healthPresent || (keepStaleHealth && prior.connectorConnected == true)
    val readinessStatus = when {
        snapshot.healthPresent -> snapshot.readinessStatus
        keepStaleHealth -> prior.readinessLabel.toStatus()
        else -> snapshot.readinessStatus ?: prior.readinessLabel.toStatus()
    }
    val connectorConnected = when {
        snapshot.healthPresent -> true
        snapshot.connectorError != null -> false
        else -> prior.connectorConnected
    }
    val baseUrl = snapshot.baseUrl.ifBlank { prior.baseUrl }
    val connectorUnavailable = !snapshot.healthPresent && snapshot.connectorError != null
    // Retain last-known company/session when Connector disconnects mid-session.
    val selectedCompanyId = snapshot.selectedCompanyId
        ?: prior.selectedCompanyId.takeIf { connectorUnavailable || !snapshot.isOnline }
    val selectedCompanyName = snapshot.selectedCompanyName
        ?: prior.selectedCompanyName.takeIf { selectedCompanyId != null }
    val sessionValidity = when {
        snapshot.sessionValidity != DashboardSessionValidity.NoCompany ||
            selectedCompanyId.isNullOrBlank() -> snapshot.sessionValidity
        connectorUnavailable || !snapshot.isOnline ->
            if (prior.sessionValidity == DashboardSessionValidity.Valid) {
                prior.sessionValidity
            } else {
                DashboardSessionValidity.Unknown
            }
        else -> snapshot.sessionValidity
    }
    val mode = deriveOperationalMode(
        DashboardOperationalInputs(
            isOnline = snapshot.isOnline,
            baseUrl = baseUrl,
            healthPresent = healthPresent,
            connectorErrorPresent = snapshot.connectorError != null,
            readinessStatus = readinessStatus,
            selectedCompanyId = selectedCompanyId,
            sessionValidity = sessionValidity,
        ),
    )
    return prior.copy(
        isInitialLoading = false,
        isRefreshing = false,
        isTestingConnection = false,
        isOnline = snapshot.isOnline,
        baseUrl = baseUrl,
        connectorConnected = connectorConnected,
        readinessLabel = readinessLabelFromStatus(readinessStatus),
        lastSuccessfulHealthCheckEpochMillis = snapshot.lastSuccessfulHealthCheckEpochMillis
            ?: prior.lastSuccessfulHealthCheckEpochMillis,
        connectorError = snapshot.connectorError?.toDashboardUiError(),
        selectedCompanyId = selectedCompanyId,
        selectedCompanyName = selectedCompanyName,
        sessionValidity = sessionValidity,
        lastSuccessfulSessionValidationEpochMillis =
            snapshot.lastSuccessfulSessionValidationEpochMillis
                ?: prior.lastSuccessfulSessionValidationEpochMillis,
        sessionError = snapshot.sessionError?.toDashboardUiError(),
        operationalMode = mode,
    )
}

internal fun DashboardUiState.withAuthoritativeMode(): DashboardUiState {
    val healthPresent = connectorConnected == true
    val mode = deriveOperationalMode(
        DashboardOperationalInputs(
            isOnline = isOnline,
            baseUrl = baseUrl,
            healthPresent = healthPresent,
            connectorErrorPresent = connectorError != null && connectorConnected != true,
            readinessStatus = readinessLabel.toStatus(),
            selectedCompanyId = selectedCompanyId,
            sessionValidity = sessionValidity,
        ),
    )
    return copy(operationalMode = mode)
}

private fun ReadinessLabel.toStatus(): String? = when (this) {
    ReadinessLabel.Ready -> "ready"
    ReadinessLabel.NotReady -> "not_ready"
    ReadinessLabel.Unknown -> null
}
