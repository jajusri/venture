package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.usecase.GetVoucherDetailsUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.sharing.InvoiceShareCoordinator
import com.budcom.android.feature.voucher.sharing.InvoiceShareResult
import com.budcom.android.feature.voucher.sharing.PreparedInvoicePdf
import com.budcom.android.feature.voucher.sharing.isShareableInvoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.ArrayDeque
import javax.inject.Inject

@HiltViewModel
class VoucherDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVoucherDetails: GetVoucherDetailsUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val invoiceShareCoordinator: InvoiceShareCoordinator,
) : ViewModel() {

    private val voucherId: String = savedStateHandle.get<String>(VOUCHER_ID_ARG)
        ?.let { URLDecoder.decode(it, "UTF-8") }
        .orEmpty()

    private val _uiState = MutableStateFlow(VoucherDetailsUiState(voucherId = voucherId))
    val uiState: StateFlow<VoucherDetailsUiState> = _uiState.asStateFlow()
    private val _shareEffects = MutableSharedFlow<VoucherDetailsShareEffect>(extraBufferCapacity = 1)
    val shareEffects: SharedFlow<VoucherDetailsShareEffect> = _shareEffects.asSharedFlow()

    private var loadJob: Job? = null
    private var shareJob: Job? = null
    private var loadedDetails: VoucherDetails? = null
    private var pendingSavePdf: PreparedInvoicePdf? = null
    private var pendingSaveOperationId: Long? = null
    private var pendingShareOperationId: Long? = null
    private val launchedSaveOperations = ArrayDeque<Long>()
    private val launchedShareOperations = ArrayDeque<Long>()
    private var nextOperationId = 0L

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
            VoucherDetailsEvent.OpenShareOptions -> {
                if (_uiState.value.canShareInvoice) _uiState.update { it.copy(showShareOptions = true) }
            }
            VoucherDetailsEvent.DismissShareOptions -> _uiState.update { it.copy(showShareOptions = false) }
            VoucherDetailsEvent.SharePdf -> preparePdf(save = false)
            VoucherDetailsEvent.ShareSummary -> shareSummary()
            VoucherDetailsEvent.SavePdf -> preparePdf(save = true)
            is VoucherDetailsEvent.SaveDestinationSelected -> resolveSaveResult(event.uri)
            is VoucherDetailsEvent.ShareActivityFinished -> finishShare(event)
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
                    loadedDetails = result.value
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            details = result.value.toContentUi(),
                            error = null,
                            canShareInvoice = result.value.isShareableInvoice(),
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

    private fun preparePdf(save: Boolean) {
        if (shareJob?.isActive == true) return
        val details = loadedDetails ?: return showShareError("Invoice data is unavailable.")
        if (!details.isShareableInvoice()) return showShareError("This voucher cannot be shared as an invoice.")
        val operationId = ++nextOperationId
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, showShareOptions = false, shareError = null, shareMessage = null) }
            when (val result = invoiceShareCoordinator.preparePdf(details)) {
                is InvoiceShareResult.Failure -> showShareError(result.message)
                is InvoiceShareResult.Success -> {
                    if (save) {
                        pendingSavePdf?.let(invoiceShareCoordinator::releasePdf)
                        pendingSavePdf = result.value
                        pendingSaveOperationId = operationId
                        launchedSaveOperations.addLast(operationId)
                        _shareEffects.emit(VoucherDetailsShareEffect.CreatePdfDocument(operationId, result.value.suggestedFilename))
                    } else {
                        when (val intent = invoiceShareCoordinator.createPdfShareIntent(result.value)) {
                            is InvoiceShareResult.Success -> {
                                pendingShareOperationId = operationId
                                launchedShareOperations.addLast(operationId)
                                _shareEffects.emit(VoucherDetailsShareEffect.LaunchShare(operationId, intent.value))
                            }
                            is InvoiceShareResult.Failure -> showShareError(intent.message)
                        }
                    }
                }
            }
            _uiState.update { it.copy(isShareBusy = false) }
        }
    }

    private fun shareSummary() {
        if (shareJob?.isActive == true) return
        val details = loadedDetails ?: return showShareError("Invoice data is unavailable.")
        val operationId = ++nextOperationId
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(showShareOptions = false, shareError = null, shareMessage = null) }
            val companyName = companySession.observeSelectedCompany().first()?.name
            when (val result = invoiceShareCoordinator.createSummaryShareIntent(details, companyName)) {
                is InvoiceShareResult.Success -> {
                    pendingShareOperationId = operationId
                    launchedShareOperations.addLast(operationId)
                    _shareEffects.emit(VoucherDetailsShareEffect.LaunchShare(operationId, result.value))
                }
                is InvoiceShareResult.Failure -> showShareError(result.message)
            }
        }
    }

    private fun saveTo(operationId: Long, uri: android.net.Uri?) {
        if (operationId != pendingSaveOperationId) return
        if (uri == null) {
            pendingSavePdf?.let(invoiceShareCoordinator::releasePdf)
            pendingSavePdf = null
            pendingSaveOperationId = null
            _uiState.update { it.copy(shareMessage = "Save cancelled.", shareError = null) }
            return
        }
        val pdf = pendingSavePdf ?: return showShareError("The prepared invoice PDF is no longer available.")
        if (shareJob?.isActive == true) return
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, shareError = null, shareMessage = null) }
            when (val result = invoiceShareCoordinator.savePdf(pdf, uri)) {
                is InvoiceShareResult.Success -> _uiState.update { it.copy(shareMessage = "Invoice PDF saved.") }
                is InvoiceShareResult.Failure -> showShareError(result.message)
            }
            pendingSavePdf = null
            pendingSaveOperationId = null
            _uiState.update { it.copy(isShareBusy = false) }
        }
    }

    private fun resolveSaveResult(uri: android.net.Uri?) {
        val operationId = launchedSaveOperations.pollFirst() ?: return
        saveTo(operationId, uri)
    }

    private fun finishShare(event: VoucherDetailsEvent.ShareActivityFinished) {
        val operationId = launchedShareOperations.pollFirst() ?: return
        if (operationId != pendingShareOperationId) return
        pendingShareOperationId = null
        launchedSaveOperations.clear()
        launchedShareOperations.clear()
        _uiState.update { it.copy(shareMessage = if (event.cancelled) "Sharing cancelled." else null) }
    }

    private fun showShareError(message: String) {
        _uiState.update { it.copy(isShareBusy = false, shareError = message, shareMessage = null) }
    }

    override fun onCleared() {
        pendingSavePdf?.let(invoiceShareCoordinator::releasePdf)
        pendingSavePdf = null
        pendingSaveOperationId = null
        pendingShareOperationId = null
        super.onCleared()
    }

    companion object {
        const val VOUCHER_ID_ARG = "voucherId"
    }
}

sealed interface VoucherDetailsShareEffect {
    val operationId: Long
    data class LaunchShare(override val operationId: Long, val intent: android.content.Intent) : VoucherDetailsShareEffect
    data class CreatePdfDocument(override val operationId: Long, val suggestedFilename: String) : VoucherDetailsShareEffect
}
