package com.jajusri.venture.feature.connect.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.party.domain.model.ProspectDraft
import com.jajusri.venture.feature.party.domain.usecase.CreateProspectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Minimal Prospect creation (architecture §27, spec §4.1) — a VENTURE Party with no Tally Ledger.
 * Works fully offline: [createProspect] never performs a network call. No CRM pipeline/stage
 * fields exist by design.
 */
@HiltViewModel
class ProspectCreateViewModel @Inject constructor(
    private val createProspect: CreateProspectUseCase,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProspectCreateUiState())
    val uiState: StateFlow<ProspectCreateUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ProspectCreateEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ProspectCreateEffect> = _effects.asSharedFlow()

    fun onEvent(event: ProspectCreateEvent) {
        when (event) {
            is ProspectCreateEvent.DisplayNameChanged -> _uiState.update { it.copy(displayName = event.value, error = null) }
            is ProspectCreateEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.value) }
            is ProspectCreateEvent.EmailChanged -> _uiState.update { it.copy(email = event.value) }
            is ProspectCreateEvent.AddressLine1Changed -> _uiState.update { it.copy(addressLine1 = event.value) }
            is ProspectCreateEvent.AddressCityChanged -> _uiState.update { it.copy(addressCity = event.value) }
            is ProspectCreateEvent.AddressStateChanged -> _uiState.update { it.copy(addressState = event.value) }
            is ProspectCreateEvent.AddressPincodeChanged -> _uiState.update { it.copy(addressPincode = event.value) }
            is ProspectCreateEvent.NoteChanged -> _uiState.update { it.copy(note = event.value) }
            ProspectCreateEvent.Save -> save()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update { it.copy(isSaving = false, error = "Select a company before creating a prospect.") }
                return@launch
            }
            val draft = ProspectDraft(
                displayName = state.displayName.trim(),
                phone = state.phone.trim().ifEmpty { null },
                email = state.email.trim().ifEmpty { null },
                addressLine1 = state.addressLine1.trim().ifEmpty { null },
                addressCity = state.addressCity.trim().ifEmpty { null },
                addressState = state.addressState.trim().ifEmpty { null },
                addressPincode = state.addressPincode.trim().ifEmpty { null },
                note = state.note.trim().ifEmpty { null },
            )
            val party = createProspect(companyId, draft)
            _uiState.update { it.copy(isSaving = false) }
            _effects.tryEmit(ProspectCreateEffect.Created(party.partyId))
        }
    }
}
