package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Ledger-statement surface of [AuthenticatedConnectorApiPort], scoped to exactly the one
 * typed operation [AuthenticatedConnectorOperation.GetLedgerStatement] — matching the confirmed
 * Connector route `GET /ledgers/{id}/statement` exactly. Reuses the existing statement DTOs and
 * `toDomain()` mapper from [LedgerRemoteDataSource]'s own package (same package, `internal`
 * visibility) — no duplicate domain model is introduced. Never reads or decrypts a credential
 * itself, never touches the vault directly, and never builds an arbitrary URL. Exposes no list,
 * detail, group, stock, voucher, or sync operation.
 */
interface AuthenticatedLedgerStatementRemoteDataSource {
    suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): AppResult<LedgerStatement>
}

@Singleton
class DefaultAuthenticatedLedgerStatementRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedLedgerStatementRemoteDataSource {

    override suspend fun fetchLedgerStatement(
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> {
        val operation = AuthenticatedConnectorOperation.GetLedgerStatement(
            ledgerId = ledgerId,
            queryParams = mapOf("from" to range.from, "to" to range.to),
        )
        return when (val result = port.execute(operation)) {
            is AuthenticatedConnectorResult.Success -> runCatching {
                json.decodeFromString(LedgerStatementEnvelopeDto.serializer(), result.payload.rawJson).toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
            else -> AppResult.Failure(failurePolicy.mapFailure(result))
        }
    }
}
