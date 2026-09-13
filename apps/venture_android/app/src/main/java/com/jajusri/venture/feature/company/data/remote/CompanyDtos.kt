package com.jajusri.venture.feature.company.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompanyListResultDto(
    val items: List<CompanyListItemDto>,
    val schemaVersion: String,
    val dataFreshnessAt: String,
    val contractVersion: String,
    val status: String,
    val tallyReachable: Boolean,
    val dataQuality: DataQualityDto? = null,
    val reason: String? = null,
)

@Serializable
data class CompanyListItemDto(
    val id: String,
    val name: String,
    val financialYear: String? = null,
    val booksFrom: String? = null,
    val baseCurrency: String? = null,
)

@Serializable
data class DataQualityDto(
    val status: String,
    val reason: String? = null,
)

@Serializable
data class SelectCompanyRequestDto(
    val companyId: String,
)

@Serializable
data class SessionEnvelopeDto(
    val session: SessionDto,
    val contractVersion: String,
)

@Serializable
data class SessionDto(
    val sessionId: String,
    val selectedCompany: SelectedCompanyDto? = null,
    val connectionStatus: String,
    val connectorVersion: String,
    val erpType: String,
    val selectedAt: String? = null,
    val lastValidatedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class SelectedCompanyDto(
    val id: String,
    val name: String,
)

@Serializable
data class CompanySelectionResultDto(
    val status: String,
    val session: SessionDto,
    val reason: String? = null,
)

@Serializable
data class SessionValidationResultDto(
    val status: String,
    val session: SessionDto,
    val reason: String? = null,
    val companyId: String? = null,
    val companyName: String? = null,
)

@Serializable
data class SessionClearResultDto(
    val status: String,
    val session: SessionDto,
    val contractVersion: String,
)
