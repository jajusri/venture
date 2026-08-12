package com.budcom.android.feature.masterdata.ledger.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.database.AppDatabase
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerEntity
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.voucher.data.local.VoucherDetailEntity
import com.budcom.android.feature.voucher.data.local.VoucherEntity
import com.budcom.android.feature.voucher.data.local.VoucherLedgerLineEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end proof against a REAL in-memory Room database, wiring the real [GetLocalLedgerStatementUseCase]
 * to the real [com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao] and
 * [com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao] — no fakes anywhere in
 * this file. Data is seeded exclusively through [com.budcom.android.feature.voucher.data.local.VoucherDao.storeListWithDetails],
 * the exact production entry point [com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl.refreshVouchers]
 * uses for one normal company/date-window sync — so "one sync" in these tests means the same
 * single atomic write the real app performs, not a hand-rolled substitute.
 *
 * Covers three locked contracts that a hand-written fake DAO cannot genuinely prove:
 *  - the Last-7-Sales lower boundary is a DATE, so a same-day non-Sales movement on the boundary
 *    date is never truncated (continuity.8 hardening report, Phase B);
 *  - one sync window populates every ledger its vouchers touch, never a per-ledger loop (Phase C);
 *  - previous-FY history survives a later, disjoint-scope current-FY sync, and a re-sync of one
 *    FY's own scope never touches the other FY's data (Phase D, including the 31-Mar/1-Apr
 *    boundary).
 */
