package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The voucher-detail surface of [AuthenticatedConnectorApiPort], scoped to exactly the one Phase
 * 3S-D2-corrected typed operation [VoucherRepositoryImpl.refreshVoucherDetails][com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl]
 * needs ([AuthenticatedConnectorOperation.GetVoucherById], matching the legacy
 * `GET /api/v1/vouchers/{id}?company={companyId}` route exactly). Deliberately a separate boundary
 * from [AuthenticatedVoucherListRemoteDataSource] rather than an added method on it — that
 * adapter's own committed contract (interface doc, Phase 3S-D1 evidence) is list-only and exposes
 * no detail operation; extending it would violate that already-committed boundary.
 *
 * Reuses the existing voucher-detail DTOs and `toDomain()` mapper from
 * [VoucherRemoteDataSource]'s own package (same package, `internal` visibility) — no duplicate
 * domain model is introduced. Never reads or decrypts a credential itself, never touches the vault
 * directly, and never builds an arbitrary URL — only the fixed typed
 * [AuthenticatedConnectorOperation.GetVoucherById] member is ever constructed, and only with the
 * caller's own voucherId/companyId (both validated non-blank by the operation itself). Exposes no
 * list, search, snapshot, or sync operation.
 */
interface AuthenticatedVoucherDetailRemoteDataSource {
    suspend fun fetchVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>
}

@Singleton
class DefaultAuthenticatedVoucherDetailRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedVoucherDetailRemoteDataSource {

    override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
        val operation = AuthenticatedConnectorOperation.GetVoucherById(voucherId = voucherId, companyId = companyId)
        return when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching {
                val envelope = json.decodeFromString(VoucherDetailsEnvelopeDto.serializer(), result.payload.rawJson)
                val voucher = envelope.data.voucher
                    ?: return AppResult.Failure(AppError.Remote(404, "NOT_FOUND", "Voucher was not found."))
                voucher.toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
    }
}
