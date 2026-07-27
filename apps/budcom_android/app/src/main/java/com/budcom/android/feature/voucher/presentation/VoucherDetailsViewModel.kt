package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.usecase.GetVoucherDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class VoucherDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVoucherDetails: GetVoucherDetailsUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val voucherId: String = savedStateHandle.get<String>(VOUCHER_ID_ARG)
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
        .orEmpty()

    private val _uiState = MutableStateFlow(VoucherDetailsUiState(voucherId = voucherId))
    val uiState: StateFlow<VoucherDetailsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        onEvent(VoucherDetailsEvent.Load)
    }

    fun onEvent(event: VoucherDetailsEvent) {
        when (event) {
            VoucherDetailsEvent.Load -> load(refreshing = false)
            VoucherDetailsEvent.Refresh -> load(refreshing = true)
            VoucherDetailsEvent.Retry -> load(refreshing = false)
        }
    }

    private fun load(refreshing: Boolean) {
        if (loadJob?.isActive == true && !refreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (voucherId.isBlank()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        error = MasterDataUiError.Message("Voucher id is missing."),
                    )
                }
                return@launch
            }

            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        error = MasterDataUiError.Message("Select a company before opening voucher details."),
                    )
                }
                return@launch
            }

            _uiState.update {
                when {
                    refreshing -> it.copy(isRefreshing = true, error = null)
                    it.hasContent -> it.copy(isRefreshing = true, error = null)
                    else -> it.copy(isInitialLoading = true, error = null)
                }
            }

            when (val result = getVoucherDetails(companyId, voucherId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            details = result.value.toContentUi(),
                            error = null,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = result.error.toVoucherDetailsUiError(),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val VOUCHER_ID_ARG = "voucherId"
    }
}
