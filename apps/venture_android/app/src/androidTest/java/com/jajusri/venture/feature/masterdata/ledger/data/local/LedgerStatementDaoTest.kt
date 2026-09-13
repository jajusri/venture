package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerStatementDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LedgerStatementDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.ledgerStatementDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun storeStatement_isAtomicAndScopedToCompanyLedgerAndPeriod() = runBlocking {
        dao.storeStatement(
            header("co-a", "ledger-1", "2026-07-01", "2026-07-31"),
            listOf(transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 0, "v-1")),
        )
        dao.storeStatement(
            header("co-a", "ledger-1", "2026-08-01", "2026-08-31"),
            listOf(transaction("co-a", "ledger-1", "2026-08-01", "2026-08-31", 0, "v-2")),
        )

        val julyTransactions = dao.transactions("co-a", "ledger-1", "2026-07-01", "2026-07-31")
        val augustTransactions = dao.transactions("co-a", "ledger-1", "2026-08-01", "2026-08-31")
        assertEquals(listOf("v-1"), julyTransactions.map { it.voucherId })
        assertEquals(listOf("v-2"), augustTransactions.map { it.voucherId })
    }

    @Test
    fun storeStatement_replacesTransactionsRatherThanAccumulatingPhantomRows() = runBlocking {
        dao.storeStatement(
            header("co-a", "ledger-1", "2026-07-01", "2026-07-31"),
            listOf(
                transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 0, "v-1"),
                transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 1, "v-2"),
            ),
        )
        // Same (ledger, period) refreshed with fewer transactions — must not leave v-2 behind.
        dao.storeStatement(
            header("co-a", "ledger-1", "2026-07-01", "2026-07-31"),
            listOf(transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 0, "v-1")),
        )
        val transactions = dao.transactions("co-a", "ledger-1", "2026-07-01", "2026-07-31")
        assertEquals(listOf("v-1"), transactions.map { it.voucherId })
    }

    @Test
    fun statement_returnsNullWhenNothingCachedForThatExactPeriod() = runBlocking {
        dao.storeStatement(header("co-a", "ledger-1", "2026-07-01", "2026-07-31"), emptyList())
        assertNull(dao.statement("co-a", "ledger-1", "2026-06-01", "2026-06-30"))
    }

    @Test
    fun transactions_areOrderedByLineIndex() = runBlocking {
        dao.storeStatement(
            header("co-a", "ledger-1", "2026-07-01", "2026-07-31"),
            listOf(
                transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 2, "v-3"),
                transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 0, "v-1"),
                transaction("co-a", "ledger-1", "2026-07-01", "2026-07-31", 1, "v-2"),
            ),
        )
        val ordered = dao.transactions("co-a", "ledger-1", "2026-07-01", "2026-07-31")
        assertEquals(listOf("v-1", "v-2", "v-3"), ordered.map { it.voucherId })
    }

    private fun header(companyId: String, ledgerId: String, from: String, to: String) = LedgerStatementEntity(
        companyId = companyId,
        ledgerId = ledgerId,
        periodFrom = from,
        periodTo = to,
        ledgerName = "Acme Traders",
        parentGroup = "Sundry Debtors",
        openingAmount = "0",
        openingSide = "Dr",
        closingAmount = "500",
        closingSide = "Dr",
        transactionsComplete = true,
        balanceAvailable = true,
        syncedFrom = from,
        syncedTo = to,
        coverageMessage = null,
        lastSyncedAt = 1_000L,
    )

    private fun transaction(
        companyId: String,
        ledgerId: String,
        from: String,
        to: String,
        lineIndex: Int,
        voucherId: String,
    ) = LedgerStatementTransactionEntity(
        companyId = companyId,
        ledgerId = ledgerId,
        periodFrom = from,
        periodTo = to,
        lineIndex = lineIndex,
        voucherId = voucherId,
        date = "2026-07-0${lineIndex + 1}",
        voucherType = "Sales",
        voucherNumber = "S-00${lineIndex + 1}",
        referenceNumber = null,
        narration = null,
        debit = "100",
        credit = null,
        runningAmount = "100",
        runningSide = "Dr",
    )
}
