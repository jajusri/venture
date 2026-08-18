package com.budcom.android.feature.connect.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerLiveDetailPort
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
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
import com.budcom.android.feature.party.domain.model.TallyExportFieldMapping
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate
import com.budcom.android.feature.party.domain.model.TimelineEntryPage
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.GetExportCandidatesUseCase
import com.budcom.android.feature.party.domain.usecase.GetExportHistoryUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyByIdUseCase
import com.budcom.android.feature.party.domain.usecase.GetSourceLinkForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.ReconcileExportedFieldFromTallyUseCase
import com.budcom.android.feature.party.domain.usecase.RecordExportUseCase
import com.budcom.android.feature.party.sharing.PartyXmlExportCoordinator
import com.budcom.android.feature.party.sharing.PartyXmlExportResult
import com.budcom.android.feature.party.sharing.PreparedPartyXmlExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PartyXmlExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryExportPartyRepository
    private lateinit var ledgerLiveDetailPort: FakeLedgerLiveDetailPort
    private lateinit var coordinator: FakePartyXmlExportCoordinator
    private lateinit var companySession: ExportTestFakeCompanySession

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryExportPartyRepository()
        ledgerLiveDetailPort = FakeLedgerLiveDetailPort()
        coordinator = FakePartyXmlExportCoordinator()
        companySession = ExportTestFakeCompanySession("co-1", "Acme Corp")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(partyId: String) = PartyXmlExportViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PartyXmlExportViewModel.PARTY_ID_ARG to partyId)),
        getPartyById = GetPartyByIdUseCase(repository),
        getSourceLinkForParty = GetSourceLinkForPartyUseCase(repository),
        getExportCandidates = GetExportCandidatesUseCase(repository),
        recordExport = RecordExportUseCase(repository),
        reconcileExportedFieldFromTally = ReconcileExportedFieldFromTallyUseCase(repository),
        getExportHistory = GetExportHistoryUseCase(repository),
        ledgerLiveDetailPort = ledgerLiveDetailPort,
        xmlExportCoordinator = coordinator,
        companySession = companySession,
    )

    @Test
    fun `loading a Tally-backed party shows candidates and defaults to selecting pending fields`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        assertEquals(6, vm.uiState.value.candidates.size)
        assertTrue(vm.uiState.value.selectedFieldNames.contains(PartyFieldNames.PRIMARY_EMAIL))
        assertTrue(vm.uiState.value.hasSourceLink)
    }

    @Test
    fun `a party with no source link shows an honest error, nothing to export`() = runTest(dispatcher) {
        val prospect = repository.createProspect("co-1", ProspectDraft(displayName = "New Bakery"))
        val vm = createViewModel(prospect.partyId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasSourceLink)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `toggling a field selection adds and removes it`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.FieldSelectionToggled(PartyFieldNames.GSTIN))
        assertTrue(vm.uiState.value.selectedFieldNames.contains(PartyFieldNames.GSTIN))
        vm.onEvent(PartyXmlExportEvent.FieldSelectionToggled(PartyFieldNames.GSTIN))
        assertFalse(vm.uiState.value.selectedFieldNames.contains(PartyFieldNames.GSTIN))
    }

    @Test
    fun `generating with a selected field prepares XML and emits a save-document effect`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        var effect: PartyXmlExportEffect? = null
        val job = launch { vm.effects.collect { effect = it } }
        advanceUntilIdle()
        vm.onEvent(PartyXmlExportEvent.GenerateTapped)
        advanceUntilIdle()

        assertTrue(effect is PartyXmlExportEffect.CreateXmlDocument)
        assertTrue(coordinator.prepared.single().contains("PARTYGSTIN"))
        job.cancel()
    }

    @Test
    fun `cancelling the save destination releases the prepared file and shows a cancelled notice`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyXmlExportEvent.GenerateTapped)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.SaveDestinationSelected(null))
        advanceUntilIdle()

        assertEquals("Export cancelled.", vm.uiState.value.notice)
        assertTrue(coordinator.released)
    }

    @Test
    fun `saving successfully records the export and transitions the field to Exported`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()
        vm.onEvent(PartyXmlExportEvent.GenerateTapped)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.SaveDestinationSelected(android.net.TestUri.create()))
        advanceUntilIdle()

        val gstinCandidate = vm.uiState.value.candidates.single { it.fieldName == PartyFieldNames.GSTIN }
        assertEquals(FieldProvenanceState.Exported, gstinCandidate.state)
        assertEquals(1, vm.uiState.value.history.size)
    }

    @Test
    fun `checking Tally with nothing exported shows an honest notice instead of a no-op`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.CheckTallyTapped)
        advanceUntilIdle()

        assertEquals("No exported fields are waiting on a Tally confirmation.", vm.uiState.value.notice)
    }

    @Test
    fun `checking Tally after export confirms a matching field`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        repository.recordExport("co-1", party.partyId, "file.xml", listOf(PartyFieldNames.GSTIN))
        ledgerLiveDetailPort.result = AppResult.Success(
            LedgerContactDetails("guid:abc", null, null, null, null, null, "29ABCDE1234F1Z5"),
        )
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.CheckTallyTapped)
        advanceUntilIdle()

        val gstinCandidate = vm.uiState.value.candidates.single { it.fieldName == PartyFieldNames.GSTIN }
        assertEquals(FieldProvenanceState.ConfirmedFromTally, gstinCandidate.state)
        assertTrue(vm.uiState.value.notice!!.contains("1 confirmed"))
    }

    @Test
    fun `checking Tally when the Connector is unreachable shows an honest error, never a silent no-op`() = runTest(dispatcher) {
        val party = repository.seedCustomer("co-1", "guid:abc", "ABC Traders")
        repository.setBudcomValue("co-1", party.partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        repository.recordExport("co-1", party.partyId, "file.xml", listOf(PartyFieldNames.GSTIN))
        ledgerLiveDetailPort.result = AppResult.Failure(AppError.Offline())
        val vm = createViewModel(party.partyId)
        advanceUntilIdle()

        vm.onEvent(PartyXmlExportEvent.CheckTallyTapped)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notice!!.contains("Could not reach the Connector"))
        val gstinCandidate = vm.uiState.value.candidates.single { it.fieldName == PartyFieldNames.GSTIN }
        assertEquals(FieldProvenanceState.Exported, gstinCandidate.state)
    }
}

