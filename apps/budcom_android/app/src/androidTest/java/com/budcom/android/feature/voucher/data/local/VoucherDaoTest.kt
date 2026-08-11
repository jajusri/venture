package com.budcom.android.feature.voucher.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct Room-level coverage for [VoucherDao.storeList]'s scope-bounded windowed-refresh
 * replacement (BUDCOM MVP-1 permanent voucher snapshot architecture, Section 3): a real SQLite
 * database, not the repository-level fake, proves the actual SQL prune/upsert logic.
 */
@RunWith(AndroidJUnit4::class)
class VoucherDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: VoucherDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.voucherDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun storeList_prunesStaleVouchersInScope_butKeepsThemOutsideScope() = runBlocking {
        dao.storeList(
            "co-a",
            listOf(
                entity("co-a", "v-1", "2026-07-10"),
                entity("co-a", "v-2", "2026-07-15"),
                entity("co-a", "v-outside", "2026-06-01"),
            ),
            1_000L,
            "2026-01-01",
            "2026-12-31",
        )

        // Resync of the same scope: v-2 is gone from Tally (cancelled), v-3 is new.
        dao.storeList(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10"), entity("co-a", "v-3", "2026-07-20")),
            2_000L,
            "2026-07-01",
            "2026-07-27",
        )

