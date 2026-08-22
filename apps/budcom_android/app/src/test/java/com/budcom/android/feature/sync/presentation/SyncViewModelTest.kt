package com.budcom.android.feature.sync.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.ReconcilePartiesFromLedgersUseCase
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.SyncTargetSnapshot
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.sync.domain.usecase.CancelTargetSyncUseCase
import com.budcom.android.feature.sync.domain.usecase.ObserveSyncProgressUseCase
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import com.budcom.android.feature.sync.domain.usecase.RunAvailableSyncsUseCase
import com.budcom.android.feature.sync.domain.usecase.StartTargetSyncUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSyncRepository
    private lateinit var company: SyncVmFakeCompany
    private lateinit var connectivity: SyncVmFakeConnectivity
    private lateinit var statusPort: FakeObserveSyncStatus
    private lateinit var voucherRepository: SyncVmFakeVoucherRepository
    private lateinit var ledgerRepository: SyncVmFakeLedgerRepository
    private lateinit var ledgerSnapshotPort: SyncVmFakeLedgerSnapshotPort
    private lateinit var partyRepository: SyncVmFakePartyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSyncRepository()
        company = SyncVmFakeCompany("estimation")
        connectivity = SyncVmFakeConnectivity(true)
        statusPort = FakeObserveSyncStatus()
        voucherRepository = SyncVmFakeVoucherRepository()
        ledgerRepository = SyncVmFakeLedgerRepository()
        ledgerSnapshotPort = SyncVmFakeLedgerSnapshotPort(
            listOf(
                Ledger(
                    id = "guid:eligible",
                    name = "ABC Traders",
                    alias = null,
                    parentGroup = "Sundry Debtors",
                    status = com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus.Active,
                    closingBalance = null,
                    dataQuality = com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality.Complete,
                    syncedAt = "t",
                ),
            ),
        )
        partyRepository = SyncVmFakePartyRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun startTargetSync() =
        StartTargetSyncUseCase(
            repository,
            company,
            RefreshVouchersUseCase(voucherRepository),
            RefreshLedgersUseCase(ledgerRepository),
        )

    private fun createVm() = SyncViewModel(
        refreshOverview = RefreshSyncOverviewUseCase(repository, company),
        startTargetSync = startTargetSync(),
        cancelTargetSync = CancelTargetSyncUseCase(repository),
        runAvailableSyncs = RunAvailableSyncsUseCase(startTargetSync()),
        observeProgress = ObserveSyncProgressUseCase(repository),
        syncStatusPort = statusPort,
        companySession = company,
        connectivityObserver = connectivity,
        reconcilePartiesFromLedgers = ReconcilePartiesFromLedgersUseCase(ledgerSnapshotPort, partyRepository),
    )

    @Test
    fun doesNotAutoStartSync() = runTest(dispatcher) {
        createVm()
        advanceUntilIdle()
        assertEquals(0, repository.startCalls)
    }

    @Test
    fun missingCompanyBlocksStart() = runTest(dispatcher) {
        company.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canStart)
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(0, repository.startCalls)
    }

    @Test
    fun startSuccess() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(1, repository.startCalls)
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `voucher start reaches the repository and does not poll an unsupported status route`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.startedTargets.clear()
        repository.statusTargets.clear()

        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Vouchers))
        advanceUntilIdle()

        assertEquals(listOf(SyncTarget.Vouchers), repository.startedTargets)
        assertFalse(repository.statusTargets.contains(SyncTarget.Vouchers))
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `run available syncs reaches vouchers after ledgers and stock items`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.startedTargets.clear()

        vm.onEvent(SyncEvent.RunAvailableSyncs)
        advanceUntilIdle()

        assertEquals(
            listOf(SyncTarget.Ledgers, SyncTarget.StockItems, SyncTarget.Vouchers),
            repository.startedTargets,
        )
        assertTrue(vm.uiState.value.aggregateMessage.orEmpty().contains("3 target(s) attempted"))
        assertFalse(vm.uiState.value.isBusy)
    }

    /**
     * Live-observed defect (real-device Connect validation, 2026-08-19): "Run available syncs"
     * completed Ledgers -> Stock items -> Vouchers successfully, but Connect never showed any
     * customers because [SyncViewModel] only ever reconciled Parties from whichever outcome was
     * LAST in the sequence (always Vouchers for this screen's fixed ordering) — Ledgers being
     * first meant its own success was silently never reconciled through this path, only through
     * a lone per-target "Sync now" tap on Ledgers specifically.
     */
    @Test
    fun `run available syncs triggers party reconciliation from the Ledgers outcome even though Vouchers is the last outcome shown`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()

        vm.onEvent(SyncEvent.RunAvailableSyncs)
        advanceUntilIdle()

        assertEquals(1, partyRepository.reconcileCalls)
    }

    @Test
    fun offlineDisablesStart() = runTest(dispatcher) {
        connectivity.online.value = false
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun `ledgers sync success triggers party reconciliation`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(1, partyRepository.reconcileCalls)
    }

    /**
     * TD-039 fix: a Ledgers "Sync Now" must refresh Android's own Room cache (via
     * [StartTargetSyncUseCase]'s `completeLedgerRoomRefresh`), not just the Connector's database --
     * otherwise Party reconciliation right afterward reads stale-or-absent Room data. Physically
     * reproduced on a real device before this fix: Connect showed zero customers after a
     * "successful" Ledgers sync.
     */
    @Test
    fun `ledgers sync success refreshes the ledger Room cache before reconciliation reads it`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(1, ledgerRepository.refreshCalls)
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
    }

    @Test
    fun `stock item sync success never touches the ledger Room refresh`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.StockItems))
        advanceUntilIdle()
        assertEquals(0, ledgerRepository.refreshCalls)
    }

    @Test
    fun `voucher sync success does not trigger party reconciliation`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Vouchers))
        advanceUntilIdle()
        assertEquals(0, partyRepository.reconcileCalls)
    }

    @Test
    fun `party reconciliation failure never affects the surfaced sync outcome`() = runTest(dispatcher) {
        partyRepository.shouldThrow = true
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(1, partyRepository.reconcileCalls)
    }
}

