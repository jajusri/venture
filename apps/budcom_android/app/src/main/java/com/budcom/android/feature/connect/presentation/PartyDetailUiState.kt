package com.budcom.android.feature.connect.presentation

import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.model.TimelineEntry

/** One Issues-section card (MVP-1.2-C) — the issue plus its computed note-count/last-activity
 * rollup, exactly like [PartyFieldRowUi] reads pre-joined data rather than the raw model +
 * provenance map. [lastActivityAt] is the most recent of the issue's own timestamps and its
 * latest linked note, so "last activity" never understates real activity. */
data class IssueCardUi(
    val issue: PartyIssue,
    val noteCount: Int,
    val lastActivityAt: Long,
)

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
    val timeline: List<TimelineEntry> = emptyList(),
    val timelinePage: Int = 1,
    val timelineCanLoadMore: Boolean = false,
    val isLoadingMoreTimeline: Boolean = false,
    val issues: List<IssueCardUi> = emptyList(),
    val issuesExpanded: Boolean = false,
    val resolvedIssuesExpanded: Boolean = false,
    val selectedIssueFilterId: String? = null,
    val error: MasterDataUiError? = null,
    val notice: String? = null,
    val activeDialog: PartyDetailDialog? = null,
) {
    val hasAccountingLink: Boolean get() = sourceLink != null

    /** The Timeline's note-kind entries, unwrapped — used by note edit/delete/linked-voucher
     * lookups that only ever operate on a note, never an export event (architecture §10: the
     * Timeline is the single presentation of this data, so notes are read from it, not from a
     * second, separately-fetched list — see [com.budcom.android.feature.connect.presentation.PartyDetailViewModel]). */
    val notesInTimeline: List<PartyNote> get() = timeline.filterIsInstance<TimelineEntry.NoteEvent>().map { it.note }

    val openIssues: List<IssueCardUi> get() = issues.filter { it.issue.status == IssueStatus.Open }
    val resolvedIssues: List<IssueCardUi> get() = issues.filter { it.issue.status == IssueStatus.Resolved }

    /** The currently-selected filter issue, if any — resolved from [issues] rather than stored
     * separately, so the filter banner can never show stale title text. */
    val selectedIssueFilter: IssueCardUi? get() = issues.firstOrNull { it.issue.issueId == selectedIssueFilterId }
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

    /** Serves both add (`noteId == null`) and edit (`noteId` set), exactly like
     * [ContactPersonEditor] already does for contact persons — one dialog, one shape, an optional
     * id field indicating mode. */
    data class NoteEditor(
        val noteId: String? = null,
        val body: String = "",
        val type: NoteType = NoteType.General,
        val dueAtText: String = "",
        val issueOptions: List<PartyIssue> = emptyList(),
        val selectedIssueId: String? = null,
        val newIssueTitle: String = "",
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
    data object LoadMoreTimeline : PartyDetailEvent
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
    data class EditNoteTapped(val noteId: String) : PartyDetailEvent
    data class NoteBodyChanged(val body: String) : PartyDetailEvent
    data class NoteTypeChanged(val type: NoteType) : PartyDetailEvent
    data class NoteDueAtChanged(val text: String) : PartyDetailEvent
    data class NoteIssueSelected(val issueId: String?) : PartyDetailEvent
    data class NoteNewIssueTitleChanged(val title: String) : PartyDetailEvent
    data class NoteVoucherSelected(val voucherId: String?) : PartyDetailEvent
    data object SaveNote : PartyDetailEvent
    data class DeleteNoteTapped(val noteId: String) : PartyDetailEvent
    data class LinkedVoucherTapped(val voucherId: String) : PartyDetailEvent
    /** Marks a `commitment`/`follow_up` note done ([PartyNote.completedAt] set to now) or reopens
     * it (cleared) — mirrors [ResolveIssueTapped]/[ReopenIssueTapped]'s exact split-event shape.
     * The only UI path to [com.budcom.android.feature.party.domain.usecase.SetNoteCompletionUseCase],
     * which existed at the repository/use-case layer since MVP-1.2-A but had no caller until now
     * (MVP-1.2-E) — without this, a Dincharya follow-up had no honest "done" action anywhere in the
     * app, only the indirect side effect of editing away its type/due-date. */
    data class MarkNoteDoneTapped(val noteId: String) : PartyDetailEvent
    data class ReopenNoteTapped(val noteId: String) : PartyDetailEvent

    data object ToggleIssuesExpanded : PartyDetailEvent
    data object ToggleResolvedIssuesExpanded : PartyDetailEvent
    data class ResolveIssueTapped(val issueId: String) : PartyDetailEvent
    data class ReopenIssueTapped(val issueId: String) : PartyDetailEvent
    /** Tapping the already-selected issue again clears the filter (toggle), matching the note/tag
     * pickers' own toggle-selection convention elsewhere on this screen. */
    data class IssueFilterTapped(val issueId: String) : PartyDetailEvent
    data object ClearIssueFilterTapped : PartyDetailEvent

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

internal fun NoteType.toUiLabel(): String = when (this) {
    NoteType.General -> "General"
    NoteType.PaymentIssue -> "Payment issue"
    NoteType.Complaint -> "Complaint"
    NoteType.DeliveryIssue -> "Delivery issue"
    NoteType.Commitment -> "Commitment"
    NoteType.ProductInterest -> "Product interest"
    NoteType.InternalRemark -> "Internal remark"
    NoteType.FollowUp -> "Follow-up"
}

/** Only [NoteType.Commitment]/[NoteType.FollowUp] carry a meaningful due date (architecture §9.1). */
internal fun NoteType.showsDueDate(): Boolean = this == NoteType.Commitment || this == NoteType.FollowUp

internal fun IssueStatus.toUiLabel(): String = when (this) {
    IssueStatus.Open -> "Open"
    IssueStatus.Resolved -> "Resolved"
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
