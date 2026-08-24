package com.budcom.android.feature.serverconfig.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire DTO for `GET /health` — fields match connector `createHealthRouter` JSON.
 */
@Serializable
data class HealthResponseDto(
    val status: String,
    val schemaVersion: String,
    val connectorVersion: String,
    val tallyReachable: Boolean,
    val readOnly: Boolean,
    val bindHost: String,
    val bindPort: Int,
    val networkExposure: String,
    val networkExposureWarning: String? = null,
    val networkPolicySatisfied: Boolean,
    val authenticatedLanAccessEnabled: Boolean,
    val services: List<ServiceStatusDto> = emptyList(),
    val startupCorrelationId: String? = null,
    /**
     * Present on current Connector contracts; omitted by some live builds (e.g. 0.3.1).
     * Default avoids rejecting an otherwise valid health payload.
     */
    val repositoryAvailable: Boolean = false,
    val databaseAccessible: Boolean = false,
    /**
     * Authoritative server-side clock reading (epoch millis) at report time. Absent on older
     * Connector builds that predate this field — default keeps an otherwise-valid health payload
     * from being rejected, mirroring [repositoryAvailable]'s own back-compat discipline above.
     */
    val serverTimeEpochMillis: Long? = null,
)

@Serializable
data class ServiceStatusDto(
    val name: String,
    val running: Boolean,
    val ready: Boolean,
    val message: String? = null,
)

/**
 * Wire DTO for `GET /ready` — fields match connector `ReadinessReport`.
 */
@Serializable
data class ReadinessResponseDto(
    val status: String,
    val repositoryAvailable: Boolean,
    val databaseAccessible: Boolean,
    val voucherSynchronizationComposed: Boolean,
    val voucherApplicationComposed: Boolean,
)
