package com.budcom.android.feature.connect.presentation

import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.Tag

/** One editable Tally-compatible field row, with its current effective value and provenance —
 * the UI reads this instead of poking at [Party] + a raw provenance map directly. */
data class PartyFieldRowUi(
    val fieldName: String,
    val label: String,
    val value: String?,
    val provenanceLabel: String,
    val isConflict: Boolean,
)

data class VoucherPickOptionUi(
    val voucherId: String,
    val label: String,
)

data class PartyDetailUiState(
    val companyId: String? = null,
    val partyId: String? = null,
    val isLoading: Boolean = true,
    val party: Party? = null,
    val sourceLink: PartySourceLink? = null,
    val balanceLabel: String? = null,
    val fieldRows: List<PartyFieldRowUi> = emptyList(),
    val contactPersons: List<PartyContactPerson> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val notes: List<PartyNote> = emptyList(),
    val notesPage: Int = 1,
    val notesCanLoadMore: Boolean = false,
    val isLoadingMoreNotes: Boolean = false,
    val error: MasterDataUiError? = null,
    val notice: String? = null,
    val activeDialog: PartyDetailDialog? = null,
) {
    val hasAccountingLink: Boolean get() = sourceLink != null
}

sealed interface PartyDetailDialog {
    data class EditField(val fieldName: String, val label: String, val currentValue: String) : PartyDetailDialog
    data class ContactPersonEditor(
        val contactPersonId: String? = null,
        val name: String = "",
        val designation: String = "",
        val mobile: String = "",
        val whatsapp: String = "",
        val email: String = "",
        val isPrimary: Boolean = false,
    ) : PartyDetailDialog
    data object AddTag : PartyDetailDialog
    data class AddNote(
        val body: String = "",
        val voucherOptions: List<VoucherPickOptionUi> = emptyList(),
        val selectedVoucherId: String? = null,
        val isLoadingVouchers: Boolean = false,
    ) : PartyDetailDialog
}

sealed interface PartyDetailEffect {
    data class OpenLedgerStatement(val ledgerId: String) : PartyDetailEffect
    data class OpenVouchers(val query: String) : PartyDetailEffect
    data class OpenVoucherDetails(val voucherId: String) : PartyDetailEffect
    data class LaunchCall(val phoneE164: String) : PartyDetailEffect
    data class LaunchWhatsApp(val phoneE164: String) : PartyDetailEffect
    data object OpenXmlExport : PartyDetailEffect
}

sealed interface PartyDetailEvent {
    data object Load : PartyDetailEvent
    data object Retry : PartyDetailEvent
    data object LoadMoreNotes : PartyDetailEvent
    data class ViewLedgerTapped(val ledgerId: String?) : PartyDetailEvent
    data class ViewVouchersTapped(val ledgerName: String) : PartyDetailEvent
    data object ExportToTallyTapped : PartyDetailEvent
    data class CallTapped(val phoneE164: String?) : PartyDetailEvent
    data class WhatsAppTapped(val phoneE164: String?) : PartyDetailEvent

    data class EditFieldTapped(val fieldName: String, val label: String) : PartyDetailEvent
    data class EditFieldValueChanged(val value: String) : PartyDetailEvent
    data object SaveEditedField : PartyDetailEvent

    data object AddContactTapped : PartyDetailEvent
    data class EditContactTapped(val contactPersonId: String) : PartyDetailEvent
    data class ContactFieldChanged(
        val name: String? = null,
        val designation: String? = null,
        val mobile: String? = null,
        val whatsapp: String? = null,
        val email: String? = null,
        val isPrimary: Boolean? = null,
    ) : PartyDetailEvent
    data object SaveContact : PartyDetailEvent
    data class DeleteContactTapped(val contactPersonId: String) : PartyDetailEvent

    data object AddTagTapped : PartyDetailEvent
    data class AssignExistingTag(val tagId: String) : PartyDetailEvent
    data class CreateAndAssignTag(val name: String) : PartyDetailEvent
    data class RemoveTagTapped(val tagId: String) : PartyDetailEvent

    data object AddNoteTapped : PartyDetailEvent
    data class NoteBodyChanged(val body: String) : PartyDetailEvent
    data class NoteVoucherSelected(val voucherId: String?) : PartyDetailEvent
    data object SaveNote : PartyDetailEvent
    data class DeleteNoteTapped(val noteId: String) : PartyDetailEvent
    data class LinkedVoucherTapped(val voucherId: String) : PartyDetailEvent

    data object DismissDialog : PartyDetailEvent
    data object DismissNotice : PartyDetailEvent
}

internal fun FieldProvenanceState.toUiLabel(): String = when (this) {
    FieldProvenanceState.ConfirmedFromTally -> "Confirmed from Tally"
    FieldProvenanceState.BudcomOnlyPending -> "Pending in BUDCOM"
    FieldProvenanceState.ExportReady -> "Ready to export"
    FieldProvenanceState.Exported -> "Exported, awaiting Tally"
    FieldProvenanceState.Conflict -> "Needs review"
    FieldProvenanceState.EmptyUnknown -> "Not set"
}

internal val PARTY_DETAIL_FIELD_LABELS: List<Pair<String, String>> = listOf(
    PartyFieldNames.PRIMARY_PHONE to "Phone",
    PartyFieldNames.PRIMARY_EMAIL to "Email",
    PartyFieldNames.ADDRESS_LINE1 to "Address",
    PartyFieldNames.ADDRESS_CITY to "City",
    PartyFieldNames.ADDRESS_STATE to "State",
    PartyFieldNames.ADDRESS_PINCODE to "Pincode",
    PartyFieldNames.GSTIN to "GSTIN",
)