private class FakeLedgerLiveDetailPort : LedgerLiveDetailPort {
    var result: AppResult<LedgerContactDetails> = AppResult.Failure(AppError.Offline())
    override suspend fun fetchContactDetails(ledgerId: String): AppResult<LedgerContactDetails> = result
}

private class FakePartyXmlExportCoordinator : PartyXmlExportCoordinator {
    val prepared = mutableListOf<String>()
    var released = false

    override suspend fun prepareXml(xmlContent: String, suggestedFilename: String): PartyXmlExportResult<PreparedPartyXmlExport> {
        prepared += xmlContent
        return PartyXmlExportResult.Success(PreparedPartyXmlExport("/cache/$suggestedFilename", suggestedFilename))
    }

    override suspend fun saveXml(export: PreparedPartyXmlExport, destination: Uri): PartyXmlExportResult<Unit> =
        PartyXmlExportResult.Success(Unit)

    override fun releaseXml(export: PreparedPartyXmlExport) {
        released = true
    }
}

private class ExportTestFakeCompanySession(companyId: String?, private val companyName: String?) : CompanySessionPort {
    val selected = MutableStateFlow(companyId)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> =
        selected.map { id -> id?.let { SessionSelectedCompany(it, companyName ?: it) } }
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, companyName))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}

/** Mirrors [PartyDetailViewModelTest]'s in-memory fake, extended with Part-D export behavior. */
private class InMemoryExportPartyRepository : PartyRepository {
    private val parties = mutableMapOf<Pair<String, String>, Party>()
    private val sourceLinks = mutableMapOf<Pair<String, String>, PartySourceLink>()
    private val provenance = mutableMapOf<Triple<String, String, String>, PartyFieldProvenance>()
    private val exportEvents = mutableListOf<PartyExportEvent>()
    private var clock = 1_000L