private class FakeSyncRepository : SyncRepository {
    var startCalls = 0
    val startedTargets = mutableListOf<SyncTarget>()
    val statusTargets = mutableListOf<SyncTarget>()
    override fun bindCompany(companyId: String?) = Unit
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        startCalls++
        startedTargets += target
        return AppResult.Success(
            SyncOutcome.Succeeded(
                target = target,
                syncRunId = "r1",
                status = SyncRunStatus.Completed,
                progress = SyncProgress(
                    "r1",
                    SyncRunStatus.Completed,
                    SyncCounts(1, 1, 0, 0, 0, 1),
                    "t0",
                    "t1",
                    1,
                    null,
                    false,
                ),
                validationIssueCount = 0,
                extractionCompleteness = null,
                statistics = SyncStatisticsSummary("t1", 1),
                warningMessage = null,
            ),
        )
    }

    override suspend fun cancelSync(target: SyncTarget) =
        AppResult.Success(
            SyncProgress("r1", SyncRunStatus.Cancelled, SyncCounts(0, 0, 0, 0, 0, null), null, null, null, null, true),
        )

    override suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress> {
        statusTargets += target
        return AppResult.Success(
            SyncProgress(null, SyncRunStatus.Idle, SyncCounts(0, 0, 0, 0, 0, null), null, null, null, null, false),
        )
    }

    override suspend fun getStatistics(target: SyncTarget) =
        AppResult.Success(SyncStatisticsSummary(null, 0))

    override suspend fun listRecentRuns(target: SyncTarget, limit: Int) =
        AppResult.Success(emptyList<SyncRunSummary>())
}

private class FakeObserveSyncStatus : ObserveSyncStatusPort {
    override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
        SyncStatusSummary(
            companyId = "estimation",
            isAnySyncActive = false,
            activeTarget = null,
            activeStatus = null,
            latestSuccessfulAt = null,
            latestFailedMessage = null,
            targets = listOf(
                SyncTargetSnapshot(SyncTarget.Ledgers, true),
                SyncTargetSnapshot(SyncTarget.StockItems, true),
                SyncTargetSnapshot(SyncTarget.Vouchers, true),
            ),
            lastUpdatedEpochMillis = 0L,
        ),
    )
}

private class SyncVmFakeCompany(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        error("unused")
}

/** Always succeeds with an empty page — the Sync-tab Voucher tests here care about the
 * Connector-extraction outcome, not the follow-up window fetch itself (that has its own
 * dedicated coverage in SyncUseCasesTest). */
private class SyncVmFakeVoucherRepository : VoucherRepository {
    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> = error("unused")
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Success(
            VoucherPage(companyId = "estimation", items = emptyList(), page = 1, pageSize = 50, totalItems = 0, totalPages = 1),
        )
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

private class SyncVmFakeLedgerRepository(private val refreshResult: AppResult<LedgerPage>? = null) : LedgerRepository {
    var refreshCalls = 0
    override suspend fun listLedgers(query: LedgerQuery): AppResult<LedgerPage> = error("unused")
    override suspend fun refreshLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        refreshCalls++
        return refreshResult ?: AppResult.Success(
            LedgerPage(items = emptyList(), page = 1, pageSize = 50, totalItems = 0, totalPages = 1, dataFreshnessAt = null),
        )
    }
}

private class SyncVmFakeConnectivity(initial: Boolean) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
    override fun current(): Boolean = online.value
}

private class SyncVmFakeLedgerSnapshotPort(private val ledgers: List<Ledger> = emptyList()) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = ledgers
}

/** Only [reconcilePartiesFromEligibleLedgers] is exercised by [SyncViewModel] — every other
 * member exists solely to satisfy [PartyRepository] and is unused here. */
private class SyncVmFakePartyRepository : PartyRepository {
    var reconcileCalls = 0
    var shouldThrow = false

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = error("unused")
    override suspend fun listByClassification(
        companyId: String,
        classification: PartyClassification,
        page: Int,
        pageSize: Int,
    ): PartyPage = error("unused")
    override suspend fun searchParties(
        companyId: String,
        query: String,
        classification: PartyClassification?,
        page: Int,
        pageSize: Int,
    ): PartyPage = error("unused")
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = error("unused")
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = error("unused")
    override suspend fun getSourceLinksForCompany(companyId: String): List<com.budcom.android.feature.party.domain.model.PartySourceLink> = error("unused")
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = error("unused")
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = error("unused")
    override suspend fun updateBudcomOnlyField(
        companyId: String,
        partyId: String,
        fieldName: String,
        value: String?,
    ): FieldProvenanceState = error("unused")
    override suspend fun confirmFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyValue: String?,
    ): FieldProvenanceState = error("unused")

    override suspend fun reconcilePartiesFromEligibleLedgers(
        companyId: String,
        seeds: List<EligibleLedgerSeed>,
    ): List<Party> {
        reconcileCalls++
        if (shouldThrow) error("boom")
        return emptyList()
    }

    override suspend fun createProspect(
        companyId: String,
        draft: com.budcom.android.feature.party.domain.model.ProspectDraft,
    ): Party = error("unused")
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): com.budcom.android.feature.party.domain.model.PartySourceLink? = error("unused")
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
