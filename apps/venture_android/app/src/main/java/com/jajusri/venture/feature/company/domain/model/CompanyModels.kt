package com.jajusri.venture.feature.company.domain.model

/**
 * Company list item from Connector discovery.
 */
data class ConnectorCompany(
    val id: String,
    val name: String,
    val financialYear: String?,
    val booksFrom: String?,
    val baseCurrency: String?,
)

/**
 * Company discovery envelope metadata from Connector.
 */
data class CompanyDiscoverySnapshot(
    val items: List<ConnectorCompany>,
    val schemaVersion: String,
    val dataFreshnessAt: String,
    val contractVersion: String,
    val status: String,
    val tallyReachable: Boolean,
    val dataQualityStatus: String?,
    val dataQualityReason: String?,
    val reason: String?,
)

/**
 * Connector session selected company projection.
 */
data class SessionSelectedCompany(
    val id: String,
    val name: String,
)

/**
 * Connector session snapshot.
 */
data class ConnectorSessionSnapshot(
    val sessionId: String,
    val selectedCompany: SessionSelectedCompany?,
    val connectionStatus: String,
    val connectorVersion: String,
    val erpType: String,
    val selectedAt: String?,
    val lastValidatedAt: String?,
    val createdAt: String,
    val contractVersion: String?,
)

/**
 * Company selection outcome from Connector session API.
 */
data class CompanySelectionOutcome(
    val status: String,
    val session: ConnectorSessionSnapshot,
    val reason: String?,
    val httpStatus: Int,
)

/**
 * Session validation outcome from Connector session API.
 */
data class SessionValidationOutcome(
    val status: String,
    val session: ConnectorSessionSnapshot,
    val reason: String?,
    val companyId: String?,
    val companyName: String?,
    val httpStatus: Int,
)
