package com.jajusri.venture.feature.connect.presentation

import androidx.lifecycle.SavedStateHandle
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
import com.jajusri.venture.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.jajusri.venture.feature.party.domain.model.EligibleLedgerSeed
import com.jajusri.venture.feature.party.domain.model.FieldProvenanceState
import com.jajusri.venture.feature.party.domain.model.IssueActivitySummary
import com.jajusri.venture.feature.party.domain.model.IssueStatus
import com.jajusri.venture.feature.party.domain.model.LedgerIdentitySource
import com.jajusri.venture.feature.party.domain.model.NoteType
import com.jajusri.venture.feature.party.domain.model.Party
import com.jajusri.venture.feature.party.domain.model.PartyClassification
import com.jajusri.venture.feature.party.domain.model.PartyContactPerson
import com.jajusri.venture.feature.party.domain.model.PartyFieldNames
import com.jajusri.venture.feature.party.domain.model.PartyFieldProvenance
import com.jajusri.venture.feature.party.domain.model.PartyIssue
import com.jajusri.venture.feature.party.domain.model.PartyNote
import com.jajusri.venture.feature.party.domain.model.PartyNotePage
import com.jajusri.venture.feature.party.domain.model.PartyPage
import com.jajusri.venture.feature.party.domain.model.PartySourceLink
import com.jajusri.venture.feature.party.domain.model.PartySourceType
import com.jajusri.venture.feature.party.domain.model.ProspectDraft
import com.jajusri.venture.feature.party.domain.model.Tag
import com.jajusri.venture.feature.party.domain.model.TimelineEntry
import com.jajusri.venture.feature.party.domain.model.TimelineEntryPage
import com.jajusri.venture.feature.party.domain.repository.PartyRepository
import com.jajusri.venture.feature.party.domain.usecase.AddNoteUseCase
import com.jajusri.venture.feature.party.domain.usecase.AssignTagUseCase
import com.jajusri.venture.feature.party.domain.usecase.CreateIssueUseCase
import com.jajusri.venture.feature.party.domain.usecase.CreateOrGetTagUseCase
import com.jajusri.venture.feature.party.domain.usecase.DeleteContactPersonUseCase
import com.jajusri.venture.feature.party.domain.usecase.DeleteNoteUseCase
import com.jajusri.venture.feature.party.domain.usecase.EditNoteUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetAllTagsUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetContactPersonsUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetFieldProvenanceUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetIssueActivitySummaryUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetIssuesForPartyUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetTimelineForPartyUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetPartyByIdUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetSourceLinkForPartyUseCase
import com.jajusri.venture.feature.party.domain.usecase.GetTagsForPartyUseCase
import com.jajusri.venture.feature.party.domain.usecase.ReopenIssueUseCase
import com.jajusri.venture.feature.party.domain.usecase.ResolveIssueUseCase
import com.jajusri.venture.feature.party.domain.usecase.SetNoteCompletionUseCase
import com.jajusri.venture.feature.party.domain.usecase.UnassignTagUseCase
import com.jajusri.venture.feature.party.domain.usecase.UpdateVentureOnlyFieldUseCase
import com.jajusri.venture.feature.party.domain.usecase.UpsertContactPersonUseCase
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import com.jajusri.venture.feature.voucher.domain.usecase.LoadVouchersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

