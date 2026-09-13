package com.jajusri.venture.feature.dincharya.data.repository

import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaItem
import com.jajusri.venture.feature.dincharya.domain.model.FollowUpUrgency
import com.jajusri.venture.feature.party.data.local.PartyDao
import com.jajusri.venture.feature.party.data.local.PartyEntity
import com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceDao
import com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceEntity
import com.jajusri.venture.feature.party.data.local.PartyNoteDao
import com.jajusri.venture.feature.party.data.local.PartyNoteEntity
import com.jajusri.venture.feature.party.data.local.PendingConfirmationRow
import com.jajusri.venture.feature.party.data.local.IssueActivityRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 86_400_000L
// "Now" is pinned to the start of day 10 — day 5 is overdue, day 10 is due-today, day 15 is upcoming.
private const val NOW = 10 * DAY_MS

class DincharyaRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val time = TimeProvider { NOW }
    private val partyDao = FakePartyDao()
    private val noteDao = FakePartyNoteDao()
    private val fieldProvenanceDao = FakePartyFieldProvenanceDao()

    private fun repository() = DincharyaRepositoryImpl(partyDao, noteDao, fieldProvenanceDao, time, dispatchers)

    // ============================== FOLLOW-UPS ==============================

    @Test
    fun `follow-ups resolve display names and classify urgency relative to now`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "ABC Traders"))
        noteDao.notes += note("co-a", "n1", "p1", type = "follow_up", dueAt = 5 * DAY_MS, body = "Call back about payment")

        val group = repository().getFollowUps("co-a", limit = 20)

        val item = group.items.single()
        assertEquals("ABC Traders", item.partyDisplayName)
        assertEquals(FollowUpUrgency.Overdue, item.urgency)
    }

    @Test
    fun `follow-up urgency covers overdue, due today, and upcoming`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "Party One"))
        noteDao.notes += listOf(
            note("co-a", "n-overdue", "p1", type = "follow_up", dueAt = 5 * DAY_MS),
            note("co-a", "n-today", "p1", type = "follow_up", dueAt = 10 * DAY_MS),
            note("co-a", "n-upcoming", "p1", type = "follow_up", dueAt = 15 * DAY_MS),
        )

        val byId = repository().getFollowUps("co-a", limit = 20).items.associateBy { it.noteId }

        assertEquals(FollowUpUrgency.Overdue, byId.getValue("n-overdue").urgency)
        assertEquals(FollowUpUrgency.DueToday, byId.getValue("n-today").urgency)
        assertEquals(FollowUpUrgency.Upcoming, byId.getValue("n-upcoming").urgency)
    }

    @Test
    fun `follow-up display name falls back to the raw partyId rather than dropping the item when the Party lookup misses`() = runTest(dispatcher) {
        // Defensive path only — Parties are never deleted in this codebase, so this should not
        // occur in practice, but a genuine follow-up must never silently vanish from Dincharya.
        noteDao.notes += note("co-a", "n1", "missing-party", type = "follow_up", dueAt = 5 * DAY_MS)

        val item = repository().getFollowUps("co-a", limit = 20).items.single()

        assertEquals("missing-party", item.partyDisplayName)
    }

    @Test
    fun `follow-up group reports moreCount when the true total exceeds the requested limit`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "Party One"))
        noteDao.notes += (1..5).map { i -> note("co-a", "n$i", "p1", type = "follow_up", dueAt = i * DAY_MS) }
        noteDao.totalOverride = 5

        val group = repository().getFollowUps("co-a", limit = 2)

        assertEquals(2, group.items.size)
        assertEquals(5, group.totalItems)
        assertEquals(3, group.moreCount)
    }

    @Test
    fun `follow-ups are company isolated end to end through the repository`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "ABC Traders"))
        partyDao.upsert(party("co-b", "p1", "ABC Traders"))
        noteDao.notes += note("co-a", "n1", "p1", type = "follow_up", dueAt = 5 * DAY_MS, body = "Company A follow-up")
        noteDao.notes += note("co-b", "n1", "p1", type = "follow_up", dueAt = 5 * DAY_MS, body = "Company B follow-up")

        val groupA = repository().getFollowUps("co-a", limit = 20)
        val groupB = repository().getFollowUps("co-b", limit = 20)

        assertEquals(1, groupA.items.size)
        assertEquals("Company A follow-up", groupA.items.single().body)
        assertEquals(1, groupB.items.size)
        assertEquals("Company B follow-up", groupB.items.single().body)
    }

    // ============================== PENDING TALLY CONFIRMATION ==============================

    @Test
    fun `pending confirmation maps field names to plain-language labels`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "ABC Traders"))
        fieldProvenanceDao.rows += PendingConfirmationRow("co-a", "p1", "primaryPhone,primaryEmail", earliestAt = 1_000L)

        val item = repository().getPendingTallyConfirmations("co-a", limit = 20).items.single()

        assertEquals("ABC Traders", item.partyDisplayName)
        assertEquals(listOf("Phone", "Email"), item.pendingFieldLabels)
    }

    @Test
    fun `pending confirmations are company isolated end to end through the repository`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "ABC Traders"))
        partyDao.upsert(party("co-b", "p1", "ABC Traders"))
        fieldProvenanceDao.rows += PendingConfirmationRow("co-a", "p1", "primaryPhone", earliestAt = 1_000L)
        fieldProvenanceDao.rows += PendingConfirmationRow("co-b", "p1", "primaryEmail", earliestAt = 1_000L)

        val groupA = repository().getPendingTallyConfirmations("co-a", limit = 20)
        val groupB = repository().getPendingTallyConfirmations("co-b", limit = 20)

        assertEquals(listOf("Phone"), groupA.items.single().pendingFieldLabels)
        assertEquals(listOf("Email"), groupB.items.single().pendingFieldLabels)
    }

    // ============================== PENDING CONTACT COMPLETION ==============================

    @Test
    fun `pending contact completions map straight from Party rows`() = runTest(dispatcher) {
        partyDao.missingContact += party("co-a", "p1", "No Contact Traders")

        val item = repository().getPendingContactCompletions("co-a", limit = 20).items.single()

        assertEquals("p1", item.partyId)
        assertEquals("No Contact Traders", item.partyDisplayName)
    }

    // ============================== COMBINED ==============================

    @Test
    fun `all three item types can be fetched independently and combined without interference`() = runTest(dispatcher) {
        partyDao.upsert(party("co-a", "p1", "Follow-up Party"))
        partyDao.upsert(party("co-a", "p2", "Confirmation Party"))
        noteDao.notes += note("co-a", "n1", "p1", type = "follow_up", dueAt = 5 * DAY_MS)
        fieldProvenanceDao.rows += PendingConfirmationRow("co-a", "p2", "primaryPhone", earliestAt = 1_000L)
        partyDao.missingContact += party("co-a", "p3", "Contact Party")

        val repo = repository()
        val followUps = repo.getFollowUps("co-a", limit = 20)
        val confirmations = repo.getPendingTallyConfirmations("co-a", limit = 20)
        val contacts = repo.getPendingContactCompletions("co-a", limit = 20)

        assertEquals(1, followUps.items.size)
        assertEquals(1, confirmations.items.size)
        assertEquals(1, contacts.items.size)
    }

    private fun party(companyId: String, partyId: String, displayName: String) = PartyEntity(
        companyId = companyId,
        partyId = partyId,
        displayName = displayName,
        classification = "customer",
        primaryPhone = null,
        primaryPhoneNormalized = null,
        primaryEmail = null,
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        gstin = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun note(
        companyId: String,
        noteId: String,
        partyId: String,
        type: String,
        dueAt: Long?,
        body: String = "Follow-up note",
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
        completedAt = null,
        issueId = null,
    )
}

