package com.jajusri.venture.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.masterdata.presentation.displayMessage
import com.jajusri.venture.feature.voucher.domain.usecase.GetCachedVoucherSummaryUseCase
import com.jajusri.venture.feature.voucher.domain.usecase.GetVoucherDetailsUseCase
import com.jajusri.venture.feature.voucher.domain.usecase.RefreshVoucherDetailsUseCase
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.sharing.InvoiceShareCoordinator
import com.jajusri.venture.feature.voucher.sharing.InvoiceShareResult
import com.jajusri.venture.feature.voucher.sharing.PreparedInvoicePdf
import com.jajusri.venture.feature.voucher.sharing.isShareableInvoice
import com.jajusri.venture.feature.voucher.sharing.shareIneligibilityReason
import com.jajusri.venture.feature.voucher.sharing.summaryShareIneligibilityReason
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
    private val refreshVoucherDetails: RefreshVoucherDetailsUseCase,
    private val getCachedVoucherSummary: GetCachedVoucherSummaryUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val invoiceShareCoordinator: InvoiceShareCoordinator,
    /** TD-028: exposed for the Compose layer to render the open preview — kept here rather than a
     * separate Hilt entry point in the Composable, matching how this screen already sources every
     * other collaborator through its ViewModel. */
    val pdfPageRenderer: com.jajusri.venture.core.pdf.PdfPageRenderer,
) : ViewModel() {

    private val voucherId: String = savedStateHandle.get<String>(VOUCHER_ID_ARG)
        ?.let { URLDecoder.decode(it, "UTF-8") }
        .orEmpty()

    private val _uiState = MutableStateFlow(VoucherDetailsUiState(voucherId = voucherId))
    val uiState: StateFlow<VoucherDetailsUiState> = _uiState.asStateFlow()
    private val _shareEffects = MutableSharedFlow<VoucherDetailsShareEffect>(extraBufferCapacity = 1)
    val shareEffects: SharedFlow<VoucherDetailsShareEffect> = _shareEffects.asSharedFlow()

    private var loadJob: Job? = null
    private var downloadJob: Job? = null
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
            VoucherDetailsEvent.Refresh -> {
                // Once details exist, keep the classic "refresh in background" flow. Before that,
                // there is nothing to refresh — route through the same explicit download path
                // DownloadDetails uses, so pull-to-refresh on the not-stored screen behaves the same way.
                if (_uiState.value.hasContent) load(refreshing = true) else downloadDetails()
            }
            VoucherDetailsEvent.Retry -> load(refreshing = false)
            VoucherDetailsEvent.DownloadDetails -> downloadDetails()
            VoucherDetailsEvent.OpenShareOptions -> {
                if (_uiState.value.canShareInvoice) _uiState.update { it.copy(showShareOptions = true) }
            }
            VoucherDetailsEvent.DismissShareOptions -> _uiState.update { it.copy(showShareOptions = false) }
            VoucherDetailsEvent.SharePdf -> preparePdf(save = false)
            VoucherDetailsEvent.ShareSummary -> shareSummary()
            VoucherDetailsEvent.SavePdf -> preparePdf(save = true)
            VoucherDetailsEvent.PreviewPdf -> openPreview()
            VoucherDetailsEvent.DismissPreview -> dismissPreview()
            VoucherDetailsEvent.SaveFromPreview -> deliverFromPreview(save = true)
            VoucherDetailsEvent.ShareFromPreview -> deliverFromPreview(save = false)
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
                    refreshing -> it.copy(isRefreshing = true, refreshError = null)
                    it.hasContent -> it.copy(isRefreshing = true, refreshError = null)
                    else -> it.copy(isInitialLoading = true, error = null)
                }
            }

            val result = if (refreshing) {
                refreshVoucherDetails(companyId, voucherId)
            } else {
                getVoucherDetails(companyId, voucherId)
            }
            when (result) {
                is AppResult.Success -> {
                    loadedDetails = result.value
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            details = result.value.toContentUi(),
                            error = null,
                            refreshError = null,
                            canShareInvoice = result.value.isShareableInvoice(),
                            shareUnavailableReason = result.value.shareIneligibilityReason(),
                            cacheState = result.value.cacheState,
                            lastSyncedAt = result.value.lastSyncedAt,
                        )
                    }
                }
                is AppResult.Failure -> {
                    if (!refreshing) {
                        // The cache-only path's only failure mode, once the id/company guards above
                        // have passed, is "Room has no stored details for this voucher yet" — never a
                        // network condition. Show the explicit not-stored state, not a generic error.
                        loadedDetails = null
                        val knownSummary = getCachedVoucherSummary(companyId, voucherId)
                        _uiState.update { state ->
                            state.copy(
                                isInitialLoading = false,
                                isRefreshing = false,
                                details = null,
                                error = null,
                                detailsNotStored = true,
                                knownSummary = knownSummary?.toRowUi(),
                                canShareInvoice = false,
                                // Full details (ledger/inventory lines) aren't available yet, so
                                // sharing genuinely isn't possible right now — but the already-cached
                                // summary is enough to say WHY: either this voucher was never going
                                // to be shareable (same reason as the full-details path), or it looks
                                // eligible and just needs its details downloaded first. Either way,
                                // never silently hide the share action without explanation.
                                shareUnavailableReason = knownSummary?.summaryShareIneligibilityReason()
                                    ?: knownSummary?.let {
                                        "Download this voucher's details to share it as an invoice."
                                    },
                            )
                        }
                    } else {
                        _uiState.update { state ->
                            if (state.hasContent) {
                                // A failed refresh must never hide or replace valid cached details.
                                state.copy(
                                    isInitialLoading = false,
                                    isRefreshing = false,
                                    refreshError = refreshFailedMessage(state.lastSyncedAt),
                                    cacheState = com.jajusri.venture.feature.voucher.domain.model.VoucherCacheState.Offline,
                                )
                            } else {
                                // Defensive fallback; normal navigation routes a no-content refresh through downloadDetails().
                                state.copy(
                                    isInitialLoading = false,
                                    isRefreshing = false,
                                    error = result.error.toVoucherDetailsUiError(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadDetails() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            if (voucherId.isBlank()) return@launch
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) return@launch
            if (!_uiState.value.isOnline) {
                // Android already knows there is no network path; don't wait through a timeout.
                _uiState.update {
                    it.copy(
                        downloadError = "Voucher details are not stored on this device. Connect to VENTURE Desktop to download them.",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isDownloadingDetails = true, downloadError = null) }
            when (val result = refreshVoucherDetails(companyId, voucherId)) {
                is AppResult.Success -> {
                    loadedDetails = result.value
                    _uiState.update {
                        it.copy(
                            isDownloadingDetails = false,
                            detailsNotStored = false,
                            downloadError = null,
                            details = result.value.toContentUi(),
                            canShareInvoice = result.value.isShareableInvoice(),
                            shareUnavailableReason = result.value.shareIneligibilityReason(),
                            cacheState = result.value.cacheState,
                            lastSyncedAt = result.value.lastSyncedAt,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isDownloadingDetails = false,
                            downloadError = result.error.toVoucherDetailsUiError().displayMessage(),
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

    /**
     * TD-028: prepares the invoice PDF (if not already held from a previous preview open in this
     * screen visit) and shows it in-app before any Save/Share action — "generate -> preview ->
     * save/share", never a separately regenerated document for the eventual action.
     */
    private fun openPreview() {
        if (_uiState.value.previewPdf != null) return
        if (shareJob?.isActive == true) return
        val details = loadedDetails ?: return showShareError("Invoice data is unavailable.")
        if (!details.isShareableInvoice()) return showShareError("This voucher cannot be shared as an invoice.")
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, showShareOptions = false, shareError = null, shareMessage = null) }
            when (val result = invoiceShareCoordinator.preparePdf(details)) {
                is InvoiceShareResult.Failure -> showShareError(result.message)
                is InvoiceShareResult.Success -> _uiState.update { it.copy(previewPdf = result.value) }
            }
            _uiState.update { it.copy(isShareBusy = false) }
        }
    }

    /** Back from preview without saving/sharing: releases the held cache file — a later re-open
     * regenerates deterministically from the same [loadedDetails], never stale content. */
    private fun dismissPreview() {
        _uiState.value.previewPdf?.let(invoiceShareCoordinator::releasePdf)
        _uiState.update { it.copy(previewPdf = null) }
    }

    /** Save/Share tapped from within the open preview — acts on the exact PDF already on screen,
     * never re-prepares. */
    private fun deliverFromPreview(save: Boolean) {
        val pdf = _uiState.value.previewPdf ?: return showShareError("The prepared invoice PDF is no longer available.")
        if (shareJob?.isActive == true) return
        val operationId = ++nextOperationId
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, shareError = null, shareMessage = null, previewPdf = null) }
            if (save) {
                pendingSavePdf?.let(invoiceShareCoordinator::releasePdf)
                pendingSavePdf = pdf
                pendingSaveOperationId = operationId
                launchedSaveOperations.addLast(operationId)
                _shareEffects.emit(VoucherDetailsShareEffect.CreatePdfDocument(operationId, pdf.suggestedFilename))
            } else {
                when (val intent = invoiceShareCoordinator.createPdfShareIntent(pdf)) {
                    is InvoiceShareResult.Success -> {
                        pendingShareOperationId = operationId
                        launchedShareOperations.addLast(operationId)
                        _shareEffects.emit(VoucherDetailsShareEffect.LaunchShare(operationId, intent.value))
                    }
                    is InvoiceShareResult.Failure -> showShareError(intent.message)
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
        _uiState.value.previewPdf?.let(invoiceShareCoordinator::releasePdf)
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