private const val FIXED_NOW = 10_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class PartyDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryPartyRepository
    private val timeProvider = TimeProvider { FIXED_NOW }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryPartyRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        partyId: String,
        ledgers: List<Ledger> = emptyList(),
        voucherRepository: VoucherRepository = PartyDetailTestFakeVoucherRepository(),
    ) = PartyDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PartyDetailViewModel.PARTY_ID_ARG to partyId)),
        getPartyById = GetPartyByIdUseCase(repository),
        getFieldProvenance = GetFieldProvenanceUseCase(repository),
        getContactPersons = GetContactPersonsUseCase(repository),
        getTagsForParty = GetTagsForPartyUseCase(repository),
        getAllTags = GetAllTagsUseCase(repository),
        getSourceLinkForParty = GetSourceLinkForPartyUseCase(repository),
        getTimelineForParty = GetTimelineForPartyUseCase(repository),
        updateVentureOnlyField = UpdateVentureOnlyFieldUseCase(repository),
        upsertContactPerson = UpsertContactPersonUseCase(repository),
        deleteContactPerson = DeleteContactPersonUseCase(repository),
        createOrGetTag = CreateOrGetTagUseCase(repository),
        assignTag = AssignTagUseCase(repository),
        unassignTag = UnassignTagUseCase(repository),
        addNote = AddNoteUseCase(repository),
        editNote = EditNoteUseCase(repository),
        deleteNote = DeleteNoteUseCase(repository),
        setNoteCompletion = SetNoteCompletionUseCase(repository),
        getIssuesForParty = GetIssuesForPartyUseCase(repository),
        createIssue = CreateIssueUseCase(repository),
        getIssueActivitySummary = GetIssueActivitySummaryUseCase(repository),
        resolveIssue = ResolveIssueUseCase(repository),
        reopenIssue = ReopenIssueUseCase(repository),
        loadVouchers = LoadVouchersUseCase(voucherRepository),
        ledgerSnapshotPort = PartyDetailTestFakeLedgerSnapshotPort(ledgers),
        companySession = PartyDetailTestFakeCompanySession("co-1"),
        timeProvider = timeProvider,
    )

    // ============================== LOAD ==============================

    @Test
    fun `Tally-backed Party shows balance and accounting link`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId, ledgers = listOf(ledger("guid:abc", "1000.00", "Dr")))
        advanceUntilIdle()

        assertEquals("1000.00 Dr", vm.uiState.value.balanceLabel)
        assertTrue(vm.uiState.value.hasAccountingLink)
    }

    @Test
    fun `a Prospect shows no balance and no accounting link`() = runTest(dispatcher) {
        val prospect = repository.createProspect("co-1", ProspectDraft(displayName = "New Bakery"))
        val vm = createViewModel(prospect.partyId)
        advanceUntilIdle()

        assertNull(vm.uiState.value.balanceLabel)
        assertFalse(vm.uiState.value.hasAccountingLink)
    }

    @Test
    fun `pending confirmed and conflict fields show distinct provenance labels`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.PRIMARY_EMAIL, FieldProvenanceState.VentureOnlyPending)
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.GSTIN, FieldProvenanceState.ConfirmedFromTally)
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.ADDRESS_STATE, FieldProvenanceState.Conflict)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        val rows = vm.uiState.value.fieldRows.associateBy { it.fieldName }
        assertEquals("Pending in VENTURE", rows.getValue(PartyFieldNames.PRIMARY_EMAIL).provenanceLabel)
        assertEquals("Confirmed from Tally", rows.getValue(PartyFieldNames.GSTIN).provenanceLabel)
        assertEquals("Needs review", rows.getValue(PartyFieldNames.ADDRESS_STATE).provenanceLabel)
        assertTrue(rows.getValue(PartyFieldNames.ADDRESS_STATE).isConflict)
    }

    @Test
    fun `a Party with no phone shows Call and WhatsApp without crashing`() = runTest(dispatcher) {
        val party = repository.createProspect("co-1", ProspectDraft(displayName = "No Phone Yet"))
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        assertNull(vm.uiState.value.party?.primaryPhone)
    }

    @Test
    fun `a missing party shows an honest error, not a crash`() = runTest(dispatcher) {
        val vm = createViewModel("does-not-exist")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
    }

    // ============================== FIELD EDITING ==============================

    @Test
    fun `editing a field saves it as pending and refreshes the row`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.EditFieldTapped(PartyFieldNames.PRIMARY_EMAIL, "Email"))
        vm.onEvent(PartyDetailEvent.EditFieldValueChanged("owner@example.com"))
        vm.onEvent(PartyDetailEvent.SaveEditedField)
        advanceUntilIdle()

        val row = vm.uiState.value.fieldRows.single { it.fieldName == PartyFieldNames.PRIMARY_EMAIL }
        assertEquals("owner@example.com", row.value)
        assertEquals("Pending in VENTURE", row.provenanceLabel)
        assertEquals(null, vm.uiState.value.activeDialog)
    }

    // ============================== CONTACT PERSONS ==============================

    @Test
    fun `adding a contact person appears in the list`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddContactTapped)
        vm.onEvent(PartyDetailEvent.ContactFieldChanged(name = "Owner Name", isPrimary = true))
        vm.onEvent(PartyDetailEvent.SaveContact)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.contactPersons.size)
        assertEquals("Owner Name", vm.uiState.value.contactPersons.single().name)
    }

    @Test
    fun `adding a second primary contact demotes the first`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddContactTapped)
        vm.onEvent(PartyDetailEvent.ContactFieldChanged(name = "Owner", isPrimary = true))
        vm.onEvent(PartyDetailEvent.SaveContact)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddContactTapped)
        vm.onEvent(PartyDetailEvent.ContactFieldChanged(name = "Accounts", isPrimary = true))
        vm.onEvent(PartyDetailEvent.SaveContact)
        advanceUntilIdle()

        val contacts = vm.uiState.value.contactPersons
        assertEquals(1, contacts.count { it.isPrimary })
        assertEquals("Accounts", contacts.first { it.isPrimary }.name)
    }

    @Test
    fun `deleting a contact person removes it from the list`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddContactTapped)
        vm.onEvent(PartyDetailEvent.ContactFieldChanged(name = "Owner"))
        vm.onEvent(PartyDetailEvent.SaveContact)
        advanceUntilIdle()
        val contactId = vm.uiState.value.contactPersons.single().contactPersonId

        vm.onEvent(PartyDetailEvent.DeleteContactTapped(contactId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.contactPersons.isEmpty())
    }

    // ============================== TAGS ==============================

    @Test
    fun `creating and assigning a new tag shows it on the party`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddTagTapped)
        vm.onEvent(PartyDetailEvent.CreateAndAssignTag("Dealer"))
        advanceUntilIdle()

        assertEquals(listOf("Dealer"), vm.uiState.value.tags.map { it.name })
    }

    @Test
    fun `removing a tag clears it from the party`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddTagTapped)
        vm.onEvent(PartyDetailEvent.CreateAndAssignTag("Dealer"))
        advanceUntilIdle()
        val tagId = vm.uiState.value.tags.single().tagId

        vm.onEvent(PartyDetailEvent.RemoveTagTapped(tagId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.tags.isEmpty())
    }

    // ============================== NOTES ==============================

    @Test
    fun `adding a note appears newest-first`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Customer called about delivery"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        assertEquals("Customer called about delivery", vm.uiState.value.notesInTimeline.first().body)
    }

    @Test
    fun `tapping an unavailable linked voucher shows a message, not a crash`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.addNote("co-1", party.partyId, "2 pieces short", "v-missing", NoteType.General, null, null)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.LinkedVoucherTapped("v-does-not-exist-in-note-list"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notice != null)
    }

    @Test
    fun `deleting a note removes it from the list`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Temp note"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()
        val noteId = vm.uiState.value.notesInTimeline.single().noteId

        vm.onEvent(PartyDetailEvent.DeleteNoteTapped(noteId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notesInTimeline.isEmpty())
    }

    @Test
    fun `a note added with no type change defaults to General`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Just a remark"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        assertEquals(NoteType.General, vm.uiState.value.notesInTimeline.single().type)
    }

    @Test
    fun `picking Follow-up type and a due date saves both`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Call back Friday"))
        vm.onEvent(PartyDetailEvent.NoteTypeChanged(NoteType.FollowUp))
        vm.onEvent(PartyDetailEvent.NoteDueAtChanged("2026-08-21"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        val note = vm.uiState.value.notesInTimeline.single()
        assertEquals(NoteType.FollowUp, note.type)
        assertTrue(note.dueAt != null && note.dueAt!! > 0)
    }

    @Test
    fun `an invalid due date shows a notice instead of silently dropping it`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Call back Friday"))
        vm.onEvent(PartyDetailEvent.NoteTypeChanged(NoteType.FollowUp))
        vm.onEvent(PartyDetailEvent.NoteDueAtChanged("not-a-date"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notice != null)
        assertTrue(vm.uiState.value.notesInTimeline.isEmpty())
    }

    @Test
    fun `editing an existing note through the note editor preserves its identity`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Original text"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()
        val noteId = vm.uiState.value.notesInTimeline.single().noteId

        vm.onEvent(PartyDetailEvent.EditNoteTapped(noteId))
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Updated text"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        val notes = vm.uiState.value.notesInTimeline
        assertEquals(1, notes.size)
        assertEquals(noteId, notes.single().noteId)
        assertEquals("Updated text", notes.single().body)
    }

    @Test
    fun `starting a new issue from the note editor groups the note under it`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("2 pieces short"))
        vm.onEvent(PartyDetailEvent.NoteNewIssueTitleChanged("Short shipment"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        val note = vm.uiState.value.notesInTimeline.single()
        assertTrue(note.issueId != null)
        assertEquals(1, repository.issuesFor("co-1", party.partyId).size)
    }

    // ============================== NOTE COMPLETION (MVP-1.2-E) ==============================
    // SetNoteCompletionUseCase existed since 1.2-A but had no UI trigger until this hardening pass —
    // without it, a Dincharya follow-up had no honest "done" action anywhere in the app.

    @Test
    fun `marking a follow-up note done sets completedAt to now, preserving the note`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Call back next week"))
        vm.onEvent(PartyDetailEvent.NoteTypeChanged(NoteType.FollowUp))
        vm.onEvent(PartyDetailEvent.NoteDueAtChanged("2026-09-01"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()
        val noteId = vm.uiState.value.notesInTimeline.single().noteId
        assertNull(vm.uiState.value.notesInTimeline.single().completedAt)

        vm.onEvent(PartyDetailEvent.MarkNoteDoneTapped(noteId))
        advanceUntilIdle()

        val notes = vm.uiState.value.notesInTimeline
        assertEquals(1, notes.size)
        assertEquals(FIXED_NOW, notes.single { it.noteId == noteId }.completedAt)
    }

    @Test
    fun `reopening a completed follow-up note clears completedAt`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.AddNoteTapped)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Call back next week"))
        vm.onEvent(PartyDetailEvent.NoteTypeChanged(NoteType.Commitment))
        vm.onEvent(PartyDetailEvent.NoteDueAtChanged("2026-09-01"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()
        val noteId = vm.uiState.value.notesInTimeline.single().noteId
        vm.onEvent(PartyDetailEvent.MarkNoteDoneTapped(noteId))
        advanceUntilIdle()
        assertEquals(FIXED_NOW, vm.uiState.value.notesInTimeline.single().completedAt)

        vm.onEvent(PartyDetailEvent.ReopenNoteTapped(noteId))
        advanceUntilIdle()

        assertNull(vm.uiState.value.notesInTimeline.single().completedAt)
    }

    // ============================== RELATIONSHIP TIMELINE (MVP-1.2-B) ==============================

    @Test
    fun `a party with no notes shows an empty timeline, not an error`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.timeline.isEmpty())
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.timelineCanLoadMore)
    }

    @Test
    fun `loading more timeline entries appends rather than replaces the page`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        repeat(25) { i ->
            repository.addNote("co-1", party.partyId, "Note $i", null, NoteType.General, null, null)
        }
        vm.onEvent(PartyDetailEvent.Retry)
        advanceUntilIdle()

        assertEquals(20, vm.uiState.value.timeline.size)
        assertTrue(vm.uiState.value.timelineCanLoadMore)

        vm.onEvent(PartyDetailEvent.LoadMoreTimeline)
        advanceUntilIdle()

        assertEquals(25, vm.uiState.value.timeline.size)
        assertFalse(vm.uiState.value.timelineCanLoadMore)
        assertEquals(25, vm.uiState.value.timeline.distinctBy { (it as com.jajusri.venture.feature.party.domain.model.TimelineEntry.NoteEvent).note.noteId }.size)
    }

    // ============================== ISSUE HISTORY (MVP-1.2-C) ==============================

    @Test
    fun `a party with no issues shows no Issues section state`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.issues.isEmpty())
    }

    @Test
    fun `issue cards carry note count and are open-first`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val resolvedIssue = repository.createIssue("co-1", party.partyId, "Already fixed")
        repository.resolveIssue("co-1", resolvedIssue.issueId)
        val openIssue = repository.createIssue("co-1", party.partyId, "Still open")
        repository.addNote("co-1", party.partyId, "2 pieces short", null, NoteType.Complaint, null, openIssue.issueId)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        assertEquals(listOf(openIssue.issueId, resolvedIssue.issueId), vm.uiState.value.issues.map { it.issue.issueId })
        assertEquals(1, vm.uiState.value.openIssues.single().noteCount)
        assertEquals(1, vm.uiState.value.resolvedIssues.size)
    }

    @Test
    fun `resolving an issue moves it out of open issues and into the timeline as resolved`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val issue = repository.createIssue("co-1", party.partyId, "Short shipment")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.ResolveIssueTapped(issue.issueId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.openIssues.isEmpty())
        assertEquals(IssueStatus.Resolved, vm.uiState.value.resolvedIssues.single().issue.status)
        assertTrue(vm.uiState.value.timeline.any { it is com.jajusri.venture.feature.party.domain.model.TimelineEntry.IssueResolvedEvent })
    }

    @Test
    fun `reopening an issue moves it back to open and clears the resolved timeline entry`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val issue = repository.createIssue("co-1", party.partyId, "Short shipment")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.ResolveIssueTapped(issue.issueId))
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.ReopenIssueTapped(issue.issueId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.resolvedIssues.isEmpty())
        assertEquals(IssueStatus.Open, vm.uiState.value.openIssues.single().issue.status)
        assertFalse(vm.uiState.value.timeline.any { it is com.jajusri.venture.feature.party.domain.model.TimelineEntry.IssueResolvedEvent })
    }

    @Test
    fun `tapping an issue filters the timeline to only that issue's notes`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val issue = repository.createIssue("co-1", party.partyId, "Short shipment")
        repository.addNote("co-1", party.partyId, "In the issue", null, NoteType.Complaint, null, issue.issueId)
        repository.addNote("co-1", party.partyId, "Unrelated", null, NoteType.General, null, null)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.IssueFilterTapped(issue.issueId))
        advanceUntilIdle()

        assertEquals(issue.issueId, vm.uiState.value.selectedIssueFilterId)
        val notes = vm.uiState.value.timeline.filterIsInstance<com.jajusri.venture.feature.party.domain.model.TimelineEntry.NoteEvent>()
        assertEquals(listOf("In the issue"), notes.map { it.note.body })
    }

    @Test
    fun `tapping the same issue filter again clears it and restores the full timeline`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val issue = repository.createIssue("co-1", party.partyId, "Short shipment")
        repository.addNote("co-1", party.partyId, "In the issue", null, NoteType.Complaint, null, issue.issueId)
        repository.addNote("co-1", party.partyId, "Unrelated", null, NoteType.General, null, null)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.IssueFilterTapped(issue.issueId))
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.IssueFilterTapped(issue.issueId))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedIssueFilterId)
        val notes = vm.uiState.value.timeline.filterIsInstance<com.jajusri.venture.feature.party.domain.model.TimelineEntry.NoteEvent>()
        assertEquals(2, notes.size)
    }

    @Test
    fun `clearing the issue filter restores the full timeline`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val issue = repository.createIssue("co-1", party.partyId, "Short shipment")
        repository.addNote("co-1", party.partyId, "Unrelated", null, NoteType.General, null, null)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.IssueFilterTapped(issue.issueId))
        advanceUntilIdle()

        vm.onEvent(PartyDetailEvent.ClearIssueFilterTapped)
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedIssueFilterId)
        assertTrue(vm.uiState.value.timeline.any { it is com.jajusri.venture.feature.party.domain.model.TimelineEntry.NoteEvent })
    }

    @Test
    fun `toggling the issues section expanded state flips it`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.createIssue("co-1", party.partyId, "Short shipment")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.issuesExpanded)

        vm.onEvent(PartyDetailEvent.ToggleIssuesExpanded)
        assertTrue(vm.uiState.value.issuesExpanded)

        vm.onEvent(PartyDetailEvent.ToggleIssuesExpanded)
        assertFalse(vm.uiState.value.issuesExpanded)
    }

    private fun ledger(id: String, amount: String, side: String) = Ledger(
        id = id,
        name = id,
        alias = null,
        parentGroup = "Sundry Debtors",
        status = com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatus.Active,
        closingBalance = com.jajusri.venture.feature.masterdata.ledger.domain.model.MoneyAmount(
            amount, "INR",
            if (side == "Dr") com.jajusri.venture.feature.masterdata.ledger.domain.model.AmountSide.Dr else com.jajusri.venture.feature.masterdata.ledger.domain.model.AmountSide.Cr,
        ),
        dataQuality = com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerDataQuality.Complete,
        syncedAt = "t",
    )
}

