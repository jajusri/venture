package com.budcom.android.feature.masterdata.stockitem.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The stock-item-list surface of [AuthenticatedConnectorApiPort], scoped to exactly the one Phase
 * 3R operation the current Android production stock-item-list call needs
 * ([AuthenticatedConnectorOperation.GetStockItems], matching the legacy `GET /stock-items` route
 * exactly — not `GetCompanyStockItems`, a different, currently-uncalled route). Reuses the
 * existing stock-item DTOs and `toDomain()`/`toApiSortBy()`/`toApiSortDirection()`/
 * `normalizedText()` mappers from [StockItemRemoteDataSource]'s own package (same package,
 * `internal` visibility) — no duplicate domain model or query-mapping logic is introduced. Never
 * reads or decrypts a credential itself, never touches the vault directly, and never builds an
 * arbitrary URL — only the fixed typed [AuthenticatedConnectorOperation.GetStockItems] member is
 * ever constructed. Exposes no stock-group, stock-category, unit, godown, or sync operation.
 */
interface AuthenticatedStockItemRemoteDataSource {
    suspend fun fetchStockItems(query: StockItemQuery): AppResult<StockItemPage>
}

@Singleton
class DefaultAuthenticatedStockItemRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedStockItemRemoteDataSource {

    override suspend fun fetchStockItems(query: StockItemQuery): AppResult<StockItemPage> {
        val operation = AuthenticatedConnectorOperation.GetStockItems(queryParams = query.toAuthenticatedQueryParams())
        return when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching {
                json.decodeFromString(StockItemListResponseDto.serializer(), result.payload.rawJson).toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
    }
}

/**
 * Mirrors exactly what [DefaultStockItemRemoteDataSource.fetchStockItems] sends as `@Query`
 * parameters to the legacy `GET /stock-items` route — `query` omitted entirely when blank
 * (matching Retrofit's null-`@Query`-param-omission behaviour), `page`/`pageSize`/`sortBy`/
 * `sortDirection` always present.
 */
private fun StockItemQuery.toAuthenticatedQueryParams(): Map<String, String> = buildMap {
    normalizedText()?.let { put("query", it) }
    put("page", page.coerceAtLeast(1).toString())
    put("pageSize", pageSize.coerceIn(1, MasterDataBrowserDefaults.MAX_PAGE_SIZE).toString())
    put("sortBy", toApiSortBy())
    put("sortDirection", toApiSortDirection())
}
