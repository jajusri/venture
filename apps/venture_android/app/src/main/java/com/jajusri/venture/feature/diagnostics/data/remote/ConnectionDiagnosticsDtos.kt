package com.jajusri.venture.feature.diagnostics.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionDiagnosticsEnvelopeDto(
    val schemaVersion: String? = null,
    val connection: ConnectionDiagnosticsDto,
)

@Serializable
data class ConnectionDiagnosticsDto(
    val state: String,
    val host: String,
    val port: Int,
    val lastSuccessfulPingAt: String? = null,
    val lastErrorAt: String? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val totalRequests: Int = 0,
    val failedRequests: Int = 0,
    val reconnectAttempts: Int = 0,
    val averageLatencyMs: Double = 0.0,
    val poolActiveConnections: Int = 0,
    val poolWaitingRequests: Int = 0,
    val safeMode: Boolean = false,
    val circuitState: String,
    val lastRequest: LastRequestDiagnosticsDto? = null,
    val runtimeLimits: RuntimeLimitsDiagnosticsDto? = null,
)

@Serializable
data class LastRequestDiagnosticsDto(
    val correlationId: String,
    val collectionId: String? = null,
    val reportId: String? = null,
    val sentAt: String,
    val outcome: String? = null,
)

@Serializable
data class RuntimeLimitsDiagnosticsDto(
    val poolMaxConnections: Int = 0,
    val retryMaxAttempts: Int = 0,
    val minRequestIntervalMs: Int = 0,
    val maxRequestBytes: Int = 0,
    val maxResponseBytes: Int = 0,
    val circuitBreakerEnabled: Boolean = false,
    val timeoutMs: Int = 0,
)