private class PartyDetailTestFakeLedgerSnapshotPort(private val ledgers: List<Ledger>) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = ledgers
}

private class PartyDetailTestFakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}

private class PartyDetailTestFakeVoucherRepository : VoucherRepository {
    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Success(VoucherPage(companyId = query.companyId, items = emptyList(), page = 1, pageSize = 20, totalItems = 0, totalPages = 0))
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> = AppResult.Failure(com.jajusri.venture.core.common.AppError.Offline())
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        AppResult.Failure(com.jajusri.venture.core.common.AppError.Message("unused"))
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        AppResult.Failure(com.jajusri.venture.core.common.AppError.Message("unused"))
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

/** A small, fully-functional in-memory [PartyRepository] used only by ViewModel tests that need
 * real create/read/update/delete semantics across many methods — cheaper than re-deriving
 * [com.jajusri.venture.feature.party.data.repository.PartyRepositoryImpl]'s Room-DAO fakes here. */
private class InMemoryPartyRepository : PartyRepository {
    private val parties = mutableMapOf<Pair<String, String>, Party>()
    private val sourceLinks = mutableMapOf<Pair<String, String>, PartySourceLink>() // (companyId, partyId) -> link
    private val provenance = mutableMapOf<Triple<String, String, String>, PartyFieldProvenance>()
    private val contacts = mutableMapOf<String, PartyContactPerson>()
    private val tags = mutableMapOf<String, Tag>()
    private val tagAssignments = mutableSetOf<Triple<String, String, String>>()
    private val notes = mutableMapOf<String, PartyNote>()
    private val issues = mutableMapOf<String, PartyIssue>()
    private var clock = 1_000L

