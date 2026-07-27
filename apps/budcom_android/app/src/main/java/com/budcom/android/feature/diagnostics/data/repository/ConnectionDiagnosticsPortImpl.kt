package com.budcom.android.feature.diagnostics.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.diagnostics.data.remote.DiagnosticsApi
import com.budcom.android.feature.diagnostics.data.remote.toDomain
import com.budcom.android.feature.diagnostics.domain.model.ConnectionDiagnostics
import com.budcom.android.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
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
