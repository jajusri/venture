package com.budcom.android.feature.company.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.CompanySelectionOutcome
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

interface CompanyRemoteDataSource {
    suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot>
    suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot>
    suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome>
    suspend fun validateSession(): ApiResult<SessionValidationOutcome>
    suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot>
}

@Singleton
class DefaultCompanyRemoteDataSource @Inject constructor(
    private val api: CompanyApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
    private val json: Json,
) : CompanyRemoteDataSource {

    override suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot> =
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getCompanies().toDomain()
            }
        }

    override suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot> =
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getSession().toDomain()
            }
        }

    override suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome> =
        safeApiCall(errorMapper, connectivityObserver) {
            val response = api.selectCompany(SelectCompanyRequestDto(companyId = companyId))
            val body = decodeResponse(response, CompanySelectionResultDto.serializer())
                ?: throw HttpException(response)
            CompanySelectionOutcome(
                status = body.status,
                session = body.session.toDomain(contractVersion = null),
                reason = body.reason,
                httpStatus = response.code(),
            )
        }

    override suspend fun validateSession(): ApiResult<SessionValidationOutcome> =
        safeApiCall(errorMapper, connectivityObserver) {
            val response = api.validateSession()
            val body = decodeResponse(response, SessionValidationResultDto.serializer())
                ?: throw HttpException(response)
            SessionValidationOutcome(
                status = body.status,
                session = body.session.toDomain(contractVersion = null),
                reason = body.reason,
                companyId = body.companyId,
                companyName = body.companyName,
                httpStatus = response.code(),
            )
        }

    override suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot> =
        safeApiCall(errorMapper, connectivityObserver) {
            val response = api.clearSession()
            val body = decodeResponse(response, SessionClearResultDto.serializer())
                ?: throw HttpException(response)
            body.session.toDomain(contractVersion = body.contractVersion)
        }

    private fun <T> decodeResponse(
        response: Response<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? {
        response.body()?.let { return it }
        val raw = response.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }
}

internal fun CompanyListResultDto.toDomain(): CompanyDiscoverySnapshot = CompanyDiscoverySnapshot(
    items = items.map {
        com.budcom.android.feature.company.domain.model.ConnectorCompany(
            id = it.id,
            name = it.name,
            financialYear = it.financialYear,
            booksFrom = it.booksFrom,
            baseCurrency = it.baseCurrency,
        )
    },
    schemaVersion = schemaVersion,
    dataFreshnessAt = dataFreshnessAt,
    contractVersion = contractVersion,
    status = status,
    tallyReachable = tallyReachable,
    dataQualityStatus = dataQuality?.status,
    dataQualityReason = dataQuality?.reason,
    reason = reason,
)

internal fun SessionEnvelopeDto.toDomain(): ConnectorSessionSnapshot =
    session.toDomain(contractVersion = contractVersion)

internal fun SessionDto.toDomain(contractVersion: String?): ConnectorSessionSnapshot =
    ConnectorSessionSnapshot(
        sessionId = sessionId,
        selectedCompany = selectedCompany?.let { SessionSelectedCompany(id = it.id, name = it.name) },
        connectionStatus = connectionStatus,
        connectorVersion = connectorVersion,
        erpType = erpType,
        selectedAt = selectedAt,
        lastValidatedAt = lastValidatedAt,
        createdAt = createdAt,
        contractVersion = contractVersion,
    )
