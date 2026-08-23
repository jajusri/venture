package com.budcom.android.feature.connect.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.MoneyAmount
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.PartySourceType
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.GetPartySourceLinksForCompanyUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyTagsForCompanyUseCase
import com.budcom.android.feature.party.domain.usecase.ListPartiesByClassificationUseCase
import com.budcom.android.feature.party.domain.usecase.SearchPartiesUseCase
import com.budcom.android.navigation.Routes
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @org.junit.Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun party(
        companyId: String,
        partyId: String,
        name: String,
        classification: PartyClassification = PartyClassification.Customer,
        phone: String? = null,
    ) = Party(
        companyId = companyId,
        partyId = partyId,
        displayName = name,
        classification = classification,
        primaryPhone = phone,
        primaryPhoneNormalized = phone,
        primaryEmail = null,
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        gstin = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun link(companyId: String, partyId: String, ledgerId: String) = PartySourceLink(
        companyId = companyId,
        partyId = partyId,
        sourceType = PartySourceType.TallyLedger,
        sourceInstanceId = companyId,
        externalEntityId = ledgerId,
        externalDisplayName = ledgerId,
        identitySource = LedgerIdentitySource.Guid,
        lastConfirmedAt = 0L,
    )

    private fun ledger(id: String, amount: String, side: AmountSide, syncedAt: String = "t") = Ledger(
        id = id,
        name = id,
        alias = null,
        parentGroup = "Sundry Debtors",
        status = LedgerStatus.Active,
        closingBalance = MoneyAmount(amount, "INR", side),
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = syncedAt,
    )

    private fun createViewModel(
        repository: FakePartyRepository = FakePartyRepository(),
        ledgerSnapshotPort: FakeLedgerSnapshotPort = FakeLedgerSnapshotPort(),
        company: FakeCompanySession = FakeCompanySession("co-1"),
        connectivity: FakeConnectivity = FakeConnectivity(true),
        query: String = "",
    ) = ConnectViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to query)),
        listPartiesByClassification = ListPartiesByClassificationUseCase(repository),
        searchParties = SearchPartiesUseCase(repository),
        getPartySourceLinksForCompany = GetPartySourceLinksForCompanyUseCase(repository),
        getPartyTagsForCompany = GetPartyTagsForCompanyUseCase(repository),
        ledgerSnapshotPort = ledgerSnapshotPort,
        companySession = company,
        connectivityObserver = connectivity,
    )

    @Test
    fun `initial load shows Customers with balance and deep-link data joined`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-1" to PartyClassification.Customer] =
                listOf(party("co-1", "p1", "ABC Traders"))
            sourceLinks["co-1"] = listOf(link("co-1", "p1", "guid:abc"))
        }
        val ledgerPort = FakeLedgerSnapshotPort(mapOf("co-1" to listOf(ledger("guid:abc", "1000.00", AmountSide.Dr))))
        val vm = createViewModel(repository = repo, ledgerSnapshotPort = ledgerPort)
        advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertEquals("ABC Traders", row.displayName)
        assertEquals("1000.00 Dr", row.balanceLabel)
        assertEquals("guid:abc", row.linkedLedgerId)
        assertTrue(row.hasAccountingLink)
    }

    @Test
    fun `a Party with no source link never shows a fabricated balance or deep links`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-1" to PartyClassification.Prospect] =
                listOf(party("co-1", "p2", "New Prospect", classification = PartyClassification.Prospect))
        }
        val vm = createViewModel(repository = repo)
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.TabChanged(ConnectTab.Prospects))
        advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertNull(row.balanceLabel)
        assertNull(row.linkedLedgerId)
        assertFalse(row.hasAccountingLink)
    }

    @Test
    fun `Prospects tab is honestly empty when nothing has been seeded`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.TabChanged(ConnectTab.Prospects))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `search is scoped to the currently selected tab`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            searchResults[Triple("co-1", "ABC", PartyClassification.Customer)] =
                listOf(party("co-1", "p1", "ABC Traders"))
        }
        val vm = createViewModel(repository = repo)
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.SearchChanged("ABC"))
        advanceUntilIdle()

        assertEquals(listOf("ABC Traders"), vm.uiState.value.rows.map { it.displayName })
        assertEquals(listOf(PartyClassification.Customer), repo.searchClassificationsSeen)
    }

    @Test
    fun `switching company resets rows and reloads for the new company`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-A" to PartyClassification.Customer] = listOf(party("co-A", "pa", "A Traders"))
            partiesByClassification["co-B" to PartyClassification.Customer] = listOf(party("co-B", "pb", "B Traders"))
        }
        val company = FakeCompanySession("co-A")
        val vm = createViewModel(repository = repo, company = company)
        advanceUntilIdle()
        assertEquals(listOf("A Traders"), vm.uiState.value.rows.map { it.displayName })

        company.selected.value = "co-B"
        advanceUntilIdle()

        assertEquals(listOf("B Traders"), vm.uiState.value.rows.map { it.displayName })
    }

    @Test
    fun `a committed search query never scopes the next company's first load after switching`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-A" to PartyClassification.Customer] = listOf(party("co-A", "pa", "Zephyr"))
            partiesByClassification["co-B" to PartyClassification.Customer] = listOf(party("co-B", "pb", "B Traders"))
            // Deliberately no entry for ("co-B", "zephyr", Customer) -- if the bug were present
            // (leftover query leaking into company B's load), searchParties would be called with
            // "zephyr" and return an empty page even though company B has real, unfiltered data.
            searchResults[Triple("co-A", "zephyr", PartyClassification.Customer)] = listOf(party("co-A", "pa", "Zephyr"))
        }
        val company = FakeCompanySession("co-A")
        val vm = createViewModel(repository = repo, company = company)
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.SearchChanged("zephyr"))
        advanceUntilIdle()
        assertEquals(listOf("Zephyr"), vm.uiState.value.rows.map { it.displayName })

        company.selected.value = "co-B"
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals(listOf("B Traders"), vm.uiState.value.rows.map { it.displayName })
    }

    @Test
    fun `a pending debounced search never re-applies the previous company's query after switching`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-A" to PartyClassification.Customer] = listOf(party("co-A", "pa", "A Traders"))
            partiesByClassification["co-B" to PartyClassification.Customer] = listOf(party("co-B", "pb", "B Traders"))
        }
        val company = FakeCompanySession("co-A")
        val vm = createViewModel(repository = repo, company = company)
        advanceUntilIdle()

        vm.onEvent(ConnectEvent.SearchChanged("stale query"))
        // Switch before the debounce delay elapses -- the pending job must never fire against co-B.
        company.selected.value = "co-B"
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals(listOf("B Traders"), vm.uiState.value.rows.map { it.displayName })
    }

    @Test
    fun `dataFreshnessAt reflects the most recent synced Ledger, since Connect has no sync of its own`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-1" to PartyClassification.Customer] = listOf(party("co-1", "pa", "A Traders"))
            sourceLinks["co-1"] = listOf(link("co-1", "pa", "guid:a"))
        }
        val ledgerPort = FakeLedgerSnapshotPort(
            mapOf(
                "co-1" to listOf(
                    ledger("guid:a", "500.00", AmountSide.Cr, syncedAt = "2026-08-23T10:00:00Z"),
                    ledger("guid:b", "10.00", AmountSide.Dr, syncedAt = "2026-08-23T12:00:00Z"),
                ),
            ),
        )
        val vm = createViewModel(repository = repo, ledgerSnapshotPort = ledgerPort)
        advanceUntilIdle()

        assertEquals("2026-08-23T12:00:00Z", vm.uiState.value.dataFreshnessAt)
    }

    @Test
    fun `dataFreshnessAt is null when no Ledger data has ever been cached`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.dataFreshnessAt)
    }

    @Test
    fun `a row's linkedLedgerAlias reflects its linked Ledger's Alias, shown separately from phone`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-1" to PartyClassification.Customer] =
                listOf(party("co-1", "pa", "A Traders", phone = "9876543210"))
            sourceLinks["co-1"] = listOf(link("co-1", "pa", "guid:a"))
        }
        val ledgerPort = FakeLedgerSnapshotPort(
            mapOf("co-1" to listOf(ledger("guid:a", "500.00", AmountSide.Cr).copy(alias = "25"))),
        )
        val vm = createViewModel(repository = repo, ledgerSnapshotPort = ledgerPort)
        advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertEquals("25", row.linkedLedgerAlias)
        assertEquals("9876543210", row.phoneDisplay)
    }

    @Test
    fun `a row's linkedLedgerAlias is null when the linked Ledger has none`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-1" to PartyClassification.Customer] = listOf(party("co-1", "pa", "A Traders"))
            sourceLinks["co-1"] = listOf(link("co-1", "pa", "guid:a"))
        }
        val ledgerPort = FakeLedgerSnapshotPort(mapOf("co-1" to listOf(ledger("guid:a", "500.00", AmountSide.Cr))))
        val vm = createViewModel(repository = repo, ledgerSnapshotPort = ledgerPort)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.rows.single().linkedLedgerAlias)
    }

    @Test
    fun `company A balance never leaks into company B's Party with the same name and phone`() = runTest(dispatcher) {
        val repo = FakePartyRepository().apply {
            partiesByClassification["co-A" to PartyClassification.Customer] =
                listOf(party("co-A", "pa", "ABC Traders", phone = "9876543210"))
            partiesByClassification["co-B" to PartyClassification.Customer] =
                listOf(party("co-B", "pb", "ABC Traders", phone = "9876543210"))
            sourceLinks["co-A"] = listOf(link("co-A", "pa", "guid:a"))
            sourceLinks["co-B"] = emptyList()
        }
        val ledgerPort = FakeLedgerSnapshotPort(mapOf("co-A" to listOf(ledger("guid:a", "500.00", AmountSide.Cr))))
        val company = FakeCompanySession("co-A")
        val vm = createViewModel(repository = repo, ledgerSnapshotPort = ledgerPort, company = company)
        advanceUntilIdle()
        assertEquals("500.00 Cr", vm.uiState.value.rows.single().balanceLabel)

        company.selected.value = "co-B"
        advanceUntilIdle()

        val rowB = vm.uiState.value.rows.single()
        assertEquals("ABC Traders", rowB.displayName)
        assertNull("company B's identically-named/phoned Party must never show company A's balance", rowB.balanceLabel)
        assertFalse(rowB.hasAccountingLink)
    }

    @Test
    fun `view ledger tap with no linked ledger shows a message instead of navigating`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.ViewLedgerTapped(null))
        advanceUntilIdle()
        assertTrue(emitted is ConnectEffect.ShowMessage)
        job.cancel()
    }

    @Test
    fun `call tap with a valid phone emits LaunchCall`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.CallTapped("+919876543210"))
        advanceUntilIdle()
        assertEquals(ConnectEffect.LaunchCall("+919876543210"), emitted)
        job.cancel()
    }

    @Test
    fun `call tap with no phone shows a message, never crashes`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.CallTapped(null))
        advanceUntilIdle()
        assertTrue(emitted is ConnectEffect.ShowMessage)
        job.cancel()
    }

    @Test
    fun `view vouchers tap emits the linked ledger name as the prefill query`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.ViewVouchersTapped("ABC Traders"))
        advanceUntilIdle()
        assertEquals(ConnectEffect.OpenVouchers("ABC Traders"), emitted)
        job.cancel()
    }

    @Test
    fun `tapping a row emits OpenPartyDetail with its partyId`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.RowTapped("party-123"))
        advanceUntilIdle()
        assertEquals(ConnectEffect.OpenPartyDetail("party-123"), emitted)
        job.cancel()
    }

    @Test
    fun `add prospect tap emits OpenProspectCreate`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        var emitted: ConnectEffect? = null
        val job = launch { vm.effects.collect { emitted = it } }
        advanceUntilIdle()
        vm.onEvent(ConnectEvent.AddProspectTapped)
        advanceUntilIdle()
        assertEquals(ConnectEffect.OpenProspectCreate, emitted)
        job.cancel()
    }

    @Test
    fun `offline flag reflects connectivity observer`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(false)
        val vm = createViewModel(connectivity = connectivity)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun `no company selected shows an honest message, not a crash`() = runTest(dispatcher) {
        val company = FakeCompanySession(null)
        val vm = createViewModel(company = company)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
        assertTrue(vm.uiState.value.rows.isEmpty())
    }
}