    fun issuesFor(companyId: String, partyId: String): List<PartyIssue> =
        issues.values.filter { it.companyId == companyId && it.partyId == partyId }

    fun seedCustomer(companyId: String, ledgerId: String, name: String): Party {
        val partyId = UUID.randomUUID().toString()
        val party = Party(companyId, partyId, name, PartyClassification.Customer, null, null, null, null, null, null, null, null, clock, clock)
        parties[companyId to partyId] = party
        sourceLinks[companyId to partyId] = PartySourceLink(companyId, partyId, PartySourceType.TallyLedger, companyId, ledgerId, name, LedgerIdentitySource.Guid, clock)
        return party
    }

    fun setProvenance(companyId: String, partyId: String, fieldName: String, state: FieldProvenanceState) {
        provenance[Triple(companyId, partyId, fieldName)] = PartyFieldProvenance(companyId, partyId, fieldName, state, null, null, null, null, clock)
    }

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = parties[companyId to partyId]
    override suspend fun promoteProspectToCustomer(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = null
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun searchParties(companyId: String, query: String, classification: PartyClassification?, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> =
        contacts.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedWith(compareByDescending<PartyContactPerson> { it.isPrimary }.thenBy { it.name })
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> =
        tagAssignments.filter { it.first == companyId && it.second == partyId }.mapNotNull { tags[it.third] }
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> =
        sourceLinks.values.filter { it.companyId == companyId }
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = emptyMap()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> =
        provenance.values.filter { it.companyId == companyId && it.partyId == partyId }

    override suspend fun updateVentureOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState {
        val state = if (value.isNullOrBlank()) FieldProvenanceState.EmptyUnknown else FieldProvenanceState.VentureOnlyPending
        provenance[Triple(companyId, partyId, fieldName)] = PartyFieldProvenance(companyId, partyId, fieldName, state, null, value, null, null, clock)
        val party = parties[companyId to partyId] ?: return state
        parties[companyId to partyId] = when (fieldName) {
            PartyFieldNames.PRIMARY_PHONE -> party.copy(primaryPhone = value)
            PartyFieldNames.PRIMARY_EMAIL -> party.copy(primaryEmail = value)
            PartyFieldNames.ADDRESS_LINE1 -> party.copy(addressLine1 = value)
            PartyFieldNames.ADDRESS_CITY -> party.copy(addressCity = value)
            PartyFieldNames.ADDRESS_STATE -> party.copy(addressState = value)
            PartyFieldNames.ADDRESS_PINCODE -> party.copy(addressPincode = value)
            PartyFieldNames.GSTIN -> party.copy(gstin = value)
            else -> party
        }
        return state
    }

    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState =
        FieldProvenanceState.ConfirmedFromTally

    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = emptyList()

    override suspend fun applyLedgerContactDetailsBulk(
        companyId: String,
        items: List<com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetails>,
    ): com.jajusri.venture.feature.party.domain.model.BulkContactSeedResult = error("unused")

    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party {
        val partyId = UUID.randomUUID().toString()
        val party = Party(
            companyId, partyId, draft.displayName, PartyClassification.Prospect, draft.phone, draft.phone, draft.email,
            draft.addressLine1, draft.addressCity, draft.addressState, draft.addressPincode, null, clock, clock,
        )
        parties[companyId to partyId] = party
        return party
    }

    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = sourceLinks[companyId to partyId]

    override suspend fun upsertContactPerson(
        companyId: String,
        partyId: String,
        contactPersonId: String?,
        name: String,
        designation: String?,
        mobile: String?,
        whatsappNumber: String?,
        email: String?,
        isPrimary: Boolean,
    ): PartyContactPerson {
        val id = contactPersonId ?: UUID.randomUUID().toString()
        if (isPrimary) {
            contacts.values.filter { it.companyId == companyId && it.partyId == partyId && it.isPrimary && it.contactPersonId != id }
                .forEach { contacts[it.contactPersonId] = it.copy(isPrimary = false) }
        }
        val contact = PartyContactPerson(companyId, id, partyId, name, designation, mobile, mobile, whatsappNumber, email, isPrimary, FieldProvenanceState.VentureOnlyPending, clock, clock)
        contacts[id] = contact
        return contact
    }

    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) {
        contacts.remove(contactPersonId)
    }

    override suspend fun getAllTags(): List<Tag> = tags.values.toList()

    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag {
        tags.values.firstOrNull { it.name == name && it.parentTagId == parentTagId }?.let { return it }
        val tag = Tag(UUID.randomUUID().toString(), parentTagId, name, name, clock)
        tags[tag.tagId] = tag
        return tag
    }

    override suspend fun assignTag(companyId: String, partyId: String, tagId: String) {
        tagAssignments += Triple(companyId, partyId, tagId)
    }

    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String) {
        tagAssignments -= Triple(companyId, partyId, tagId)
    }

