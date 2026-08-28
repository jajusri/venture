package com.budcom.android.feature.businessprofile.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.usecase.ClearBusinessProfileLogoUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.GetBusinessProfileUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.ResolveBusinessProfileLogoFileUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.SaveBusinessProfileUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.UpdateBusinessProfileLogoUseCase
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoFailureReason
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.core.common.UserVisibleErrorText
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
    private val updateBusinessProfileLogo: UpdateBusinessProfileLogoUseCase,
    private val clearBusinessProfileLogo: ClearBusinessProfileLogoUseCase,
    private val resolveBusinessProfileLogoFile: ResolveBusinessProfileLogoFileUseCase,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusinessProfileUiState())
    val uiState: StateFlow<BusinessProfileUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BusinessProfileEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BusinessProfileEffect> = _effects.asSharedFlow()

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
            BusinessProfileEvent.ChangeLogoTapped -> _effects.tryEmit(BusinessProfileEffect.RequestLogoPick)
            is BusinessProfileEvent.LogoPicked -> updateLogo(event.uri)
            BusinessProfileEvent.ClearLogoTapped -> clearLogo()
        }
    }

    private inline fun updateForm(crossinline transform: BusinessProfileFormState.() -> BusinessProfileFormState) {
        _uiState.update { it.copy(form = it.form.transform()) }
    }

    /** Applies [transform] only if the state is still showing [requestedCompanyId] — a no-op
     * otherwise. Guards every async save/logo operation below: without this, a company switch that
     * completes *while* a save or logo update for the previous company is still in flight would let
     * that stale result silently overwrite the newly-loaded company's state once it finally
     * resolves, mixing data across companies. `load()`'s own `loadJob` cancellation prevents the
     * analogous problem for reads; this is the equivalent guard for writes, which are not cancelled
     * mid-flight (a half-written save should still complete on disk, it just must not clobber the
     * UI of whichever company is now showing). */
    private inline fun updateIfStillOnCompany(requestedCompanyId: String, crossinline transform: BusinessProfileUiState.() -> BusinessProfileUiState) {
        _uiState.update { if (it.companyId == requestedCompanyId) it.transform() else it }
    }

    private fun load(companyId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { getBusinessProfile(companyId) }
                .onSuccess { profile ->
                    val logoFile = resolveBusinessProfileLogoFile(profile?.logoAssetPath)
                    _uiState.update { state ->
                        if (profile != null) {
                            val loaded = profile.toFormState()
                            state.copy(
                                isLoading = false,
                                hasSavedProfile = true,
                                isEditing = false,
                                logoAssetPath = profile.logoAssetPath,
                                logoFile = logoFile,
                                form = loaded,
                                savedForm = loaded,
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                hasSavedProfile = false,
                                isEditing = false,
                                logoAssetPath = null,
                                logoFile = null,
                                form = BusinessProfileFormState(),
                                savedForm = BusinessProfileFormState(),
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    Timber.w(throwable, "Business Profile load failed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = MasterDataUiError.Unexpected(UserVisibleErrorText.fromThrowable(throwable)),
                        )
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
                    val saved = profile.toFormState()
                    updateIfStillOnCompany(companyId) {
                        copy(
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
                    Timber.w(throwable, "Business Profile save failed")
                    updateIfStillOnCompany(companyId) {
                        copy(
                            isSaving = false,
                            notice = UserVisibleErrorText.fromThrowable(throwable),
                        )
                    }
                }
        }
    }

    private fun updateLogo(uri: Uri) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            updateIfStillOnCompany(companyId) { copy(isUpdatingLogo = true) }
            val result = runCatching { updateBusinessProfileLogo(companyId, uri) }.getOrNull()
            when (result) {
                null -> updateIfStillOnCompany(companyId) {
                    copy(isUpdatingLogo = false, notice = "Save the Business Profile before adding a logo.")
                }
                is BusinessProfileLogoResult.Success -> {
                    val logoFile = resolveBusinessProfileLogoFile(result.logoAssetPath)
                    updateIfStillOnCompany(companyId) {
                        copy(
                            isUpdatingLogo = false,
                            logoAssetPath = result.logoAssetPath,
                            logoFile = logoFile,
                            notice = "Logo updated.",
                        )
                    }
                }
                is BusinessProfileLogoResult.Failure -> updateIfStillOnCompany(companyId) {
                    copy(isUpdatingLogo = false, notice = result.reason.toUserMessage())
                }
            }
        }
    }

    private fun clearLogo() {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            updateIfStillOnCompany(companyId) { copy(isUpdatingLogo = true) }
            runCatching { clearBusinessProfileLogo(companyId) }
            updateIfStillOnCompany(companyId) {
                copy(isUpdatingLogo = false, logoAssetPath = null, logoFile = null, notice = "Logo removed.")
            }
        }
    }
}

private fun BusinessProfileLogoFailureReason.toUserMessage(): String = when (this) {
    BusinessProfileLogoFailureReason.UnsupportedFileType -> "Please choose a JPG, PNG, or WEBP image."
    BusinessProfileLogoFailureReason.FileTooLarge -> "That image is too large (max 5 MB)."
    BusinessProfileLogoFailureReason.UnreadableSource -> "Could not read the selected image. Please try again."
    BusinessProfileLogoFailureReason.StorageError -> "Could not save the logo. Please try again."
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
