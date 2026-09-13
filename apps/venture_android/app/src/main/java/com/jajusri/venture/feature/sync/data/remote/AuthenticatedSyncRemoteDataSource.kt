package com.jajusri.venture.feature.sync.data.remote

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.jajusri.venture.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.feature.sync.domain.model.SyncMode
import com.jajusri.venture.feature.sync.domain.model.SyncOutcome
import com.jajusri.venture.feature.sync.domain.model.SyncProgress
import com.jajusri.venture.feature.sync.domain.model.SyncRunSummary
import com.jajusri.venture.feature.sync.domain.model.SyncStatisticsSummary
import com.jajusri.venture.feature.sync.domain.model.SyncTarget
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared-synchronization surface of [AuthenticatedConnectorApiPort], mirroring
 * [SyncRemoteDataSource]'s own method shape exactly (same five methods, `ApiResult` swapped for
 * `AppResult`) so [com.jajusri.venture.feature.sync.data.repository.SyncRepositoryImpl] can select
 * between them without any other change to its own dispatch/state logic. Covers only the 11
 * Connector sync operations the current legacy [SyncApi] exposes — no run-detail, remote
 * cache-clear, storage-integrity, or backup operation is included, since none of those has a
 * current Android production caller (confirmed by [SyncRepository]/[SyncApi] having no such
 * method at all).
 *
 * `Vouchers` has no public cancel/status/statistics/runs route — [SyncRepositoryImpl] already
 * short-circuits those four calls for `Vouchers` with a local-only result before ever resolving a
 * transport, so the `Vouchers` branches below exist only for `when` exhaustiveness, mirroring the
 * legacy [DefaultSyncRemoteDataSource]'s own `error(...)` backstop for the same case — never
 * actually reached in practice.
 *
 * Reuses the existing sync DTOs and `toOutcome()`/`toDomain()`/`toSummary()` mappers from
 * [SyncMappers.kt]'s own package (same package, `internal` visibility) — no duplicate domain
 * model is introduced. Never reads or decrypts a credential itself, never touches the vault
 * directly, and never builds an arbitrary URL.
 */
interface AuthenticatedSyncRemoteDataSource {
    suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome>
    suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress>
    suspend fun fetchStatus(target: SyncTarget): AppResult<SyncProgress>
    suspend fun fetchStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary>
    suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>>
}

@Singleton
class DefaultAuthenticatedSyncRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedSyncRemoteDataSource {

    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        val operation = when (target) {
            SyncTarget.Ledgers -> AuthenticatedConnectorOperation.StartLedgerSync
            SyncTarget.StockItems -> AuthenticatedConnectorOperation.StartStockItemSync
            SyncTarget.Vouchers -> AuthenticatedConnectorOperation.StartVoucherSync
        }
        return execute(operation) { rawJson ->
            when (target) {
                SyncTarget.Ledgers -> json.decodeFromString(LedgerSyncResultDto.serializer(), rawJson).toOutcome()
                SyncTarget.StockItems -> json.decodeFromString(StockSyncResultDto.serializer(), rawJson).toOutcome()
                SyncTarget.Vouchers -> json.decodeFromString(VoucherSyncResultDto.serializer(), rawJson).toOutcome()
            }
        }
    }

    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> {
        val operation = when (target) {
            SyncTarget.Ledgers -> AuthenticatedConnectorOperation.CancelLedgerSync
            SyncTarget.StockItems -> AuthenticatedConnectorOperation.CancelStockItemSync
            SyncTarget.Vouchers -> return AppResult.Failure(AppError.Message("Public voucher sync cancel is not available."))
        }
        return execute(operation) { rawJson -> json.decodeFromString(SyncCancelResponseDto.serializer(), rawJson).progress.toDomain() }
    }

    override suspend fun fetchStatus(target: SyncTarget): AppResult<SyncProgress> {
        val operation = when (target) {
            SyncTarget.Ledgers -> AuthenticatedConnectorOperation.LedgerSyncStatus
            SyncTarget.StockItems -> AuthenticatedConnectorOperation.StockItemSyncStatus
            SyncTarget.Vouchers -> return AppResult.Failure(AppError.Message("Voucher sync status is unavailable."))
        }
        return execute(operation) { rawJson ->
            json.decodeFromString(SyncStatusResponseDto.serializer(), rawJson).let { it.progress.toDomain(it.schedulerState?.toDomain()) }
        }
    }

    override suspend fun fetchStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> {
        val operation = when (target) {
            SyncTarget.Ledgers -> AuthenticatedConnectorOperation.LedgerSyncStatistics
            SyncTarget.StockItems -> AuthenticatedConnectorOperation.StockItemSyncStatistics
            SyncTarget.Vouchers -> return AppResult.Failure(AppError.Message("Voucher sync statistics are unavailable."))
        }
        return execute(operation) { rawJson ->
            when (target) {
                SyncTarget.Ledgers -> json.decodeFromString(LedgerStatisticsResponseDto.serializer(), rawJson).statistics.toSummary()
                SyncTarget.StockItems -> json.decodeFromString(StockStatisticsResponseDto.serializer(), rawJson).statistics.toSummary()
                SyncTarget.Vouchers -> error("unreachable — Vouchers returns above before an operation is constructed")
            }
        }
    }

    override suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>> {
        val operation = when (target) {
            SyncTarget.Ledgers -> AuthenticatedConnectorOperation.LedgerSyncRuns(queryParams = mapOf("limit" to limit.toString()))
            SyncTarget.StockItems -> AuthenticatedConnectorOperation.StockItemSyncRuns(queryParams = mapOf("limit" to limit.toString()))
            SyncTarget.Vouchers -> return AppResult.Success(emptyList())
        }
        return execute(operation) { rawJson -> json.decodeFromString(SyncRunsResponseDto.serializer(), rawJson).runs.map { it.toDomain(target) } }
    }

    private suspend fun <T> execute(operation: AuthenticatedConnectorOperation, decode: (String) -> T): AppResult<T> =
        when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching { decode(result.payload.rawJson) }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
}
