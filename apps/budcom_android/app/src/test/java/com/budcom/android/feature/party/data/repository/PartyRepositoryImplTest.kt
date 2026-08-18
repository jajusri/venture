package com.budcom.android.feature.party.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyContactPersonEntity
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyEntity
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceEntity
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartySourceLinkEntity
import com.budcom.android.feature.party.data.local.PartyTagCrossRefEntity
import com.budcom.android.feature.party.data.local.TagDao
import com.budcom.android.feature.party.data.local.TagEntity
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val time = FakeTimeProvider()
    private val partyDao = FakePartyDao()
    private val sourceLinkDao = FakePartySourceLinkDao()
    private val fieldProvenanceDao = FakePartyFieldProvenanceDao()
    private val contactPersonDao = FakePartyContactPersonDao()
    private val tagDao = FakeTagDao()
    private val noteDao = FakePartyNoteDao()
    private val exportEventDao = FakePartyExportEventDao()
    private val issueDao = FakePartyIssueDao()
    private val timelineDao = FakePartyTimelineDao(noteDao, exportEventDao, issueDao)

    private fun repository() = PartyRepositoryImpl(
        partyDao, sourceLinkDao, fieldProvenanceDao, contactPersonDao, tagDao, noteDao, exportEventDao, issueDao, timelineDao, time, dispatchers,
    )

    private fun seed(
        ledgerId: String = "guid:cash-customer",
        name: String = "ABC Traders",
        alias: String? = null,
        classification: PartyClassification = PartyClassification.Customer,
    ) = EligibleLedgerSeed(ledgerId, name, alias, classification)

    // ============================== IDENTITY ==============================

    @Test
    fun `creates a Party from an eligible ledger`() = runTest(dispatcher) {
        val result = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed()))
        assertEquals(1, result.size)
        assertEquals("ABC Traders", result.single().displayName)
        assertEquals(PartyClassification.Customer, result.single().classification)
    }

    @Test
    fun `repeat seed with identical input is idempotent`() = runTest(dispatcher) {
        val repo = repository()
        val first = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed())).single()
        val second = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed())).single()

        assertEquals(first.partyId, second.partyId)
        assertEquals(1, partyDao.store.size)
        assertEquals(1, sourceLinkDao.store.size)
    }

    @Test
    fun `same GUID preserves partyId across separate reconciliation runs`() = runTest(dispatcher) {
        val repo = repository()
        val run1 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc"))).single()
        val run2 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc"))).single()
        assertEquals(run1.partyId, run2.partyId)
    }

    @Test
    fun `ledger rename preserves partyId and updates the display name`() = runTest(dispatcher) {
        val repo = repository()
        val before = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc", name = "Old Name"))).single()
        val after = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc", name = "New Name"))).single()

        assertEquals(before.partyId, after.partyId)
        assertEquals("New Name", after.displayName)
        assertEquals(1, partyDao.store.size)
    }

    @Test
    fun `same name different GUID creates two distinct Parties`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:aaa", name = "Same Name"))).single()
        val b = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:bbb", name = "Same Name"))).single()

        assertNotEquals(a.partyId, b.partyId)
        assertEquals(2, partyDao.store.size)
    }

    @Test
    fun `getPartyForLedger resolves via the source link`() = runTest(dispatcher) {
        val repo = repository()
        val created = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:xyz"))).single()

        val resolved = repo.getPartyForLedger("co-1", "guid:xyz")
        assertEquals(created.partyId, resolved?.partyId)
        assertNull(repo.getPartyForLedger("co-1", "guid:does-not-exist"))
    }

    // ============================== COMPANY ISOLATION ==============================

    @Test
    fun `same ledger name across two companies never collapses into one Party`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers("co-A", listOf(seed(ledgerId = "guid:same", name = "Shared Name"))).single()
        val b = repo.reconcilePartiesFromEligibleLedgers("co-B", listOf(seed(ledgerId = "guid:same", name = "Shared Name"))).single()

        assertNotEquals(a.partyId, b.partyId)
        assertEquals(a.partyId, repo.getPartyForLedger("co-A", "guid:same")?.partyId)
        assertEquals(b.partyId, repo.getPartyForLedger("co-B", "guid:same")?.partyId)
        assertNull(repo.getPartyForLedger("co-A", "guid:same-not-in-b").also { assertNull(it) })
    }

    @Test
    fun `same phone alias across two companies produces two independent Parties`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers(
            "co-A",
            listOf(seed(ledgerId = "guid:1", name = "A Traders", alias = "9876543210")),
        ).single()
        val b = repo.reconcilePartiesFromEligibleLedgers(
            "co-B",
            listOf(seed(ledgerId = "guid:2", name = "B Traders", alias = "9876543210")),
        ).single()

        assertEquals("9876543210", a.primaryPhone)
        assertEquals("9876543210", b.primaryPhone)
        assertNotEquals(a.partyId, b.partyId)
    }

    // ============================== CONNECT LIST-ENRICHMENT BULK READS ==============================

    @Test
    fun `getSourceLinksForCompany returns every link scoped to that company only`() = runTest(dispatcher) {
        val repo = repository()
        repo.reconcilePartiesFromEligibleLedgers("co-A", listOf(seed(ledgerId = "guid:1", name = "A Traders")))
        repo.reconcilePartiesFromEligibleLedgers("co-B", listOf(seed(ledgerId = "guid:2", name = "B Traders")))

        val linksA = repo.getSourceLinksForCompany("co-A")
        assertEquals(1, linksA.size)
        assertEquals("guid:1", linksA.single().externalEntityId)
    }

    @Test
    fun `searchParties with a classification filter never returns another classification`() = runTest(dispatcher) {
        val repo = repository()
        repo.reconcilePartiesFromEligibleLedgers(
            "co-1",
            listOf(
                seed(ledgerId = "guid:cust", name = "ABC Traders", classification = PartyClassification.Customer),
                seed(ledgerId = "guid:supp", name = "ABC Supplies", classification = PartyClassification.Supplier),
            ),
        )

        val customerMatches = repo.searchParties("co-1", "ABC", PartyClassification.Customer, 1, 50)
        assertEquals(1, customerMatches.items.size)
        assertEquals(PartyClassification.Customer, customerMatches.items.single().classification)

        val allMatches = repo.searchParties("co-1", "ABC", null, 1, 50)
        assertEquals(2, allMatches.items.size)
    }

    @Test
    fun `getTagsForCompany groups tag assignments by partyId`() = runTest(dispatcher) {
        val repo = repository()
        val party = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed())).single()
        tagDao.upsert(com.budcom.android.feature.party.data.local.TagEntity("tag-1", null, "Dealer", "Dealer", time.now))
        tagDao.assign(
            com.budcom.android.feature.party.data.local.PartyTagCrossRefEntity("co-1", party.partyId, "tag-1", time.now),
        )

        val tagsByParty = repo.getTagsForCompany("co-1")
        assertEquals(listOf("Dealer"), tagsByParty[party.partyId]?.map { it.name })
    }

    // ============================== PHONE / ALIAS SEEDING ==============================

    @Test
    fun `exactly 10-digit valid alias seeds the phone as confirmed from Tally`() = runTest(dispatcher) {
        val repo = repository()
        val party = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()

        assertEquals("9876543210", party.primaryPhone)
        assertEquals("9876543210", party.primaryPhoneNormalized)
        val provenance = fieldProvenanceDao.findField("co-1", party.partyId, PartyFieldNames.PRIMARY_PHONE)
        assertEquals("confirmed_from_tally", provenance?.state)
        assertEquals("9876543210", provenance?.tallyValue)
    }

    @Test
    fun `9-digit alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "987654321"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `11-digit alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98765432101"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `alphabetic alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98A7654321"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `formatted alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98765-43210"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `existing phone is preserved when a later alias would seed a different value`() = runTest(dispatcher) {
        val repo = repository()
        repo.updateBudcomOnlyField("co-1", partyIdFor(repo, "guid:cash-customer"), PartyFieldNames.PRIMARY_PHONE, "9111111111")
        val party = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()

        assertEquals("9111111111", party.primaryPhone)
    }

    @Test
    fun `a different alias value than the existing phone surfaces as conflict, never silently overwritten`() = runTest(dispatcher) {
        val repo = repository()
        val party1 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()
        val party2 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9111111111"))).single()

        assertEquals(party1.partyId, party2.partyId)
        assertEquals("9876543210", party2.primaryPhone) // unchanged
        val provenance = fieldProvenanceDao.findField("co-1", party2.partyId, PartyFieldNames.PRIMARY_PHONE)
        assertEquals("conflict", provenance?.state)
        assertEquals("9111111111", provenance?.tallyValue)
    }

    // ============================== PROVENANCE ==============================

    @Test
    fun `BUDCOM-entered field is pending, never confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val state = repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")

        assertEquals(FieldProvenanceState.BudcomOnlyPending, state)
        assertEquals("owner@example.com", repo.getPartyById("co-1", partyId)?.primaryEmail)
    }

    @Test
    fun `Tally exact-match confirmation promotes a field with no pending value to confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
        assertEquals("22AAAAA0000A1Z5", repo.getPartyById("co-1", partyId)?.gstin)
    }

    @Test
    fun `Tally value that mismatches a pending BUDCOM value remains conflict, not silently confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "22BBBBB0000B1Z5")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.Conflict, state)
        val provenance = repo.getFieldProvenance("co-1", partyId).single { it.fieldName == PartyFieldNames.GSTIN }
        assertEquals("22BBBBB0000B1Z5", provenance.budcomValue)
        assertEquals("22AAAAA0000A1Z5", provenance.tallyValue)
    }

    @Test
    fun `Tally value matching the pending BUDCOM value confirms it`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
    }

    @Test
    fun `exported state round-trips through the repository read path`() = runTest(dispatcher) {
        // 1.1-A models but does not implement the XML export workflow (1.1-D scope) -- this
        // proves the state itself is representable and correctly surfaced via getFieldProvenance.
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        fieldProvenanceDao.upsert(
            PartyFieldProvenanceEntity(
                companyId = "co-1", partyId = partyId, fieldName = PartyFieldNames.ADDRESS_CITY,
                state = "exported", tallyValue = null, budcomValue = "Hyderabad",
                lastConfirmedAt = null, lastExportedAt = time.now, updatedAt = time.now,
            ),
        )
        val provenance = repo.getFieldProvenance("co-1", partyId).single { it.fieldName == PartyFieldNames.ADDRESS_CITY }
        assertEquals(FieldProvenanceState.Exported, provenance.state)
    }

    // ============================== CONTACT PERSONS (read path) ==============================

    @Test
    fun `getContactPersons returns multiple contacts with primary ordered first`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        contactPersonDao.upsert(contact(partyId, "cp-1", "Accounts Person", isPrimary = false))
        contactPersonDao.upsert(contact(partyId, "cp-2", "Owner", isPrimary = true))

        val contacts = repo.getContactPersons("co-1", partyId)
        assertEquals(2, contacts.size)
        assertTrue(contacts.first().isPrimary)
        assertEquals("Owner", contacts.first().name)
    }

    // ============================== PROSPECT CREATION ==============================

    @Test
    fun `createProspect creates a BUDCOM-native Party with no source link`() = runTest(dispatcher) {
        val repo = repository()
        val prospect = repo.createProspect(
            "co-1",
            com.budcom.android.feature.party.domain.model.ProspectDraft(
                displayName = "New Bakery",
                phone = "9876543210",
                email = "hello@newbakery.example",
            ),
        )

        assertEquals(PartyClassification.Prospect, prospect.classification)
        assertEquals("New Bakery", prospect.displayName)
        assertEquals("9876543210", prospect.primaryPhone)
        assertNull(repo.getSourceLinkForParty("co-1", prospect.partyId))
    }

    @Test
    fun `Prospect provided fields are tracked as pending, never confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val prospect = repo.createProspect(
            "co-1",
            com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "New Bakery", email = "hello@newbakery.example"),
        )
        val provenance = repo.getFieldProvenance("co-1", prospect.partyId).single { it.fieldName == PartyFieldNames.PRIMARY_EMAIL }
        assertEquals(FieldProvenanceState.BudcomOnlyPending, provenance.state)
    }

    @Test
    fun `two Prospects with duplicate-looking names and phones are never auto-merged`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.createProspect("co-1", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders", phone = "9876543210"))
        val b = repo.createProspect("co-1", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders", phone = "9876543210"))

        assertNotEquals(a.partyId, b.partyId)
        assertEquals(2, partyDao.countForCompany("co-1"))
    }

    @Test
    fun `Prospect creation with only a display name works, no accounting information required`() = runTest(dispatcher) {
        val prospect = repository().createProspect("co-1", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "Just A Name"))
        assertEquals("Just A Name", prospect.displayName)
        assertNull(prospect.primaryPhone)
    }

    @Test
    fun `Prospect optional tags and note are created alongside the Party`() = runTest(dispatcher) {
        val repo = repository()
        val tag = repo.createOrGetTag("Dealer", null)
        val prospect = repo.createProspect(
            "co-1",
            com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "New Bakery", tagIds = listOf(tag.tagId), note = "Met at expo"),
        )

        assertEquals(listOf("Dealer"), repo.getTagsForParty("co-1", prospect.partyId).map { it.name })
        val notes = repo.getNotesForParty("co-1", prospect.partyId, 1, 20)
        assertEquals("Met at expo", notes.items.single().body)
    }

    // ============================== CONTACT PERSON MANAGEMENT ==============================

    @Test
    fun `upsertContactPerson creates a new contact when contactPersonId is null`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val created = repo.upsertContactPerson("co-1", partyId, null, "Owner Name", "Owner", "9876543210", null, null, true)

        assertTrue(created.contactPersonId.isNotBlank())
        assertEquals(1, repo.getContactPersons("co-1", partyId).size)
    }

    @Test
    fun `upsertContactPerson with an existing id edits in place, not creating a duplicate`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val created = repo.upsertContactPerson("co-1", partyId, null, "Owner", null, null, null, null, false)
        repo.upsertContactPerson("co-1", partyId, created.contactPersonId, "Owner Renamed", "Owner", "9111111111", null, null, false)

        val contacts = repo.getContactPersons("co-1", partyId)
        assertEquals(1, contacts.size)
        assertEquals("Owner Renamed", contacts.single().name)
    }

    @Test
    fun `only one contact person can be primary at a time`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val first = repo.upsertContactPerson("co-1", partyId, null, "Owner", null, null, null, null, true)
        repo.upsertContactPerson("co-1", partyId, null, "Accounts", null, null, null, null, true)

        val contacts = repo.getContactPersons("co-1", partyId)
        assertEquals(1, contacts.count { it.isPrimary })
        assertEquals("Accounts", contacts.first { it.isPrimary }.name)
        assertEquals(false, contacts.single { it.contactPersonId == first.contactPersonId }.isPrimary)
    }

    @Test
    fun `deleting the current primary contact leaves no primary, does not crash`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val primary = repo.upsertContactPerson("co-1", partyId, null, "Owner", null, null, null, null, true)
        repo.upsertContactPerson("co-1", partyId, null, "Accounts", null, null, null, null, false)

        repo.deleteContactPerson("co-1", primary.contactPersonId)

        val remaining = repo.getContactPersons("co-1", partyId)
        assertEquals(1, remaining.size)
        assertEquals("Accounts", remaining.single().name)
        assertEquals(0, remaining.count { it.isPrimary })
    }

    @Test
    fun `multiple contact persons with duplicate phone numbers are allowed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.upsertContactPerson("co-1", partyId, null, "Owner", null, "9876543210", null, null, false)
        repo.upsertContactPerson("co-1", partyId, null, "Manager", null, "9876543210", null, null, false)

        assertEquals(2, repo.getContactPersons("co-1", partyId).size)
    }

    // ============================== TAG MANAGEMENT ==============================

    @Test
    fun `createOrGetTag never creates a duplicate for the same name and parent`() = runTest(dispatcher) {
        val repo = repository()
        val first = repo.createOrGetTag("Dealer", null)
        val second = repo.createOrGetTag("Dealer", null)
        assertEquals(first.tagId, second.tagId)
    }

    @Test
    fun `createOrGetTag builds a hierarchical path from its parent`() = runTest(dispatcher) {
        val repo = repository()
        val ap = repo.createOrGetTag("AP", null)
        val chittoor = repo.createOrGetTag("Chittoor", ap.tagId)
        assertEquals("AP/Chittoor", chittoor.path)
        assertEquals(ap.tagId, chittoor.parentTagId)
    }

    @Test
    fun `assign and unassign tags on a Party`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val tag = repo.createOrGetTag("Dealer", null)

        repo.assignTag("co-1", partyId, tag.tagId)
        assertEquals(listOf("Dealer"), repo.getTagsForParty("co-1", partyId).map { it.name })

        repo.unassignTag("co-1", partyId, tag.tagId)
        assertTrue(repo.getTagsForParty("co-1", partyId).isEmpty())
    }

    // ============================== NOTES ==============================

    @Test
    fun `notes are ordered newest first`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        time.now = 1_000L
        repo.addNote("co-1", partyId, "First note", null, NoteType.General, null, null)
        time.now = 2_000L
        repo.addNote("co-1", partyId, "Second note", null, NoteType.General, null, null)

        val notes = repo.getNotesForParty("co-1", partyId, 1, 20)
        assertEquals(listOf("Second note", "First note"), notes.items.map { it.body })
    }

    @Test
    fun `a note can link to a voucher by stable id`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "2 pieces short", "v-1842", NoteType.General, null, null)
        assertEquals("v-1842", note.linkedVoucherId)
    }

    @Test
    fun `a note with no type specified defaults to General, preserving pre-1_2-A behavior`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "Just a quick remark", null, NoteType.General, null, null)

        assertEquals(NoteType.General, note.type)
        assertNull(note.dueAt)
        assertNull(note.completedAt)
        assertNull(note.issueId)
    }

    @Test
    fun `a commitment note carries a due date`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "Will pay by Friday", null, NoteType.Commitment, 9_999_999L, null)

        assertEquals(NoteType.Commitment, note.type)
        assertEquals(9_999_999L, note.dueAt)
    }

    @Test
    fun `editing a note updates its body, type, due date and issue, and updatedAt`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "Original", null, NoteType.General, null, null)
        val issue = repo.createIssue("co-1", partyId, "2 pieces short")
        time.now = 5_000L
        val edited = repo.editNote("co-1", note.noteId, "Edited body", NoteType.FollowUp, 8_000L, issue.issueId)

        assertEquals("Edited body", edited?.body)
        assertEquals(NoteType.FollowUp, edited?.type)
        assertEquals(8_000L, edited?.dueAt)
        assertEquals(issue.issueId, edited?.issueId)
        assertEquals(5_000L, edited?.updatedAt)
    }

    @Test
    fun `marking a note complete does not delete it`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "Call back tomorrow", null, NoteType.FollowUp, 1_000L, null)
        time.now = 2_000L
        val completed = repo.setNoteCompletion("co-1", note.noteId, 2_000L)

        assertEquals(2_000L, completed?.completedAt)
        assertEquals(1, repo.getNotesForParty("co-1", partyId, 1, 20).items.size)
    }

    @Test
    fun `reopening a completed note clears completedAt`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "Call back tomorrow", null, NoteType.FollowUp, 1_000L, null)
        repo.setNoteCompletion("co-1", note.noteId, 2_000L)
        val reopened = repo.setNoteCompletion("co-1", note.noteId, null)

        assertNull(reopened?.completedAt)
    }

    @Test
    fun `deleting a note removes it from the party's list`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val note = repo.addNote("co-1", partyId, "To be deleted", null, NoteType.General, null, null)
        repo.deleteNote("co-1", note.noteId)

        assertTrue(repo.getNotesForParty("co-1", partyId, 1, 20).items.isEmpty())
    }

    @Test
    fun `notes are company isolated`() = runTest(dispatcher) {
        val repo = repository()
        val partyIdA = repo.createProspect("co-A", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "A Party")).partyId
        repo.addNote("co-A", partyIdA, "Note in company A", null, NoteType.General, null, null)

        assertTrue(repo.getNotesForParty("co-B", partyIdA, 1, 20).items.isEmpty())
    }

    // ============================== ISSUES ==============================

    @Test
    fun `creating an issue starts it open`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "2 pieces short — Sales Voucher #1842")

        assertEquals(IssueStatus.Open, issue.status)
        assertNull(issue.resolvedAt)
    }

    @Test
    fun `resolving an issue sets resolvedAt and never deletes its notes`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        repo.addNote("co-1", partyId, "2 pieces short", null, NoteType.Complaint, null, issue.issueId)
        time.now = 3_000L

        val resolved = repo.resolveIssue("co-1", issue.issueId)

        assertEquals(IssueStatus.Resolved, resolved?.status)
        assertEquals(3_000L, resolved?.resolvedAt)
        assertEquals(1, repo.getNotesForParty("co-1", partyId, 1, 20).items.size)
    }

    @Test
    fun `reopening a resolved issue clears resolvedAt`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        repo.resolveIssue("co-1", issue.issueId)

        val reopened = repo.reopenIssue("co-1", issue.issueId)

        assertEquals(IssueStatus.Open, reopened?.status)
        assertNull(reopened?.resolvedAt)
    }

    @Test
    fun `open issues are listed before resolved issues`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val resolved = repo.createIssue("co-1", partyId, "Already fixed")
        repo.resolveIssue("co-1", resolved.issueId)
        val open = repo.createIssue("co-1", partyId, "Still open")

        val issues = repo.getIssuesForParty("co-1", partyId)

        assertEquals(listOf(open.issueId, resolved.issueId), issues.map { it.issueId })
    }

    @Test
    fun `issues are company isolated`() = runTest(dispatcher) {
        val repo = repository()
        val partyIdA = repo.createProspect("co-A", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "A Party")).partyId
        repo.createIssue("co-A", partyIdA, "Issue in company A")

        assertTrue(repo.getIssuesForParty("co-B", partyIdA).isEmpty())
    }

    // ============================== RELATIONSHIP TIMELINE (MVP-1.2-B) ==============================

    @Test
    fun `timeline merges notes and export events newest first`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        time.now = 1_000L
        repo.addNote("co-1", partyId, "First note", null, NoteType.General, null, null)
        time.now = 2_000L
        repo.recordExport("co-1", partyId, "export.xml", listOf(PartyFieldNames.PRIMARY_EMAIL))
        time.now = 3_000L
        repo.addNote("co-1", partyId, "Second note", null, NoteType.General, null, null)

        val timeline = repo.getTimelineForParty("co-1", partyId, 1, 20, null)

        assertEquals(3, timeline.totalItems)
        assertEquals(
            listOf(3_000L, 2_000L, 1_000L),
            timeline.items.map { it.timestamp },
        )
        assertTrue(timeline.items.first() is com.budcom.android.feature.party.domain.model.TimelineEntry.NoteEvent)
        assertTrue(timeline.items[1] is com.budcom.android.feature.party.domain.model.TimelineEntry.ExportEvent)
    }

    @Test
    fun `timeline pagination respects page boundaries with no gaps or duplicates`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repeat(5) { i ->
            time.now = (i + 1) * 1_000L
            repo.addNote("co-1", partyId, "Note $i", null, NoteType.General, null, null)
        }

        val page1 = repo.getTimelineForParty("co-1", partyId, 1, 2, null)
        val page2 = repo.getTimelineForParty("co-1", partyId, 2, 2, null)
        val page3 = repo.getTimelineForParty("co-1", partyId, 3, 2, null)

        assertEquals(5, page1.totalItems)
        assertTrue(page1.canLoadMore)
        assertTrue(page2.canLoadMore)
        assertFalse(page3.canLoadMore)
        val allIds = (page1.items + page2.items + page3.items)
            .map { (it as com.budcom.android.feature.party.domain.model.TimelineEntry.NoteEvent).note.noteId }
        assertEquals(5, allIds.distinct().size)
    }

    @Test
    fun `an empty party has an empty timeline that cannot load more`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")

        val timeline = repo.getTimelineForParty("co-1", partyId, 1, 20, null)

        assertTrue(timeline.items.isEmpty())
        assertEquals(0, timeline.totalItems)
        assertFalse(timeline.canLoadMore)
    }

    @Test
    fun `filtering the timeline by issueId returns only that issue's notes, never export events`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        repo.addNote("co-1", partyId, "Unrelated note", null, NoteType.General, null, null)
        repo.addNote("co-1", partyId, "2 pieces short", null, NoteType.Complaint, null, issue.issueId)
        repo.recordExport("co-1", partyId, "export.xml", listOf(PartyFieldNames.PRIMARY_EMAIL))

        val filtered = repo.getTimelineForParty("co-1", partyId, 1, 20, issue.issueId)

        assertEquals(1, filtered.items.size)
        val note = (filtered.items.single() as com.budcom.android.feature.party.domain.model.TimelineEntry.NoteEvent).note
        assertEquals("2 pieces short", note.body)
    }

    @Test
    fun `timeline never leaks another company's notes or export events, even with identical content`() = runTest(dispatcher) {
        val repo = repository()
        val partyIdA = repo.createProspect("co-A", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders", phone = "9876543210")).partyId
        val partyIdB = repo.createProspect("co-B", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders", phone = "9876543210")).partyId
        repo.addNote("co-A", partyIdA, "Customer says 2 pieces short", null, NoteType.Complaint, null, null)
        repo.recordExport("co-A", partyIdA, "export.xml", listOf(PartyFieldNames.PRIMARY_EMAIL))

        val timelineA = repo.getTimelineForParty("co-A", partyIdA, 1, 20, null)
        val timelineB = repo.getTimelineForParty("co-B", partyIdB, 1, 20, null)
        val crossCompanyLookup = repo.getTimelineForParty("co-B", partyIdA, 1, 20, null)

        assertEquals(2, timelineA.totalItems)
        assertTrue("a same-named/same-phone Party in a different company must start with an empty timeline", timelineB.items.isEmpty())
        assertTrue("company A's partyId must not resolve any data under company B's scope", crossCompanyLookup.items.isEmpty())
    }

    // ============================== ISSUE HISTORY (MVP-1.2-C) ==============================

    @Test
    fun `creating an issue produces an Issue opened timeline entry using the issue's own createdAt`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        time.now = 5_000L
        val issue = repo.createIssue("co-1", partyId, "Short shipment")

        val timeline = repo.getTimelineForParty("co-1", partyId, 1, 20, null)

        val opened = timeline.items.single() as com.budcom.android.feature.party.domain.model.TimelineEntry.IssueOpenedEvent
        assertEquals(issue.issueId, opened.issueId)
        assertEquals("Short shipment", opened.title)
        assertEquals(5_000L, opened.openedAt)
    }

    @Test
    fun `resolving an issue adds an Issue resolved entry without removing the opened entry`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        time.now = 9_000L
        repo.resolveIssue("co-1", issue.issueId)

        val timeline = repo.getTimelineForParty("co-1", partyId, 1, 20, null)

        assertEquals(2, timeline.items.size)
        assertTrue(timeline.items.any { it is com.budcom.android.feature.party.domain.model.TimelineEntry.IssueOpenedEvent })
        val resolved = timeline.items.filterIsInstance<com.budcom.android.feature.party.domain.model.TimelineEntry.IssueResolvedEvent>().single()
        assertEquals(9_000L, resolved.resolvedAt)
    }

    @Test
    fun `reopening a resolved issue removes its Issue resolved entry from the timeline, keeps opened`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        repo.resolveIssue("co-1", issue.issueId)
        repo.reopenIssue("co-1", issue.issueId)

        val timeline = repo.getTimelineForParty("co-1", partyId, 1, 20, null)

        assertEquals(1, timeline.items.size)
        assertTrue(timeline.items.single() is com.budcom.android.feature.party.domain.model.TimelineEntry.IssueOpenedEvent)
    }

    @Test
    fun `issue-filtered timeline shows only that issue's notes, never its own lifecycle rows`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        repo.addNote("co-1", partyId, "2 pieces short", null, NoteType.Complaint, null, issue.issueId)
        repo.resolveIssue("co-1", issue.issueId)

        val filtered = repo.getTimelineForParty("co-1", partyId, 1, 20, issue.issueId)

        assertEquals(1, filtered.items.size)
        assertTrue(filtered.items.single() is com.budcom.android.feature.party.domain.model.TimelineEntry.NoteEvent)
    }

    @Test
    fun `issue activity summary reports note count and latest note timestamp per issue`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val issue = repo.createIssue("co-1", partyId, "Short shipment")
        time.now = 1_000L
        repo.addNote("co-1", partyId, "First", null, NoteType.Complaint, null, issue.issueId)
        time.now = 2_000L
        repo.addNote("co-1", partyId, "Second", null, NoteType.Complaint, null, issue.issueId)

        val summary = repo.getIssueActivitySummary("co-1", partyId)

        assertEquals(2, summary.getValue(issue.issueId).noteCount)
        assertEquals(2_000L, summary.getValue(issue.issueId).latestNoteAt)
    }

    @Test
    fun `an issue with no notes has no entry in the activity summary`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.createIssue("co-1", partyId, "Short shipment")

        assertTrue(repo.getIssueActivitySummary("co-1", partyId).isEmpty())
    }

    @Test
    fun `issue lifecycle timeline entries and activity summaries never leak across companies`() = runTest(dispatcher) {
        val repo = repository()
        val partyIdA = repo.createProspect("co-A", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders")).partyId
        val partyIdB = repo.createProspect("co-B", com.budcom.android.feature.party.domain.model.ProspectDraft(displayName = "ABC Traders")).partyId
        val issueA = repo.createIssue("co-A", partyIdA, "Short shipment")
        repo.addNote("co-A", partyIdA, "2 pieces short", null, NoteType.Complaint, null, issueA.issueId)

        val timelineB = repo.getTimelineForParty("co-B", partyIdB, 1, 20, null)
        val summaryB = repo.getIssueActivitySummary("co-B", partyIdB)

        assertTrue("company B must never see company A's issue-lifecycle timeline entries", timelineB.items.isEmpty())
        assertTrue("company B must never see company A's issue activity summary", summaryB.isEmpty())
    }

    // ============================== TALLY XML EXPORT (MVP-1.1-D) ==============================

    @Test
    fun `export candidates cover exactly the six Tally-eligible fields, never city or BUDCOM-only data`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")

        val candidates = repo.getExportCandidates("co-1", partyId)

        assertEquals(
            listOf("primaryPhone", "primaryEmail", "addressLine1", "addressState", "addressPincode", "gstin"),
            candidates.map { it.fieldName },
        )
        assertTrue("addressCity must never be export-eligible", candidates.none { it.fieldName == "addressCity" })
    }

    @Test
    fun `an untouched field shows as EmptyUnknown with no pending or Tally value`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")

        val candidate = repo.getExportCandidates("co-1", partyId).first { it.fieldName == PartyFieldNames.GSTIN }

        assertEquals(FieldProvenanceState.EmptyUnknown, candidate.state)
        assertNull(candidate.tallyValue)
        assertNull(candidate.budcomValue)
    }

    @Test
    fun `an edited field shows as pending with its BUDCOM value in the review list`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")

        val candidate = repo.getExportCandidates("co-1", partyId).first { it.fieldName == PartyFieldNames.PRIMARY_EMAIL }

        assertEquals(FieldProvenanceState.BudcomOnlyPending, candidate.state)
        assertEquals("owner@example.com", candidate.budcomValue)
    }

    @Test
    fun `recording an export transitions only the included fields to Exported`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")

        repo.recordExport("co-1", partyId, "BUDCOM-Tally-Export-ABC-Traders.xml", listOf(PartyFieldNames.PRIMARY_EMAIL))

        val candidates = repo.getExportCandidates("co-1", partyId).associateBy { it.fieldName }
        assertEquals(FieldProvenanceState.Exported, candidates.getValue(PartyFieldNames.PRIMARY_EMAIL).state)
        assertEquals(FieldProvenanceState.BudcomOnlyPending, candidates.getValue(PartyFieldNames.GSTIN).state)
    }

    @Test
    fun `recording an export writes a lightweight audit event with field names only`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")

        val event = repo.recordExport(
            "co-1", partyId, "BUDCOM-Tally-Export-ABC-Traders.xml", listOf(PartyFieldNames.PRIMARY_EMAIL),
        )

        assertEquals(listOf(PartyFieldNames.PRIMARY_EMAIL), event.fieldNames)
        assertEquals("BUDCOM-Tally-Export-ABC-Traders.xml", event.outputFileName)
        val history = repo.getExportHistory("co-1", partyId)
        assertEquals(1, history.size)
        assertEquals(event.exportId, history.single().exportId)
    }

    @Test
    fun `recording an export with only ineligible field names throws rather than exporting nothing`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        var threw = false
        try {
            repo.recordExport("co-1", partyId, "file.xml", listOf("addressCity"))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `export history is newest first and bounded`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        time.now = 1_000L
        repo.recordExport("co-1", partyId, "first.xml", listOf(PartyFieldNames.GSTIN))
        time.now = 2_000L
        repo.recordExport("co-1", partyId, "second.xml", listOf(PartyFieldNames.GSTIN))

        val history = repo.getExportHistory("co-1", partyId)
        assertEquals(listOf("second.xml", "first.xml"), history.map { it.outputFileName })
    }

    @Test
    fun `re-sync confirms a phone that matches only after canonical normalization`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_PHONE, "9876543210")
        repo.recordExport("co-1", partyId, "file.xml", listOf(PartyFieldNames.PRIMARY_PHONE))

        val state = repo.reconcileExportedFieldFromTally("co-1", partyId, PartyFieldNames.PRIMARY_PHONE, "+91 98765 43210")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
    }

    @Test
    fun `re-sync confirms an email that matches only after case and trim normalization`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")
        repo.recordExport("co-1", partyId, "file.xml", listOf(PartyFieldNames.PRIMARY_EMAIL))

        val state = repo.reconcileExportedFieldFromTally("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "  Owner@Example.com  ")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
    }

    @Test
    fun `re-sync confirms a GSTIN regardless of case`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "29abcde1234f1z5")
        repo.recordExport("co-1", partyId, "file.xml", listOf(PartyFieldNames.GSTIN))

        val state = repo.reconcileExportedFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
    }

    @Test
    fun `re-sync flags a genuinely different address as a conflict, never over-normalized`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.ADDRESS_LINE1, "12 Market Road")
        repo.recordExport("co-1", partyId, "file.xml", listOf(PartyFieldNames.ADDRESS_LINE1))

        val state = repo.reconcileExportedFieldFromTally("co-1", partyId, PartyFieldNames.ADDRESS_LINE1, "14 Market Road")

        assertEquals(FieldProvenanceState.Conflict, state)
    }

    @Test
    fun `a blank Tally read-back never downgrades an already-exported field, per TD-027 honesty`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "29ABCDE1234F1Z5")
        repo.recordExport("co-1", partyId, "file.xml", listOf(PartyFieldNames.GSTIN))

        val state = repo.reconcileExportedFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, null)

        assertEquals(FieldProvenanceState.Exported, state)
    }

    // ============================== helpers ==============================

    private suspend fun partyIdFor(repo: com.budcom.android.feature.party.domain.repository.PartyRepository, ledgerId: String): String =
        repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = ledgerId))).single().partyId

    private fun contact(partyId: String, contactPersonId: String, name: String, isPrimary: Boolean) = PartyContactPersonEntity(
        companyId = "co-1",
        contactPersonId = contactPersonId,
        partyId = partyId,
        name = name,
        designation = null,
        mobile = null,
        mobileNormalized = null,
        whatsappNumber = null,
        email = null,
        isPrimary = isPrimary,
        provenance = "budcom_only",
        createdAt = time.now,
        updatedAt = time.now,
    )
}

