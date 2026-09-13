package com.jajusri.venture.feature.serverconfig.domain.model

/**
 * Connector health report fields exactly as returned by `GET /health`.
 */
data class ConnectorHealth(
    val status: String,
    val schemaVersion: String,
    val connectorVersion: String,
    val tallyReachable: Boolean,
    val readOnly: Boolean,
    val bindHost: String,
    val bindPort: Int,
    val networkExposure: String,
    val networkExposureWarning: String?,
    val networkPolicySatisfied: Boolean,
    val authenticatedLanAccessEnabled: Boolean,
    val services: List<ConnectorServiceStatus>,
    val startupCorrelationId: String?,
    val repositoryAvailable: Boolean,
    val databaseAccessible: Boolean,
    val serverTimeEpochMillis: Long? = null,
)

/**
 * Service status entry inside the health `services` array.
 */
data class ConnectorServiceStatus(
    val name: String,
    val running: Boolean,
    val ready: Boolean,
    val message: String? = null,
)

/**
 * Connector readiness report fields exactly as returned by `GET /ready`.
 */
data class ConnectorReadiness(
    val status: String,
    val repositoryAvailable: Boolean,
    val databaseAccessible: Boolean,
    val voucherSynchronizationComposed: Boolean,
    val voucherApplicationComposed: Boolean,
    /** HTTP status observed (200 when ready, 503 when not_ready). */
    val httpStatus: Int,
)

/**
 * Combined probe result after a successful health call (readiness optional).
 */
data class ConnectorConnectionProbe(
    val health: ConnectorHealth,
    val readiness: ConnectorReadiness?,
    val checkedAtEpochMillis: Long,
)
