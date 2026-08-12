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
    fun earliestSyncedDate_returnsTheMinimumDateAcrossAllLedgers() = runBlocking {
        insertVoucher("co-a", "v1", "2026-07-10", "Sales", ledgerName = "Cash")
        insertVoucher("co-a", "v2", "2026-06-01", "Sales", ledgerName = "Bank")

        assertEquals("2026-06-01", dao.earliestSyncedDate("co-a"))
    }

    @Test
    fun earliestSyncedDate_isNullWhenNothingSyncedForThatCompany() = runBlocking {
        assertNull(dao.earliestSyncedDate("empty-co"))
    }
}
