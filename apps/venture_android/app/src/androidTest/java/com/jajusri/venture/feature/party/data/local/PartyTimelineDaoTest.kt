package com.jajusri.venture.feature.party.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-Room instrumented coverage for [PartyTimelineDao] — the MVP-1.2-B/C Relationship Timeline
 * merge query. Follows the same real-in-memory-Room setup as [PartyRelatedDaoTest]. */
@RunWith(AndroidJUnit4::class)
class PartyTimelineDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var timelineDao: PartyTimelineDao
    private lateinit var noteDao: PartyNoteDao
    private lateinit var exportEventDao: PartyExportEventDao
    private lateinit var issueDao: PartyIssueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        timelineDao = db.partyTimelineDao()
        noteDao = db.partyNoteDao()
        exportEventDao = db.partyExportEventDao()
        issueDao = db.partyIssueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(
        companyId: String = "co-a",
        noteId: String,
        partyId: String = "party-1",
        body: String = "A note",
        createdAt: Long,
        issueId: String? = null,
    ) = PartyNoteEntity(
        companyId = companyId, noteId = noteId, partyId = partyId, body = body, linkedVoucherId = null,
        createdAt = createdAt, updatedAt = createdAt, type = "general", dueAt = null, completedAt = null, issueId = issueId,
    )

    private fun export(
        companyId: String = "co-a",
        exportId: String,
        partyId: String = "party-1",
        createdAt: Long,
    ) = PartyExportEventEntity(
        companyId = companyId, exportId = exportId, partyId = partyId, createdAt = createdAt,
        outputFileName = "export.xml", fieldNamesCsv = "primaryEmail",
    )

    private fun issue(
        companyId: String = "co-a",
        issueId: String,
        partyId: String = "party-1",
        title: String = "Short shipment",
        status: String = "open",
        createdAt: Long,
        resolvedAt: Long? = null,
        updatedAt: Long = createdAt,
    ) = PartyIssueEntity(companyId, issueId, partyId, title, status, createdAt, resolvedAt, updatedAt)

    @Test
    fun mergesNotesAndExportEventsNewestFirst() = runBlocking {
        noteDao.upsert(note(noteId = "note-1", createdAt = 1_000L))
        exportEventDao.insert(export(exportId = "export-1", createdAt = 2_000L))
        noteDao.upsert(note(noteId = "note-2", createdAt = 3_000L))

        val page = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(listOf("note-2", "export-1", "note-1"), page.map { it.id })
        assertEquals(listOf("note", "export", "note"), page.map { it.kind })
    }

    @Test
    fun sameTimestampEntriesGetADeterministicTieBreakSoPagingNeverDuplicatesOrDrops() = runBlocking {
        // Same-day (here, same-millisecond) events are a realistic case — e.g. a note added right
        // after an export completes. Without a deterministic secondary sort key, paging across
        // such a tie could show the same row twice or skip one entirely.
        noteDao.upsert(note(noteId = "note-a", createdAt = 5_000L))
        noteDao.upsert(note(noteId = "note-b", createdAt = 5_000L))
        exportEventDao.insert(export(exportId = "export-a", createdAt = 5_000L))

        val firstRead = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)
        val secondRead = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(3, firstRead.size)
        assertEquals("repeated reads of a tied timestamp must return the same order", firstRead.map { it.id }, secondRead.map { it.id })
        val page1 = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 2, offset = 0)
        val page2 = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 2, offset = 2)
        assertEquals(3, (page1 + page2).map { it.id }.distinct().size)
    }

    @Test
    fun countMatchesTheCombinedNoteAndExportTotal() = runBlocking {
        noteDao.upsert(note(noteId = "note-1", createdAt = 1_000L))
        noteDao.upsert(note(noteId = "note-2", createdAt = 2_000L))
        exportEventDao.insert(export(exportId = "export-1", createdAt = 3_000L))

        assertEquals(3, timelineDao.countTimelineForParty("co-a", "party-1", issueId = null))
    }

    @Test
    fun anEmptyPartyHasAnEmptyTimeline() = runBlocking {
        assertTrue(timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0).isEmpty())
        assertEquals(0, timelineDao.countTimelineForParty("co-a", "party-1", issueId = null))
    }

    @Test
    fun pagingAcrossTheBoundaryHasNoGapsOrDuplicates() = runBlocking {
        repeat(10) { i -> noteDao.upsert(note(noteId = "note-$i", createdAt = (i + 1) * 1_000L)) }

        val page1 = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 4, offset = 0)
        val page2 = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 4, offset = 4)
        val page3 = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 4, offset = 8)

        val allIds = (page1 + page2 + page3).map { it.id }
        assertEquals(10, allIds.size)
        assertEquals(10, allIds.distinct().size)
        // Newest (note-9, createdAt=10000) must be first; oldest (note-0) must be last.
        assertEquals("note-9", page1.first().id)
        assertEquals("note-0", page3.last().id)
    }

    @Test
    fun filteringByIssueIdExcludesExportEventsAndOtherNotes() = runBlocking {
        noteDao.upsert(note(noteId = "note-issue", createdAt = 1_000L, issueId = "issue-1"))
        noteDao.upsert(note(noteId = "note-other", createdAt = 2_000L, issueId = null))
        exportEventDao.insert(export(exportId = "export-1", createdAt = 3_000L))

        val filtered = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = "issue-1", limit = 20, offset = 0)

        assertEquals(listOf("note-issue"), filtered.map { it.id })
    }

    @Test
    fun timelineNeverLeaksAnotherCompanysNotesOrExportEvents() = runBlocking {
        noteDao.upsert(note(companyId = "co-a", noteId = "note-1", partyId = "party-1", body = "Company A note", createdAt = 1_000L))
        exportEventDao.insert(export(companyId = "co-a", exportId = "export-1", partyId = "party-1", createdAt = 2_000L))
        // Same partyId natural key reused under a different company, with different content.
        noteDao.upsert(note(companyId = "co-b", noteId = "note-2", partyId = "party-1", body = "Company B note", createdAt = 3_000L))

        val timelineA = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)
        val timelineB = timelineDao.pageTimelineForParty("co-b", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(2, timelineA.size)
        assertTrue(timelineA.all { it.companyId == "co-a" })
        assertEquals(1, timelineB.size)
        assertEquals("Company B note", timelineB.single().body)
    }

    @Test
    fun timelineStaysBoundedAndFastWithALargeFixture() = runBlocking {
        // Mirrors PartyDaoTest's own 500-row precedent (architecture §16's performance-proof bar).
        repeat(300) { i -> noteDao.upsert(note(noteId = "note-$i", createdAt = (i + 1) * 1_000L)) }
        repeat(50) { i -> exportEventDao.insert(export(exportId = "export-$i", createdAt = (i + 1) * 1_000L + 500L)) }

        val start = System.nanoTime()
        val page = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(20, page.size)
        assertEquals(350, timelineDao.countTimelineForParty("co-a", "party-1", issueId = null))
        assertTrue("bounded page read over 350 rows took ${elapsedMs}ms, expected well under 2000ms", elapsedMs < 2_000)
        // Newest overall is export-49 (createdAt 50000) vs note-299 (createdAt 300000) — the note wins.
        assertEquals("note-299", page.first().id)
    }

    // ============================== ISSUE LIFECYCLE (MVP-1.2-C) ==============================

    @Test
    fun anOpenIssueProducesExactlyOneIssueOpenedRow() = runBlocking {
        issueDao.upsert(issue(issueId = "issue-1", createdAt = 1_000L, status = "open"))

        val page = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(listOf("issue_opened"), page.map { it.kind })
        assertEquals("issue-1", page.single().id)
        assertEquals(1_000L, page.single().timestamp)
    }

    @Test
    fun aResolvedIssueProducesBothOpenedAndResolvedRows() = runBlocking {
        issueDao.upsert(issue(issueId = "issue-1", createdAt = 1_000L, status = "resolved", resolvedAt = 5_000L))

        val page = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(2, page.size)
        assertEquals(setOf("issue_opened", "issue_resolved"), page.map { it.kind }.toSet())
        val resolvedRow = page.single { it.kind == "issue_resolved" }
        assertEquals(5_000L, resolvedRow.timestamp)
    }

    @Test
    fun countIncludesIssueLifecycleRows() = runBlocking {
        issueDao.upsert(issue(issueId = "issue-1", createdAt = 1_000L, status = "resolved", resolvedAt = 2_000L))
        noteDao.upsert(note(noteId = "note-1", createdAt = 3_000L))

        // 1 note + 1 issue-opened + 1 issue-resolved = 3.
        assertEquals(3, timelineDao.countTimelineForParty("co-a", "party-1", issueId = null))
    }

    @Test
    fun issueFilteredTimelineExcludesTheIssuesOwnLifecycleRows() = runBlocking {
        issueDao.upsert(issue(issueId = "issue-1", createdAt = 1_000L, status = "resolved", resolvedAt = 2_000L))
        noteDao.upsert(note(noteId = "note-1", createdAt = 3_000L, issueId = "issue-1"))

        val filtered = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = "issue-1", limit = 20, offset = 0)

        assertEquals(listOf("note-1"), filtered.map { it.id })
        assertEquals(listOf("note"), filtered.map { it.kind })
    }

    @Test
    fun issueLifecycleRowsNeverLeakAcrossCompanies() = runBlocking {
        issueDao.upsert(issue(companyId = "co-a", issueId = "issue-1", partyId = "party-1", createdAt = 1_000L))
        issueDao.upsert(issue(companyId = "co-b", issueId = "issue-2", partyId = "party-1", createdAt = 2_000L))

        val timelineA = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)
        val timelineB = timelineDao.pageTimelineForParty("co-b", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(listOf("issue-1"), timelineA.map { it.id })
        assertEquals(listOf("issue-2"), timelineB.map { it.id })
    }

    @Test
    fun sameIssueIdOpenedAndResolvedRowsGetADeterministicTieBreakWhenTimestampsCollide() = runBlocking {
        // An issue resolved in the exact same millisecond it was created is an edge case worth
        // proving explicitly, since both rows would otherwise share id AND timestamp.
        issueDao.upsert(issue(issueId = "issue-1", createdAt = 5_000L, status = "resolved", resolvedAt = 5_000L))

        val firstRead = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)
        val secondRead = timelineDao.pageTimelineForParty("co-a", "party-1", issueId = null, limit = 20, offset = 0)

        assertEquals(2, firstRead.size)
        assertEquals(firstRead.map { it.kind }, secondRead.map { it.kind })
    }
}