        val remaining = dao.vouchers("co-a").map { it.voucherId }.toSet()
        assertEquals(setOf("v-1", "v-3", "v-outside"), remaining)
    }

    @Test
    fun storeList_neverPrunesAnotherCompany() = runBlocking {
        dao.storeList("co-a", listOf(entity("co-a", "v-1", "2026-07-10")), 1_000L, "2026-01-01", "2026-12-31")
        dao.storeList("co-b", listOf(entity("co-b", "v-2", "2026-07-10")), 1_000L, "2026-01-01", "2026-12-31")

        dao.storeList("co-a", emptyList(), 2_000L, "2026-01-01", "2026-12-31")

        assertEquals(0, dao.vouchers("co-a").size)
        assertEquals(1, dao.vouchers("co-b").size)
    }

    @Test
    fun storeList_changedDerivedVoucherIdLeavesNoPhantomAfterScopeReplacement() = runBlocking {
        // Simulates an edited voucher whose derived identity changed between syncs — the old
        // representation must disappear once its scope is replaced, per the "scope replacement,
        // not identity stability" correctness model (Section 4).
        dao.storeList("co-a", listOf(entity("co-a", "v-old-identity", "2026-07-10")), 1_000L, "2026-07-01", "2026-07-27")

        dao.storeList("co-a", listOf(entity("co-a", "v-new-identity", "2026-07-10")), 2_000L, "2026-07-01", "2026-07-27")

        val remaining = dao.vouchers("co-a").map { it.voucherId }
        assertEquals(listOf("v-new-identity"), remaining)
    }

    @Test
    fun storeList_prunesDependentDetailAndLineRowsForRemovedVouchers() = runBlocking {
        dao.storeList("co-a", listOf(entity("co-a", "v-1", "2026-07-10")), 1_000L, "2026-07-01", "2026-07-27")
        dao.storeDetails(
            entity("co-a", "v-1", "2026-07-10"),
            VoucherDetailEntity("co-a", "v-1", "2026-07-10", "note", 1_000L),
            listOf(VoucherLedgerLineEntity("co-a", "v-1", 1, "Cash", "100", "Debit", null)),
            listOf(VoucherInventoryLineEntity("co-a", "v-1", 1, "Item", "1", "100", "100", "Debit")),
        )

        // v-1 is cancelled: absent from the next authoritative window refresh.
        dao.storeList("co-a", emptyList(), 2_000L, "2026-07-01", "2026-07-27")

        assertEquals(null, dao.detail("co-a", "v-1"))
        assertTrue(dao.ledgerLines("co-a", "v-1").isEmpty())
        assertTrue(dao.inventoryLines("co-a", "v-1").isEmpty())
    }

    @Test
    fun storeList_emptyResultForScopeClearsEverythingInThatScopeOnly() = runBlocking {
        dao.storeList(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10"), entity("co-a", "v-2", "2026-06-01")),
            1_000L,
            "2026-01-01",
            "2026-12-31",
        )

        dao.storeList("co-a", emptyList(), 2_000L, "2026-07-01", "2026-07-27")

        val remaining = dao.vouchers("co-a").map { it.voucherId }
        assertEquals(listOf("v-2"), remaining)
    }

    @Test
    fun storeListWithDetails_persistsSummaryAndFullDetailAtomicallyInOneWindowSync() = runBlocking {
        dao.storeListWithDetails(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10")),
            listOf(VoucherDetailEntity("co-a", "v-1", "2026-07-10", "delivered by noon", 1_000L)),
            listOf(VoucherLedgerLineEntity("co-a", "v-1", 1, "Cash", "100", "Debit", null)),
            listOf(VoucherInventoryLineEntity("co-a", "v-1", 1, "Item", "1", "100", "100", "Debit")),
            1_000L,
            "2026-07-01",
            "2026-07-27",
        )

        // Immediately queryable — no second per-Voucher download needed for list, detail,
        // ledger, or inventory data.
        assertEquals(listOf("v-1"), dao.vouchers("co-a").map { it.voucherId })
        assertEquals("delivered by noon", dao.detail("co-a", "v-1")?.narration)
        assertEquals(1, dao.ledgerLines("co-a", "v-1").size)
        assertEquals(1, dao.inventoryLines("co-a", "v-1").size)
    }

    @Test
    fun storeListWithDetails_shrunkenLineCountLeavesNoPhantomTrailingLines() = runBlocking {
        dao.storeListWithDetails(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10")),
            listOf(VoucherDetailEntity("co-a", "v-1", "2026-07-10", null, 1_000L)),
            listOf(
                VoucherLedgerLineEntity("co-a", "v-1", 1, "Cash", "100", "Debit", null),
                VoucherLedgerLineEntity("co-a", "v-1", 2, "Sales", "100", "Credit", null),
            ),
            listOf(
                VoucherInventoryLineEntity("co-a", "v-1", 1, "Item A", "1", "50", "50", "Debit"),
                VoucherInventoryLineEntity("co-a", "v-1", 2, "Item B", "1", "50", "50", "Debit"),
            ),
            1_000L,
            "2026-07-01",
            "2026-07-27",
        )

        // Resynced with fewer lines than before (an edited Voucher) — the old line 2s must not
        // survive as phantom rows.
        dao.storeListWithDetails(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10")),
            listOf(VoucherDetailEntity("co-a", "v-1", "2026-07-10", null, 2_000L)),
            listOf(VoucherLedgerLineEntity("co-a", "v-1", 1, "Cash", "100", "Debit", null)),
            listOf(VoucherInventoryLineEntity("co-a", "v-1", 1, "Item A", "1", "100", "100", "Debit")),
            2_000L,
            "2026-07-01",
            "2026-07-27",
        )

        assertEquals(1, dao.ledgerLines("co-a", "v-1").size)
        assertEquals(1, dao.inventoryLines("co-a", "v-1").size)
    }

    @Test
    fun storeListWithDetails_prunesStaleVoucherAndItsDetailLinesTogether() = runBlocking {
        dao.storeListWithDetails(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10")),
            listOf(VoucherDetailEntity("co-a", "v-1", "2026-07-10", null, 1_000L)),
            listOf(VoucherLedgerLineEntity("co-a", "v-1", 1, "Cash", "100", "Debit", null)),
            emptyList(),
            1_000L,
            "2026-07-01",
            "2026-07-27",
        )

        // v-1 cancelled: absent from the next authoritative offline-complete window refresh.
        dao.storeListWithDetails("co-a", emptyList(), emptyList(), emptyList(), emptyList(), 2_000L, "2026-07-01", "2026-07-27")

        assertEquals(null, dao.detail("co-a", "v-1"))
        assertTrue(dao.ledgerLines("co-a", "v-1").isEmpty())
    }

    @Test
    fun storeListWithDetails_neverTouchesAnotherCompany() = runBlocking {
        dao.storeListWithDetails(
            "co-a",
            listOf(entity("co-a", "v-1", "2026-07-10")),
            listOf(VoucherDetailEntity("co-a", "v-1", "2026-07-10", null, 1_000L)),
            emptyList(),
            emptyList(),
            1_000L,
            "2026-01-01",
            "2026-12-31",
        )
        dao.storeListWithDetails(
            "co-b",
            listOf(entity("co-b", "v-2", "2026-07-10")),
            listOf(VoucherDetailEntity("co-b", "v-2", "2026-07-10", null, 1_000L)),
            emptyList(),
            emptyList(),
            1_000L,
            "2026-01-01",
            "2026-12-31",
        )

        dao.storeListWithDetails("co-a", emptyList(), emptyList(), emptyList(), emptyList(), 2_000L, "2026-01-01", "2026-12-31")

        assertEquals(0, dao.vouchers("co-a").size)
        assertEquals(1, dao.vouchers("co-b").size)
        assertEquals("2026-07-10", dao.detail("co-b", "v-2")?.effectiveDate)
    }

    private fun entity(companyId: String, voucherId: String, date: String) = VoucherEntity(
        companyId = companyId,
        voucherId = voucherId,
        date = date,
        type = "Sales",
        number = "N-$voucherId",
        partyName = "Party",
        referenceNumber = null,
        amountValue = "100",
        amountSide = "Debit",
        status = "Active",
        dataQuality = "Complete",
        lastSyncedAt = 0L,
    )
}
