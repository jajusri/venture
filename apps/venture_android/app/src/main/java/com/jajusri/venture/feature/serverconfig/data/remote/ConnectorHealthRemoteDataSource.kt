package com.jajusri.venture.feature.serverconfig.data.remote

import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness

/**
 * Remote probe API for Connector system health endpoints.
 */
interface ConnectorHealthRemoteDataSource {
    suspend fun fetchHealth(): ApiResult<ConnectorHealth>
    suspend fun fetchReadiness(): ApiResult<ConnectorReadiness>
}
