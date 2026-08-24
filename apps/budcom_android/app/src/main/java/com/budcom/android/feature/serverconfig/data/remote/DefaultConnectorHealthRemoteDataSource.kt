package com.budcom.android.feature.serverconfig.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.model.ConnectorServiceStatus
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit-backed remote data source for Connector `/health` and `/ready`.
 */
@Singleton
class DefaultConnectorHealthRemoteDataSource @Inject constructor(
    private val api: ConnectorSystemApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
    private val json: Json,
) : ConnectorHealthRemoteDataSource {

    override suspend fun fetchHealth(): ApiResult<ConnectorHealth> {
        return withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getHealth().toDomain()
            }
        }
    }

    override suspend fun fetchReadiness(): ApiResult<ConnectorReadiness> {
        return withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                val response = api.getReady()
                val code = response.code()
                val body = response.body()
                    ?: response.errorBody()?.string()?.let { raw ->
                        runCatching { json.decodeFromString(ReadinessResponseDto.serializer(), raw) }
                            .getOrNull()
                    }
                when {
                    body != null && (code == 200 || code == 503) -> body.toDomain(httpStatus = code)
                    else -> throw retrofit2.HttpException(response)
                }
            }
        }
    }
}

internal fun HealthResponseDto.toDomain(): ConnectorHealth = ConnectorHealth(
    status = status,
    schemaVersion = schemaVersion,
    connectorVersion = connectorVersion,
    tallyReachable = tallyReachable,
    readOnly = readOnly,
    bindHost = bindHost,
    bindPort = bindPort,
    networkExposure = networkExposure,
    networkExposureWarning = networkExposureWarning,
    networkPolicySatisfied = networkPolicySatisfied,
    authenticatedLanAccessEnabled = authenticatedLanAccessEnabled,
    services = services.map {
        ConnectorServiceStatus(
            name = it.name,
            running = it.running,
            ready = it.ready,
            message = it.message,
        )
    },
    startupCorrelationId = startupCorrelationId,
    repositoryAvailable = repositoryAvailable,
    databaseAccessible = databaseAccessible,
    serverTimeEpochMillis = serverTimeEpochMillis,
)

internal fun ReadinessResponseDto.toDomain(httpStatus: Int): ConnectorReadiness = ConnectorReadiness(
    status = status,
    repositoryAvailable = repositoryAvailable,
    databaseAccessible = databaseAccessible,
    voucherSynchronizationComposed = voucherSynchronizationComposed,
    voucherApplicationComposed = voucherApplicationComposed,
    httpStatus = httpStatus,
)
