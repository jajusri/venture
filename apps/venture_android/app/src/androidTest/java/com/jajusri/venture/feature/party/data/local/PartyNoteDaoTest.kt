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

/** Real-Room instrumented coverage for [PartyNoteDao]'s MVP-1.2-A additions (`type`/`dueAt`/
 * `completedAt`/`issueId`) — the pre-1.2-A CRUD/paging surface is already covered indirectly via
 * [com.jajusri.venture.feature.party.data.repository.PartyRepositoryImplTest], this file targets
 * the new columns specifically, following the same real-in-memory-Room setup as [PartyRelatedDaoTest]. */
@RunWith(AndroidJUnit4::class)
class PartyNoteDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var noteDao: PartyNoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        noteDao = db.partyNoteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(
        companyId: String = "co-a",
        noteId: String = "note-1",
        partyId: String = "party-1",
        body: String = "Customer says 2 pieces short",
        type: String = "general",
        dueAt: Long? = null,
        completedAt: Long? = null,
        issueId: String? = null,
    ) = PartyNoteEntity(
        companyId = companyId,
        noteId = noteId,
        partyId = partyId,
        body = body,
        linkedVoucherId = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        type = type,
        dueAt = dueAt,
        completedAt = completedAt,
        issueId = issueId,
    )

    @Test
    fun insertingWithoutSpecifyingNewColumnsUsesTheEntityDefaults() = runBlocking {
        noteDao.upsert(
            PartyNoteEntity(
                companyId = "co-a", noteId = "note-1", partyId = "party-1", body = "Quick remark",
                linkedVoucherId = null, createdAt = 1_000L, updatedAt = 1_000L,
            ),
        )
        val stored = noteDao.findById("co-a", "note-1")
        assertEquals("general", stored?.type)
        assertNull(stored?.dueAt)
        assertNull(stored?.completedAt)
        assertNull(stored?.issueId)
    }

    @Test
    fun aTypedNoteWithDueDateAndIssueRoundTrips() = runBlocking {
        noteDao.upsert(note(type = "follow_up", dueAt = 5_000L, issueId = "issue-1"))
        val stored = noteDao.findById("co-a", "note-1")
        assertEquals("follow_up", stored?.type)
        assertEquals(5_000L, stored?.dueAt)
        assertEquals("issue-1", stored?.issueId)
    }

    @Test
    fun markingANoteCompleteThenReopeningItRoundTrips() = runBlocking {
        noteDao.upsert(note(type = "commitment", dueAt = 5_000L))

        noteDao.upsert(note(type = "commitment", dueAt = 5_000L, completedAt = 6_000L))
        assertEquals(6_000L, noteDao.findById("co-a", "note-1")?.completedAt)

        noteDao.upsert(note(type = "commitment", dueAt = 5_000L, completedAt = null))
        assertNull(noteDao.findById("co-a", "note-1")?.completedAt)
    }

    @Test
    fun typedNotesAreCompanyIsolated() = runBlocking {
        noteDao.upsert(note(companyId = "co-a", noteId = "note-1", type = "complaint"))
        noteDao.upsert(note(companyId = "co-b", noteId = "note-1", type = "general"))

        assertEquals("complaint", noteDao.findById("co-a", "note-1")?.type)
        assertEquals("general", noteDao.findById("co-b", "note-1")?.type)
    }

    @Test
    fun pagingReturnsTheNewColumnsAlongsideExistingOnes() = runBlocking {
        noteDao.upsert(note(noteId = "note-1", type = "payment_issue"))
        val page = noteDao.pageForParty("co-a", "party-1", limit = 20, offset = 0)
        assertTrue(page.isNotEmpty())
        assertEquals("payment_issue", page.first().type)
    }

    // ============================== ISSUE ACTIVITY SUMMARY (MVP-1.2-C) ==============================

    @Test
    fun issueActivitySummaryReportsCountAndLatestTimestampPerIssue() = runBlocking {
        noteDao.upsert(note(noteId = "note-1", issueId = "issue-1").copy(createdAt = 1_000L, updatedAt = 1_000L))
        noteDao.upsert(note(noteId = "note-2", issueId = "issue-1").copy(createdAt = 2_000L, updatedAt = 2_000L))
        noteDao.upsert(note(noteId = "note-3", issueId = "issue-2").copy(createdAt = 3_000L, updatedAt = 3_000L))

        val summary = noteDao.issueActivitySummary("co-a", "party-1").associateBy { it.issueId }

        assertEquals(2, summary.getValue("issue-1").noteCount)
        assertEquals(2_000L, summary.getValue("issue-1").latestNoteAt)
        assertEquals(1, summary.getValue("issue-2").noteCount)
    }

    @Test
    fun issueActivitySummaryExcludesNotesWithNoIssue() = runBlocking {
        noteDao.upsert(note(noteId = "note-1", issueId = null))

        assertTrue(noteDao.issueActivitySummary("co-a", "party-1").isEmpty())
    }

    @Test
    fun issueActivitySummaryIsCompanyIsolated() = runBlocking {
        noteDao.upsert(note(companyId = "co-a", noteId = "note-1", issueId = "issue-1"))
        noteDao.upsert(note(companyId = "co-b", noteId = "note-1", issueId = "issue-1"))

        assertEquals(1, noteDao.issueActivitySummary("co-a", "party-1").single().noteCount)
        assertEquals(1, noteDao.issueActivitySummary("co-b", "party-1").single().noteCount)
    }

    // ============================== DINCHARYA FOLLOW-UPS (MVP-1.2-D) ==============================

    @Test
    fun pageFollowUpsForCompanyReturnsOnlyEligibleFollowUpAndCommitmentNotes() = runBlocking {
        noteDao.upsert(note(noteId = "note-followup", type = "follow_up", dueAt = 5_000L))
        noteDao.upsert(note(noteId = "note-commitment", type = "commitment", dueAt = 6_000L))
        noteDao.upsert(note(noteId = "note-general", type = "general", dueAt = 7_000L)) // wrong type
        noteDao.upsert(note(noteId = "note-no-due", type = "follow_up", dueAt = null)) // no due date
        noteDao.upsert(note(noteId = "note-completed", type = "follow_up", dueAt = 8_000L, completedAt = 9_000L)) // completed

        val page = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)

        assertEquals(2, page.size)
        assertEquals(setOf("note-followup", "note-commitment"), page.map { it.noteId }.toSet())
        assertEquals(2, noteDao.countFollowUpsForCompany("co-a"))
    }

    @Test
    fun followUpsOrderByDueDateAscendingSoOverdueSurfacesBeforeUpcoming() = runBlocking {
        noteDao.upsert(note(noteId = "note-upcoming", type = "follow_up", dueAt = 9_000L))
        noteDao.upsert(note(noteId = "note-overdue", type = "follow_up", dueAt = 1_000L))
        noteDao.upsert(note(noteId = "note-due-today", type = "follow_up", dueAt = 5_000L))

        val page = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)

        assertEquals(listOf("note-overdue", "note-due-today", "note-upcoming"), page.map { it.noteId })
    }

    @Test
    fun followUpsWithASharedDueDateTieBreakDeterministicallyByNoteId() = runBlocking {
        noteDao.upsert(note(noteId = "note-b", type = "follow_up", dueAt = 5_000L))
        noteDao.upsert(note(noteId = "note-a", type = "follow_up", dueAt = 5_000L))

        val page = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)

        assertEquals(listOf("note-a", "note-b"), page.map { it.noteId })
    }

    @Test
    fun aVeryOldOverdueFollowUpNeverAutoExpiresFromTheQuery() = runBlocking {
        // No lower bound on dueAt: a follow-up overdue by years must remain eligible until
        // completed or rescheduled (PDL-018 — no automatic expiry).
        noteDao.upsert(note(noteId = "note-ancient", type = "follow_up", dueAt = 1L))

        val page = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)

        assertEquals(1, page.size)
        assertEquals("note-ancient", page.single().noteId)
    }

    @Test
    fun followUpsForCompanyReturnsEmptyWhenThereAreNoEligibleNotes() = runBlocking {
        noteDao.upsert(note(noteId = "note-general", type = "general"))

        assertTrue(noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0).isEmpty())
        assertEquals(0, noteDao.countFollowUpsForCompany("co-a"))
    }

    @Test
    fun followUpsForCompanyAreCompanyIsolatedEvenWithIdenticalNoteIdsAndDueDates() = runBlocking {
        noteDao.upsert(note(companyId = "co-a", noteId = "note-1", partyId = "party-1", type = "follow_up", dueAt = 5_000L, body = "Company A follow-up"))
        noteDao.upsert(note(companyId = "co-b", noteId = "note-1", partyId = "party-1", type = "follow_up", dueAt = 5_000L, body = "Company B follow-up"))

        val pageA = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)
        val pageB = noteDao.pageFollowUpsForCompany("co-b", limit = 20, offset = 0)

        assertEquals(1, pageA.size)
        assertEquals("Company A follow-up", pageA.single().body)
        assertEquals(1, pageB.size)
        assertEquals("Company B follow-up", pageB.single().body)
        assertEquals(1, noteDao.countFollowUpsForCompany("co-a"))
        assertEquals(1, noteDao.countFollowUpsForCompany("co-b"))
    }

    @Test
    fun pageFollowUpsForCompanyStaysBoundedAndFastWithALargeFixture() = runBlocking {
        // Mirrors PartyDaoTest's own 500-row precedent (architecture §16's performance-proof bar).
        repeat(500) { i ->
            noteDao.upsert(
                note(
                    noteId = "note-$i",
                    partyId = "party-${i % 50}",
                    type = if (i % 2 == 0) "follow_up" else "commitment",
                    dueAt = (i + 1) * 1_000L,
                ),
            )
        }

        var page: List<PartyNoteEntity>
        val elapsed = kotlin.system.measureTimeMillis {
            page = noteDao.pageFollowUpsForCompany("co-a", limit = 20, offset = 0)
        }

        assertEquals(20, page.size)
        assertEquals(500, noteDao.countFollowUpsForCompany("co-a"))
        assertTrue("bounded company-wide follow-up query should stay well under 2s even with 500 rows, took ${elapsed}ms", elapsed < 2000)
        // Lowest dueAt (note-0, 1_000L) must sort first.
        assertEquals("note-0", page.first().noteId)
    }
}
