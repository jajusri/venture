package com.budcom.android.feature.party.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class PartyDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PartyDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.partyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_isCompanyScopedAndReplacesOnSameKey() = runBlocking {
        dao.upsert(party("co-a", "p1", "ABC Traders"))
        dao.upsert(party("co-a", "p1", "ABC Traders Renamed"))
        dao.upsert(party("co-b", "p1", "Different Company Same PartyId"))

        assertEquals(1, dao.countForCompany("co-a"))
        assertEquals(1, dao.countForCompany("co-b"))
        assertEquals("ABC Traders Renamed", dao.findById("co-a", "p1")?.displayName)
        assertEquals("Different Company Same PartyId", dao.findById("co-b", "p1")?.displayName)
    }

    @Test
    fun pageByClassification_filtersAndPagesCorrectly() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p1", "Alpha", classification = "customer"),
                party("co-a", "p2", "Beta", classification = "customer"),
                party("co-a", "p3", "Gamma", classification = "supplier"),
            ),
        )
        val customers = dao.pageByClassification("co-a", "customer", limit = 10, offset = 0)
        assertEquals(2, customers.size)
        assertEquals(2, dao.countByClassification("co-a", "customer"))
        assertEquals(1, dao.countByClassification("co-a", "supplier"))

        val firstPage = dao.pageByClassification("co-a", "customer", limit = 1, offset = 0)
        val secondPage = dao.pageByClassification("co-a", "customer", limit = 1, offset = 1)
        assertEquals(1, firstPage.size)
        assertEquals(1, secondPage.size)
        assertTrue(firstPage.single().partyId != secondPage.single().partyId)
    }

    @Test
    fun search_matchesNameOrNormalizedPhone() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p1", "ABC Traders", phone = "9876543210", phoneNormalized = "9876543210"),
                party("co-a", "p2", "XYZ Suppliers", phone = null, phoneNormalized = null),
            ),
        )
        assertEquals(1, dao.search("co-a", "ABC", 10, 0).size)
        assertEquals(1, dao.search("co-a", "9876543210", 10, 0).size)
        assertEquals(0, dao.search("co-a", "no-match", 10, 0).size)
        assertEquals(1, dao.countSearch("co-a", "ABC"))
    }

    @Test
    fun findById_returnsNullWhenAbsent() = runBlocking {
        assertNull(dao.findById("co-a", "does-not-exist"))
    }

    @Test
    fun pageByClassification_staysFastAndBoundedWithALargeFixture() = runBlocking {
        val large = (1..500).map { i ->
            party("co-perf", "p$i", "Party $i", classification = if (i % 2 == 0) "customer" else "supplier")
        }
        dao.upsertAll(large)

        var page: List<PartyEntity>
        val elapsed = measureTimeMillis {
            page = dao.pageByClassification("co-perf", "customer", limit = 20, offset = 0)
        }
        assertEquals(20, page.size)
        assertEquals(250, dao.countByClassification("co-perf", "customer"))
        assertTrue("bounded paged query should stay well under 2s even with 500 rows, took ${elapsed}ms", elapsed < 2000)
    }

    private fun party(
        companyId: String,
        partyId: String,
        displayName: String,
        classification: String = "customer",
        phone: String? = null,
        phoneNormalized: String? = null,
    ) = PartyEntity(
        companyId = companyId,
        partyId = partyId,
        displayName = displayName,
        classification = classification,
        primaryPhone = phone,
        primaryPhoneNormalized = phoneNormalized,
        primaryEmail = null,
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        gstin = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
