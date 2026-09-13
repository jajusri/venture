package com.jajusri.venture.feature.dincharya.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaItem
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaSnapshot
import com.jajusri.venture.feature.dincharya.domain.usecase.GetDincharyaSnapshotUseCase
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.core.common.UserVisibleErrorText
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dincharya (MVP-1.2-D) — a deterministic, bounded, company-wide worklist read from local Room data
 * only (architecture §14 — zero network/Connector call anywhere in this class). Reloads fresh on
 * every company change exactly like [com.jajusri.venture.feature.connect.presentation.ConnectViewModel]
 * (`companySession.observeSelectedCompanyId().distinctUntilChanged()`), so switching companies can
 * never leave a stale, wrongly-scoped list on screen (architecture §13/§20 Risk #1).
 */
@HiltViewModel
class DincharyaViewModel @Inject constructor(
    private val getDincharyaSnapshot: GetDincharyaSnapshotUseCase,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DincharyaUiState())
    val uiState: StateFlow<DincharyaUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DincharyaEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<DincharyaEffect> = _effects.asSharedFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { companyId ->
                loadJob?.cancel()
                if (companyId.isNullOrBlank()) {
                    _uiState.update {
                        DincharyaUiState(
                            companyId = null,
                            isInitialLoading = false,
                            error = MasterDataUiError.Message("Select a company to see Dincharya."),
                        )
                    }
                } else {
                    _uiState.update { DincharyaUiState(companyId = companyId, isInitialLoading = true) }
                    load(companyId, refreshing = false)
                }
            }
        }
    }

    fun onEvent(event: DincharyaEvent) {
        when (event) {
            DincharyaEvent.Load -> _uiState.value.companyId?.let { load(it, refreshing = false) }
            DincharyaEvent.Refresh -> _uiState.value.companyId?.let { load(it, refreshing = true) }
            DincharyaEvent.Retry -> _uiState.value.companyId?.let { load(it, refreshing = false) }
            is DincharyaEvent.ItemTapped -> _effects.tryEmit(DincharyaEffect.OpenPartyDetail(event.partyId))
        }
    }

    private fun load(companyId: String, refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (refreshing) it.copy(isRefreshing = true, error = null) else it.copy(isInitialLoading = true, error = null)
            }
            runCatching { getDincharyaSnapshot(companyId) }
                .onSuccess { snapshot -> _uiState.update { it.applySnapshot(snapshot) } }
                .onFailure { throwable ->
                    Timber.w(throwable, "Dincharya load failed")
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = MasterDataUiError.Unexpected(UserVisibleErrorText.fromThrowable(throwable)),
                        )
                    }
                }
        }
    }
}

private fun DincharyaUiState.applySnapshot(snapshot: DincharyaSnapshot): DincharyaUiState = copy(
    isInitialLoading = false,
    isRefreshing = false,
    error = null,
    followUps = snapshot.followUps.items.map { it.toUi() },
    followUpsMoreCount = snapshot.followUps.moreCount,
    pendingConfirmations = snapshot.pendingConfirmations.items.map { it.toUi() },
    pendingConfirmationsMoreCount = snapshot.pendingConfirmations.moreCount,
    pendingContactCompletions = snapshot.pendingContactCompletions.items.map { it.toUi() },
    pendingContactCompletionsMoreCount = snapshot.pendingContactCompletions.moreCount,
)

private fun DincharyaItem.FollowUp.toUi(): FollowUpUi = FollowUpUi(
    noteId = noteId,
    partyId = partyId,
    partyDisplayName = partyDisplayName,
    body = body,
    dueAt = dueAt,
    urgency = urgency,
)

private fun DincharyaItem.PendingTallyConfirmation.toUi(): PendingConfirmationUi = PendingConfirmationUi(
    partyId = partyId,
    partyDisplayName = partyDisplayName,
    fieldsLabel = pendingFieldLabels.joinToString(", "),
)

private fun DincharyaItem.PendingContactCompletion.toUi(): PendingContactCompletionUi = PendingContactCompletionUi(
    partyId = partyId,
    partyDisplayName = partyDisplayName,
)
