package com.budcom.android.feature.party.data.local

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

@RunWith(AndroidJUnit4::class)
class TagDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TagDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.tagDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun hierarchy_parentChildRelationshipsAreQueryable() = runBlocking {
        dao.upsert(tag("ap", null, "AP", "AP"))
        dao.upsert(tag("rayalaseema", "ap", "Rayalaseema", "AP/Rayalaseema"))
        dao.upsert(tag("chittoor", "rayalaseema", "Chittoor", "AP/Rayalaseema/Chittoor"))
        dao.upsert(tag("tirupati", "chittoor", "Tirupati", "AP/Rayalaseema/Chittoor/Tirupati"))

        assertEquals(listOf("Rayalaseema"), dao.findChildren("ap").map { it.name })
        assertEquals(listOf("Chittoor"), dao.findChildren("rayalaseema").map { it.name })
        assertEquals("AP/Rayalaseema/Chittoor/Tirupati", dao.findById("tirupati")?.path)
    }

    @Test
    fun manyToManyAssignment_onePartyCanHaveMultipleTagsAndOneTagMultipleParties() = runBlocking {
        dao.upsert(tag("dealer", null, "Dealer", "Dealer"))
        dao.upsert(tag("tirupati", null, "Tirupati", "Tirupati"))

        dao.assign(assignment("co-a", "party-1", "dealer"))
        dao.assign(assignment("co-a", "party-1", "tirupati"))
        dao.assign(assignment("co-a", "party-2", "dealer"))

        assertEquals(setOf("Dealer", "Tirupati"), dao.findTagsForParty("co-a", "party-1").map { it.name }.toSet())
        assertEquals(setOf("party-1", "party-2"), dao.findPartyIdsForTag("co-a", "dealer").toSet())
    }

    @Test
    fun assignment_isCompanyAndPartyScoped() = runBlocking {
        dao.upsert(tag("dealer", null, "Dealer", "Dealer"))
        dao.assign(assignment("co-a", "party-1", "dealer"))
        dao.assign(assignment("co-b", "party-1", "dealer"))

        assertEquals(1, dao.findTagsForParty("co-a", "party-1").size)
        assertEquals(1, dao.findTagsForParty("co-b", "party-1").size)
        assertTrue(dao.findPartyIdsForTag("co-a", "dealer").contains("party-1"))
    }

    @Test
    fun findTagsForCompany_bulkReadStaysFastAndBoundedWithALargeFixture() = runBlocking {
        dao.upsert(tag("dealer", null, "Dealer", "Dealer"))
        (1..500).forEach { i -> dao.assign(assignment("co-perf", "party-$i", "dealer")) }

        var result: List<PartyTagAssignmentRow>
        val elapsed = kotlin.system.measureTimeMillis {
            result = dao.findTagsForCompany("co-perf")
        }
        assertEquals(500, result.size)
        assertTrue("bulk company-wide tag read (used once per Connect list load, never per row) should stay well under 2s even with 500 assignments, took ${elapsed}ms", elapsed < 2000)
    }

    @Test
    fun unassign_removesOnlyTheTargetedAssignment() = runBlocking {
        dao.upsert(tag("dealer", null, "Dealer", "Dealer"))
        dao.upsert(tag("retailer", null, "Retailer", "Retailer"))
        dao.assign(assignment("co-a", "party-1", "dealer"))
        dao.assign(assignment("co-a", "party-1", "retailer"))

        dao.unassign("co-a", "party-1", "dealer")

        assertEquals(listOf("Retailer"), dao.findTagsForParty("co-a", "party-1").map { it.name })
    }

    private fun tag(tagId: String, parentTagId: String?, name: String, path: String) =
        TagEntity(tagId = tagId, parentTagId = parentTagId, name = name, path = path, createdAt = 1_000L)

    private fun assignment(companyId: String, partyId: String, tagId: String) =
        PartyTagCrossRefEntity(companyId = companyId, partyId = partyId, tagId = tagId, assignedAt = 1_000L)
}
