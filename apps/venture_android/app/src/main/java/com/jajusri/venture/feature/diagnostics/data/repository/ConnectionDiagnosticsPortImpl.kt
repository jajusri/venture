package com.jajusri.venture.feature.diagnostics.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.RetryPolicy
import com.jajusri.venture.core.network.safeApiCall
import com.jajusri.venture.core.network.withRetry
import com.jajusri.venture.feature.diagnostics.data.remote.DiagnosticsApi
import com.jajusri.venture.feature.diagnostics.data.remote.toDomain
import com.jajusri.venture.feature.diagnostics.domain.model.ConnectionDiagnostics
import com.jajusri.venture.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionDiagnosticsPortImpl @Inject constructor(
    private val api: DiagnosticsApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
) : ConnectionDiagnosticsPort {
    override suspend fun loadConnectionDiagnostics(): AppResult<ConnectionDiagnostics> =
        when (
            val result = withRetry(retryPolicy) {
                safeApiCall(errorMapper, connectivityObserver) {
                    api.getConnectionDiagnostics().connection.toDomain()
                }
            }
        ) {
            is ApiResult.Success -> AppResult.Success(result.data)
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
        }
}
