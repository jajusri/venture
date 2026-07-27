package com.budcom.android.feature.diagnostics.domain.usecase

import com.budcom.android.BuildConfig
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.diagnostics.domain.model.ApplicationIdentity
import com.budcom.android.feature.diagnostics.domain.model.CompanyDiagnostic
import com.budcom.android.feature.diagnostics.domain.model.DiagnosticsSnapshot
import com.budcom.android.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import javax.inject.Inject

/**
 * Aggregates confirmed diagnostic sources. Does not invent health or readiness.
 */
class LoadDiagnosticsUseCase @Inject constructor(
    private val connectorStatus: ConnectorStatusPort,
    private val companySession: CompanySessionPort,
    private val connectionDiagnostics: ConnectionDiagnosticsPort,
    private val syncStatus: ObserveSyncStatusPort,
    private val refreshSyncOverview: RefreshSyncOverviewUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(refreshSync: Boolean = true): DiagnosticsSnapshot {
        if (refreshSync) {
            runCatching { refreshSyncOverview() }
        }

        val baseUrl = connectorStatus.currentBaseUrl()
        val application = ApplicationIdentity(
            appName = BuildConfig.APP_NAME,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            isDebuggable = BuildConfig.DEBUG,
        )

        var healthError: AppError? = null
        var readinessError: AppError? = null
        val probe = when (val result = connectorStatus.probeConnection()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                healthError = result.error
                null
            }
        }
        val readiness = probe?.readiness
        if (probe != null && readiness == null) {
            readinessError = AppError.Message("Readiness was not returned with the health probe.")
        }

        var connectionError: AppError? = null
        val connection = when (val result = connectionDiagnostics.loadConnectionDiagnostics()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                connectionError = result.error
                null
            }
        }

        val company = loadCompanyDiagnostic()
        val syncSummary = syncStatus.summary.value
        val ledgerLast = syncSummary.targets
            .firstOrNull { it.target == SyncTarget.Ledgers }
            ?.let { it.statistics?.lastSyncedAt ?: it.lastSuccessfulAt }
        val stockLast = syncSummary.targets
            .firstOrNull { it.target == SyncTarget.StockItems }
            ?.let { it.statistics?.lastSyncedAt ?: it.lastSuccessfulAt }
        val masterDataNote = buildMasterDataNote(ledgerLast, stockLast)

        return DiagnosticsSnapshot(
            loadedAtEpochMillis = timeProvider.nowEpochMillis(),
            baseUrl = baseUrl,
            application = application,
            health = probe?.health,
            healthError = healthError,
            readiness = readiness,
            readinessError = readinessError,
            connection = connection,
            connectionError = connectionError,
            company = company,
            syncSummary = syncSummary,
            searchAvailabilityNote =
                "Universal search is available in this app when a company session is active. " +
                    "There is no Connector search-availability diagnostic endpoint.",
            masterDataNote = masterDataNote,
            voucherNote =
                "Public voucher synchronization diagnostics are not available on the Connector. " +
                    "Voucher reads use the live company session.",
        )
    }

    private suspend fun loadCompanyDiagnostic(): CompanyDiagnostic {
        return when (val selected = companySession.readSelectedCompany()) {
            is AppResult.Failure -> CompanyDiagnostic(
                companyId = null,
                companyName = null,
                sessionValidity = SessionValidity.Unknown,
                sessionError = selected.error,
            )
            is AppResult.Success -> {
                val companyId = selected.value.companyId
                val companyName = selected.value.companyName
                if (companyId.isNullOrBlank()) {
                    return CompanyDiagnostic(
                        companyId = null,
                        companyName = null,
                        sessionValidity = SessionValidity.NoCompany,
                        sessionError = null,
                    )
                }
                when (val validation = companySession.validateSessionStatus()) {
                    is AppResult.Success -> CompanyDiagnostic(
                        companyId = validation.value.companyId ?: companyId,
                        companyName = validation.value.companyName ?: companyName,
                        sessionValidity = validation.value.validity,
                        sessionError = validation.value.error,
                    )
                    is AppResult.Failure -> CompanyDiagnostic(
                        companyId = companyId,
                        companyName = companyName,
                        sessionValidity = SessionValidity.Unknown,
                        sessionError = validation.error,
                    )
                }
            }
        }
    }

    private fun buildMasterDataNote(ledgerLast: String?, stockLast: String?): String {
        val parts = buildList {
            if (!ledgerLast.isNullOrBlank()) add("Ledgers last synced at $ledgerLast")
            if (!stockLast.isNullOrBlank()) add("Stock items last synced at $stockLast")
        }
        return if (parts.isEmpty()) {
            "No confirmed master-data sync timestamps are available yet."
        } else {
            parts.joinToString(". ") + "."
        }
    }
}
