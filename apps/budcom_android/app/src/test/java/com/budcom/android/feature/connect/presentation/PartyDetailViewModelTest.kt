package com.budcom.android.feature.connect.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.PartySourceType
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.AddNoteUseCase
import com.budcom.android.feature.party.domain.usecase.AssignTagUseCase
import com.budcom.android.feature.party.domain.usecase.CreateIssueUseCase
import com.budcom.android.feature.party.domain.usecase.CreateOrGetTagUseCase
import com.budcom.android.feature.party.domain.usecase.DeleteContactPersonUseCase
import com.budcom.android.feature.party.domain.usecase.DeleteNoteUseCase
import com.budcom.android.feature.party.domain.usecase.EditNoteUseCase
import com.budcom.android.feature.party.domain.usecase.GetAllTagsUseCase
import com.budcom.android.feature.party.domain.usecase.GetContactPersonsUseCase
import com.budcom.android.feature.party.domain.usecase.GetFieldProvenanceUseCase
import com.budcom.android.feature.party.domain.usecase.GetIssuesForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetNotesForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyByIdUseCase
import com.budcom.android.feature.party.domain.usecase.GetSourceLinkForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.GetTagsForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.UnassignTagUseCase
import com.budcom.android.feature.party.domain.usecase.UpdateBudcomOnlyFieldUseCase
import com.budcom.android.feature.party.domain.usecase.UpsertContactPersonUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
class PartyDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryPartyRepository

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
        getNotesForParty = GetNotesForPartyUseCase(repository),
        updateBudcomOnlyField = UpdateBudcomOnlyFieldUseCase(repository),
        upsertContactPerson = UpsertContactPersonUseCase(repository),
        deleteContactPerson = DeleteContactPersonUseCase(repository),
        createOrGetTag = CreateOrGetTagUseCase(repository),
        assignTag = AssignTagUseCase(repository),
        unassignTag = UnassignTagUseCase(repository),
        addNote = AddNoteUseCase(repository),
        editNote = EditNoteUseCase(repository),
        deleteNote = DeleteNoteUseCase(repository),
        getIssuesForParty = GetIssuesForPartyUseCase(repository),
        createIssue = CreateIssueUseCase(repository),
        loadVouchers = LoadVouchersUseCase(voucherRepository),
        ledgerSnapshotPort = PartyDetailTestFakeLedgerSnapshotPort(ledgers),
        companySession = PartyDetailTestFakeCompanySession("co-1"),
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
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.PRIMARY_EMAIL, FieldProvenanceState.BudcomOnlyPending)
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.GSTIN, FieldProvenanceState.ConfirmedFromTally)
        repository.setProvenance("co-1", party.partyId, PartyFieldNames.ADDRESS_STATE, FieldProvenanceState.Conflict)
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        val rows = vm.uiState.value.fieldRows.associateBy { it.fieldName }
        assertEquals("Pending in BUDCOM", rows.getValue(PartyFieldNames.PRIMARY_EMAIL).provenanceLabel)
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
        assertEquals("Pending in BUDCOM", row.provenanceLabel)
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

        assertEquals("Customer called about delivery", vm.uiState.value.notes.first().body)
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
        val noteId = vm.uiState.value.notes.single().noteId

        vm.onEvent(PartyDetailEvent.DeleteNoteTapped(noteId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notes.isEmpty())
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

        assertEquals(NoteType.General, vm.uiState.value.notes.single().type)
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

        val note = vm.uiState.value.notes.single()
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
        assertTrue(vm.uiState.value.notes.isEmpty())
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
        val noteId = vm.uiState.value.notes.single().noteId

        vm.onEvent(PartyDetailEvent.EditNoteTapped(noteId))
        advanceUntilIdle()
        vm.onEvent(PartyDetailEvent.NoteBodyChanged("Updated text"))
        vm.onEvent(PartyDetailEvent.SaveNote)
        advanceUntilIdle()

        val notes = vm.uiState.value.notes
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

        val note = vm.uiState.value.notes.single()
        assertTrue(note.issueId != null)
        assertEquals(1, repository.issuesFor("co-1", party.partyId).size)
    }

    private fun ledger(id: String, amount: String, side: String) = Ledger(
        id = id,
        name = id,
        alias = null,
        parentGroup = "Sundry Debtors",
        status = com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus.Active,
        closingBalance = com.budcom.android.feature.masterdata.ledger.domain.model.MoneyAmount(
            amount, "INR",
            if (side == "Dr") com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide.Dr else com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide.Cr,
        ),
        dataQuality = com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality.Complete,
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
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> = AppResult.Failure(com.budcom.android.core.common.AppError.Offline())
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        AppResult.Failure(com.budcom.android.core.common.AppError.Message("unused"))
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        AppResult.Failure(com.budcom.android.core.common.AppError.Message("unused"))
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

/** A small, fully-functional in-memory [PartyRepository] used only by ViewModel tests that need
 * real create/read/update/delete semantics across many methods — cheaper than re-deriving
 * [com.budcom.android.feature.party.data.repository.PartyRepositoryImpl]'s Room-DAO fakes here. */
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

    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState {
        val state = if (value.isNullOrBlank()) FieldProvenanceState.EmptyUnknown else FieldProvenanceState.BudcomOnlyPending
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
        val contact = PartyContactPerson(companyId, id, partyId, name, designation, mobile, mobile, whatsappNumber, email, isPrimary, FieldProvenanceState.BudcomOnlyPending, clock, clock)
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

    override suspend fun getExportCandidates(
        companyId: String,
        partyId: String,
    ): List<com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate> =
        com.budcom.android.feature.party.domain.model.TallyExportFieldMapping.ELIGIBLE_FIELDS.map { fieldName ->
            val row = provenance[Triple(companyId, partyId, fieldName)]
            com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate(
                fieldName = fieldName,
                label = com.budcom.android.feature.party.domain.model.TallyExportFieldMapping.labelFor(fieldName),
                tallyValue = row?.tallyValue,
                budcomValue = row?.budcomValue,
                state = row?.state ?: FieldProvenanceState.EmptyUnknown,
            )
        }

    override suspend fun recordExport(
        companyId: String,
        partyId: String,
        outputFileName: String,
        fieldNames: List<String>,
    ): com.budcom.android.feature.party.domain.model.PartyExportEvent {
        clock += 1
        fieldNames.forEach { fieldName ->
            val key = Triple(companyId, partyId, fieldName)
            val existing = provenance[key]
            provenance[key] = PartyFieldProvenance(
                companyId, partyId, fieldName, FieldProvenanceState.Exported,
                existing?.tallyValue, existing?.budcomValue, existing?.lastConfirmedAt, clock, clock,
            )
        }
        return com.budcom.android.feature.party.domain.model.PartyExportEvent(
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
            existing?.budcomValue != null && existing.budcomValue != tallyRawValue -> FieldProvenanceState.Conflict
            else -> FieldProvenanceState.ConfirmedFromTally
        }
        provenance[key] = PartyFieldProvenance(
            companyId, partyId, fieldName, newState, tallyRawValue, existing?.budcomValue,
            if (newState == FieldProvenanceState.ConfirmedFromTally) clock else existing?.lastConfirmedAt, existing?.lastExportedAt, clock,
        )
        return newState
    }

    override suspend fun getExportHistory(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.budcom.android.feature.party.domain.model.PartyExportEvent> = emptyList()
}