    override suspend fun addNote(
        companyId: String,
        partyId: String,
        body: String,
        linkedVoucherId: String?,
        type: NoteType,
        dueAt: Long?,
        issueId: String?,
    ): PartyNote {
        clock += 1
        val note = PartyNote(companyId, UUID.randomUUID().toString(), partyId, body, linkedVoucherId, clock, clock, type, dueAt, null, issueId)
        notes[note.noteId] = note
        return note
    }

    override suspend fun editNote(
        companyId: String,
        noteId: String,
        body: String,
        type: NoteType,
        dueAt: Long?,
        issueId: String?,
    ): PartyNote? {
        val existing = notes[noteId] ?: return null
        val updated = existing.copy(body = body, type = type, dueAt = dueAt, issueId = issueId, updatedAt = clock)
        notes[noteId] = updated
        return updated
    }

    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? {
        val existing = notes[noteId] ?: return null
        val updated = existing.copy(completedAt = completedAt, updatedAt = clock)
        notes[noteId] = updated
        return updated
    }

    override suspend fun deleteNote(companyId: String, noteId: String) {
        notes.remove(noteId)
    }

    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage {
        val items = notes.values.filter { it.companyId == companyId && it.partyId == partyId }.sortedByDescending { it.createdAt }
        return PartyNotePage(items, page, pageSize, items.size)
    }

