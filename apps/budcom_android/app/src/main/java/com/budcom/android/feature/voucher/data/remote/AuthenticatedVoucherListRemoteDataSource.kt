package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The voucher-list surface of [AuthenticatedConnectorApiPort], scoped to exactly the one Phase 3R
 * operation [VoucherRepositoryImpl.refreshVouchers][com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl]
 * needs ([AuthenticatedConnectorOperation.ListVouchers], matching the legacy `GET /api/v1/vouchers`
 * route exactly — not `SearchVouchers`, a different, currently-uncalled route). Voucher-detail
 * refresh is deliberately out of scope for this adapter: `GetVoucherById` cannot yet carry the
 * Connector-required `company` query parameter, so `refreshVoucherDetails` stays on the legacy
 * transport until that operation gains query-parameter support in a later phase.
 *
 * Reuses the existing voucher DTOs and `toDomain()`/`toApiSortParam()`/`normalizedSearch()`/
 * `normalizedOptional()` mappers from [VoucherRemoteDataSource]'s own package (same package,
 * `internal` visibility) — no duplicate domain model or query-mapping logic is introduced. Never
 * reads or decrypts a credential itself, never touches the vault directly, and never builds an
 * arbitrary URL — only the fixed typed [AuthenticatedConnectorOperation.ListVouchers] member is
 * ever constructed. Exposes no detail, search, snapshot, or sync operation.
 */
interface AuthenticatedVoucherListRemoteDataSource {
    suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage>
}

@Singleton
class DefaultAuthenticatedVoucherListRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedVoucherListRemoteDataSource {

    override suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        val operation = AuthenticatedConnectorOperation.ListVouchers(queryParams = query.toAuthenticatedQueryParams())
        return when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching {
                val envelope = json.decodeFromString(VoucherListEnvelopeDto.serializer(), result.payload.rawJson)
                val page = envelope.toDomain()
                if (query.includeDetails) page.copy(fullDetails = envelope.data.toDetailsDomain()) else page
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
    }
}

/**
 * Mirrors exactly what [DefaultVoucherRemoteDataSource.fetchVouchers] sends as `@Query` parameters
 * to the legacy `GET /api/v1/vouchers` route — `company`/`from`/`to`/`page`/`pageSize`/`sort`
 * always present (matching the legacy call, which always resolves and sends all of these); `q`/
 * `voucherType`/`voucherNumber`/`partyName` omitted entirely when blank (matching Retrofit's
 * null-`@Query`-param-omission behaviour).
 */
private fun VoucherQuery.toAuthenticatedQueryParams(): Map<String, String> = buildMap {
    put("company", companyId)
    put("from", dateRange.from)
    put("to", dateRange.to)
    put("page", page.coerceAtLeast(1).toString())
    put("pageSize", pageSize.coerceIn(1, 100).toString())
    put("sort", sort.toApiSortParam())
    normalizedSearch()?.let { put("q", it) }
    normalizedOptional(voucherType)?.let { put("voucherType", it) }
    normalizedOptional(voucherNumber)?.let { put("voucherNumber", it) }
    normalizedOptional(partyName)?.let { put("partyName", it) }
    if (includeDetails) put("includeDetails", "true")
}
