package com.jajusri.venture.feature.serverconfig.data.remote

import retrofit2.Response
import retrofit2.http.GET

/**
 * Confirmed Connector system endpoints only.
 */
interface ConnectorSystemApi {
    @GET("health")
    suspend fun getHealth(): HealthResponseDto

    /**
     * Returns 200 when ready and 503 when not_ready; both carry [ReadinessResponseDto].
     */
    @GET("ready")
    suspend fun getReady(): Response<ReadinessResponseDto>
}
