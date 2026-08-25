package com.budcom.android.feature.party.domain.usecase

import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.budcom.android.feature.party.domain.model.BulkContactSeedResult
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate
import com.budcom.android.feature.party.domain.model.TimelineEntryPage
import com.budcom.android.feature.party.domain.repository.PartyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApplyLedgerContactDetailsBulkUseCaseTest {
    @Test
    fun `delegates straight to the repository with the given companyId and items`() = runTest {
        var seenCompanyId: String? = null
        var seenItems: List<LedgerContactDetails>? = null
        val expected = BulkContactSeedResult(matchedLedgers = 1, unmatchedLedgers = 0, fieldsFilled = 2, fieldsConfirmed = 1, fieldsConflicted = 0)
        val repository = object : ThrowingPartyRepository() {
            override suspend fun applyLedgerContactDetailsBulk(companyId: String, items: List<LedgerContactDetails>): BulkContactSeedResult {
                seenCompanyId = companyId
                seenItems = items
                return expected
            }
        }
        val items = listOf(LedgerContactDetails("guid:abc", null, "x@example.com", null, null, null, null))

        val result = ApplyLedgerContactDetailsBulkUseCase(repository)("co-1", items)

        assertEquals("co-1", seenCompanyId)
        assertSame(items, seenItems)
        assertSame(expected, result)
    }
}

/** Every member throws by default -- tests override only what they exercise. */
private abstract class ThrowingPartyRepository : PartyRepository {
    override suspend fun getPartyById(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun promoteProspectToCustomer(companyId: String, partyId: String): Party? = error("unused")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = error("unused")
    override suspend fun listByClassification(companyId: String, classification: com.budcom.android.feature.party.domain.model.PartyClassification, page: Int, pageSize: Int): PartyPage = error("unused")
    override suspend fun searchParties(companyId: String, query: String, classification: com.budcom.android.feature.party.domain.model.PartyClassification?, page: Int, pageSize: Int): PartyPage = error("unused")
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = error("unused")
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = error("unused")
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> = error("unused")
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = error("unused")
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = error("unused")
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState = error("unused")
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState = error("unused")
    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = error("unused")
    override suspend fun applyLedgerContactDetailsBulk(companyId: String, items: List<LedgerContactDetails>): BulkContactSeedResult = error("unused")
    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party = error("unused")
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = error("unused")
    override suspend fun upsertContactPerson(companyId: String, partyId: String, contactPersonId: String?, name: String, designation: String?, mobile: String?, whatsappNumber: String?, email: String?, isPrimary: Boolean): PartyContactPerson = error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) = error("unused")
    override suspend fun getAllTags(): List<Tag> = error("unused")
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = error("unused")
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String) = error("unused")
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String) = error("unused")
    override suspend fun addNote(companyId: String, partyId: String, body: String, linkedVoucherId: String?, type: NoteType, dueAt: Long?, issueId: String?): PartyNote = error("unused")
    override suspend fun editNote(companyId: String, noteId: String, body: String, type: NoteType, dueAt: Long?, issueId: String?): PartyNote? = error("unused")
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? = error("unused")
    override suspend fun deleteNote(companyId: String, noteId: String) = error("unused")
    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage = error("unused")
    override suspend fun getTimelineForParty(companyId: String, partyId: String, page: Int, pageSize: Int, issueId: String?): TimelineEntryPage = error("unused")
    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue = error("unused")
    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = error("unused")
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> = error("unused")
    override suspend fun getIssueActivitySummary(companyId: String, partyId: String): Map<String, com.budcom.android.feature.party.domain.model.IssueActivitySummary> = error("unused")
    override suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate> = error("unused")
    override suspend fun recordExport(companyId: String, partyId: String, outputFileName: String, fieldNames: List<String>): PartyExportEvent = error("unused")
    override suspend fun reconcileExportedFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyRawValue: String?): FieldProvenanceState = error("unused")
    override suspend fun getExportHistory(companyId: String, partyId: String, limit: Int): List<PartyExportEvent> = error("unused")
}