@RunWith(AndroidJUnit4::class)
class GetLocalLedgerStatementUseCaseIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var useCase: GetLocalLedgerStatementUseCase

    // Matches "today" used throughout the rest of this hardening pass's fixtures: 2026-08-12 is
    // inside FY 2026-27 (1 Apr 2026 -> today), so FY 2025-26 (1 Apr 2025 -> 31 Mar 2026) is the
    // previous FY.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-12T04:00:00Z"), ZoneId.of("Asia/Kolkata"))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        useCase = GetLocalLedgerStatementUseCase(db.ledgerDao(), db.ledgerMovementDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ledger(id: String, name: String) = LedgerEntity(
        companyId = "co-a", id = id, name = name, alias = null, parentGroup = "Sundry Debtors",
        status = "active", closingAmount = null, closingSide = null, closingCurrencyCode = null,
        dataQuality = "complete", syncedAt = "2026-08-12T00:00:00Z", dataFreshnessAt = null,
    )

    private fun voucher(id: String, date: String, type: String = "Sales") = VoucherEntity(
        companyId = "co-a", voucherId = id, date = date, type = type, number = "N-$id",
        partyName = "Party", referenceNumber = null, amountValue = "100", amountSide = "Debit",
        status = "Active", dataQuality = "Complete", lastSyncedAt = 1_000L,
    )

    private fun detail(id: String, date: String) = VoucherDetailEntity(
        companyId = "co-a", voucherId = id, effectiveDate = date, narration = null, lastSyncedAt = 1_000L,
    )

    private fun ledgerLine(voucherId: String, ledgerName: String) =
        VoucherLedgerLineEntity(companyId = "co-a", voucherId = voucherId, lineNumber = 1, ledgerName = ledgerName, amountValue = "100", amountSide = "Debit", isDeemedPositive = true)

    /** Mirrors exactly one [com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl.refreshVouchers] call. */
    private suspend fun oneSync(vouchers: List<Pair<VoucherEntity, String>>, scopeFrom: String, scopeTo: String) {
        db.voucherDao().storeListWithDetails(
            "co-a",
            vouchers.map { it.first },
            vouchers.map { detail(it.first.voucherId, it.first.date) },
            vouchers.map { ledgerLine(it.first.voucherId, it.second) },
            emptyList(),
            System.currentTimeMillis(),
            scopeFrom,
            scopeTo,
        )
    }

    // ==================== Phase B: same-day boundary inclusion, end-to-end ====================

    @Test
    fun sameDayReceiptOnTheSeventhSalesBoundaryDateIsNotTruncated() = runBlocking {
        db.ledgerDao().upsertAll(listOf(ledger("ledger-1", "Cash")))
        val sales = listOf(
            voucher("s7", "2026-08-10") to "Cash",
            voucher("s6", "2026-08-05") to "Cash",
            voucher("s5", "2026-08-01") to "Cash",
            voucher("s4", "2026-07-26") to "Cash",
            voucher("s3", "2026-07-20") to "Cash",
            voucher("s2", "2026-07-15") to "Cash",
            voucher("s1", "2026-06-10") to "Cash", // the 7th/boundary Sales
        )
        val sameDayReceipt = voucher("r-same-day", "2026-06-10", type = "Receipt") to "Cash"
        oneSync(sales + sameDayReceipt, "2026-01-01", "2026-08-12")

        val result = useCase("co-a", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value

        assertEquals("2026-06-10", statement.period.from)
        assertTrue(
            "same-day Receipt on the boundary date must not be truncated",
            statement.transactions.any { it.voucherId == "r-same-day" },
        )
    }

    // ==================== Phase C: one sync populates every ledger it touches ====================

    @Test
    fun oneSyncPopulatesEveryLedgerTheWindowsVouchersTouch() = runBlocking {
        db.ledgerDao().upsertAll(
            listOf(ledger("ledger-a", "Ledger A"), ledger("ledger-b", "Ledger B"), ledger("ledger-c", "Ledger C")),
        )

        // ONE sync call — the same shape as one normal company/date-window Voucher sync —
        // containing movements for three different ledgers.
        oneSync(
            listOf(
                voucher("v-a", "2026-07-10") to "Ledger A",
                voucher("v-b", "2026-07-11") to "Ledger B",
                voucher("v-c", "2026-07-12") to "Ledger C",
            ),
            "2026-07-01",
            "2026-07-31",
        )

        val a = (useCase("co-a", "ledger-a", LedgerPeriodSelection.Custom("2026-07-01", "2026-07-31")) as AppResult.Success).value
        val b = (useCase("co-a", "ledger-b", LedgerPeriodSelection.Custom("2026-07-01", "2026-07-31")) as AppResult.Success).value
        val c = (useCase("co-a", "ledger-c", LedgerPeriodSelection.Custom("2026-07-01", "2026-07-31")) as AppResult.Success).value

        assertEquals(listOf("v-a"), a.transactions.map { it.voucherId })
        assertEquals(listOf("v-b"), b.transactions.map { it.voucherId })
        assertEquals(listOf("v-c"), c.transactions.map { it.voucherId })
    }

    // ==================== Phase D: FY retention across disjoint-scope syncs, 31-Mar/1-Apr boundary ====================

    @Test
    fun previousFinancialYearSurvivesALaterDisjointCurrentFinancialYearSync() = runBlocking {
        db.ledgerDao().upsertAll(listOf(ledger("ledger-1", "Cash")))

        // Sync 1: FY 2025-26 window, including both the first day of that FY and 31 March (the
        // FY's own last day).
        oneSync(
            listOf(
                voucher("fy2526-first", "2025-04-01") to "Cash",
                voucher("fy2526-last", "2026-03-31") to "Cash",
            ),
            "2025-04-01",
            "2026-03-31",
        )

        // Sync 2: a LATER, disjoint-scope FY 2026-27 sync (1 April 2026 -> today) — must never
        // touch FY 2025-26 data, which is outside its own scope.
        oneSync(
            listOf(voucher("fy2627-first", "2026-04-01") to "Cash"),
            "2026-04-01",
            "2026-08-12",
        )

        val previousFy = (useCase("co-a", "ledger-1", LedgerPeriodSelection.PreviousFinancialYear) as AppResult.Success).value
        val currentFy = (useCase("co-a", "ledger-1", LedgerPeriodSelection.CurrentFinancialYear) as AppResult.Success).value

        assertEquals("2025-04-01", previousFy.period.from)
        assertEquals("2026-03-31", previousFy.period.to)
        assertEquals(setOf("fy2526-first", "fy2526-last"), previousFy.transactions.map { it.voucherId }.toSet())

        assertEquals("2026-04-01", currentFy.period.from)
        assertEquals(setOf("fy2627-first"), currentFy.transactions.map { it.voucherId }.toSet())
    }

    @Test
    fun reSyncingOnlyThePreviousFinancialYearScopeNeverTouchesCurrentFinancialYearData() = runBlocking {
        db.ledgerDao().upsertAll(listOf(ledger("ledger-1", "Cash")))

        oneSync(listOf(voucher("fy2526-original", "2025-06-01") to "Cash"), "2025-04-01", "2026-03-31")
        oneSync(listOf(voucher("fy2627-voucher", "2026-05-01") to "Cash"), "2026-04-01", "2026-08-12")

        // Historical reconciliation of ONLY the previous FY's own scope: the original voucher is
        // gone from Tally (edited/replaced), a new one takes its place.
        oneSync(listOf(voucher("fy2526-reconciled", "2025-06-01") to "Cash"), "2025-04-01", "2026-03-31")

        val previousFy = (useCase("co-a", "ledger-1", LedgerPeriodSelection.PreviousFinancialYear) as AppResult.Success).value
        val currentFy = (useCase("co-a", "ledger-1", LedgerPeriodSelection.CurrentFinancialYear) as AppResult.Success).value

        // Only the authoritative replacement survives within the reconciled scope — no duplicate,
        // no phantom of the old row.
        assertEquals(listOf("fy2526-reconciled"), previousFy.transactions.map { it.voucherId })
        // Current FY, outside the reconciled scope, is completely unaffected.
        assertEquals(listOf("fy2627-voucher"), currentFy.transactions.map { it.voucherId })
    }

    @Test
    fun aCustomPeriodSeveralYearsBeforeAnySyncedDataIsAnHonestEmptyResultNotAHardcodedCutoffFailure() = runBlocking {
        // No artificial "two years back" schema cutoff exists anywhere in the local-first Ledger
        // query path: an arbitrarily old manually-requested period must still resolve as a
        // successful (if honestly incomplete) read, never a hard failure baked into the range
        // itself.
        db.ledgerDao().upsertAll(listOf(ledger("ledger-1", "Cash")))
        oneSync(listOf(voucher("recent", "2026-07-01") to "Cash"), "2026-07-01", "2026-07-31")

        val result = useCase("co-a", "ledger-1", LedgerPeriodSelection.Custom("2020-01-01", "2020-12-31"))
        val statement = (result as AppResult.Success).value

        assertTrue(statement.transactions.isEmpty())
        assertTrue("coverage must honestly report the gap, not fabricate completeness", !statement.coverage.transactionsComplete)
    }
}
