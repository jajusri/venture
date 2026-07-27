package com.budcom.android.feature.diagnostics.domain.model

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary

/**
 * Local application identity from Android BuildConfig (not Connector).
 */
data class ApplicationIdentity(
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val isDebuggable: Boolean,
)

/**
 * Confirmed Connector connection diagnostics from `GET /diagnostics/connection`.
 */
data class ConnectionDiagnostics(
    val state: String,
    val host: String,
    val port: Int,
    val lastSuccessfulPingAt: String?,
    val lastErrorAt: String?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val totalRequests: Int,
    val failedRequests: Int,
    val reconnectAttempts: Int,
    val averageLatencyMs: Double,
    val poolActiveConnections: Int,
    val poolWaitingRequests: Int,
    val safeMode: Boolean,
    val circuitState: String,
    val lastRequestCorrelationId: String?,
    val lastRequestSentAt: String?,
    val lastRequestOutcome: String?,
    val runtimeTimeoutMs: Int?,
)

data class CompanyDiagnostic(
    val companyId: String?,
    val companyName: String?,
    val sessionValidity: SessionValidity,
    val sessionError: AppError?,
)

/**
 * Aggregated diagnostics snapshot. Missing sections use Failure/Unknown honestly.
 */
data class DiagnosticsSnapshot(
    val loadedAtEpochMillis: Long,
    val baseUrl: String,
    val application: ApplicationIdentity,
    val health: ConnectorHealth?,
    val healthError: AppError?,
    val readiness: ConnectorReadiness?,
    val readinessError: AppError?,
    val connection: ConnectionDiagnostics?,
    val connectionError: AppError?,
    val company: CompanyDiagnostic,
    val syncSummary: SyncStatusSummary,
    val searchAvailabilityNote: String,
    val masterDataNote: String,
    val voucherNote: String,
)

sealed interface DiagnosticsSectionState<out T> {
    data object Loading : DiagnosticsSectionState<Nothing>
    data class Available<T>(val value: T) : DiagnosticsSectionState<T>
    data class Unavailable(val error: AppError) : DiagnosticsSectionState<Nothing>
    data object Unknown : DiagnosticsSectionState<Nothing>
}
