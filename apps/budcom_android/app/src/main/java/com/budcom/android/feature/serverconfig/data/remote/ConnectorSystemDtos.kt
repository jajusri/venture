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
    val repositoryAvailable: Boolean,
    val databaseAccessible: Boolean,
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