/** Fake [PartyDao] — a plain in-memory store. [pageMissingContactInfo]/[countMissingContactInfo]
 * read directly from [missingContact] rather than re-deriving the real predicate: DAO-level filter
 * correctness is already covered by the real-Room `PartyDaoTest` instrumented suite; this fake only
 * needs to exercise [DincharyaRepositoryImpl]'s own orchestration. */
private class FakePartyDao : PartyDao {
    val store = mutableMapOf<Pair<String, String>, PartyEntity>()
    val missingContact = mutableListOf<PartyEntity>()

    override suspend fun countForCompany(companyId: String): Int = store.values.count { it.companyId == companyId }
    override suspend fun findById(companyId: String, partyId: String): PartyEntity? = store[companyId to partyId]
    override suspend fun upsert(entity: PartyEntity) {
        store[entity.companyId to entity.partyId] = entity
    }
    override suspend fun upsertAll(entities: List<PartyEntity>) = entities.forEach { upsert(it) }
    override suspend fun pageByClassification(companyId: String, classification: String, limit: Int, offset: Int): List<PartyEntity> =
        error("unused")
    override suspend fun countByClassification(companyId: String, classification: String): Int = error("unused")
    override suspend fun search(companyId: String, query: String, classification: String?, limit: Int, offset: Int): List<PartyEntity> =
        error("unused")
    override suspend fun countSearch(companyId: String, query: String, classification: String?): Int = error("unused")

