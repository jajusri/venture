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

/** Real-Room instrumented coverage for the new MVP-1.2-A [PartyIssueDao], following the same
 * real-in-memory-Room setup as [PartyRelatedDaoTest]. */
@RunWith(AndroidJUnit4::class)
class PartyIssueDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var issueDao: PartyIssueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        issueDao = db.partyIssueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun issue(
        companyId: String = "co-a",
        issueId: String = "issue-1",
        partyId: String = "party-1",
        title: String = "2 pieces short — Sales Voucher #1842",
        status: String = "open",
        createdAt: Long = 1_000L,
        resolvedAt: Long? = null,
        updatedAt: Long = 1_000L,
    ) = PartyIssueEntity(companyId, issueId, partyId, title, status, createdAt, resolvedAt, updatedAt)

    @Test
    fun anInsertedIssueIsFindableById() = runBlocking {
        issueDao.upsert(issue())
        val stored = issueDao.findById("co-a", "issue-1")
        assertEquals("2 pieces short — Sales Voucher #1842", stored?.title)
        assertEquals("open", stored?.status)
        assertNull(stored?.resolvedAt)
    }

    @Test
    fun resolvingAnIssueUpdatesStatusAndResolvedAt() = runBlocking {
        issueDao.upsert(issue())
        issueDao.upsert(issue(status = "resolved", resolvedAt = 3_000L, updatedAt = 3_000L))

        val stored = issueDao.findById("co-a", "issue-1")
        assertEquals("resolved", stored?.status)
        assertEquals(3_000L, stored?.resolvedAt)
    }

    @Test
    fun openIssuesSortBeforeResolvedIssues() = runBlocking {
        issueDao.upsert(issue(issueId = "resolved-1", status = "resolved", createdAt = 5_000L))
        issueDao.upsert(issue(issueId = "open-1", status = "open", createdAt = 1_000L))

        val issues = issueDao.findAllForParty("co-a", "party-1")
        assertEquals(listOf("open-1", "resolved-1"), issues.map { it.issueId })
    }

    @Test
    fun issuesAreCompanyIsolated() = runBlocking {
        issueDao.upsert(issue(companyId = "co-a", issueId = "issue-1"))
        issueDao.upsert(issue(companyId = "co-b", issueId = "issue-1", title = "Different company's issue"))

        assertEquals("2 pieces short — Sales Voucher #1842", issueDao.findById("co-a", "issue-1")?.title)
        assertEquals("Different company's issue", issueDao.findById("co-b", "issue-1")?.title)
    }

    @Test
    fun findAllForPartyNeverReturnsAnotherPartysIssues() = runBlocking {
        issueDao.upsert(issue(partyId = "party-1", issueId = "issue-1"))
        issueDao.upsert(issue(partyId = "party-2", issueId = "issue-2"))

        val issues = issueDao.findAllForParty("co-a", "party-1")
        assertTrue(issues.all { it.partyId == "party-1" })
        assertEquals(1, issues.size)
    }
}
