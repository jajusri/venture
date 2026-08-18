package com.budcom.android.feature.connect.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.CreateProspectUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProspectCreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ProspectCreateTestFakePartyRepository
    private lateinit var companySession: ProspectCreateTestFakeCompanySession

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ProspectCreateTestFakePartyRepository()
        companySession = ProspectCreateTestFakeCompanySession("co-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProspectCreateViewModel(
        createProspect = CreateProspectUseCase(repository),
        companySession = companySession,
    )

    @Test
    fun `Save is disabled until a display name is entered`() = runTest(dispatcher) {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.canSave)

        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("  "))
        assertFalse(vm.uiState.value.canSave)

        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("New Bakery"))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `creating a prospect with only a name works fully offline`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("New Bakery"))

        var effect: ProspectCreateEffect? = null
        val job = launch { vm.effects.collect { effect = it } }
        advanceUntilIdle()
        vm.onEvent(ProspectCreateEvent.Save)
        advanceUntilIdle()

        val created = repository.created.single()
        assertEquals("New Bakery", created.displayName)
        assertNull(created.phone)
        assertNull(created.email)
        assertTrue(effect is ProspectCreateEffect.Created)
        job.cancel()
    }

    @Test
    fun `creating a prospect with every optional field populates the draft`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("Full Details Store"))
        vm.onEvent(ProspectCreateEvent.PhoneChanged("9876543210"))
        vm.onEvent(ProspectCreateEvent.EmailChanged("store@example.com"))
        vm.onEvent(ProspectCreateEvent.AddressLine1Changed("12 Market Road"))
        vm.onEvent(ProspectCreateEvent.AddressCityChanged("Pune"))
        vm.onEvent(ProspectCreateEvent.AddressStateChanged("MH"))
        vm.onEvent(ProspectCreateEvent.AddressPincodeChanged("411001"))
        vm.onEvent(ProspectCreateEvent.NoteChanged("Interested in bulk pricing"))

        vm.onEvent(ProspectCreateEvent.Save)
        advanceUntilIdle()

        val created = repository.created.single()
        assertEquals("Full Details Store", created.displayName)
        assertEquals("9876543210", created.phone)
        assertEquals("store@example.com", created.email)
        assertEquals("12 Market Road", created.addressLine1)
        assertEquals("Pune", created.addressCity)
        assertEquals("MH", created.addressState)
        assertEquals("411001", created.addressPincode)
        assertEquals("Interested in bulk pricing", created.note)
    }

    @Test
    fun `saving emits Created with the new party id`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("New Bakery"))

        var effect: ProspectCreateEffect? = null
        val job = launch { vm.effects.collect { effect = it } }
        advanceUntilIdle()
        vm.onEvent(ProspectCreateEvent.Save)
        advanceUntilIdle()

        val partyId = (effect as ProspectCreateEffect.Created).partyId
        assertEquals(repository.lastCreatedPartyId, partyId)
        job.cancel()
    }

    @Test
    fun `no company selected shows an honest error, not a crash`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createViewModel()
        vm.onEvent(ProspectCreateEvent.DisplayNameChanged("New Bakery"))

        vm.onEvent(ProspectCreateEvent.Save)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `tapping Save while blank does nothing`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onEvent(ProspectCreateEvent.Save)
        advanceUntilIdle()

        assertTrue(repository.created.isEmpty())
        assertFalse(vm.uiState.value.isSaving)
    }
}

private class ProspectCreateTestFakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}

private class ProspectCreateTestFakePartyRepository : PartyRepository {
    val created = mutableListOf<ProspectDraft>()
    var lastCreatedPartyId: String? = null

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = error("unused")
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int): PartyPage = error("unused")
    override suspend fun searchParties(companyId: String, query: String, classification: PartyClassification?, page: Int, pageSize: Int): PartyPage = error("unused")
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = error("unused")
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = error("unused")
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> = error("unused")
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = error("unused")
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = error("unused")
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState = error("unused")
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState = error("unused")
    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = error("unused")

    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party {
        created += draft
        val partyId = UUID.randomUUID().toString()
        lastCreatedPartyId = partyId
        return Party(companyId, partyId, draft.displayName, PartyClassification.Prospect, draft.phone, draft.phone, draft.email, draft.addressLine1, draft.addressCity, draft.addressState, draft.addressPincode, null, 0L, 0L)
    }

    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = error("unused")
    override suspend fun upsertContactPerson(companyId: String, partyId: String, contactPersonId: String?, name: String, designation: String?, mobile: String?, whatsappNumber: String?, email: String?, isPrimary: Boolean): PartyContactPerson = error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) = error("unused")
    override suspend fun getAllTags(): List<Tag> = error("unused")
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = error("unused")
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String) = error("unused")
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String) = error("unused")
    override suspend fun addNote(
        companyId: String,
        partyId: String,
        body: String,
        linkedVoucherId: String?,
        type: NoteType,
        dueAt: Long?,
        issueId: String?,
    ): PartyNote = error("unused")
    override suspend fun editNote(companyId: String, noteId: String, body: String, type: NoteType, dueAt: Long?, issueId: String?): PartyNote? =
        error("unused")
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? = error("unused")
    override suspend fun deleteNote(companyId: String, noteId: String) = error("unused")
    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage = error("unused")
    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue = error("unused")
    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> = error("unused")
    override suspend fun getExportCandidates(
        companyId: String,
        partyId: String,
    ): List<com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate> = error("unused")
    override suspend fun recordExport(
        companyId: String,
        partyId: String,
        outputFileName: String,
        fieldNames: List<String>,
    ): com.budcom.android.feature.party.domain.model.PartyExportEvent = error("unused")
    override suspend fun reconcileExportedFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyRawValue: String?,
    ): FieldProvenanceState = error("unused")
    override suspend fun getExportHistory(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.budcom.android.feature.party.domain.model.PartyExportEvent> = error("unused")
}