    override suspend fun pageMissingContactInfo(companyId: String, limit: Int, offset: Int): List<PartyEntity> =
        missingContact.filter { it.companyId == companyId }.drop(offset).take(limit)

    override suspend fun countMissingContactInfo(companyId: String): Int =
        missingContact.count { it.companyId == companyId }

    override suspend fun findByIds(companyId: String, partyIds: List<String>): List<PartyEntity> =
        store.values.filter { it.companyId == companyId && it.partyId in partyIds }
}

/** Fake [PartyNoteDao] — [pageFollowUpsForCompany]/[countFollowUpsForCompany] read directly from
 * [notes] (already treated as eligible), sorted by `dueAt ASC, noteId ASC` — mirroring the real
 * query's ordering, which is exactly what [DincharyaRepositoryImpl] relies on. */
private class FakePartyNoteDao : PartyNoteDao {
    val notes = mutableListOf<PartyNoteEntity>()
    var totalOverride: Int? = null

    override suspend fun pageForParty(companyId: String, partyId: String, limit: Int, offset: Int): List<PartyNoteEntity> = error("unused")
    override suspend fun countForParty(companyId: String, partyId: String): Int = error("unused")
    override suspend fun findById(companyId: String, noteId: String): PartyNoteEntity? = error("unused")
    override suspend fun upsert(entity: PartyNoteEntity) = error("unused")
    override suspend fun delete(companyId: String, noteId: String) = error("unused")
    override suspend fun issueActivitySummary(companyId: String, partyId: String): List<IssueActivityRow> = error("unused")

    override suspend fun pageFollowUpsForCompany(companyId: String, limit: Int, offset: Int): List<PartyNoteEntity> =
        notes.filter { it.companyId == companyId }
            .sortedWith(compareBy({ it.dueAt }, { it.noteId }))
            .drop(offset).take(limit)

    override suspend fun countFollowUpsForCompany(companyId: String): Int =
        totalOverride ?: notes.count { it.companyId == companyId }
}

/** Fake [PartyFieldProvenanceDao] — [pagePendingConfirmationForCompany]/[countPendingConfirmationForCompany]
 * read directly from [rows] (already grouped-per-party, as the real `GROUP BY` query would produce). */
private class FakePartyFieldProvenanceDao : PartyFieldProvenanceDao {
    val rows = mutableListOf<PendingConfirmationRow>()

    override suspend fun findField(companyId: String, partyId: String, fieldName: String) = error("unused")
    override suspend fun findAllForParty(companyId: String, partyId: String) = error("unused")
    override suspend fun upsert(entity: PartyFieldProvenanceEntity) = error("unused")

    override suspend fun pagePendingConfirmationForCompany(companyId: String, limit: Int, offset: Int): List<PendingConfirmationRow> =
        rows.filter { it.companyId == companyId }.sortedBy { it.earliestAt }.drop(offset).take(limit)

    override suspend fun countPendingConfirmationForCompany(companyId: String): Int =
        rows.count { it.companyId == companyId }
}
