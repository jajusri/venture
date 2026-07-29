package com.budcom.android.feature.masterdata.ledger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.database.AppDatabase
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
        assertEquals("Debtors", dao.queryPage("co-a", null, "name", 1, 10, 0).single().name)
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
        val page = dao.queryPage("co-a", "cash", "name", 1, 10, 0)
        assertEquals(2, page.size)
    }

    private fun entity(companyId: String, id: String, name: String) = LedgerEntity(
        companyId = companyId,
        id = id,
        name = name,
        alias = null,
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
