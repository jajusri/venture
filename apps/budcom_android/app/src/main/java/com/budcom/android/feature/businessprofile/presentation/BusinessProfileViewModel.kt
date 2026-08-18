package com.budcom.android.feature.businessprofile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.usecase.GetBusinessProfileUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.SaveBusinessProfileUseCase
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Business Profile (MVP-1.3-A) — a single BUDCOM-owned identity record per selected Tally company
 * (PDL-019). Reloads fresh on every company change, mirroring
 * [com.budcom.android.feature.connect.presentation.ConnectViewModel]/
 * [com.budcom.android.feature.dincharya.presentation.DincharyaViewModel]'s own
 * `companySession.observeSelectedCompanyId().distinctUntilChanged()` pattern exactly — an in-progress
 * unsaved edit is discarded on company switch (the same "fresh state, never stale cross-company
 * data" discipline every prior company-scoped screen in this codebase already follows).
 */
@HiltViewModel
class BusinessProfileViewModel @Inject constructor(
    private val getBusinessProfile: GetBusinessProfileUseCase,
    private val saveBusinessProfile: SaveBusinessProfileUseCase,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusinessProfileUiState())
    val uiState: StateFlow<BusinessProfileUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { companyId ->
                loadJob?.cancel()
                if (companyId.isNullOrBlank()) {
                    _uiState.update {
                        BusinessProfileUiState(
                            companyId = null,
                            isLoading = false,
                            error = MasterDataUiError.Message("Select a company to see the Business Profile."),
                        )
                    }
                } else {
                    _uiState.update { BusinessProfileUiState(companyId = companyId, isLoading = true) }
                    load(companyId)
                }
            }
        }
    }

    fun onEvent(event: BusinessProfileEvent) {
        when (event) {
            BusinessProfileEvent.Load, BusinessProfileEvent.Retry -> _uiState.value.companyId?.let { load(it) }
            BusinessProfileEvent.EditTapped -> _uiState.update { it.copy(isEditing = true, form = it.savedForm) }
            BusinessProfileEvent.CancelEditTapped -> _uiState.update { it.copy(isEditing = false, form = it.savedForm) }
            is BusinessProfileEvent.TradingNameChanged -> updateForm { copy(tradingName = event.value) }
            is BusinessProfileEvent.LegalNameChanged -> updateForm { copy(legalName = event.value) }
            is BusinessProfileEvent.AddressLine1Changed -> updateForm { copy(addressLine1 = event.value) }
            is BusinessProfileEvent.AddressCityChanged -> updateForm { copy(addressCity = event.value) }
            is BusinessProfileEvent.AddressStateChanged -> updateForm { copy(addressState = event.value) }
            is BusinessProfileEvent.AddressPincodeChanged -> updateForm { copy(addressPincode = event.value) }
            is BusinessProfileEvent.PhoneChanged -> updateForm { copy(phone = event.value) }
            is BusinessProfileEvent.EmailChanged -> updateForm { copy(email = event.value) }
            is BusinessProfileEvent.GstinChanged -> updateForm { copy(gstin = event.value) }
            is BusinessProfileEvent.WebsiteChanged -> updateForm { copy(website = event.value) }
            is BusinessProfileEvent.DescriptionChanged -> updateForm { copy(description = event.value) }
            BusinessProfileEvent.SaveTapped -> save()
            BusinessProfileEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    private inline fun updateForm(crossinline transform: BusinessProfileFormState.() -> BusinessProfileFormState) {
        _uiState.update { it.copy(form = it.form.transform()) }
    }

    private fun load(companyId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { getBusinessProfile(companyId) }
                .onSuccess { profile ->
                    _uiState.update { state ->
                        if (profile != null) {
                            val loaded = profile.toFormState()
                            state.copy(
                                isLoading = false,
                                hasSavedProfile = true,
                                isEditing = false,
                                logoAssetPath = profile.logoAssetPath,
                                form = loaded,
                                savedForm = loaded,
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                hasSavedProfile = false,
                                isEditing = false,
                                logoAssetPath = null,
                                form = BusinessProfileFormState(),
                                savedForm = BusinessProfileFormState(),
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = MasterDataUiError.Unexpected(throwable.message ?: "Could not load the Business Profile."))
                    }
                }
        }
    }

    private fun save() {
        val state = _uiState.value
        val companyId = state.companyId ?: return
        val tradingName = state.form.tradingName.trim()
        if (tradingName.isBlank()) {
            _uiState.update { it.copy(notice = "Business / Trading Name is required.") }
            return
        }
        val draft = BusinessProfileDraft(
            tradingName = tradingName,
            legalName = state.form.legalName.trim().ifBlank { null },
            addressLine1 = state.form.addressLine1.trim().ifBlank { null },
            addressCity = state.form.addressCity.trim().ifBlank { null },
            addressState = state.form.addressState.trim().ifBlank { null },
            addressPincode = state.form.addressPincode.trim().ifBlank { null },
            phone = state.form.phone.trim().ifBlank { null },
            email = state.form.email.trim().ifBlank { null },
            gstin = state.form.gstin.trim().ifBlank { null },
            website = state.form.website.trim().ifBlank { null },
            description = state.form.description.trim().ifBlank { null },
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { saveBusinessProfile(companyId, draft) }
                .onSuccess { profile ->
                    _uiState.update {
                        val saved = profile.toFormState()
                        it.copy(
                            isSaving = false,
                            isEditing = false,
                            hasSavedProfile = true,
                            logoAssetPath = profile.logoAssetPath,
                            form = saved,
                            savedForm = saved,
                            notice = "Business Profile saved.",
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSaving = false, notice = throwable.message ?: "Could not save the Business Profile. Please try again.")
                    }
                }
        }
    }
}

private fun BusinessProfile.toFormState(): BusinessProfileFormState = BusinessProfileFormState(
    tradingName = tradingName,
    legalName = legalName.orEmpty(),
    addressLine1 = addressLine1.orEmpty(),
    addressCity = addressCity.orEmpty(),
    addressState = addressState.orEmpty(),
    addressPincode = addressPincode.orEmpty(),
    phone = phone.orEmpty(),
    email = email.orEmpty(),
    gstin = gstin.orEmpty(),
    website = website.orEmpty(),
    description = description.orEmpty(),
)