private class FakeTimeProvider(var now: Long = 1_000_000L) : TimeProvider {
    override fun nowEpochMillis(): Long = now
}

private class FakePartyDao : PartyDao {
    val store = mutableMapOf<Pair<String, String>, PartyEntity>()
    private fun key(companyId: String, partyId: String) = companyId to partyId

    override suspend fun countForCompany(companyId: String): Int = store.values.count { it.companyId == companyId }
    override suspend fun findById(companyId: String, partyId: String): PartyEntity? = store[key(companyId, partyId)]
    override suspend fun upsert(entity: PartyEntity) {
        store[key(entity.companyId, entity.partyId)] = entity
    }
    override suspend fun upsertAll(entities: List<PartyEntity>) {
        entities.forEach { upsert(it) }
    }
    override suspend fun pageByClassification(companyId: String, classification: String, limit: Int, offset: Int): List<PartyEntity> =
        store.values.filter { it.companyId == companyId && it.classification == classification }
            .sortedBy { it.displayName.lowercase() }.drop(offset).take(limit)
    override suspend fun countByClassification(companyId: String, classification: String): Int =
        store.values.count { it.companyId == companyId && it.classification == classification }
    override suspend fun search(companyId: String, query: String, classification: String?, limit: Int, offset: Int): List<PartyEntity> =
        store.values.filter {
            it.companyId == companyId &&
                (classification == null || it.classification == classification) &&
                (it.displayName.contains(query, ignoreCase = true) || it.primaryPhoneNormalized?.contains(query) == true)
        }.sortedBy { it.displayName.lowercase() }.drop(offset).take(limit)
    override suspend fun countSearch(companyId: String, query: String, classification: String?): Int =
        store.values.count {
            it.companyId == companyId &&
                (classification == null || it.classification == classification) &&
                (it.displayName.contains(query, ignoreCase = true) || it.primaryPhoneNormalized?.contains(query) == true)
        }
}

