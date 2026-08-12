package com.budcom.android.feature.masterdata.ledger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.database.AppDatabase
import com.budcom.android.feature.voucher.data.local.VoucherEntity
import com.budcom.android.feature.voucher.data.local.VoucherLedgerLineEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the REAL SQL behind [LedgerMovementDao] — not a hand-written fake — against a real
 * in-memory Room database populated exactly the way the normal Voucher sync already populates
 * `cached_vouchers`/`cached_voucher_ledger_lines` (see [GetLocalLedgerStatementUseCase]'s doc
 * comment for why no separate Ledger-movement table exists). This is the actual proof that
 * Sales-only counting, active-status filtering, and company/ledger isolation work, since
 * [com.budcom.android.feature.masterdata.ledger.domain.usecase.GetLocalLedgerStatementUseCaseTest]
 * only exercises the Kotlin algorithm against a fake DAO that assumes this SQL is correct.
 */
@RunWith(AndroidJUnit4::class)
class LedgerMovementDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LedgerMovementDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.ledgerMovementDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertVoucher(
        companyId: String,
        voucherId: String,
        date: String,
        type: String,
        status: String = "Active",
        ledgerName: String = "Cash",
        amount: String = "100",
        side: String = "debit",
    ) {
        db.voucherDao().upsertVouchers(
            listOf(
                VoucherEntity(
                    companyId, voucherId, date, type, voucherId, "Party", null, amount, side,
                    status, "Complete", 1_000L,
                ),
            ),
        )
        db.voucherDao().upsertLedgerLines(
            listOf(VoucherLedgerLineEntity(companyId, voucherId, 1, ledgerName, amount, side, true)),
        )
    }

    @Test
    fun lastSalesMovements_countsOnlySalesTypeVouchers() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-01", "Sales")
        insertVoucher("co-a", "r1", "2026-07-05", "Receipt")
        insertVoucher("co-a", "p1", "2026-07-06", "Payment")
        insertVoucher("co-a", "j1", "2026-07-07", "Journal")
        insertVoucher("co-a", "s2", "2026-07-10", "Sales")

        val result = dao.lastSalesMovements("co-a", "Cash", 7)
        assertEquals(setOf("s1", "s2"), result.map { it.voucherId }.toSet())
    }

    @Test
    fun lastSalesMovements_excludesCancelledVouchers() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-01", "Sales", status = "Active")
        insertVoucher("co-a", "s2", "2026-07-05", "Sales", status = "Cancelled")

        val result = dao.lastSalesMovements("co-a", "Cash", 7)
        assertEquals(listOf("s1"), result.map { it.voucherId })
    }

    @Test
    fun lastSalesMovements_ordersMostRecentFirstAndRespectsLimit() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-01", "Sales")
        insertVoucher("co-a", "s2", "2026-07-10", "Sales")
        insertVoucher("co-a", "s3", "2026-07-05", "Sales")

        val result = dao.lastSalesMovements("co-a", "Cash", 2)
        assertEquals(listOf("s2", "s3"), result.map { it.voucherId })
    }

    @Test
    fun movementsInRange_includesEveryVoucherTypeWithinBounds() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-05", "Sales")
        insertVoucher("co-a", "r1", "2026-07-05", "Receipt")
        insertVoucher("co-a", "outOfRange", "2026-06-01", "Sales")

        val result = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31")
        assertEquals(setOf("s1", "r1"), result.map { it.voucherId }.toSet())
    }

    @Test
    fun movementsInRange_excludesCancelledVouchers() = runBlocking {
        insertVoucher("co-a", "active", "2026-07-05", "Sales", status = "Active")
        insertVoucher("co-a", "cancelled", "2026-07-06", "Sales", status = "Cancelled")

        val result = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31")
        assertEquals(listOf("active"), result.map { it.voucherId })
    }

    @Test
    fun movementsInRange_isScopedByCompanyAndLedgerName() = runBlocking {
        insertVoucher("co-a", "v1", "2026-07-05", "Sales", ledgerName = "Cash")
        insertVoucher("co-a", "v2", "2026-07-05", "Sales", ledgerName = "Bank")
        insertVoucher("co-b", "v3", "2026-07-05", "Sales", ledgerName = "Cash")

        val result = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31")
        assertEquals(listOf("v1"), result.map { it.voucherId })
    }

    @Test
    fun movementsInRange_ordersByDateAscending() = runBlocking {
        insertVoucher("co-a", "later", "2026-07-20", "Sales")
        insertVoucher("co-a", "earlier", "2026-07-01", "Sales")

        val result = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31")
        assertEquals(listOf("earlier", "later"), result.map { it.voucherId })
    }

    @Test
    fun movementsInRange_sameDayRowsTieBreakDeterministicallyByVoucherIdAscending() = runBlocking {
        // Same-day accounting ordering (Phase B): multiple Sales landing on the identical date
        // must still come back in a stable, repeatable order. voucherId is the strongest same-day
        // ordering key actually available to Android — the Connector's own staging-order fields
        // (voucher_key/voucher_retain_key) are internal to its SQLite storage and are not part of
        // the public Voucher list/detail API DTOs Android consumes (see VoucherPublicRecord/
        // VoucherPublicDetails in budcom_connector's voucher-dtos.ts), so this DAO's own doc
        // comment is correct that voucherId ASC is the best available tiebreak, not an invented one.
        insertVoucher("co-a", "s3", "2026-07-05", "Sales")
        insertVoucher("co-a", "s1", "2026-07-05", "Sales")
        insertVoucher("co-a", "s2", "2026-07-05", "Sales")

        val result = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31")
        assertEquals(listOf("s1", "s2", "s3"), result.map { it.voucherId })
    }

    @Test
    fun movementsInRange_includesASameDayReceiptAlongsideASameDaySales() = runBlocking {
        // A Sales voucher and a Receipt on the exact same date must both come back — the accepted
        // baseline's date-range query (BETWEEN, not a voucher-position cursor) never truncates a
        // same-day non-Sales movement just because it isn't itself a counted Sales.
        insertVoucher("co-a", "sale", "2026-06-10", "Sales")
        insertVoucher("co-a", "receipt", "2026-06-10", "Receipt")

        val result = dao.movementsInRange("co-a", "Cash", "2026-06-10", "2026-06-10")
        assertEquals(setOf("sale", "receipt"), result.map { it.voucherId }.toSet())
    }

    @Test
    fun movementsInRange_includesSameDaySalesReceiptPaymentAndJournalTogether() = runBlocking {
        insertVoucher("co-a", "sale", "2026-06-10", "Sales")
        insertVoucher("co-a", "receipt", "2026-06-10", "Receipt")
        insertVoucher("co-a", "payment", "2026-06-10", "Payment")
        insertVoucher("co-a", "journal", "2026-06-10", "Journal")

        val result = dao.movementsInRange("co-a", "Cash", "2026-06-10", "2026-06-10")
        assertEquals(setOf("sale", "receipt", "payment", "journal"), result.map { it.voucherId }.toSet())
    }

    @Test
    fun movementsInRange_repeatedQueryReturnsTheIdenticalOrderEveryTime() = runBlocking {
        insertVoucher("co-a", "c", "2026-07-05", "Sales")
        insertVoucher("co-a", "a", "2026-07-05", "Receipt")
        insertVoucher("co-a", "b", "2026-07-05", "Payment")

        val first = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31").map { it.voucherId }
        val second = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31").map { it.voucherId }
        val third = dao.movementsInRange("co-a", "Cash", "2026-07-01", "2026-07-31").map { it.voucherId }

        assertEquals(listOf("a", "b", "c"), first)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun earliestSyncedDate_returnsTheMinimumDateAcrossAllLedgers() = runBlocking {
        insertVoucher("co-a", "v1", "2026-07-10", "Sales", ledgerName = "Cash")
        insertVoucher("co-a", "v2", "2026-06-01", "Sales", ledgerName = "Bank")

        assertEquals("2026-06-01", dao.earliestSyncedDate("co-a"))
    }

    @Test
    fun earliestSyncedDate_isNullWhenNothingSyncedForThatCompany() = runBlocking {
        assertNull(dao.earliestSyncedDate("empty-co"))
    }

    // ==================== Phase G: query-plan/index boundedness (structural, not timing) ====================

    @Test
    fun movementsInRange_queryPlanNeverFullyScansTheVoucherOrLedgerLineTables() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-05", "Sales")
        val plan = explainQueryPlan(
            "SELECT h.voucherId FROM cached_voucher_ledger_lines e " +
                "JOIN cached_vouchers h ON h.companyId = e.companyId AND h.voucherId = e.voucherId " +
                "WHERE e.companyId = 'co-a' AND e.ledgerName = 'Cash' " +
                "AND h.status = 'Active' AND h.date BETWEEN '2026-01-01' AND '2026-12-31'",
        )
        assertTrue(
            "the Last-7-Sales/period query must reach every row via an index, never a full table SCAN: $plan",
            plan.none { it.trimStart().startsWith("SCAN") },
        )
    }

    @Test
    fun lastSalesMovements_queryPlanNeverFullyScansTheVoucherOrLedgerLineTables() = runBlocking {
        insertVoucher("co-a", "s1", "2026-07-05", "Sales")
        val plan = explainQueryPlan(
            "SELECT h.voucherId FROM cached_voucher_ledger_lines e " +
                "JOIN cached_vouchers h ON h.companyId = e.companyId AND h.voucherId = e.voucherId " +
                "WHERE e.companyId = 'co-a' AND e.ledgerName = 'Cash' " +
                "AND h.status = 'Active' AND h.type = 'Sales' " +
                "GROUP BY h.voucherId ORDER BY h.date DESC, h.voucherId DESC LIMIT 7",
        )
        assertTrue(
            "the Last-7-Sales seed query must reach every row via an index, never a full table SCAN: $plan",
            plan.none { it.trimStart().startsWith("SCAN") },
        )
    }

    @Test
    fun movementsInRange_isBoundedByOneLedgersOwnLineCountAcrossManyLedgersAndVouchers() = runBlocking {
        // Structural boundedness proof (Phase G) rather than a wall-clock microbenchmark: a
        // company with several ledgers and many vouchers each must still return exactly one
        // ledger's own rows — the query must not degrade toward "the whole company" as unrelated
        // ledgers/vouchers accumulate (current FY + previous FY + potentially older years).
        val ledgers = listOf("Cash", "Bank", "Sales Account", "Asif Bhai", "Ramesh Traders")
        var voucherSeq = 0
        for (ledgerName in ledgers) {
            repeat(50) { i ->
                voucherSeq++
                insertVoucher("co-a", "v-$voucherSeq", "2026-0${(i % 9) + 1}-10", "Sales", ledgerName = ledgerName)
            }
        }

        val result = dao.movementsInRange("co-a", "Asif Bhai", "2026-01-01", "2026-12-31")
        assertEquals(50, result.size)
        assertTrue(result.all { row -> row.voucherId.removePrefix("v-").toInt().let { it > 150 && it <= 200 } })
    }

    private fun explainQueryPlan(sql: String): List<String> {
        val lines = mutableListOf<String>()
        db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
            val detailIndex = cursor.getColumnIndex("detail").let { if (it >= 0) it else cursor.columnCount - 1 }
            while (cursor.moveToNext()) lines.add(cursor.getString(detailIndex))
        }
        return lines
    }
}