    fun seedCustomer(companyId: String, ledgerId: String, name: String): Party {
        val partyId = UUID.randomUUID().toString()
        val party = Party(companyId, partyId, name, PartyClassification.Customer, null, null, null, null, null, null, null, null, clock, clock)
        parties[companyId to partyId] = party
        sourceLinks[companyId to partyId] = PartySourceLink(companyId, partyId, PartySourceType.TallyLedger, companyId, ledgerId, name, LedgerIdentitySource.Guid, clock)
        return party
    }

    fun setBudcomValue(companyId: String, partyId: String, fieldName: String, value: String) {
        provenance[Triple(companyId, partyId, fieldName)] =
            PartyFieldProvenance(companyId, partyId, fieldName, FieldProvenanceState.BudcomOnlyPending, null, value, null, null, clock)
    }

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = parties[companyId to partyId]
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = null
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun searchParties(companyId: String, query: String, classification: PartyClassification?, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = emptyList()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = emptyList()
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> =
        sourceLinks.values.filter { it.companyId == companyId }
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = emptyMap()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> =
        provenance.values.filter { it.companyId == companyId && it.partyId == partyId }

    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState {
        val state = if (value.isNullOrBlank()) FieldProvenanceState.EmptyUnknown else FieldProvenanceState.BudcomOnlyPending
        provenance[Triple(companyId, partyId, fieldName)] = PartyFieldProvenance(companyId, partyId, fieldName, state, null, value, null, null, clock)
        return state
    }

    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState =
        FieldProvenanceState.ConfirmedFromTally

    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = emptyList()

    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party {
        val partyId = UUID.randomUUID().toString()
        val party = Party(companyId, partyId, draft.displayName, PartyClassification.Prospect, draft.phone, draft.phone, draft.email, draft.addressLine1, draft.addressCity, draft.addressState, draft.addressPincode, null, clock, clock)
        parties[companyId to partyId] = party
        return party
    }

    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = sourceLinks[companyId to partyId]
    override suspend fun upsertContactPerson(companyId: String, partyId: String, contactPersonId: String?, name: String, designation: String?, mobile: String?, whatsappNumber: String?, email: String?, isPrimary: Boolean): PartyContactPerson =
        error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) = error("unused")
    override suspend fun getAllTags(): List<Tag> = emptyList()
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
    override suspend fun getTimelineForParty(companyId: String, partyId: String, page: Int, pageSize: Int, issueId: String?): TimelineEntryPage =
        error("unused")
    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue = error("unused")
    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> = error("unused")

    override suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate> =
        TallyExportFieldMapping.ELIGIBLE_FIELDS.map { fieldName ->
            val row = provenance[Triple(companyId, partyId, fieldName)]
            TallyFieldExportCandidate(
                fieldName = fieldName,
                label = TallyExportFieldMapping.labelFor(fieldName),
                tallyValue = row?.tallyValue,
                budcomValue = row?.budcomValue,
                state = row?.state ?: FieldProvenanceState.EmptyUnknown,
            )
        }

    override suspend fun recordExport(companyId: String, partyId: String, outputFileName: String, fieldNames: List<String>): PartyExportEvent {
        clock += 1
        fieldNames.forEach { fieldName ->
            val key = Triple(companyId, partyId, fieldName)
            val existing = provenance[key]
            provenance[key] = PartyFieldProvenance(
                companyId, partyId, fieldName, FieldProvenanceState.Exported,
                existing?.tallyValue, existing?.budcomValue, existing?.lastConfirmedAt, clock, clock,
            )
        }
        val event = PartyExportEvent(companyId, UUID.randomUUID().toString(), partyId, clock, outputFileName, fieldNames)
        exportEvents += event
        return event
    }

    override suspend fun reconcileExportedFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyRawValue: String?): FieldProvenanceState {
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

    override suspend fun getExportHistory(companyId: String, partyId: String, limit: Int): List<PartyExportEvent> =
        exportEvents.filter { it.companyId == companyId && it.partyId == partyId }.sortedByDescending { it.createdAt }.take(limit)
}