private class FakePartySourceLinkDao : PartySourceLinkDao {
    val store = mutableMapOf<Triple<String, String, String>, PartySourceLinkEntity>()
    private fun key(companyId: String, sourceType: String, externalEntityId: String) = Triple(companyId, sourceType, externalEntityId)

    override suspend fun findByExternalKey(companyId: String, sourceType: String, externalEntityId: String): PartySourceLinkEntity? =
        store[key(companyId, sourceType, externalEntityId)]
    override suspend fun findByPartyId(companyId: String, partyId: String): List<PartySourceLinkEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
    override suspend fun findAllForCompany(companyId: String): List<PartySourceLinkEntity> =
        store.values.filter { it.companyId == companyId }
    override suspend fun upsert(entity: PartySourceLinkEntity) {
        store[key(entity.companyId, entity.sourceType, entity.externalEntityId)] = entity
    }
}

private class FakePartyFieldProvenanceDao : PartyFieldProvenanceDao {
    val store = mutableMapOf<Triple<String, String, String>, PartyFieldProvenanceEntity>()
    private fun key(companyId: String, partyId: String, fieldName: String) = Triple(companyId, partyId, fieldName)

    override suspend fun findField(companyId: String, partyId: String, fieldName: String): PartyFieldProvenanceEntity? =
        store[key(companyId, partyId, fieldName)]
    override suspend fun findAllForParty(companyId: String, partyId: String): List<PartyFieldProvenanceEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
    override suspend fun upsert(entity: PartyFieldProvenanceEntity) {
        store[key(entity.companyId, entity.partyId, entity.fieldName)] = entity
    }
}

