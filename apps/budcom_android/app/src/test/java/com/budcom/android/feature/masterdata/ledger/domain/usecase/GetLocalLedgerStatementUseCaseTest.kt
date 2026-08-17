package com.budcom.android.feature.masterdata.ledger.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerEntity
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementInventoryRow
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementRow
import com.budcom.android.feature.masterdata.ledger.data.local.VoucherNarrationRow
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-tests the Kotlin-level algorithm ([GetLocalLedgerStatementUseCase]) against fake DAOs.
 * The real Room SQL filtering this algorithm depends on (Sales-only counting, active-status
 * filtering, company/ledger scoping) is verified separately against a real in-memory database in
 * [com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDaoTest] — this file
 * assumes the DAO contract and tests what the use case does with whatever the DAO returns.
 */
class GetLocalLedgerStatementUseCaseTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-12T04:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private val ledgerDao = FakeLedgerDao()
    private val movementDao = FakeLedgerMovementDao()
    private val useCase = GetLocalLedgerStatementUseCase(ledgerDao, movementDao, clock)

    private val ledger = LedgerEntity(
        companyId = "estimation", id = "ledger-1", name = "Asif Bhai, Supplier Khilwat", alias = null,
        parentGroup = "Sundry Debtors", status = "active", closingAmount = "1000", closingSide = "dr",
        closingCurrencyCode = "INR", dataQuality = "complete", syncedAt = "2026-08-12T03:56:42Z", dataFreshnessAt = null,
    )

    private fun row(voucherId: String, date: String, type: String, amount: String, side: String) =
        LedgerMovementRow(voucherId, date, type, "v-$voucherId", null, 1, amount, side)

    @Test
    fun `Last7Sales anchors from at the earliest of the last 7 Sales vouchers only`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.lastSales = listOf(
            row("s7", "2026-08-10", "Sales", "10", "dr"),
            row("s6", "2026-08-05", "Sales", "10", "dr"),
            row("s5", "2026-08-01", "Sales", "10", "dr"),
            row("s4", "2026-07-26", "Sales", "10", "dr"),
            row("s3", "2026-07-20", "Sales", "10", "dr"),
            row("s2", "2026-07-15", "Sales", "10", "dr"),
            row("s1", "2026-07-10", "Sales", "10", "dr"),
        )
        movementDao.movements = movementDao.lastSales

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value
        assertEquals("2026-07-10", statement.period.from)
        assertEquals("2026-08-12", statement.period.to)
    }

    @Test
    fun `Receipts Payments and Journals never consume the Last-7-Sales count`() = runBlockingTest {
        ledgerDao.entity = ledger
        // The fake's lastSalesMovements already models the DAO's own Sales-only SQL filter (see
        // LedgerMovementDaoTest for that filter proven against a real database) — this test
        // proves the USE CASE anchors on exactly what that filtered set contains, not on the
        // wider movements list which does include Receipt/Payment/Journal rows.
        movementDao.lastSales = listOf(
            row("s3", "2026-08-05", "Sales", "10", "dr"),
            row("s2", "2026-07-20", "Sales", "10", "dr"),
            row("s1", "2026-07-01", "Sales", "10", "dr"),
        )
        movementDao.movements = movementDao.lastSales + listOf(
            row("r1", "2026-08-08", "Receipt", "5", "cr"),
            row("p1", "2026-08-09", "Payment", "5", "dr"),
            row("j1", "2026-08-11", "Journal", "5", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value
        assertEquals("2026-07-01", statement.period.from)
        // All non-Sales movements from that boundary onward are still included in the DISPLAY
        // window (they just don't affect where the window starts).
        assertEquals(6, statement.transactions.size)
    }

    @Test
    fun `fewer than 7 Sales vouchers uses all of them as the boundary`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.lastSales = listOf(
            row("s2", "2026-08-01", "Sales", "10", "dr"),
            row("s1", "2026-07-15", "Sales", "10", "dr"),
        )
        movementDao.movements = movementDao.lastSales

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value
        assertEquals("2026-07-15", statement.period.from)
    }

    @Test
    fun `zero Sales vouchers falls back to this ledger's own earliest known movement`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.lastSales = emptyList()
        movementDao.movements = listOf(row("r1", "2026-06-01", "Receipt", "5", "cr"))

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value
        assertEquals("2026-06-01", statement.period.from)
    }

    @Test
    fun `a same-day Receipt on the 7th-Sales boundary date is not truncated`() = runBlockingTest {
        // Locked contract: the Last-7-Sales lower boundary is a DATE, not a voucher position. If
        // the 7th (oldest-of-the-7) Sales lands on 2026-06-10 and a Receipt for the same ledger
        // also happened on 2026-06-10, that Receipt must appear in the statement — never dropped
        // just because it isn't itself one of the 7 counted Sales.
        ledgerDao.entity = ledger
        movementDao.lastSales = listOf(
            row("s7", "2026-08-10", "Sales", "10", "dr"),
            row("s6", "2026-08-05", "Sales", "10", "dr"),
            row("s5", "2026-08-01", "Sales", "10", "dr"),
            row("s4", "2026-07-26", "Sales", "10", "dr"),
            row("s3", "2026-07-20", "Sales", "10", "dr"),
            row("s2", "2026-07-15", "Sales", "10", "dr"),
            row("s1", "2026-06-10", "Sales", "10", "dr"), // the 7th/boundary Sales
        )
        val sameDayReceipt = row("r-same-day", "2026-06-10", "Receipt", "5", "cr")
        movementDao.movements = movementDao.lastSales + sameDayReceipt

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales)
        val statement = (result as AppResult.Success).value

        assertEquals("2026-06-10", statement.period.from)
        assertTrue(
            "same-day Receipt on the boundary date must be included, not truncated",
            statement.transactions.any { it.voucherId == "r-same-day" },
        )
    }

    @Test
    fun `movements on the boundary date are returned in deterministic date-then-voucherId order`() = runBlockingTest {
        // Multiple movement types landing on the same date (Sales + Receipt + Payment + Journal)
        // must come back in a stable, repeatable order — the fake mirrors the real DAO's
        // `date ASC, voucherId ASC, lineNumber ASC` contract (see LedgerMovementDaoTest for the
        // real-SQL proof of that same ordering).
        ledgerDao.entity = ledger
        val sameDay = listOf(
            row("v-journal", "2026-08-05", "Journal", "5", "dr"),
            row("v-payment", "2026-08-05", "Payment", "5", "dr"),
            row("v-receipt", "2026-08-05", "Receipt", "5", "cr"),
            row("v-sales", "2026-08-05", "Sales", "10", "dr"),
        ).sortedBy { it.voucherId } // fake's `.filter` preserves input order — feed it pre-sorted
        movementDao.lastSales = listOf(row("v-sales", "2026-08-05", "Sales", "10", "dr"))
        movementDao.movements = sameDay

        val first = (useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales) as AppResult.Success).value
        val second = (useCase("estimation", "ledger-1", LedgerPeriodSelection.Last7Sales) as AppResult.Success).value

        val expectedOrder = listOf("v-journal", "v-payment", "v-receipt", "v-sales")
        assertEquals(expectedOrder, first.transactions.map { it.voucherId })
        assertEquals("repeated query must return the identical order", first.transactions.map { it.voucherId }, second.transactions.map { it.voucherId })
    }

    @Test
    fun `custom period is used verbatim`() = runBlockingTest {
        ledgerDao.entity = ledger
        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-01-01", "2026-01-31"))
        val statement = (result as AppResult.Success).value
        assertEquals("2026-01-01", statement.period.from)
        assertEquals("2026-01-31", statement.period.to)
    }

    @Test
    fun `opening closing and running balances anchor on the ledger's synced closing balance`() = runBlockingTest {
        ledgerDao.entity = ledger // closing 1000 Dr as of 2026-08-12
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "200", "dr"))
        movementDao.lastSales = movementDao.movements

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"))
        val statement = (result as AppResult.Success).value

        // Opening = closing(1000 Dr) - netToSynced(200 Dr, since the one movement is on/before
        // syncedAt) = 800 Dr.
        assertEquals("800", statement.openingBalance?.amount)
        assertEquals("1000", statement.closingBalance?.amount)
        assertEquals(1, statement.transactions.size)
        assertEquals("1000", statement.transactions[0].runningBalance?.amount)
        assertTrue(statement.coverage.balanceAvailable)
    }

    @Test
    fun `a Cr movement reduces a Dr balance correctly`() = runBlockingTest {
        ledgerDao.entity = ledger // closing 1000 Dr
        movementDao.movements = listOf(row("v1", "2026-08-01", "Receipt", "300", "cr"))
        movementDao.lastSales = emptyList()

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"))
        val statement = (result as AppResult.Success).value
        // Opening = 1000 - (-300) = 1300 Dr; closing = 1300 + (-300) = 1000 Dr (back to anchor).
        assertEquals("1300", statement.openingBalance?.amount)
        assertEquals("1000", statement.closingBalance?.amount)
    }

    @Test
    fun `balance is unavailable and never fabricated when the ledger has no synced closing balance`() = runBlockingTest {
        ledgerDao.entity = ledger.copy(closingAmount = null, closingSide = null)
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "200", "dr"))

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"))
        val statement = (result as AppResult.Success).value
        assertNull(statement.openingBalance)
        assertNull(statement.closingBalance)
        assertNull(statement.transactions[0].runningBalance)
        assertFalse(statement.coverage.balanceAvailable)
        assertTrue(statement.coverage.message!!.contains("balance has not been synced"))
    }

    @Test
    fun `balance is unavailable when the period starts before the ledger's last balance sync`() = runBlockingTest {
        ledgerDao.entity = ledger // syncedAt 2026-08-12
        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-09-01", "2026-09-30"))
        val statement = (result as AppResult.Success).value
        assertFalse(statement.coverage.balanceAvailable)
    }

    @Test
    fun `coverage is honestly incomplete when the requested period predates any locally synced Voucher`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.earliestDate = "2026-07-01"
        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-01-01", "2026-01-31"))
        val statement = (result as AppResult.Success).value
        assertFalse(statement.coverage.transactionsComplete)
        assertTrue(statement.coverage.message!!.contains("2026-07-01"))
    }

    @Test
    fun `an unsynced ledger returns a failure rather than a fabricated empty statement`() = runBlockingTest {
        ledgerDao.entity = null
        val result = useCase("estimation", "not-synced", LedgerPeriodSelection.Last7Sales)
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `company and ledger scope are passed through to every DAO call`() = runBlockingTest {
        ledgerDao.entity = ledger
        useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"))
        assertEquals("estimation" to "ledger-1", ledgerDao.lastLookup)
        assertEquals("estimation" to ledger.name, movementDao.lastRangeScope)
    }

    // --- Detailed mode (Ledger Sharing item-detail feature) ---

    @Test
    fun `Summary mode never queries inventory lines`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "200", "dr"))
        useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Summary)
        assertEquals(0, movementDao.inventoryQueryCalls)
    }

    @Test
    fun `Detailed mode queries inventory lines exactly once regardless of transaction count`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(
            row("v1", "2026-08-01", "Sales", "200", "dr"),
            row("v2", "2026-08-02", "Sales", "300", "dr"),
            row("v3", "2026-08-03", "Receipt", "50", "cr"),
        )
        useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-03"), LedgerStatementMode.Detailed)
        assertEquals("one batched query must serve every displayed transaction, never N+1", 1, movementDao.inventoryQueryCalls)
        assertEquals(setOf("v1", "v2", "v3"), movementDao.lastInventoryVoucherIds?.toSet())
    }

    @Test
    fun `Detailed mode attaches correct item rows including quantity rate and amount`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "9450", "dr"))
        movementDao.inventoryLines = listOf(
            // "450.00/Nos" mirrors Tally's actual stored rate format (compound, not a plain
            // decimal) — see `rate preserves the exact stored Tally text, including compound
            // unit-rate strings` below for the dedicated regression coverage.
            LedgerMovementInventoryRow("v1", 1, "Angle Cock - Flora", "20 Nos", "450.00/Nos", "9000.00", "dr"),
            LedgerMovementInventoryRow("v1", 2, "Wall Mixer", "5 Nos", "90.00/Nos", "450.00", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val statement = (result as AppResult.Success).value
        val detail = statement.transactions.single().itemDetail

        assertEquals(2, detail?.items?.size)
        assertEquals("Angle Cock - Flora", detail?.items?.get(0)?.itemName)
        assertEquals("20 Nos", detail?.items?.get(0)?.quantityLabel)
        assertEquals("450.00/Nos", detail?.items?.get(0)?.rateLabel)
        assertEquals("₹9,000.00", detail?.items?.get(0)?.amountLabel)
    }

    @Test
    fun `rate preserves the exact stored Tally text, including compound unit-rate strings`() = runBlockingTest {
        // Reproduces the physical continuity.12 acceptance failure: Tally's stored rate for an
        // inventory line is a compound display string like "26.00/Nos", not a plain decimal.
        // Against the previous mapping (`line.rate?.toBigDecimalOrNull()?.let(::formatInr)`),
        // "26.00/Nos".toBigDecimalOrNull() is null, so rateLabel silently became null and every
        // Rate cell rendered blank in the shared PDF. The corrected mapping must preserve the
        // stored text verbatim — never parse, reformat, or derive it.
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "1300", "dr"))
        movementDao.inventoryLines = listOf(
            LedgerMovementInventoryRow("v1", 1, "Turkey Tap", "50 Nos", "26.00/Nos", "1300", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val item = (result as AppResult.Success).value.transactions.single().itemDetail?.items?.single()

        assertEquals("26.00/Nos", item?.rateLabel)
        // Quantity/unit and amount are independent fields and must be unaffected by the rate fix.
        assertEquals("50 Nos", item?.quantityLabel)
        assertEquals("₹1,300.00", item?.amountLabel)
    }

    @Test
    fun `a blank or null rate remains safely blank, never a fabricated value`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "100", "dr"))
        movementDao.inventoryLines = listOf(
            LedgerMovementInventoryRow("v1", 1, "Item A", "1 Nos", null, "50", "dr"),
            LedgerMovementInventoryRow("v1", 2, "Item B", "1 Nos", "", "50", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val items = (result as AppResult.Success).value.transactions.single().itemDetail?.items

        assertNull(items?.get(0)?.rateLabel)
        assertNull(items?.get(1)?.rateLabel)
    }

    @Test
    fun `a plain decimal rate string is preserved as-is, not reformatted with a currency symbol`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "100", "dr"))
        movementDao.inventoryLines = listOf(
            LedgerMovementInventoryRow("v1", 1, "Item A", "1 Nos", "100.00", "100", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val item = (result as AppResult.Success).value.transactions.single().itemDetail?.items?.single()

        assertEquals("100.00", item?.rateLabel)
    }

    @Test
    fun `the rate fix does not alter ledger accounting values`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "1300", "dr"))
        movementDao.inventoryLines = listOf(
            LedgerMovementInventoryRow("v1", 1, "Turkey Tap", "50 Nos", "26.00/Nos", "1300", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val transaction = (result as AppResult.Success).value.transactions.single()

        assertEquals("1300", transaction.debit)
        assertEquals(null, transaction.credit)
    }

    @Test
    fun `Detailed mode item Total is the sum of the item amounts, independent of the ledger debit`() = runBlockingTest {
        ledgerDao.entity = ledger
        // The ledger-line accounting amount (200) deliberately differs from the item lines' sum
        // (9450) — a plausible real scenario (partial ledger allocation, rounding, tax lines not
        // modeled as inventory) — proving the item Total is never derived from or compared against
        // the authoritative accounting amount.
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "200", "dr"))
        movementDao.inventoryLines = listOf(
            LedgerMovementInventoryRow("v1", 1, "Angle Cock - Flora", "20 Nos", "450.00", "9000.00", "dr"),
            LedgerMovementInventoryRow("v1", 2, "Wall Mixer", "5 Nos", "90.00", "450.00", "dr"),
        )

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val statement = (result as AppResult.Success).value
        val transaction = statement.transactions.single()

        assertEquals("₹9,450.00", transaction.itemDetail?.totalLabel)
        assertEquals("the ledger debit must remain the untouched authoritative value", "200", transaction.debit)
    }

    @Test
    fun `a non-item voucher never receives a fabricated item table in Detailed mode`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Receipt", "500", "cr"))
        movementDao.inventoryLines = emptyList()

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val statement = (result as AppResult.Success).value
        assertNull(statement.transactions.single().itemDetail)
    }

    @Test
    fun `Detailed mode never changes debit, credit, or running balance versus Summary mode`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "200", "dr"))
        movementDao.lastSales = movementDao.movements
        movementDao.inventoryLines = listOf(LedgerMovementInventoryRow("v1", 1, "Item", "1 Nos", "200.00", "200.00", "dr"))

        val summary = (useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Summary) as AppResult.Success).value
        val detailed = (useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed) as AppResult.Success).value

        assertEquals(summary.openingBalance, detailed.openingBalance)
        assertEquals(summary.closingBalance, detailed.closingBalance)
        assertEquals(summary.transactions.single().debit, detailed.transactions.single().debit)
        assertEquals(summary.transactions.single().credit, detailed.transactions.single().credit)
        assertEquals(summary.transactions.single().runningBalance, detailed.transactions.single().runningBalance)
    }

    @Test
    fun `all item lines are included with no truncation`() = runBlockingTest {
        ledgerDao.entity = ledger
        movementDao.movements = listOf(row("v1", "2026-08-01", "Sales", "1000", "dr"))
        movementDao.inventoryLines = (1..12).map { n ->
            LedgerMovementInventoryRow("v1", n, "Item $n", "$n Nos", "10.00", "${n * 10}.00", "dr")
        }

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val statement = (result as AppResult.Success).value
        assertEquals(12, statement.transactions.single().itemDetail?.items?.size)
    }

    @Test
    fun `item detail is attached to only the first ledger-line row for a voucher, never duplicated`() = runBlockingTest {
        // A voucher with two ledger lines against the same ledger (a split entry) must still show
        // the item block exactly once, not once per line.
        ledgerDao.entity = ledger
        movementDao.movements = listOf(
            LedgerMovementRow("v1", "2026-08-01", "Sales", "v-1", null, 1, "100", "dr"),
            LedgerMovementRow("v1", "2026-08-01", "Sales", "v-1", null, 2, "100", "dr"),
        )
        movementDao.inventoryLines = listOf(LedgerMovementInventoryRow("v1", 1, "Item", "1 Nos", "200.00", "200.00", "dr"))

        val result = useCase("estimation", "ledger-1", LedgerPeriodSelection.Custom("2026-08-01", "2026-08-01"), LedgerStatementMode.Detailed)
        val statement = (result as AppResult.Success).value
        assertEquals(2, statement.transactions.size)
        assertEquals(1, statement.transactions.count { it.itemDetail != null })
    }
}

