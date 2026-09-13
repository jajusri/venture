package com.jajusri.venture.feature.diagnostics.data.remote

import com.jajusri.venture.feature.diagnostics.domain.model.ConnectionDiagnostics

internal fun ConnectionDiagnosticsDto.toDomain(): ConnectionDiagnostics = ConnectionDiagnostics(
    state = state,
    host = host,
    port = port,
    lastSuccessfulPingAt = lastSuccessfulPingAt,
    lastErrorAt = lastErrorAt,
    lastErrorCode = lastErrorCode,
    lastErrorMessage = lastErrorMessage,
    totalRequests = totalRequests,
    failedRequests = failedRequests,
    reconnectAttempts = reconnectAttempts,
    averageLatencyMs = averageLatencyMs,
    poolActiveConnections = poolActiveConnections,
    poolWaitingRequests = poolWaitingRequests,
    safeMode = safeMode,
    circuitState = circuitState,
    lastRequestCorrelationId = lastRequest?.correlationId,
    lastRequestSentAt = lastRequest?.sentAt,
    lastRequestOutcome = lastRequest?.outcome,
    runtimeTimeoutMs = runtimeLimits?.timeoutMs,
)
