package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LedgerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.ledgerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun replaceAllForCompany_isAtomicAndCompanyScoped() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("co-a", "l1", "Cash"),
                entity("co-b", "l2", "Bank"),
            ),
        )
        dao.replaceAllForCompany(
            companyId = "co-a",
            entities = listOf(entity("co-a", "l3", "Debtors")),
        )
        assertEquals(1, dao.countForCompany("co-a"))
        assertEquals(1, dao.countForCompany("co-b"))
        assertEquals("Debtors", dao.queryPage("co-a", null, "name", 1, 10, 0, 0).single().name)
    }

    @Test
    fun queryPage_filtersByName() = runBlocking {
        dao.replaceAllForCompany(
            "co-a",
            listOf(
                entity("co-a", "1", "Cash"),
                entity("co-a", "2", "Bank OD"),
                entity("co-a", "3", "Petty Cash"),
            ),
        )
        val page = dao.queryPage("co-a", "cash", "name", 1, 10, 0, 0)
        assertEquals(2, page.size)
    }

    /**
     * Part B (Connect Alias Intelligence): a 1-5 digit numeric query is a ledger-lookup shortcut,
     * not a substring search -- an exact Alias match must be deterministically ranked first, even
     * though several other ledgers' names/aliases also contain "25" as a substring.
     */
    @Test
    fun queryPage_exactAliasFirst_ranksExactMatchAheadOfSubstringMatches() = runBlocking {
        dao.replaceAllForCompany(
            "co-a",
            listOf(
                entity("co-a", "1", "AZ Traders 250", alias = "1250"),
                entity("co-a", "2", "The 25 Corner Store", alias = null),
                entity("co-a", "3", "Shortcut Target", alias = "25"),
            ),
        )
        val page = dao.queryPage("co-a", "25", "name", 1, 10, 0, exactAliasFirst = 1)
        assertEquals(3, page.size)
        assertEquals("Shortcut Target", page.first().name)
    }

    @Test
    fun queryPage_exactAliasFirst_zero_leavesNameOrderingUnchanged() = runBlocking {
        dao.replaceAllForCompany(
            "co-a",
            listOf(
                entity("co-a", "1", "AZ Traders 250", alias = "1250"),
                entity("co-a", "2", "Shortcut Target", alias = "25"),
            ),
        )
        val page = dao.queryPage("co-a", "25", "name", 1, 10, 0, exactAliasFirst = 0)
        assertEquals(listOf("AZ Traders 250", "Shortcut Target"), page.map { it.name })
    }

    private fun entity(companyId: String, id: String, name: String, alias: String? = null) = LedgerEntity(
        companyId = companyId,
        id = id,
        name = name,
        alias = alias,
        parentGroup = null,
        status = "active",
        closingAmount = null,
        closingCurrencyCode = null,
        closingSide = null,
        dataQuality = "complete",
        syncedAt = "t",
        dataFreshnessAt = "t",
    )
}
