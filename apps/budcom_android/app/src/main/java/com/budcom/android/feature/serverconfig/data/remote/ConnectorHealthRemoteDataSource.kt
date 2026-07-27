package com.budcom.android.feature.serverconfig.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness

/**
 * Remote probe API for Connector system health endpoints.
 */
interface ConnectorHealthRemoteDataSource {
    suspend fun fetchHealth(): ApiResult<ConnectorHealth>
    suspend fun fetchReadiness(): ApiResult<ConnectorReadiness>
}
