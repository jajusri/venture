package com.budcom.android.feature.sync.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.domain.usecase.ReconcilePartiesFromLedgersUseCase
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncUseCasesTest {

    private fun succeeded(target: SyncTarget) = SyncOutcome.Succeeded(
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
    )

    private fun emptyPage() = VoucherPage(
        companyId = "estimation",
        items = emptyList(),
        page = 1,
        pageSize = 50,
        totalItems = 0,
        totalPages = 1,
    )

    private fun emptyLedgerPage() = LedgerPage(
        items = emptyList(),
        page = 1,
        pageSize = 50,
        totalItems = 0,
        totalPages = 1,
        dataFreshnessAt = null,
    )

    private fun useCase(
        syncRepo: FakeSyncRepo,
        company: FakeCompany = FakeCompany("estimation"),
        voucherRepo: FakeVoucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage())),
        ledgerRepo: FakeLedgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage())),
        reconcileParties: ReconcilePartiesFromLedgersUseCase = ReconcilePartiesFromLedgersUseCase(
            FakeLedgerSnapshotPort(emptyList()),
            FakePartyRepo(),
        ),
    ) = StartTargetSyncUseCase(
        syncRepo,
        company,
        RefreshVouchersUseCase(voucherRepo),
        RefreshLedgersUseCase(ledgerRepo),
        reconcileParties,
    )

    @Test
    fun `voucher sync success followed by a successful window fetch reports the original success`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Success)
        assertEquals(SyncTarget.Vouchers, (result as AppResult.Success).value.target)
        assertEquals(1, voucherRepo.refreshCalls)
        assertEquals("estimation", voucherRepo.lastQuery?.companyId)
    }

    @Test
    fun `voucher sync success followed by a failed window fetch is reported as a failure, not Completed`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(
            refreshResult = AppResult.Failure(AppError.Message("Room persistence failed")),
        )
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(1, voucherRepo.refreshCalls)
    }

    @Test
    fun `voucher extraction failure never attempts a window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(
            AppResult.Failure(AppError.Message("Connector extraction failed")),
        )
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, voucherRepo.refreshCalls)
    }

    /**
     * TD-039 fix regression test: before this fix, a Ledgers "Sync Now" left Android's Room cache
     * (`cached_ledgers`) untouched, so Connect's Customer/Supplier population (which reads Room via
     * `ReconcilePartiesFromLedgersUseCase`) silently stayed stale-or-empty after a "successful"
     * sync — physically reproduced on a real device.
     */
    @Test
    fun `ledger sync success is followed by a Room refresh, mirroring the Voucher window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(SyncTarget.Ledgers, (result as AppResult.Success).value.target)
        assertEquals(1, ledgerRepo.refreshCalls)
        // Ledgers must never also trigger the unrelated Voucher window fetch.
        assertEquals(0, voucherRepo.refreshCalls)
    }

    @Test
    fun `ledger sync success followed by a failed Room refresh is reported as a failure, not Completed`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(
            refreshResult = AppResult.Failure(AppError.Message("Room persistence failed")),
        )
        val result = useCase(syncRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(1, ledgerRepo.refreshCalls)
    }

    @Test
    fun `ledger extraction failure never attempts a Room refresh`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Failure(AppError.Message("Connector extraction failed")))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val result = useCase(syncRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, ledgerRepo.refreshCalls)
    }

    @Test
    fun `stock item sync touches neither the ledger Room refresh nor the voucher window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.StockItems)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo, ledgerRepo = ledgerRepo)(SyncTarget.StockItems)

        assertTrue(result is AppResult.Success)
        assertEquals(0, ledgerRepo.refreshCalls)
        assertEquals(0, voucherRepo.refreshCalls)
        assertEquals(1, syncRepo.startCalls)
    }

    @Test
    fun `missing company blocks start before touching any repository`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val result = useCase(syncRepo, FakeCompany(null), voucherRepo, ledgerRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, syncRepo.startCalls)
        assertEquals(0, voucherRepo.refreshCalls)
        assertEquals(0, ledgerRepo.refreshCalls)
    }

    /**
     * TD-041 fix: Party reconciliation used to be a `viewModelScope.launch` fire-and-forget
     * triggered from [com.budcom.android.feature.sync.presentation.SyncViewModel] AFTER this
     * use-case already returned "Completed" — live-reproduced on a real device as silently
     * cancelled by navigating away from the Sync screen within the few seconds a real
     * reconciliation takes, leaving Connect's Party data stale with no error surfaced anywhere.
     * Reconciliation now happens inside this use-case's own suspend chain, so it is awaited
     * before [StartTargetSyncUseCase.invoke] can return at all — there is no longer a separate,
     * independently-cancellable job for a caller's coroutine scope to lose.
     */
    @Test
    fun `ledger sync success reconciles parties before this call returns`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val partyRepo = FakePartyRepo()
        val reconcile = ReconcilePartiesFromLedgersUseCase(
            FakeLedgerSnapshotPort(listOf(eligibleLedger())),
            partyRepo,
        )
        val result = useCase(syncRepo, ledgerRepo = ledgerRepo, reconcileParties = reconcile)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(1, partyRepo.reconcileCalls)
    }

    @Test
    fun `stock item and voucher syncs never reconcile parties`() = runTest {
        val partyRepo = FakePartyRepo()
        val reconcile = ReconcilePartiesFromLedgersUseCase(FakeLedgerSnapshotPort(listOf(eligibleLedger())), partyRepo)

        useCase(
            FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.StockItems))),
            reconcileParties = reconcile,
        )(SyncTarget.StockItems)
        useCase(
            FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers))),
            reconcileParties = reconcile,
        )(SyncTarget.Vouchers)

        assertEquals(0, partyRepo.reconcileCalls)
    }

    @Test
    fun `ledger sync success followed by a failed Room refresh never reconciles parties`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Failure(AppError.Message("Room persistence failed")))
        val partyRepo = FakePartyRepo()
        val reconcile = ReconcilePartiesFromLedgersUseCase(FakeLedgerSnapshotPort(listOf(eligibleLedger())), partyRepo)

        useCase(syncRepo, ledgerRepo = ledgerRepo, reconcileParties = reconcile)(SyncTarget.Ledgers)

        assertEquals(0, partyRepo.reconcileCalls)
    }

    @Test
    fun `party reconciliation failure never turns a completed ledger sync into a failure`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val partyRepo = FakePartyRepo(shouldThrow = true)
        val reconcile = ReconcilePartiesFromLedgersUseCase(FakeLedgerSnapshotPort(listOf(eligibleLedger())), partyRepo)

        val result = useCase(syncRepo, ledgerRepo = ledgerRepo, reconcileParties = reconcile)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(1, partyRepo.reconcileCalls)
    }

    private fun eligibleLedger() = Ledger(
        id = "guid:eligible",
        name = "ABC Traders",
        alias = null,
        parentGroup = "Sundry Debtors",
        status = LedgerStatus.Active,
        closingBalance = null,
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = "t",
    )
}

private class FakeSyncRepo(private val startResult: AppResult<SyncOutcome>) : SyncRepository {
    var startCalls = 0
    override fun bindCompany(companyId: String?) = Unit
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        startCalls++
        return startResult
    }
    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> = error("unused")
    override suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress> = error("unused")
    override suspend fun getStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> = error("unused")
    override suspend fun listRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>> =
        AppResult.Success(emptyList())
}

private class FakeVoucherRepo(private val refreshResult: AppResult<VoucherPage>) : VoucherRepository {
    var refreshCalls = 0
    var lastQuery: VoucherQuery? = null
    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> = error("unused")
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        refreshCalls++
        lastQuery = query
        return refreshResult
    }
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

private class FakeLedgerRepo(private val refreshResult: AppResult<LedgerPage>) : LedgerRepository {
    var refreshCalls = 0
    override suspend fun listLedgers(query: LedgerQuery): AppResult<LedgerPage> = error("unused")
    override suspend fun refreshLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        refreshCalls++
        return refreshResult
    }
}

private class FakeCompany(initial: String?) : CompanySessionPort {
    private val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}

private class FakeLedgerSnapshotPort(private val ledgers: List<Ledger> = emptyList()) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = ledgers
}

/** Only [reconcilePartiesFromEligibleLedgers] is exercised by [StartTargetSyncUseCase] — every
 * other member exists solely to satisfy [PartyRepository] and is unused here. */
private class FakePartyRepo(private val shouldThrow: Boolean = false) : PartyRepository {
    var reconcileCalls = 0

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = error("unused")
    override suspend fun listByClassification(
        companyId: String,
        classification: com.budcom.android.feature.party.domain.model.PartyClassification,
        page: Int,
        pageSize: Int,
    ): com.budcom.android.feature.party.domain.model.PartyPage = error("unused")
    override suspend fun searchParties(
        companyId: String,
        query: String,
        classification: com.budcom.android.feature.party.domain.model.PartyClassification?,
        page: Int,
        pageSize: Int,
    ): com.budcom.android.feature.party.domain.model.PartyPage = error("unused")
    override suspend fun getContactPersons(companyId: String, partyId: String): List<com.budcom.android.feature.party.domain.model.PartyContactPerson> =
        error("unused")
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<com.budcom.android.feature.party.domain.model.Tag> = error("unused")
    override suspend fun getSourceLinksForCompany(companyId: String): List<com.budcom.android.feature.party.domain.model.PartySourceLink> = error("unused")
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<com.budcom.android.feature.party.domain.model.Tag>> = error("unused")
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<com.budcom.android.feature.party.domain.model.PartyFieldProvenance> =
        error("unused")
    override suspend fun updateBudcomOnlyField(
        companyId: String,
        partyId: String,
        fieldName: String,
        value: String?,
    ): com.budcom.android.feature.party.domain.model.FieldProvenanceState = error("unused")
    override suspend fun confirmFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyValue: String?,
    ): com.budcom.android.feature.party.domain.model.FieldProvenanceState = error("unused")

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
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): com.budcom.android.feature.party.domain.model.PartySourceLink? =
        error("unused")
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
    ): com.budcom.android.feature.party.domain.model.PartyContactPerson = error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String): Unit = error("unused")
    override suspend fun getAllTags(): List<com.budcom.android.feature.party.domain.model.Tag> = error("unused")
    override suspend fun createOrGetTag(name: String, parentTagId: String?): com.budcom.android.feature.party.domain.model.Tag = error("unused")
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
    ): com.budcom.android.feature.party.domain.model.FieldProvenanceState = error("unused")
    override suspend fun getExportHistory(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.budcom.android.feature.party.domain.model.PartyExportEvent> = error("unused")
}