private class FakePartyContactPersonDao : PartyContactPersonDao {
    val store = mutableMapOf<Pair<String, String>, PartyContactPersonEntity>()
    private fun key(companyId: String, contactPersonId: String) = companyId to contactPersonId

    override suspend fun findAllForParty(companyId: String, partyId: String): List<PartyContactPersonEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedWith(compareByDescending<PartyContactPersonEntity> { it.isPrimary }.thenBy { it.name.lowercase() })
    override suspend fun findById(companyId: String, contactPersonId: String): PartyContactPersonEntity? =
        store[key(companyId, contactPersonId)]
    override suspend fun upsert(entity: PartyContactPersonEntity) {
        store[key(entity.companyId, entity.contactPersonId)] = entity
    }
    override suspend fun delete(companyId: String, contactPersonId: String) {
        store.remove(key(companyId, contactPersonId))
    }
}

private class FakeTagDao : TagDao {
    val tags = mutableMapOf<String, TagEntity>()
    val assignments = mutableSetOf<Triple<String, String, String>>()

    override suspend fun findById(tagId: String): TagEntity? = tags[tagId]
    override suspend fun findAll(): List<TagEntity> = tags.values.sortedBy { it.path.lowercase() }
    override suspend fun findByNameUnderParent(name: String, parentTagId: String?): TagEntity? =
        tags.values.firstOrNull { it.name == name && it.parentTagId == parentTagId }
    override suspend fun findChildren(parentTagId: String?): List<TagEntity> = tags.values.filter { it.parentTagId == parentTagId }
    override suspend fun upsert(entity: TagEntity) {
        tags[entity.tagId] = entity
    }
    override suspend fun assign(entity: PartyTagCrossRefEntity) {
        assignments += Triple(entity.companyId, entity.partyId, entity.tagId)
    }
    override suspend fun unassign(companyId: String, partyId: String, tagId: String) {
        assignments -= Triple(companyId, partyId, tagId)
    }
    override suspend fun findTagsForParty(companyId: String, partyId: String): List<TagEntity> =
        assignments.filter { it.first == companyId && it.second == partyId }.mapNotNull { tags[it.third] }
    override suspend fun findPartyIdsForTag(companyId: String, tagId: String): List<String> =
        assignments.filter { it.first == companyId && it.third == tagId }.map { it.second }
    override suspend fun findTagsForCompany(companyId: String): List<com.budcom.android.feature.party.data.local.PartyTagAssignmentRow> =
        assignments.filter { it.first == companyId }.mapNotNull { (company, partyId, tagId) ->
            val tag = tags[tagId] ?: return@mapNotNull null
            com.budcom.android.feature.party.data.local.PartyTagAssignmentRow(
                partyId = partyId,
                tagId = tag.tagId,
                parentTagId = tag.parentTagId,
                name = tag.name,
                path = tag.path,
                createdAt = tag.createdAt,
            )
        }
}

