package com.jajusri.venture.feature.company.presentation

import com.jajusri.venture.feature.company.domain.model.ConnectorCompany

data class CompanyUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSelecting: Boolean = false,
    val searchQuery: String = "",
    val companies: List<ConnectorCompany> = emptyList(),
    val filteredCompanies: List<ConnectorCompany> = emptyList(),
    val selectedCompanyId: String? = null,
    val selectedCompanyName: String? = null,
    val sessionValidated: Boolean = false,
    val message: String? = null,
    val error: CompanyUiError? = null,
)

sealed class CompanyUiError(
    open val message: String,
) {
    data class Offline(override val message: String) : CompanyUiError(message)
    data class Timeout(override val message: String) : CompanyUiError(message)
    data class Http(override val message: String) : CompanyUiError(message)
    data class Serialization(override val message: String) : CompanyUiError(message)
    data class Unknown(override val message: String) : CompanyUiError(message)
}

sealed interface CompanyEvent {
    data object Load : CompanyEvent
    data object Refresh : CompanyEvent
    data class SearchChanged(val query: String) : CompanyEvent
    data class SelectCompany(val companyId: String) : CompanyEvent
    data object Retry : CompanyEvent
    data object ClearMessage : CompanyEvent
}
