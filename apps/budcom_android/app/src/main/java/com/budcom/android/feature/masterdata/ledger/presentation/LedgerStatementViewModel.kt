package com.budcom.android.feature.masterdata.ledger.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.usecase.GetLocalLedgerStatementUseCase
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgerCoverageUseCase
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareCoordinator
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareResult
import com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.ArrayDeque
import javax.inject.Inject

@HiltViewModel
class LedgerStatementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getLocalStatement: GetLocalLedgerStatementUseCase,
    private val refreshCoverage: RefreshLedgerCoverageUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val shareCoordinator: LedgerStatementShareCoordinator,
) : ViewModel() {

    private val ledgerId: String = savedStateHandle.get<String>(LEDGER_ID_ARG)
        ?.let { URLDecoder.decode(it, "UTF-8") }
        .orEmpty()

    private val _uiState = MutableStateFlow(
        LedgerStatementUiState(ledgerId = ledgerId, periodSelection = LedgerPeriodSelection.Last7Sales),
    )
    val uiState: StateFlow<LedgerStatementUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LedgerStatementEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<LedgerStatementEffect> = _effects.asSharedFlow()
    private val _shareEffects = MutableSharedFlow<LedgerStatementShareEffect>(extraBufferCapacity = 1)
    val shareEffects: SharedFlow<LedgerStatementShareEffect> = _shareEffects.asSharedFlow()

    private var loadJob: Job? = null
    private var shareJob: Job? = null
    private var loadedStatement: LedgerStatement? = null
    private var pendingSavePdf: PreparedLedgerStatementPdf? = null
    private var pendingSaveOperationId: Long? = null
    private var pendingShareOperationId: Long? = null
    private val launchedSaveOperations = ArrayDeque<Long>()
    private val launchedShareOperations = ArrayDeque<Long>()
    private var nextOperationId = 0L

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        onEvent(LedgerStatementEvent.Load)
    }

    fun onEvent(event: LedgerStatementEvent) {
        when (event) {
            LedgerStatementEvent.Load -> load(refreshing = false)
            LedgerStatementEvent.Refresh -> load(refreshing = true)
            LedgerStatementEvent.Retry -> load(refreshing = false)
            is LedgerStatementEvent.PeriodChanged -> {
                // Custom period, from the date dialog. Locked contract: changing the period is a
                // Room-only read — it must never itself trigger a network call.
                _uiState.update { it.copy(periodSelection = LedgerPeriodSelection.Custom(event.from, event.to)) }
                load(refreshing = false)
            }
            is LedgerStatementEvent.PeriodSelected -> {
                _uiState.update { it.copy(periodSelection = event.period) }
                load(refreshing = false)
            }
            is LedgerStatementEvent.TransactionTapped -> {
                if (event.voucherId.isNotBlank()) {
                    viewModelScope.launch { _effects.emit(LedgerStatementEffect.OpenVoucherDetails(event.voucherId)) }
                }
            }
            LedgerStatementEvent.OpenShareOptions -> {
                if (_uiState.value.hasContent) _uiState.update { it.copy(showShareOptions = true) }
            }
            LedgerStatementEvent.DismissShareOptions -> _uiState.update { it.copy(showShareOptions = false) }
            LedgerStatementEvent.SharePdf -> preparePdf(save = false)
            LedgerStatementEvent.SavePdf -> preparePdf(save = true)
            is LedgerStatementEvent.SaveDestinationSelected -> resolveSaveResult(event.uri)
            is LedgerStatementEvent.ShareActivityFinished -> finishShare(event)
        }
    }

    private fun load(refreshing: Boolean) {
        if (loadJob?.isActive == true && !refreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (ledgerId.isBlank()) {
                _uiState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false, error = MasterDataUiError.Message("Ledger id is missing."))
                }
                return@launch
            }
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        error = MasterDataUiError.Message("Select a company before opening a ledger statement."),
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

            // Refresh's only job is to bring Room up to date via the NORMAL Voucher sync
            // mechanism, scoped to whatever window is currently on screen — never a bespoke
            // per-ledger network call. A refresh failure is surfaced but does not itself stop the
            // subsequent local read: existing cached content must remain visible.
            var refreshFailureMessage: String? = null
            if (refreshing) {
                val window = _uiState.value.fromDate to _uiState.value.toDate
                if (window.first.isNotBlank() && window.second.isNotBlank()) {
                    when (val refreshResult = refreshCoverage(companyId, window.first, window.second)) {
                        is AppResult.Failure -> refreshFailureMessage = refreshResult.error.toLedgerStatementUiError().displayMessage()
                        is AppResult.Success -> Unit
                    }
                }
            }

            val result = getLocalStatement(companyId, ledgerId, _uiState.value.periodSelection)
            when (result) {
                is AppResult.Success -> {
                    loadedStatement = result.value
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = null,
                            refreshError = refreshFailureMessage,
                            fromDate = result.value.period.from,
                            toDate = result.value.period.to,
                            content = result.value.toContentUi(lastSyncedAt = null),
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update { state ->
                    if (state.hasContent) {
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            refreshError = refreshFailureMessage ?: result.error.toLedgerStatementUiError().displayMessage(),
                        )
                    } else {
                        state.copy(isInitialLoading = false, isRefreshing = false, error = result.error.toLedgerStatementUiError())
                    }
                }
            }
        }
    }

    private fun preparePdf(save: Boolean) {
        if (shareJob?.isActive == true) return
        val statement = loadedStatement ?: return showShareError("Ledger statement data is unavailable.")
        val operationId = ++nextOperationId
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, showShareOptions = false, shareError = null, shareMessage = null) }
            val companyName = companySession.observeSelectedCompany().first()?.name
            when (val result = shareCoordinator.preparePdf(statement, companyName)) {
                is LedgerStatementShareResult.Failure -> showShareError(result.message)
                is LedgerStatementShareResult.Success -> {
                    if (save) {
                        pendingSavePdf?.let(shareCoordinator::releasePdf)
                        pendingSavePdf = result.value
                        pendingSaveOperationId = operationId
                        launchedSaveOperations.addLast(operationId)
                        _shareEffects.emit(LedgerStatementShareEffect.CreatePdfDocument(operationId, result.value.suggestedFilename))
                    } else {
                        when (val intent = shareCoordinator.createPdfShareIntent(result.value)) {
                            is LedgerStatementShareResult.Success -> {
                                pendingShareOperationId = operationId
                                launchedShareOperations.addLast(operationId)
                                _shareEffects.emit(LedgerStatementShareEffect.LaunchShare(operationId, intent.value))
                            }
                            is LedgerStatementShareResult.Failure -> showShareError(intent.message)
                        }
                    }
                }
            }
            _uiState.update { it.copy(isShareBusy = false) }
        }
    }

    private fun saveTo(operationId: Long, uri: android.net.Uri?) {
        if (operationId != pendingSaveOperationId) return
        if (uri == null) {
            pendingSavePdf?.let(shareCoordinator::releasePdf)
            pendingSavePdf = null
            pendingSaveOperationId = null
            _uiState.update { it.copy(shareMessage = "Save cancelled.", shareError = null) }
            return
        }
        val pdf = pendingSavePdf ?: return showShareError("The prepared ledger statement PDF is no longer available.")
        if (shareJob?.isActive == true) return
        shareJob = viewModelScope.launch {
            _uiState.update { it.copy(isShareBusy = true, shareError = null, shareMessage = null) }
            when (val result = shareCoordinator.savePdf(pdf, uri)) {
                is LedgerStatementShareResult.Success -> _uiState.update { it.copy(shareMessage = "Ledger statement PDF saved.") }
                is LedgerStatementShareResult.Failure -> showShareError(result.message)
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

    private fun finishShare(event: LedgerStatementEvent.ShareActivityFinished) {
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
        pendingSavePdf?.let(shareCoordinator::releasePdf)
        pendingSavePdf = null
        pendingSaveOperationId = null
        pendingShareOperationId = null
        super.onCleared()
    }

    companion object {
        const val LEDGER_ID_ARG = "ledgerId"
    }
}
