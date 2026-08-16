package com.budcom.android.feature.company.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import com.budcom.android.feature.company.domain.usecase.LoadCompaniesUseCase
import com.budcom.android.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.budcom.android.feature.company.domain.usecase.SelectCompanyUseCase
import com.budcom.android.feature.company.domain.usecase.ValidateSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompanyViewModel @Inject constructor(
    private val repository: CompanyRepository,
    private val loadCompanies: LoadCompaniesUseCase,
    private val restoreSelection: RestoreCompanySelectionUseCase,
    private val selectCompany: SelectCompanyUseCase,
    private val validateSession: ValidateSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyUiState())
    val uiState: StateFlow<CompanyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSelectedCompanyId().collect { selected ->
                _uiState.update { state ->
                    state.copy(selectedCompanyId = selected)
                        .applyFilter()
                }
            }
        }
        onEvent(CompanyEvent.Load)
    }

    fun onEvent(event: CompanyEvent) {
        when (event) {
            CompanyEvent.Load -> load(false)
            CompanyEvent.Refresh -> load(true)
            is CompanyEvent.SearchChanged -> {
                _uiState.update {
                    it.copy(searchQuery = event.query).applyFilter()
                }
            }
            is CompanyEvent.SelectCompany -> select(event.companyId)
            CompanyEvent.Retry -> load(false)
            CompanyEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun load(refresh: Boolean) {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || current.isSelecting) return

        viewModelScope.launch {
            if (refresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
                restoreSelectionAndValidate()
            }

            when (val result = loadCompanies(refresh)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            companies = result.value.items,
                            error = null,
                        ).applyFilter()
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.error.toUiError(),
                        )
                    }
                }
            }
        }
    }

    private suspend fun restoreSelectionAndValidate() {
        when (val restored = restoreSelection()) {
            is AppResult.Success -> {
                val outcome = restored.value
                _uiState.update {
                    it.copy(
                        selectedCompanyId = outcome?.companyId ?: it.selectedCompanyId,
                        selectedCompanyName = outcome?.companyName ?: it.selectedCompanyName,
                        sessionValidated = outcome?.status == "SUCCESS",
                    )
                }
            }
            is AppResult.Failure -> {
                _uiState.update {
                    it.copy(
                        sessionValidated = false,
                        message = restored.error.toMessage(),
                    )
                }
            }
        }
    }

    private fun select(companyId: String) {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isSelecting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSelecting = true, message = null, error = null) }
            when (val result = selectCompany(companyId)) {
                is AppResult.Success -> {
                    val validation = validateSession()
                    when (validation) {
                        is AppResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isSelecting = false,
                                    selectedCompanyId = validation.value.companyId ?: companyId,
                                    selectedCompanyName = validation.value.companyName,
                                    sessionValidated = validation.value.status == "SUCCESS",
                                    message = "Company selected and session validated.",
                                ).applyFilter()
                            }
                        }
                        is AppResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isSelecting = false,
                                    sessionValidated = false,
                                    error = validation.error.toUiError(),
                                )
                            }
                        }
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSelecting = false,
                            error = result.error.toUiError(),
                        )
                    }
                }
            }
        }
    }
}

internal fun CompanyUiState.applyFilter(): CompanyUiState {
    val query = searchQuery.trim().lowercase()
    val filtered = if (query.isBlank()) {
        companies
    } else {
        companies.filter { company ->
            company.name.lowercase().contains(query) ||
                company.id.lowercase().contains(query) ||
                (company.financialYear?.lowercase()?.contains(query) == true) ||
                (company.baseCurrency?.lowercase()?.contains(query) == true)
        }
    }
    return copy(filteredCompanies = filtered)
}

private fun AppError.toUiError(): CompanyUiError = when (this) {
    is AppError.Offline -> CompanyUiError.Offline("Device is offline.")
    is AppError.Timeout -> CompanyUiError.Timeout("The request timed out.")
    is AppError.Remote -> CompanyUiError.Http(
        buildString {
            if (httpStatus != null) append("HTTP ").append(httpStatus).append(": ")
            if (!code.isNullOrBlank()) append('[').append(code).append("] ")
            append(message)
        },
    )
    is AppError.Serialization -> CompanyUiError.Serialization(message)
    is AppError.Message -> CompanyUiError.Unknown(message)
    is AppError.Unexpected -> CompanyUiError.Unknown(cause.message ?: "An unexpected error occurred.")
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Offline -> "Device is offline."
    is AppError.Timeout -> "The request timed out."
    is AppError.Remote -> message
    is AppError.Serialization -> message
    is AppError.Message -> message
    is AppError.Unexpected -> cause.message ?: "An unexpected error occurred."
}