    override suspend fun getTimelineForParty(companyId: String, partyId: String, page: Int, pageSize: Int, issueId: String?): TimelineEntryPage {
        val noteEntries: List<TimelineEntry> = notes.values
            .filter { it.companyId == companyId && it.partyId == partyId && (issueId == null || it.issueId == issueId) }
            .map { TimelineEntry.NoteEvent(it) }
        val issueEntries: List<TimelineEntry> = if (issueId == null) {
            issues.values.filter { it.companyId == companyId && it.partyId == partyId }.flatMap { issue ->
                listOfNotNull(
                    TimelineEntry.IssueOpenedEvent(issue.issueId, issue.title, issue.createdAt),
                    issue.resolvedAt?.let { TimelineEntry.IssueResolvedEvent(issue.issueId, issue.title, it) },
                )
            }
        } else {
            emptyList()
        }
        val all = (noteEntries + issueEntries).sortedByDescending { it.timestamp }
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        val paged = all.drop((safePage - 1) * safeSize).take(safeSize)
        return TimelineEntryPage(paged, safePage, safeSize, all.size)
    }

    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue {
        clock += 1
        val issue = PartyIssue(companyId, UUID.randomUUID().toString(), partyId, title, IssueStatus.Open, clock, null, clock)
        issues[issue.issueId] = issue
        return issue
    }

    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? {
        val existing = issues[issueId] ?: return null
        val updated = existing.copy(status = IssueStatus.Resolved, resolvedAt = clock, updatedAt = clock)
        issues[issueId] = updated
        return updated
    }

    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? {
        val existing = issues[issueId] ?: return null
        val updated = existing.copy(status = IssueStatus.Open, resolvedAt = null, updatedAt = clock)
        issues[issueId] = updated
        return updated
    }

    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> =
        issues.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedWith(compareBy<PartyIssue> { it.status }.thenByDescending { it.createdAt })