private class FakeLedgerDao : LedgerDao {
    var entity: LedgerEntity? = null
    var lastLookup: Pair<String, String>? = null

    override suspend fun countForCompany(companyId: String): Int = if (entity != null) 1 else 0
    override suspend fun findById(companyId: String, ledgerId: String): LedgerEntity? {
        lastLookup = companyId to ledgerId
        return entity?.takeIf { it.companyId == companyId && it.id == ledgerId }
    }
    override suspend fun getAllForCompany(companyId: String): List<LedgerEntity> = error("not used")
    override suspend fun upsertAll(entities: List<LedgerEntity>) = error("not used")
    override suspend fun deleteForCompany(companyId: String) = error("not used")
    override suspend fun queryPage(
        companyId: String, query: String?, sortBy: String, ascending: Int, limit: Int, offset: Int,
    ): List<LedgerEntity> = error("not used")
    override suspend fun countMatching(companyId: String, query: String?): Int = error("not used")
}

private class FakeLedgerMovementDao : LedgerMovementDao {
    var lastSales: List<LedgerMovementRow> = emptyList()
    var movements: List<LedgerMovementRow> = emptyList()
    var earliestDate: String? = "2000-01-01"
    var lastRangeScope: Pair<String, String>? = null
    var inventoryLines: List<LedgerMovementInventoryRow> = emptyList()
    var inventoryQueryCalls = 0
        private set
    var lastInventoryVoucherIds: List<String>? = null

    override suspend fun lastSalesMovements(companyId: String, ledgerName: String, limit: Int): List<LedgerMovementRow> =
        lastSales.take(limit)
    override suspend fun movementsInRange(companyId: String, ledgerName: String, from: String, to: String): List<LedgerMovementRow> {
        lastRangeScope = companyId to ledgerName
        return movements.filter { it.date in from..to }
    }
    override suspend fun narrations(companyId: String, voucherIds: List<String>): List<VoucherNarrationRow> = emptyList()
    override suspend fun earliestSyncedDate(companyId: String): String? = earliestDate
    override suspend fun inventoryLinesForVouchers(companyId: String, voucherIds: List<String>): List<LedgerMovementInventoryRow> {
        inventoryQueryCalls++
        lastInventoryVoucherIds = voucherIds
        return inventoryLines.filter { it.voucherId in voucherIds }
    }
}

/** No coroutine test dispatcher is needed: every DAO call here is a synchronous fake, so a plain
 * blocking runner keeps these tests simple without pulling in kotlinx-coroutines-test. */
private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
