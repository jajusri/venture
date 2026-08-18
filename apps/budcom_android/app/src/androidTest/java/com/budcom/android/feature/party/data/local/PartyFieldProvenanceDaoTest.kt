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

/** Real-Room instrumented coverage for [PartyFieldProvenanceDao] — this file did not exist before
 * MVP-1.2-D, which is the first milestone to add a company-wide (cross-party) query to this DAO
 * (Dincharya Type B, Pending Tally Confirmation). Follows the same real-in-memory-Room setup as
 * [PartyDaoTest]/[PartyNoteDaoTest]. */
@RunWith(AndroidJUnit4::class)
class PartyFieldProvenanceDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PartyFieldProvenanceDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.partyFieldProvenanceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun field(
        companyId: String = "co-a",
        partyId: String = "party-1",
        fieldName: String = "primaryPhone",
        state: String = "exported",
        tallyValue: String? = null,
        budcomValue: String? = null,
        lastConfirmedAt: Long? = null,
        lastExportedAt: Long? = 1_000L,
        updatedAt: Long = 1_000L,
    ) = PartyFieldProvenanceEntity(
        companyId = companyId,
        partyId = partyId,
        fieldName = fieldName,
        state = state,
        tallyValue = tallyValue,
        budcomValue = budcomValue,
        lastConfirmedAt = lastConfirmedAt,
        lastExportedAt = lastExportedAt,
        updatedAt = updatedAt,
    )

    // ============================== BASIC CRUD ==============================

    @Test
    fun upsertThenFindFieldRoundTrips() = runBlocking {
        dao.upsert(field(fieldName = "primaryEmail", state = "confirmed_from_tally", tallyValue = "a@b.com"))

        val stored = dao.findField("co-a", "party-1", "primaryEmail")

        assertEquals("confirmed_from_tally", stored?.state)
        assertEquals("a@b.com", stored?.tallyValue)
    }

    @Test
    fun findFieldReturnsNullWhenAbsent() = runBlocking {
        assertNull(dao.findField("co-a", "party-1", "primaryPhone"))
    }

    @Test
    fun findAllForPartyReturnsEveryTrackedFieldForThatPartyOnly() = runBlocking {
        dao.upsert(field(partyId = "party-1", fieldName = "primaryPhone"))
        dao.upsert(field(partyId = "party-1", fieldName = "primaryEmail"))
        dao.upsert(field(partyId = "party-2", fieldName = "primaryPhone"))

        assertEquals(2, dao.findAllForParty("co-a", "party-1").size)
        assertEquals(1, dao.findAllForParty("co-a", "party-2").size)
    }

    // ============================== DINCHARYA PENDING TALLY CONFIRMATION (MVP-1.2-D) ==============================

    @Test
    fun pagePendingConfirmationForCompanyReturnsOnlyExportedStateGroupedByParty() = runBlocking {
        dao.upsert(field(partyId = "party-1", fieldName = "primaryPhone", state = "exported"))
        dao.upsert(field(partyId = "party-1", fieldName = "primaryEmail", state = "exported"))
        dao.upsert(field(partyId = "party-2", fieldName = "primaryPhone", state = "confirmed_from_tally"))
        dao.upsert(field(partyId = "party-3", fieldName = "primaryPhone", state = "budcom_only_pending"))

        val page = dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0)

        assertEquals(1, page.size)
        val row = page.single()
        assertEquals("party-1", row.partyId)
        assertEquals(setOf("primaryPhone", "primaryEmail"), row.fieldNamesCsv.split(",").toSet())
        assertEquals(1, dao.countPendingConfirmationForCompany("co-a"))
    }

    @Test
    fun pagePendingConfirmationForCompanyNeverProducesMoreThanOneRowPerParty() = runBlocking {
        // A Party with several exported-pending fields must surface as one Dincharya item, not
        // several near-duplicate-looking rows for the same Party.
        dao.upsert(field(partyId = "party-1", fieldName = "primaryPhone", state = "exported"))
        dao.upsert(field(partyId = "party-1", fieldName = "primaryEmail", state = "exported"))
        dao.upsert(field(partyId = "party-1", fieldName = "gstin", state = "exported"))

        val page = dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0)

        assertEquals(1, page.size)
        assertEquals(3, page.single().fieldNamesCsv.split(",").size)
    }

    @Test
    fun pendingConfirmationOrdersByEarliestExportedAtAscending() = runBlocking {
        dao.upsert(field(partyId = "party-late", fieldName = "primaryPhone", state = "exported", lastExportedAt = 9_000L, updatedAt = 9_000L))
        dao.upsert(field(partyId = "party-early", fieldName = "primaryPhone", state = "exported", lastExportedAt = 1_000L, updatedAt = 1_000L))

        val page = dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0)

        assertEquals(listOf("party-early", "party-late"), page.map { it.partyId })
    }

    @Test
    fun pendingConfirmationClearsOnceEveryFieldLeavesExportedState() = runBlocking {
        dao.upsert(field(partyId = "party-1", fieldName = "primaryPhone", state = "exported"))
        assertEquals(1, dao.countPendingConfirmationForCompany("co-a"))

        // A later Tally re-sync confirms the field — the same natural key (companyId, partyId,
        // fieldName), REPLACE semantics, exactly like PartyRepositoryImpl.confirmFieldFromTally.
        dao.upsert(field(partyId = "party-1", fieldName = "primaryPhone", state = "confirmed_from_tally"))

        assertEquals(0, dao.countPendingConfirmationForCompany("co-a"))
        assertTrue(dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0).isEmpty())
    }

    @Test
    fun pendingConfirmationForCompanyIsCompanyIsolatedEvenWithIdenticalPartyIds() = runBlocking {
        dao.upsert(field(companyId = "co-a", partyId = "party-1", fieldName = "primaryPhone", state = "exported"))
        dao.upsert(field(companyId = "co-b", partyId = "party-1", fieldName = "primaryEmail", state = "exported"))

        val pageA = dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0)
        val pageB = dao.pagePendingConfirmationForCompany("co-b", limit = 20, offset = 0)

        assertEquals(1, pageA.size)
        assertEquals("primaryPhone", pageA.single().fieldNamesCsv)
        assertEquals(1, pageB.size)
        assertEquals("primaryEmail", pageB.single().fieldNamesCsv)
        assertEquals(1, dao.countPendingConfirmationForCompany("co-a"))
        assertEquals(1, dao.countPendingConfirmationForCompany("co-b"))
    }

    @Test
    fun pagePendingConfirmationForCompanyStaysBoundedAndFastWithALargeFixture() = runBlocking {
        // Mirrors PartyDaoTest's own 500-row precedent (architecture §16's performance-proof bar).
        repeat(500) { i ->
            dao.upsert(
                field(
                    partyId = "party-$i",
                    fieldName = "primaryPhone",
                    state = "exported",
                    lastExportedAt = (i + 1) * 1_000L,
                    updatedAt = (i + 1) * 1_000L,
                ),
            )
        }

        var page: List<PendingConfirmationRow>
        val elapsed = measureTimeMillis {
            page = dao.pagePendingConfirmationForCompany("co-a", limit = 20, offset = 0)
        }

        assertEquals(20, page.size)
        assertEquals(500, dao.countPendingConfirmationForCompany("co-a"))
        assertTrue(
            "bounded company-wide pending-confirmation query should stay well under 2s even with 500 rows, took ${elapsed}ms",
            elapsed < 2000,
        )
        assertEquals("party-0", page.first().partyId)
    }
}