    override suspend fun getIssueActivitySummary(companyId: String, partyId: String): Map<String, IssueActivitySummary> =
        notes.values.filter { it.companyId == companyId && it.partyId == partyId && it.issueId != null }
            .groupBy { it.issueId!! }
            .mapValues { (_, notesForIssue) -> IssueActivitySummary(notesForIssue.size, notesForIssue.maxOf { it.createdAt }) }

    override suspend fun getExportCandidates(
        companyId: String,
        partyId: String,
    ): List<com.jajusri.venture.feature.party.domain.model.TallyFieldExportCandidate> =
        com.jajusri.venture.feature.party.domain.model.TallyExportFieldMapping.ELIGIBLE_FIELDS.map { fieldName ->
            val row = provenance[Triple(companyId, partyId, fieldName)]
            com.jajusri.venture.feature.party.domain.model.TallyFieldExportCandidate(
                fieldName = fieldName,
                label = com.jajusri.venture.feature.party.domain.model.TallyExportFieldMapping.labelFor(fieldName),
                tallyValue = row?.tallyValue,
                ventureValue = row?.ventureValue,
                state = row?.state ?: FieldProvenanceState.EmptyUnknown,
            )
        }

    override suspend fun recordExport(
        companyId: String,
        partyId: String,
        outputFileName: String,
        fieldNames: List<String>,
    ): com.jajusri.venture.feature.party.domain.model.PartyExportEvent {
        clock += 1
        fieldNames.forEach { fieldName ->
            val key = Triple(companyId, partyId, fieldName)
            val existing = provenance[key]
            provenance[key] = PartyFieldProvenance(
                companyId, partyId, fieldName, FieldProvenanceState.Exported,
                existing?.tallyValue, existing?.ventureValue, existing?.lastConfirmedAt, clock, clock,
            )
        }
        return com.jajusri.venture.feature.party.domain.model.PartyExportEvent(
            companyId, java.util.UUID.randomUUID().toString(), partyId, clock, outputFileName, fieldNames,
        )
    }

    override suspend fun reconcileExportedFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyRawValue: String?,
    ): FieldProvenanceState {
        clock += 1
        val key = Triple(companyId, partyId, fieldName)
        val existing = provenance[key]
        val newState = when {
            tallyRawValue.isNullOrBlank() -> existing?.state ?: FieldProvenanceState.EmptyUnknown
            existing?.ventureValue != null && existing.ventureValue != tallyRawValue -> FieldProvenanceState.Conflict
            else -> FieldProvenanceState.ConfirmedFromTally
        }
        provenance[key] = PartyFieldProvenance(
            companyId, partyId, fieldName, newState, tallyRawValue, existing?.ventureValue,
            if (newState == FieldProvenanceState.ConfirmedFromTally) clock else existing?.lastConfirmedAt, existing?.lastExportedAt, clock,
        )
        return newState
    }

    override suspend fun getExportHistory(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.jajusri.venture.feature.party.domain.model.PartyExportEvent> = emptyList()
}
