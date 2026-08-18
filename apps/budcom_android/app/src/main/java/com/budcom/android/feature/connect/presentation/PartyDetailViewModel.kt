package com.budcom.android.feature.connect.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.PhoneNumberNormalizer
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.usecase.AssignTagUseCase
import com.budcom.android.feature.party.domain.usecase.CreateIssueUseCase
import com.budcom.android.feature.party.domain.usecase.CreateOrGetTagUseCase
import com.budcom.android.feature.party.domain.usecase.DeleteContactPersonUseCase
import com.budcom.android.feature.party.domain.usecase.DeleteNoteUseCase
import com.budcom.android.feature.party.domain.usecase.EditNoteUseCase
import com.budcom.android.feature.party.domain.usecase.GetAllTagsUseCase
import com.budcom.android.feature.party.domain.usecase.GetContactPersonsUseCase
import com.budcom.android.feature.party.domain.usecase.GetFieldProvenanceUseCase
import com.budcom.android.feature.party.domain.usecase.GetIssueActivitySummaryUseCase
import com.budcom.android.feature.party.domain.usecase.GetIssuesForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetTimelineForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyByIdUseCase
import com.budcom.android.feature.party.domain.usecase.GetSourceLinkForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetTagsForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.AddNoteUseCase
import com.budcom.android.feature.party.domain.usecase.ReopenIssueUseCase
import com.budcom.android.feature.party.domain.usecase.ResolveIssueUseCase
import com.budcom.android.feature.party.domain.usecase.UnassignTagUseCase
import com.budcom.android.feature.party.domain.usecase.UpdateBudcomOnlyFieldUseCase
import com.budcom.android.feature.party.domain.usecase.UpsertContactPersonUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class PartyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPartyById: GetPartyByIdUseCase,
    private val getFieldProvenance: GetFieldProvenanceUseCase,
    private val getContactPersons: GetContactPersonsUseCase,
    private val getTagsForParty: GetTagsForPartyUseCase,
    private val getAllTags: GetAllTagsUseCase,
    private val getSourceLinkForParty: GetSourceLinkForPartyUseCase,
    private val getTimelineForParty: GetTimelineForPartyUseCase,
    private val updateBudcomOnlyField: UpdateBudcomOnlyFieldUseCase,
    private val upsertContactPerson: UpsertContactPersonUseCase,
    private val deleteContactPerson: DeleteContactPersonUseCase,
    private val createOrGetTag: CreateOrGetTagUseCase,
    private val assignTag: AssignTagUseCase,
    private val unassignTag: UnassignTagUseCase,
    private val addNote: AddNoteUseCase,
    private val editNote: EditNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val getIssuesForParty: GetIssuesForPartyUseCase,
    private val createIssue: CreateIssueUseCase,
    private val getIssueActivitySummary: GetIssueActivitySummaryUseCase,
    private val resolveIssue: ResolveIssueUseCase,
    private val reopenIssue: ReopenIssueUseCase,
    private val loadVouchers: LoadVouchersUseCase,
    private val ledgerSnapshotPort: LedgerSnapshotPort,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val partyId: String = requireNotNull(savedStateHandle.get<String>(PARTY_ID_ARG)) { "partyId is required" }

    companion object {
        const val PARTY_ID_ARG = "partyId"
    }

    private val _uiState = MutableStateFlow(PartyDetailUiState(partyId = partyId))
    val uiState: StateFlow<PartyDetailUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PartyDetailEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<PartyDetailEffect> = _effects.asSharedFlow()

    init {
        onEvent(PartyDetailEvent.Load)
    }

    fun onEvent(event: PartyDetailEvent) {
        when (event) {
            PartyDetailEvent.Load, PartyDetailEvent.Retry -> load()
            PartyDetailEvent.LoadMoreTimeline -> loadMoreTimeline()
            is PartyDetailEvent.ViewLedgerTapped -> {
                val ledgerId = event.ledgerId
                if (ledgerId.isNullOrBlank()) showNotice("No linked Ledger for this party.") else _effects.tryEmit(PartyDetailEffect.OpenLedgerStatement(ledgerId))
            }
            is PartyDetailEvent.ViewVouchersTapped -> _effects.tryEmit(PartyDetailEffect.OpenVouchers(event.ledgerName))
            PartyDetailEvent.ExportToTallyTapped -> _effects.tryEmit(PartyDetailEffect.OpenXmlExport)
            is PartyDetailEvent.CallTapped -> {
                val phone = event.phoneE164
                if (phone.isNullOrBlank()) showNotice("No phone number available.") else _effects.tryEmit(PartyDetailEffect.LaunchCall(phone))
            }
            is PartyDetailEvent.WhatsAppTapped -> {
                val phone = event.phoneE164
                if (phone.isNullOrBlank()) showNotice("No phone number available.") else _effects.tryEmit(PartyDetailEffect.LaunchWhatsApp(phone))
            }

            is PartyDetailEvent.EditFieldTapped -> _uiState.update {
                it.copy(activeDialog = PartyDetailDialog.EditField(event.fieldName, event.label, currentFieldValue(it, event.fieldName)))
            }
            is PartyDetailEvent.EditFieldValueChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.EditField ?: return@update state
                state.copy(activeDialog = dialog.copy(currentValue = event.value))
            }
            PartyDetailEvent.SaveEditedField -> saveEditedField()

            PartyDetailEvent.AddContactTapped -> _uiState.update { it.copy(activeDialog = PartyDetailDialog.ContactPersonEditor()) }
            is PartyDetailEvent.EditContactTapped -> editContactTapped(event.contactPersonId)
            is PartyDetailEvent.ContactFieldChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.ContactPersonEditor ?: return@update state
                state.copy(
                    activeDialog = dialog.copy(
                        name = event.name ?: dialog.name,
                        designation = event.designation ?: dialog.designation,
                        mobile = event.mobile ?: dialog.mobile,
                        whatsapp = event.whatsapp ?: dialog.whatsapp,
                        email = event.email ?: dialog.email,
                        isPrimary = event.isPrimary ?: dialog.isPrimary,
                    ),
                )
            }
            PartyDetailEvent.SaveContact -> saveContact()
            is PartyDetailEvent.DeleteContactTapped -> deleteContact(event.contactPersonId)

            PartyDetailEvent.AddTagTapped -> _uiState.update { it.copy(activeDialog = PartyDetailDialog.AddTag) }
            is PartyDetailEvent.AssignExistingTag -> assignExistingTag(event.tagId)
            is PartyDetailEvent.CreateAndAssignTag -> createAndAssignTag(event.name)
            is PartyDetailEvent.RemoveTagTapped -> removeTag(event.tagId)

            PartyDetailEvent.AddNoteTapped -> openAddNoteDialog()
            is PartyDetailEvent.EditNoteTapped -> openEditNoteDialog(event.noteId)
            is PartyDetailEvent.NoteBodyChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(activeDialog = dialog.copy(body = event.body))
            }
            is PartyDetailEvent.NoteTypeChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(
                    activeDialog = dialog.copy(
                        type = event.type,
                        dueAtText = if (event.type.showsDueDate()) dialog.dueAtText else "",
                    ),
                )
            }
            is PartyDetailEvent.NoteDueAtChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(activeDialog = dialog.copy(dueAtText = event.text))
            }
            is PartyDetailEvent.NoteIssueSelected -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(
                    activeDialog = dialog.copy(
                        selectedIssueId = event.issueId,
                        newIssueTitle = if (event.issueId != null) "" else dialog.newIssueTitle,
                    ),
                )
            }
            is PartyDetailEvent.NoteNewIssueTitleChanged -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(
                    activeDialog = dialog.copy(
                        newIssueTitle = event.title,
                        selectedIssueId = if (event.title.isNotBlank()) null else dialog.selectedIssueId,
                    ),
                )
            }
            is PartyDetailEvent.NoteVoucherSelected -> _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(activeDialog = dialog.copy(selectedVoucherId = event.voucherId))
            }
            PartyDetailEvent.SaveNote -> saveNote()
            is PartyDetailEvent.DeleteNoteTapped -> deleteNoteTapped(event.noteId)
            is PartyDetailEvent.LinkedVoucherTapped -> {
                val note = _uiState.value.notesInTimeline.firstOrNull { it.linkedVoucherId == event.voucherId }
                if (note != null) _effects.tryEmit(PartyDetailEffect.OpenVoucherDetails(event.voucherId))
                else showNotice("Linked voucher is not available.")
            }

            PartyDetailEvent.ToggleIssuesExpanded -> _uiState.update { it.copy(issuesExpanded = !it.issuesExpanded) }
            PartyDetailEvent.ToggleResolvedIssuesExpanded -> _uiState.update { it.copy(resolvedIssuesExpanded = !it.resolvedIssuesExpanded) }
            is PartyDetailEvent.ResolveIssueTapped -> resolveIssueTapped(event.issueId)
            is PartyDetailEvent.ReopenIssueTapped -> reopenIssueTapped(event.issueId)
            is PartyDetailEvent.IssueFilterTapped -> {
                val next = if (_uiState.value.selectedIssueFilterId == event.issueId) null else event.issueId
                _uiState.update { it.copy(selectedIssueFilterId = next) }
                loadTimeline(next)
            }
            PartyDetailEvent.ClearIssueFilterTapped -> {
                _uiState.update { it.copy(selectedIssueFilterId = null) }
                loadTimeline(null)
            }

            PartyDetailEvent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            PartyDetailEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    private fun currentFieldValue(state: PartyDetailUiState, fieldName: String): String =
        state.fieldRows.firstOrNull { it.fieldName == fieldName }?.value.orEmpty()

    private fun showNotice(message: String) {
        _uiState.update { it.copy(notice = message) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = MasterDataUiError.Message("Select a company before viewing this party.")) }
                return@launch
            }
            val party = getPartyById(companyId, partyId)
            if (party == null) {
                _uiState.update { it.copy(isLoading = false, error = MasterDataUiError.Message("This party could not be found.")) }
                return@launch
            }

            val provenance = getFieldProvenance(companyId, partyId).associateBy { it.fieldName }
            val fieldRows = PARTY_DETAIL_FIELD_LABELS.map { (fieldName, label) ->
                val effectiveValue = effectiveFieldValue(party, fieldName)
                val state = provenance[fieldName]?.state ?: if (effectiveValue.isNullOrBlank()) FieldProvenanceState.EmptyUnknown else FieldProvenanceState.BudcomOnlyPending
                PartyFieldRowUi(
                    fieldName = fieldName,
                    label = label,
                    value = effectiveValue,
                    provenanceLabel = state.toUiLabel(),
                    isConflict = state == FieldProvenanceState.Conflict,
                )
            }

            val sourceLink = getSourceLinkForParty(companyId, partyId)
            val balanceLabel = sourceLink?.let { link ->
                ledgerSnapshotPort.getCachedLedgers(companyId).firstOrNull { it.id == link.externalEntityId }
                    ?.closingBalance?.let { money -> "${money.amount} ${money.side.name}" }
            }

            val contactPersons = getContactPersons(companyId, partyId)
            val tags = getTagsForParty(companyId, partyId)
            val allTags = getAllTags()
            val timelinePage = getTimelineForParty(companyId, partyId, page = 1, pageSize = 20, issueId = null)
            val issueCards = loadIssueCards(companyId)

            _uiState.update {
                it.copy(
                    companyId = companyId,
                    isLoading = false,
                    party = party,
                    sourceLink = sourceLink,
                    balanceLabel = balanceLabel,
                    fieldRows = fieldRows,
                    contactPersons = contactPersons,
                    tags = tags,
                    allTags = allTags,
                    timeline = timelinePage.items,
                    timelinePage = timelinePage.page,
                    timelineCanLoadMore = timelinePage.canLoadMore,
                    issues = issueCards,
                    selectedIssueFilterId = null,
                    error = null,
                )
            }
        }
    }

    /** Open issues first, then resolved — [PartyIssue]'s own DAO ordering (1.2-A) preserved
     * verbatim; note-count/last-activity joined in from a single bounded aggregate read, never
     * one query per issue. */
    private suspend fun loadIssueCards(companyId: String): List<IssueCardUi> {
        val issues = getIssuesForParty(companyId, partyId)
        val activity = getIssueActivitySummary(companyId, partyId)
        return issues.map { issue ->
            val summary = activity[issue.issueId]
            val lastActivityAt = maxOf(issue.createdAt, issue.updatedAt, issue.resolvedAt ?: 0L, summary?.latestNoteAt ?: 0L)
            IssueCardUi(issue = issue, noteCount = summary?.noteCount ?: 0, lastActivityAt = lastActivityAt)
        }
    }

    private fun loadMoreTimeline() {
        val state = _uiState.value
        val companyId = state.companyId ?: return
        if (!state.timelineCanLoadMore || state.isLoadingMoreTimeline) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreTimeline = true) }
            val nextPage = getTimelineForParty(companyId, partyId, page = state.timelinePage + 1, pageSize = 20, issueId = state.selectedIssueFilterId)
            _uiState.update {
                it.copy(
                    isLoadingMoreTimeline = false,
                    timeline = it.timeline + nextPage.items,
                    timelinePage = nextPage.page,
                    timelineCanLoadMore = nextPage.canLoadMore,
                )
            }
        }
    }

    /** Partial refresh of just the Timeline section (e.g. tapping an issue to filter it) — never
     * re-fetches party/fields/contacts/tags, unlike the full [load]. */
    private fun loadTimeline(issueId: String?) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            val timelinePage = getTimelineForParty(companyId, partyId, page = 1, pageSize = 20, issueId = issueId)
            _uiState.update {
                it.copy(timeline = timelinePage.items, timelinePage = timelinePage.page, timelineCanLoadMore = timelinePage.canLoadMore)
            }
        }
    }

    private fun resolveIssueTapped(issueId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            resolveIssue(companyId, issueId)
            _uiState.update { it.copy(issues = loadIssueCards(companyId)) }
            loadTimeline(_uiState.value.selectedIssueFilterId)
        }
    }

    private fun reopenIssueTapped(issueId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            reopenIssue(companyId, issueId)
            _uiState.update { it.copy(issues = loadIssueCards(companyId)) }
            loadTimeline(_uiState.value.selectedIssueFilterId)
        }
    }

    private fun saveEditedField() {
        val dialog = _uiState.value.activeDialog as? PartyDetailDialog.EditField ?: return
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            updateBudcomOnlyField(companyId, partyId, dialog.fieldName, dialog.currentValue.trim().ifEmpty { null })
            _uiState.update { it.copy(activeDialog = null) }
            load()
        }
    }

    private fun editContactTapped(contactPersonId: String) {
        val contact = _uiState.value.contactPersons.firstOrNull { it.contactPersonId == contactPersonId } ?: return
        _uiState.update {
            it.copy(
                activeDialog = PartyDetailDialog.ContactPersonEditor(
                    contactPersonId = contact.contactPersonId,
                    name = contact.name,
                    designation = contact.designation.orEmpty(),
                    mobile = contact.mobile.orEmpty(),
                    whatsapp = contact.whatsappNumber.orEmpty(),
                    email = contact.email.orEmpty(),
                    isPrimary = contact.isPrimary,
                ),
            )
        }
    }

    private fun saveContact() {
        val dialog = _uiState.value.activeDialog as? PartyDetailDialog.ContactPersonEditor ?: return
        val companyId = _uiState.value.companyId ?: return
        if (dialog.name.isBlank()) {
            showNotice("A contact needs a name.")
            return
        }
        viewModelScope.launch {
            upsertContactPerson(
                companyId, partyId, dialog.contactPersonId, dialog.name.trim(),
                dialog.designation.trim().ifEmpty { null }, dialog.mobile.trim().ifEmpty { null },
                dialog.whatsapp.trim().ifEmpty { null }, dialog.email.trim().ifEmpty { null }, dialog.isPrimary,
            )
            _uiState.update { it.copy(activeDialog = null) }
            load()
        }
    }

    private fun deleteContact(contactPersonId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            deleteContactPerson(companyId, contactPersonId)
            load()
        }
    }

    private fun assignExistingTag(tagId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            assignTag(companyId, partyId, tagId)
            _uiState.update { it.copy(activeDialog = null) }
            load()
        }
    }

    private fun createAndAssignTag(name: String) {
        val companyId = _uiState.value.companyId ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = createOrGetTag(name.trim(), null)
            assignTag(companyId, partyId, tag.tagId)
            _uiState.update { it.copy(activeDialog = null) }
            load()
        }
    }

    private fun removeTag(tagId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            unassignTag(companyId, partyId, tagId)
            load()
        }
    }

    private fun openAddNoteDialog() {
        _uiState.update { it.copy(activeDialog = PartyDetailDialog.NoteEditor(isLoadingVouchers = true)) }
        loadNoteEditorOptions()
    }

    private fun openEditNoteDialog(noteId: String) {
        val note = _uiState.value.notesInTimeline.firstOrNull { it.noteId == noteId } ?: return
        _uiState.update {
            it.copy(
                activeDialog = PartyDetailDialog.NoteEditor(
                    noteId = note.noteId,
                    body = note.body,
                    type = note.type,
                    dueAtText = note.dueAt?.let(::formatDueAt).orEmpty(),
                    selectedIssueId = note.issueId,
                    selectedVoucherId = note.linkedVoucherId,
                    isLoadingVouchers = true,
                ),
            )
        }
        loadNoteEditorOptions()
    }

    private fun loadNoteEditorOptions() {
        val companyId = _uiState.value.companyId
        val isEditingExistingNote = (_uiState.value.activeDialog as? PartyDetailDialog.NoteEditor)?.noteId != null
        val ledgerName = _uiState.value.sourceLink?.let { _uiState.value.party?.displayName }
        viewModelScope.launch {
            val issues = companyId?.let { getIssuesForParty(it, partyId) }
                ?.filter { it.status == IssueStatus.Open }
                .orEmpty()
            // The voucher picker is Add-only (editNote never carries linkedVoucherId — see the
            // Compose dialog's own comment) — skip the fetch entirely in edit mode.
            val vouchers = if (!isEditingExistingNote && companyId != null && ledgerName != null) {
                val today = LocalDate.now()
                val query = VoucherQuery(
                    companyId = companyId,
                    dateRange = VoucherDateRange(today.minusDays(365).toString(), today.toString()),
                    partyName = ledgerName,
                    page = 1,
                    pageSize = 20,
                )
                when (val result = loadVouchers(query)) {
                    is AppResult.Success -> result.value.items.map {
                        VoucherPickOptionUi(it.identity.id, "${it.date} · ${it.type} · ${it.number.orEmpty()}")
                    }
                    is AppResult.Failure -> emptyList()
                }
            } else {
                emptyList()
            }
            _uiState.update { state ->
                val dialog = state.activeDialog as? PartyDetailDialog.NoteEditor ?: return@update state
                state.copy(activeDialog = dialog.copy(issueOptions = issues, voucherOptions = vouchers, isLoadingVouchers = false))
            }
        }
    }

    private fun saveNote() {
        val dialog = _uiState.value.activeDialog as? PartyDetailDialog.NoteEditor ?: return
        val companyId = _uiState.value.companyId ?: return
        if (dialog.body.isBlank()) {
            showNotice("A note needs some text.")
            return
        }
        val dueAtText = dialog.dueAtText.trim()
        if (dialog.type.showsDueDate() && dueAtText.isNotEmpty() && parseDueAt(dueAtText) == null) {
            showNotice("Due date must be in YYYY-MM-DD format.")
            return
        }
        val dueAt = if (dialog.type.showsDueDate()) dueAtText.ifEmpty { null }?.let(::parseDueAt) else null
        viewModelScope.launch {
            val issueId = resolveIssueId(companyId, dialog)
            if (dialog.noteId == null) {
                addNote(companyId, partyId, dialog.body.trim(), dialog.selectedVoucherId, dialog.type, dueAt, issueId)
            } else {
                editNote(companyId, dialog.noteId, dialog.body.trim(), dialog.type, dueAt, issueId)
            }
            _uiState.update { it.copy(activeDialog = null) }
            load()
        }
    }

    private suspend fun resolveIssueId(companyId: String, dialog: PartyDetailDialog.NoteEditor): String? {
        dialog.selectedIssueId?.let { return it }
        val title = dialog.newIssueTitle.trim()
        if (title.isEmpty()) return null
        return createIssue(companyId, partyId, title).issueId
    }

    private fun deleteNoteTapped(noteId: String) {
        val companyId = _uiState.value.companyId ?: return
        viewModelScope.launch {
            deleteNote(companyId, noteId)
            load()
        }
    }
}

/** ISO `yyyy-MM-dd`, matching this app's existing date-string convention (e.g. [VoucherDateRange]).
 * Deliberately a plain text field, not a full date-picker dialog — an interim, minimal-UI choice
 * for this foundation milestone; invalid/blank text is treated as "no due date" (see [saveNote]),
 * never crashes. */
private fun parseDueAt(text: String): Long? =
    runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

private fun formatDueAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun effectiveFieldValue(party: Party, fieldName: String): String? = when (fieldName) {
    PartyFieldNames.PRIMARY_PHONE -> party.primaryPhone
    PartyFieldNames.PRIMARY_EMAIL -> party.primaryEmail
    PartyFieldNames.ADDRESS_LINE1 -> party.addressLine1
    PartyFieldNames.ADDRESS_CITY -> party.addressCity
    PartyFieldNames.ADDRESS_STATE -> party.addressState
    PartyFieldNames.ADDRESS_PINCODE -> party.addressPincode
    PartyFieldNames.GSTIN -> party.gstin
    else -> null
}
