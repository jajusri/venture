package com.jajusri.venture.feature.businessprofile.presentation

import android.net.Uri
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import java.io.File

/**
 * MVP-1.3-A Business Profile screen state. A single inline view/edit toggle over one whole-entity
 * form — deliberately not eleven separate per-field dialogs like Party Detail's `EditField`
 * pattern: unlike Party Detail (a multi-section screen where fields are one part alongside
 * contacts/tags/notes), this entire screen *is* one entity's editor, so one Save action for the
 * full field set is simpler for a first-time setup than eleven repeated dialog interactions, while
 * still being "simple, no wizard" (task's own explicit boundary).
 */
data class BusinessProfileUiState(
    val companyId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: MasterDataUiError? = null,
    val notice: String? = null,
    val hasSavedProfile: Boolean = false,
    val isEditing: Boolean = false,
    val logoAssetPath: String? = null,
    /** Resolved, path-traversal-re-validated file for [logoAssetPath] — `null` whenever there is
     * no logo, or the stored path fails to resolve (missing/corrupt/tampered). Display code should
     * decode this, never the raw [logoAssetPath] string directly. */
    val logoFile: File? = null,
    val isUpdatingLogo: Boolean = false,
    /** The live, possibly-unsaved form the user is editing. */
    val form: BusinessProfileFormState = BusinessProfileFormState(),
    /** The last value actually persisted (or blank if never saved) — distinct from [form] so
     * Cancel can honestly revert to what was saved, not to whatever the user had half-typed. */
    val savedForm: BusinessProfileFormState = BusinessProfileFormState(),
) {
    val isBusy: Boolean get() = isLoading || isSaving
}

/** The editable field set — mirrors [com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfileDraft]'s
 * shape but as plain editable strings (never null, matching every other VENTURE text-field dialog's
 * own convention, e.g. `PartyDetailDialog.ContactPersonEditor`). */
data class BusinessProfileFormState(
    val tradingName: String = "",
    val legalName: String = "",
    val addressLine1: String = "",
    val addressCity: String = "",
    val addressState: String = "",
    val addressPincode: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val website: String = "",
    val description: String = "",
)

sealed interface BusinessProfileEvent {
    data object Load : BusinessProfileEvent
    data object Retry : BusinessProfileEvent
    data object EditTapped : BusinessProfileEvent
    data object CancelEditTapped : BusinessProfileEvent
    data class TradingNameChanged(val value: String) : BusinessProfileEvent
    data class LegalNameChanged(val value: String) : BusinessProfileEvent
    data class AddressLine1Changed(val value: String) : BusinessProfileEvent
    data class AddressCityChanged(val value: String) : BusinessProfileEvent
    data class AddressStateChanged(val value: String) : BusinessProfileEvent
    data class AddressPincodeChanged(val value: String) : BusinessProfileEvent
    data class PhoneChanged(val value: String) : BusinessProfileEvent
    data class EmailChanged(val value: String) : BusinessProfileEvent
    data class GstinChanged(val value: String) : BusinessProfileEvent
    data class WebsiteChanged(val value: String) : BusinessProfileEvent
    data class DescriptionChanged(val value: String) : BusinessProfileEvent
    data object SaveTapped : BusinessProfileEvent
    data object DismissNotice : BusinessProfileEvent
    /** Requests the system photo picker be launched — handled by [BusinessProfileRoute], which owns
     * the `ActivityResultLauncher` (a ViewModel cannot launch one directly). */
    data object ChangeLogoTapped : BusinessProfileEvent
    data class LogoPicked(val uri: Uri) : BusinessProfileEvent
    data object ClearLogoTapped : BusinessProfileEvent
}

/** One-shot, non-state UI effects — mirrors
 * [com.jajusri.venture.feature.connect.presentation.PartyXmlExportViewModel]'s own
 * `MutableSharedFlow<Effect>(extraBufferCapacity = 1)` pattern for the identical reason: launching
 * an `ActivityResultLauncher` is an Activity-scoped action a ViewModel cannot perform directly. */
sealed interface BusinessProfileEffect {
    data object RequestLogoPick : BusinessProfileEffect
}
