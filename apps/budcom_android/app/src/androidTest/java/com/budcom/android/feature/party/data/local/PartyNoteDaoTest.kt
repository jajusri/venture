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

/** Real-Room instrumented coverage for [PartyNoteDao]'s MVP-1.2-A additions (`type`/`dueAt`/
 * `completedAt`/`issueId`) — the pre-1.2-A CRUD/paging surface is already covered indirectly via
 * [com.budcom.android.feature.party.data.repository.PartyRepositoryImplTest], this file targets
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
}
