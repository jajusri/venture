package com.budcom.android.feature.company.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.CompanySelectionOutcome
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The company/session surface of [AuthenticatedConnectorApiPort], scoped to exactly the five
 * Phase 3R operations this repository needs. Reuses the existing company/session DTOs and
 * `toDomain()` mappers from [CompanyRemoteDataSource]/[CompanyDtos] (same package) — no duplicate
 * domain model is introduced. Every non-Success outcome is mapped through the shared
 * [AuthenticatedRepositoryFailurePolicy]; this class never reads or decrypts a credential itself,
 * never touches the vault directly, and never parses an arbitrary URL — only the fixed typed
 * [AuthenticatedConnectorOperation] members are ever constructed.
 *
 * A successful (2xx) authenticated response only ever carries the response body — the exact HTTP
 * status (200/201/204) is not distinguishable from [AuthenticatedConnectorResult.Success] alone,
 * so [CompanySelectionOutcome.httpStatus]/[SessionValidationOutcome.httpStatus] are populated with
 * 200 for this path, matching the common case documented in the Phase 3P route matrix. Exact
 * recovery-relevant non-2xx statuses (`NO_COMPANY_SELECTED` and `SESSION_EXPIRED`) are converted to
 * typed results by the transport/failure policy without exposing arbitrary response detail. Other
 * structured business rejections still surface as [AppResult.Failure] rather than the legacy
 * path's structured-outcome-at-non-2xx shape; Room/local state remain preserved either way.
 */
interface AuthenticatedCompanyRemoteDataSource {
    suspend fun fetchCompanies(): AppResult<CompanyDiscoverySnapshot>
    suspend fun fetchSession(): AppResult<ConnectorSessionSnapshot>
    suspend fun selectCompany(companyId: String): AppResult<CompanySelectionOutcome>
    suspend fun validateSession(): AppResult<SessionValidationOutcome>
    suspend fun clearSession(): AppResult<ConnectorSessionSnapshot>
}

@Singleton
class DefaultAuthenticatedCompanyRemoteDataSource @Inject constructor(
    private val port: AuthenticatedConnectorApiPort,
    private val failurePolicy: AuthenticatedRepositoryFailurePolicy,
    private val json: Json,
) : AuthenticatedCompanyRemoteDataSource {

    override suspend fun fetchCompanies(): AppResult<CompanyDiscoverySnapshot> =
        execute(AuthenticatedConnectorOperation.GetCompanies, CompanyListResultDto.serializer()) { it.toDomain() }

    override suspend fun fetchSession(): AppResult<ConnectorSessionSnapshot> =
        execute(AuthenticatedConnectorOperation.GetSession, SessionEnvelopeDto.serializer()) { it.toDomain() }

    override suspend fun selectCompany(companyId: String): AppResult<CompanySelectionOutcome> =
        execute(AuthenticatedConnectorOperation.SelectCompany(companyId), CompanySelectionResultDto.serializer()) { body ->
            CompanySelectionOutcome(
                status = body.status,
                session = body.session.toDomain(contractVersion = null),
                reason = body.reason,
                httpStatus = SUCCESSFUL_HTTP_STATUS,
            )
        }

    override suspend fun validateSession(): AppResult<SessionValidationOutcome> =
        execute(AuthenticatedConnectorOperation.ValidateSession, SessionValidationResultDto.serializer()) { body ->
            SessionValidationOutcome(
                status = body.status,
                session = body.session.toDomain(contractVersion = null),
                reason = body.reason,
                companyId = body.companyId,
                companyName = body.companyName,
                httpStatus = SUCCESSFUL_HTTP_STATUS,
            )
        }

    override suspend fun clearSession(): AppResult<ConnectorSessionSnapshot> =
        execute(AuthenticatedConnectorOperation.ClearSessionCompany, SessionClearResultDto.serializer()) { body ->
            body.session.toDomain(contractVersion = body.contractVersion)
        }

    private suspend fun <Dto, Domain> execute(
        operation: AuthenticatedConnectorOperation,
        serializer: KSerializer<Dto>,
        toDomain: (Dto) -> Domain,
    ): AppResult<Domain> = when (val result = port.execute(operation)) {
        is AuthenticatedConnectorResult.Success -> runCatching { toDomain(json.decodeFromString(serializer, result.payload.rawJson)) }
            .fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.Serialization("The Connector response could not be parsed.", it)) },
            )
        else -> AppResult.Failure(failurePolicy.mapFailure(result))
    }

    private companion object {
        const val SUCCESSFUL_HTTP_STATUS = 200
    }
}