private class FakePartyNoteDao : com.budcom.android.feature.party.data.local.PartyNoteDao {
    val store = mutableMapOf<Pair<String, String>, com.budcom.android.feature.party.data.local.PartyNoteEntity>()
    private fun key(companyId: String, noteId: String) = companyId to noteId

    override suspend fun pageForParty(
        companyId: String,
        partyId: String,
        limit: Int,
        offset: Int,
    ): List<com.budcom.android.feature.party.data.local.PartyNoteEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedByDescending { it.createdAt }.drop(offset).take(limit)
    override suspend fun countForParty(companyId: String, partyId: String): Int =
        store.values.count { it.companyId == companyId && it.partyId == partyId }
    override suspend fun findById(companyId: String, noteId: String): com.budcom.android.feature.party.data.local.PartyNoteEntity? =
        store[key(companyId, noteId)]
    override suspend fun upsert(entity: com.budcom.android.feature.party.data.local.PartyNoteEntity) {
        store[key(entity.companyId, entity.noteId)] = entity
    }
    override suspend fun delete(companyId: String, noteId: String) {
        store.remove(key(companyId, noteId))
    }
    override suspend fun issueActivitySummary(companyId: String, partyId: String): List<com.budcom.android.feature.party.data.local.IssueActivityRow> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId && it.issueId != null }
            .groupBy { it.issueId!! }
            .map { (issueId, notes) -> com.budcom.android.feature.party.data.local.IssueActivityRow(issueId, notes.size, notes.maxOf { it.createdAt }) }
}

