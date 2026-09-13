package com.jajusri.venture.feature.dashboard.domain.model

import com.jajusri.venture.core.common.AppError

/**
 * Aggregated operational snapshot for the dashboard vertical slice.
 *
 * Fields are independent so partial success is representable (e.g. health OK +
 * readiness 503, or selected company with failed validation).
 *
 * Does not embed feature-internal health DTO graphs — only status projections.
 */
data class DashboardSnapshot(
    val baseUrl: String,
    val isOnline: Boolean,
    val healthPresent: Boolean,
    val readinessStatus: String?,
    val lastSuccessfulHealthCheckEpochMillis: Long?,
    val connectorError: AppError?,
    val selectedCompanyId: String?,
    val selectedCompanyName: String?,
    val sessionValidity: DashboardSessionValidity,
    val lastSuccessfulSessionValidationEpochMillis: Long?,
    val sessionError: AppError?,
) {
    fun toOperationalInputs(): DashboardOperationalInputs = DashboardOperationalInputs(
        isOnline = isOnline,
        baseUrl = baseUrl,
        healthPresent = healthPresent,
        connectorErrorPresent = connectorError != null,
        readinessStatus = readinessStatus,
        selectedCompanyId = selectedCompanyId,
        sessionValidity = sessionValidity,
    )

    fun operationalMode(): DashboardOperationalMode =
        deriveOperationalMode(toOperationalInputs())
}

/**
 * Minimal inputs required to derive [DashboardOperationalMode].
 * Single authority for mode derivation — presentation must not re-implement this.
 */
data class DashboardOperationalInputs(
    val isOnline: Boolean,
    val baseUrl: String,
    val healthPresent: Boolean,
    val connectorErrorPresent: Boolean,
    val readinessStatus: String?,
    val selectedCompanyId: String?,
    val sessionValidity: DashboardSessionValidity,
)

/**
 * Local interpretation of Connector session validity for the dashboard.
 */
enum class DashboardSessionValidity {
    NoCompany,
    Valid,
    Invalid,
    Unknown,
}

/**
 * High-level operational mode for banners and a11y.
 */
enum class DashboardOperationalMode {
    Offline,
    NoServerConfiguration,
    ConnectorUnavailable,
    NotReady,
    NoCompanySelected,
    SessionInvalid,
    PartiallyAvailable,
    FullyOperational,
}

/**
 * Authoritative operational-mode derivation.
 */
fun deriveOperationalMode(inputs: DashboardOperationalInputs): DashboardOperationalMode {
    if (!inputs.isOnline) return DashboardOperationalMode.Offline
    if (inputs.baseUrl.isBlank()) return DashboardOperationalMode.NoServerConfiguration
    if (!inputs.healthPresent && inputs.connectorErrorPresent) {
        return DashboardOperationalMode.ConnectorUnavailable
    }
    val readinessStatus = inputs.readinessStatus
    if (inputs.healthPresent && readinessStatus != null && readinessStatus != "ready") {
        return DashboardOperationalMode.NotReady
    }
    if (inputs.selectedCompanyId.isNullOrBlank()) {
        return if (inputs.healthPresent && readinessStatus == "ready") {
            DashboardOperationalMode.NoCompanySelected
        } else {
            DashboardOperationalMode.PartiallyAvailable
        }
    }
    if (inputs.sessionValidity == DashboardSessionValidity.Invalid) {
        return DashboardOperationalMode.SessionInvalid
    }
    val fullyReady = inputs.healthPresent &&
        readinessStatus == "ready" &&
        inputs.sessionValidity == DashboardSessionValidity.Valid
    return if (fullyReady) {
        DashboardOperationalMode.FullyOperational
    } else {
        DashboardOperationalMode.PartiallyAvailable
    }
}