private class FakePartyRepository : PartyRepository {
    val partiesByClassification = mutableMapOf<Pair<String, PartyClassification>, List<Party>>()
    val searchResults = mutableMapOf<Triple<String, String, PartyClassification?>, List<Party>>()
    val sourceLinks = mutableMapOf<String, List<PartySourceLink>>()
    val tagsByCompany = mutableMapOf<String, Map<String, List<Tag>>>()
    val searchClassificationsSeen = mutableListOf<PartyClassification?>()

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = null
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = null

    override suspend fun listByClassification(
        companyId: String,
        classification: PartyClassification,
        page: Int,
        pageSize: Int,
    ): PartyPage {
        val items = partiesByClassification[companyId to classification].orEmpty()
        return PartyPage(items, page, pageSize, items.size)
    }

    override suspend fun searchParties(
        companyId: String,
        query: String,
        classification: PartyClassification?,
        page: Int,
        pageSize: Int,
    ): PartyPage {
        searchClassificationsSeen += classification
        val items = searchResults[Triple(companyId, query, classification)].orEmpty()
        return PartyPage(items, page, pageSize, items.size)
    }

    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = emptyList()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = emptyList()
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> = sourceLinks[companyId].orEmpty()
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = tagsByCompany[companyId].orEmpty()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = emptyList()
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?) =
        FieldProvenanceState.BudcomOnlyPending
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?) =
        FieldProvenanceState.ConfirmedFromTally
    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = emptyList()

    override suspend fun createProspect(
        companyId: String,
        draft: com.budcom.android.feature.party.domain.model.ProspectDraft,
    ): Party = error("unused")
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = error("unused")
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
    ): PartyContactPerson = error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String): Unit = error("unused")
    override suspend fun getAllTags(): List<Tag> = error("unused")
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = error("unused")
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String): Unit = error("unused")
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String): Unit = error("unused")
    override suspend fun addNote(
        companyId: String,
        partyId: String,
        body: String,
        linkedVoucherId: String?,
        type: com.budcom.android.feature.party.domain.model.NoteType,
        dueAt: Long?,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.PartyNote = error("unused")
    override suspend fun editNote(
        companyId: String,
        noteId: String,
        body: String,
        type: com.budcom.android.feature.party.domain.model.NoteType,
        dueAt: Long?,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.PartyNote? = error("unused")
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): com.budcom.android.feature.party.domain.model.PartyNote? =
        error("unused")
    override suspend fun deleteNote(companyId: String, noteId: String): Unit = error("unused")
    override suspend fun createIssue(companyId: String, partyId: String, title: String): com.budcom.android.feature.party.domain.model.PartyIssue =
        error("unused")
    override suspend fun resolveIssue(companyId: String, issueId: String): com.budcom.android.feature.party.domain.model.PartyIssue? = error("unused")
    override suspend fun reopenIssue(companyId: String, issueId: String): com.budcom.android.feature.party.domain.model.PartyIssue? = error("unused")
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<com.budcom.android.feature.party.domain.model.PartyIssue> =
        error("unused")
    override suspend fun getIssueActivitySummary(
        companyId: String,
        partyId: String,
    ): Map<String, com.budcom.android.feature.party.domain.model.IssueActivitySummary> = error("unused")
    override suspend fun getNotesForParty(
        companyId: String,
        partyId: String,
        page: Int,
        pageSize: Int,
    ): com.budcom.android.feature.party.domain.model.PartyNotePage = error("unused")
    override suspend fun getTimelineForParty(
        companyId: String,
        partyId: String,
        page: Int,
        pageSize: Int,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.TimelineEntryPage = error("unused")
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

private class FakeLedgerSnapshotPort(private val byCompany: Map<String, List<Ledger>> = emptyMap()) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = byCompany[companyId].orEmpty()
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}

private class FakeConnectivity(initial: Boolean) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
    override fun current(): Boolean = online.value
}
