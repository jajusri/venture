package com.jajusri.venture.feature.masterdata.ledger.data.remote

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.jajusri.venture.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ledger-list surface of [AuthenticatedConnectorApiPort], scoped to exactly the one Phase
 * 3R operation the current Android production ledger-list call needs
 * ([AuthenticatedConnectorOperation.GetLedgers], matching the legacy `GET /ledgers` route exactly
 * — not `GetCompanyLedgers`, which is a different, currently-uncalled route). Reuses the existing
 * ledger DTOs and `toDomain()`/`toApiSortBy()`/`toApiSortDirection()`/`normalizedText()` mappers
 * from [LedgerRemoteDataSource]'s own package (same package, `internal` visibility) — no duplicate
 * domain model or query-mapping logic is introduced. Never reads or decrypts a credential itself,
 * never touches the vault directly, and never builds an arbitrary URL — only the fixed typed
 * [AuthenticatedConnectorOperation.GetLedgers] member is ever constructed. Exposes no stock,
 * voucher, ledger-detail, ledger-group, or sync operation.
 */
interface AuthenticatedLedgerRemoteDataSource {
    suspend fun fetchLedgers(query: LedgerQuery): AppResult<LedgerPage>
}

@Singleton
class DefaultAuthenticatedLedgerRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedLedgerRemoteDataSource {

    override suspend fun fetchLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        val operation = AuthenticatedConnectorOperation.GetLedgers(queryParams = query.toAuthenticatedQueryParams())
        return when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching {
                json.decodeFromString(LedgerListResponseDto.serializer(), result.payload.rawJson).toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
    }
}

/**
 * Mirrors exactly what [DefaultLedgerRemoteDataSource.fetchLedgers] sends as `@Query` parameters
 * to the legacy `GET /ledgers` route — `query` omitted entirely when blank (matching Retrofit's
 * null-`@Query`-param-omission behaviour), `page`/`pageSize`/`sortBy`/`sortDirection` always
 * present.
 */
private fun LedgerQuery.toAuthenticatedQueryParams(): Map<String, String> = buildMap {
    normalizedText()?.let { put("query", it) }
    put("page", page.coerceAtLeast(1).toString())
    put("pageSize", pageSize.coerceIn(1, 100).toString())
    put("sortBy", toApiSortBy())
    put("sortDirection", toApiSortDirection())
}
