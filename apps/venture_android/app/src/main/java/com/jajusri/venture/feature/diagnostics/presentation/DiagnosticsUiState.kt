package com.jajusri.venture.feature.diagnostics.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.feature.company.domain.port.SessionValidity
import com.jajusri.venture.feature.diagnostics.domain.model.DiagnosticsSnapshot
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.masterdata.presentation.toMasterDataUiError
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.sync.domain.model.SyncStatusSummary

/**
 * Presentation state for Diagnostics. Contains no Connector DTO types.
 */
data class DiagnosticsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val loadedAtLabel: String? = null,
    val application: ApplicationSectionUi? = null,
    val connector: ConnectorSectionUi = ConnectorSectionUi(),
    val company: CompanySectionUi = CompanySectionUi(),
    val sync: SyncSectionUi = SyncSectionUi(),
    val searchNote: String = "",
    val masterDataNote: String = "",
    val voucherNote: String = "",
    val bannerError: MasterDataUiError? = null,
) {
    val isBusy: Boolean
        get() = isInitialLoading || isRefreshing
}

data class ApplicationSectionUi(
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val buildTypeLabel: String,
)

data class ConnectorSectionUi(
    val baseUrl: String = "",
    val health: ConnectorHealth? = null,
    val healthError: MasterDataUiError? = null,
    val readiness: ConnectorReadiness? = null,
    val readinessError: MasterDataUiError? = null,
    val connectionState: String? = null,
    val connectionHostPort: String? = null,
    val connectionCircuit: String? = null,
    val connectionSafeMode: Boolean? = null,
    val connectionLatency: String? = null,
    val connectionLastPing: String? = null,
    val connectionLastError: String? = null,
    val connectionError: MasterDataUiError? = null,
)

data class CompanySectionUi(
    val companyId: String? = null,
    val companyName: String? = null,
    val sessionLabel: String = "Unknown",
    val sessionError: MasterDataUiError? = null,
)

data class SyncSectionUi(
    val activeLabel: String = "Idle",
    val lastSuccess: String? = null,
    val lastFailure: String? = null,
    val updatedAtEpochMillis: Long? = null,
)

sealed interface DiagnosticsEvent {
    data object Refresh : DiagnosticsEvent
    data object RecheckHealth : DiagnosticsEvent
    data object RecheckReadiness : DiagnosticsEvent
    data object Retry : DiagnosticsEvent
    data object OpenServerConfig : DiagnosticsEvent
    data object OpenCompanySelection : DiagnosticsEvent
}

enum class DiagnosticsNavigation {
    ServerConfig,
    CompanySelection,
}

internal fun DiagnosticsSnapshot.toUiState(
    prior: DiagnosticsUiState,
    isOnline: Boolean,
): DiagnosticsUiState {
    val hasAnyConfirmed = health != null ||
        readiness != null ||
        connection != null ||
        company.companyId != null ||
        syncSummary.targets.isNotEmpty() ||
        application.versionName.isNotBlank()

    val sectionErrors = listOfNotNull(healthError, readinessError, connectionError, company.sessionError)
    val banner = when {
        !isOnline -> MasterDataUiError.Offline("Device is offline.")
        !hasAnyConfirmed && sectionErrors.isNotEmpty() -> sectionErrors.first().toMasterDataUiError()
        else -> null
    }

    return prior.copy(
        isInitialLoading = false,
        isRefreshing = false,
        isOnline = isOnline,
        loadedAtLabel = "Captured at epoch $loadedAtEpochMillis",
        application = ApplicationSectionUi(
            appName = application.appName,
            versionName = application.versionName,
            versionCode = application.versionCode,
            buildTypeLabel = if (application.isDebuggable) "Debug" else "Release",
        ),
        connector = ConnectorSectionUi(
            baseUrl = baseUrl,
            health = health,
            healthError = healthError?.toMasterDataUiError(),
            readiness = readiness,
            readinessError = readinessError?.toMasterDataUiError(),
            connectionState = connection?.state,
            connectionHostPort = connection?.let { "${it.host}:${it.port}" },
            connectionCircuit = connection?.circuitState,
            connectionSafeMode = connection?.safeMode,
            connectionLatency = connection?.let { "%.1f ms".format(it.averageLatencyMs) },
            connectionLastPing = connection?.lastSuccessfulPingAt,
            connectionLastError = connection?.let { c ->
                listOfNotNull(c.lastErrorCode, c.lastErrorMessage, c.lastErrorAt)
                    .joinToString(" · ")
                    .ifBlank { null }
            },
            connectionError = connectionError?.toMasterDataUiError(),
        ),
        company = CompanySectionUi(
            companyId = company.companyId,
            companyName = company.companyName,
            sessionLabel = company.sessionValidity.toLabel(),
            sessionError = company.sessionError?.toMasterDataUiError(),
        ),
        sync = syncSummary.toSectionUi(),
        searchNote = searchAvailabilityNote,
        masterDataNote = masterDataNote,
        voucherNote = voucherNote,
        bannerError = banner,
    )
}

private fun SessionValidity.toLabel(): String = when (this) {
    SessionValidity.NoCompany -> "No company"
    SessionValidity.Valid -> "Valid"
    SessionValidity.Invalid -> "Invalid"
    SessionValidity.Unknown -> "Unknown"
}

private fun SyncStatusSummary.toSectionUi(): SyncSectionUi {
    val active = when {
        isAnySyncActive -> "Active: ${activeTarget?.name ?: "unknown"} (${activeStatus?.name ?: "unknown"})"
        else -> "Idle"
    }
    return SyncSectionUi(
        activeLabel = active,
        lastSuccess = latestSuccessfulAt,
        lastFailure = latestFailedMessage,
        updatedAtEpochMillis = lastUpdatedEpochMillis.takeIf { it > 0L },
    )
}

internal fun AppError.toDiagnosticsUiError(): MasterDataUiError = toMasterDataUiError()