private class FakePartyExportEventDao : com.budcom.android.feature.party.data.local.PartyExportEventDao {
    val store = mutableMapOf<Pair<String, String>, com.budcom.android.feature.party.data.local.PartyExportEventEntity>()

    override suspend fun recentForParty(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.budcom.android.feature.party.data.local.PartyExportEventEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedByDescending { it.createdAt }.take(limit)

    override suspend fun insert(entity: com.budcom.android.feature.party.data.local.PartyExportEventEntity) {
        store[entity.companyId to entity.exportId] = entity
    }
}

private class FakePartyIssueDao : com.budcom.android.feature.party.data.local.PartyIssueDao {
    val store = mutableMapOf<Pair<String, String>, com.budcom.android.feature.party.data.local.PartyIssueEntity>()

    override suspend fun findAllForParty(companyId: String, partyId: String): List<com.budcom.android.feature.party.data.local.PartyIssueEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedWith(compareBy<com.budcom.android.feature.party.data.local.PartyIssueEntity> { it.status }.thenByDescending { it.createdAt })

    override suspend fun findById(companyId: String, issueId: String): com.budcom.android.feature.party.data.local.PartyIssueEntity? =
        store[companyId to issueId]

    override suspend fun upsert(entity: com.budcom.android.feature.party.data.local.PartyIssueEntity) {
        store[entity.companyId to entity.issueId] = entity
    }
}

/** Mirrors [com.budcom.android.feature.party.data.local.PartyTimelineDao]'s real `UNION ALL`
 * semantics by merging the exact same [FakePartyNoteDao]/[FakePartyExportEventDao] instances the
 * repository-under-test already writes through — never a separately duplicated store, so this
 * fake can never silently drift from what a real merged read would show. */
private class FakePartyTimelineDao(
    private val noteDao: FakePartyNoteDao,
    private val exportEventDao: FakePartyExportEventDao,
    private val issueDao: FakePartyIssueDao,
) : com.budcom.android.feature.party.data.local.PartyTimelineDao {

    private fun merged(companyId: String, partyId: String, issueId: String?): List<com.budcom.android.feature.party.data.local.TimelineRowEntity> {
        val notes = noteDao.store.values
            .filter { it.companyId == companyId && it.partyId == partyId && (issueId == null || it.issueId == issueId) }
            .map { note ->
                com.budcom.android.feature.party.data.local.TimelineRowEntity(
                    kind = "note", id = note.noteId, companyId = note.companyId, partyId = note.partyId,
                    timestamp = note.createdAt, updatedAt = note.updatedAt, body = note.body,
                    linkedVoucherId = note.linkedVoucherId, type = note.type, dueAt = note.dueAt,
                    completedAt = note.completedAt, issueId = note.issueId, outputFileName = null, fieldNamesCsv = null,
                )
            }
        val exports = if (issueId == null) {
            exportEventDao.store.values.filter { it.companyId == companyId && it.partyId == partyId }.map { event ->
                com.budcom.android.feature.party.data.local.TimelineRowEntity(
                    kind = "export", id = event.exportId, companyId = event.companyId, partyId = event.partyId,
                    timestamp = event.createdAt, updatedAt = event.createdAt, body = null, linkedVoucherId = null,
                    type = null, dueAt = null, completedAt = null, issueId = null,
                    outputFileName = event.outputFileName, fieldNamesCsv = event.fieldNamesCsv,
                )
            }
        } else {
            emptyList()
        }
        val issueEvents = if (issueId == null) {
            issueDao.store.values.filter { it.companyId == companyId && it.partyId == partyId }.flatMap { issue ->
                val opened = com.budcom.android.feature.party.data.local.TimelineRowEntity(
                    kind = "issue_opened", id = issue.issueId, companyId = issue.companyId, partyId = issue.partyId,
                    timestamp = issue.createdAt, updatedAt = issue.updatedAt, body = issue.title, linkedVoucherId = null,
                    type = null, dueAt = null, completedAt = null, issueId = null, outputFileName = null, fieldNamesCsv = null,
                )
                val resolved = issue.resolvedAt?.let { resolvedAt ->
                    com.budcom.android.feature.party.data.local.TimelineRowEntity(
                        kind = "issue_resolved", id = issue.issueId, companyId = issue.companyId, partyId = issue.partyId,
                        timestamp = resolvedAt, updatedAt = issue.updatedAt, body = issue.title, linkedVoucherId = null,
                        type = null, dueAt = null, completedAt = null, issueId = null, outputFileName = null, fieldNamesCsv = null,
                    )
                }
                listOfNotNull(opened, resolved)
            }
        } else {
            emptyList()
        }
        return (notes + exports + issueEvents).sortedWith(
            compareByDescending<com.budcom.android.feature.party.data.local.TimelineRowEntity> { it.timestamp }
                .thenBy { it.id }.thenBy { it.kind },
        )
    }

    override suspend fun pageTimelineForParty(
        companyId: String,
        partyId: String,
        issueId: String?,
        limit: Int,
        offset: Int,
    ): List<com.budcom.android.feature.party.data.local.TimelineRowEntity> =
        merged(companyId, partyId, issueId).drop(offset).take(limit)

    override suspend fun countTimelineForParty(companyId: String, partyId: String, issueId: String?): Int =
        merged(companyId, partyId, issueId).size
}
