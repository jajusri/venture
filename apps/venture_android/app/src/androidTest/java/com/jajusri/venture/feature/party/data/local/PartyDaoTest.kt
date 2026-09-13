package com.jajusri.venture.feature.party.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.database.AppDatabase
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
        assertEquals(1, dao.search("co-a", "ABC", null, 10, 0).size)
        assertEquals(1, dao.search("co-a", "9876543210", null, 10, 0).size)
        assertEquals(0, dao.search("co-a", "no-match", null, 10, 0).size)
        assertEquals(1, dao.countSearch("co-a", "ABC", null))
    }

    @Test
    fun search_classificationFilterNarrowsToOneSection() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p1", "ABC Traders", classification = "customer"),
                party("co-a", "p2", "ABC Supplies", classification = "supplier"),
            ),
        )
        val customerMatches = dao.search("co-a", "ABC", "customer", 10, 0)
        assertEquals(1, customerMatches.size)
        assertEquals("ABC Traders", customerMatches.single().displayName)
        assertEquals(2, dao.search("co-a", "ABC", null, 10, 0).size)
        assertEquals(1, dao.countSearch("co-a", "ABC", "supplier"))
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

    // ============================== DINCHARYA CONTACT COMPLETION (MVP-1.2-D) ==============================

    @Test
    fun pageMissingContactInfoReturnsOnlyPartiesMissingBothValidPhoneAndValidEmail() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p-neither", "Neither", phone = null, phoneNormalized = null, email = null),
                party("co-a", "p-phone-only", "Phone Only", phone = "9876543210", phoneNormalized = "9876543210", email = null),
                party("co-a", "p-email-only", "Email Only", phone = null, phoneNormalized = null, email = "a@b.com"),
                party("co-a", "p-both", "Both", phone = "9876543210", phoneNormalized = "9876543210", email = "a@b.com"),
                party("co-a", "p-blank-email", "Blank Email", phone = null, phoneNormalized = null, email = "   "),
            ),
        )

        val missing = dao.pageMissingContactInfo("co-a", limit = 20, offset = 0)

        assertEquals(setOf("p-neither", "p-blank-email"), missing.map { it.partyId }.toSet())
        assertEquals(2, dao.countMissingContactInfo("co-a"))
    }

    @Test
    fun pageMissingContactInfoExcludesProspects() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p-customer", "Customer With No Contact", classification = "customer"),
                party("co-a", "p-prospect", "Prospect With No Contact", classification = "prospect"),
            ),
        )

        val missing = dao.pageMissingContactInfo("co-a", limit = 20, offset = 0)

        assertEquals(listOf("p-customer"), missing.map { it.partyId })
        assertEquals(1, dao.countMissingContactInfo("co-a"))
    }

    @Test
    fun pageMissingContactInfoOrdersByDisplayNameThenPartyIdForDeterminism() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p2", "Beta"),
                party("co-a", "p1", "Alpha"),
            ),
        )

        assertEquals(listOf("p1", "p2"), dao.pageMissingContactInfo("co-a", limit = 20, offset = 0).map { it.partyId })
    }

    @Test
    fun pageMissingContactInfoIsCompanyIsolatedEvenWithIdenticalNamesAndPhones() = runBlocking {
        dao.upsert(party("co-a", "p1", "ABC Traders", phone = null, phoneNormalized = null, email = null))
        dao.upsert(party("co-b", "p1", "ABC Traders", phone = "9876543210", phoneNormalized = "9876543210", email = "a@b.com"))

        val missingA = dao.pageMissingContactInfo("co-a", limit = 20, offset = 0)
        val missingB = dao.pageMissingContactInfo("co-b", limit = 20, offset = 0)

        assertEquals(1, missingA.size)
        assertTrue(missingB.isEmpty())
        assertEquals(1, dao.countMissingContactInfo("co-a"))
        assertEquals(0, dao.countMissingContactInfo("co-b"))
    }

    @Test
    fun pageMissingContactInfoStaysBoundedAndFastWithALargeFixture() = runBlocking {
        // Mirrors pageByClassification_staysFastAndBoundedWithALargeFixture's own 500-row precedent.
        val large = (1..500).map { i ->
            party(
                "co-perf",
                "p$i",
                "Party $i",
                classification = if (i % 2 == 0) "customer" else "prospect",
                phone = null,
                phoneNormalized = null,
                email = null,
            )
        }
        dao.upsertAll(large)

        var page: List<PartyEntity>
        val elapsed = measureTimeMillis {
            page = dao.pageMissingContactInfo("co-perf", limit = 20, offset = 0)
        }
        assertEquals(20, page.size)
        // Only the 250 non-prospect (customer) rows are eligible.
        assertEquals(250, dao.countMissingContactInfo("co-perf"))
        assertTrue("bounded company-wide contact-completion query should stay well under 2s even with 500 rows, took ${elapsed}ms", elapsed < 2000)
    }

    @Test
    fun findByIdsReturnsOnlyTheRequestedIdsWithinTheGivenCompany() = runBlocking {
        dao.upsertAll(
            listOf(
                party("co-a", "p1", "Alpha"),
                party("co-a", "p2", "Beta"),
                party("co-a", "p3", "Gamma"),
                party("co-b", "p1", "Different Company Alpha"),
            ),
        )

        val found = dao.findByIds("co-a", listOf("p1", "p3", "does-not-exist"))

        assertEquals(setOf("p1", "p3"), found.map { it.partyId }.toSet())
        assertTrue(found.none { it.companyId == "co-b" })
    }

    private fun party(
        companyId: String,
        partyId: String,
        displayName: String,
        classification: String = "customer",
        phone: String? = null,
        phoneNormalized: String? = null,
        email: String? = null,
    ) = PartyEntity(
        companyId = companyId,
        partyId = partyId,
        displayName = displayName,
        classification = classification,
        primaryPhone = phone,
        primaryPhoneNormalized = phoneNormalized,
        primaryEmail = email,
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        gstin = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
