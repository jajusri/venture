package com.budcom.android.feature.party.domain.usecase

import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.MoneyAmount
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilePartiesFromLedgersUseCaseTest {

    private fun ledger(id: String, name: String, parentGroup: String?, alias: String? = null) = Ledger(
        id = id,
        name = name,
        alias = alias,
        parentGroup = parentGroup,
        status = LedgerStatus.Active,
        closingBalance = MoneyAmount("100", "INR", AmountSide.Dr),
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = "t",
    )

    @Test
    fun `only ledgers under an eligible group are handed to the repository`() = runTest {
        val port = FakeLedgerSnapshotPort(
            listOf(
                ledger("guid:1", "ABC Traders", "Sundry Debtors"),
                ledger("guid:2", "XYZ Suppliers", "Sundry Creditors"),
                ledger("guid:3", "Cash", "Current Assets"),
                ledger("guid:4", "Bank OD", "Bank Accounts"),
            ),
        )
        val repository = FakePartyRepository()
        val useCase = ReconcilePartiesFromLedgersUseCase(port, repository)

        useCase("co-1")

        assertEquals(2, repository.lastSeeds?.size)
        assertEquals(
            setOf("guid:1" to PartyClassification.Customer, "guid:2" to PartyClassification.Supplier),
            repository.lastSeeds!!.map { it.ledgerId to it.classification }.toSet(),
        )
    }

    @Test
    fun `no eligible ledgers never calls the repository`() = runTest {
        val port = FakeLedgerSnapshotPort(listOf(ledger("guid:1", "Cash", "Current Assets")))
        val repository = FakePartyRepository()

        val result = ReconcilePartiesFromLedgersUseCase(port, repository)("co-1")

        assertTrue(result.isEmpty())
        assertEquals(null, repository.lastSeeds)
    }

    @Test
    fun `alias is passed through unchanged to the repository for the Alias-phone rule`() = runTest {
        val port = FakeLedgerSnapshotPort(listOf(ledger("guid:1", "ABC Traders", "Sundry Debtors", alias = "9876543210")))
        val repository = FakePartyRepository()

        ReconcilePartiesFromLedgersUseCase(port, repository)("co-1")

        assertEquals("9876543210", repository.lastSeeds!!.single().alias)
    }

    // ============================== Company isolation (TD-035, Part 5) ==============================

    /**
     * Two companies with an identical ledger natural key (same name, same parentGroup, same
     * alias/phone) — only the ledgerId and companyId differ, exactly the adversarial shape Part 5
     * asks for. Each invocation must see and reconcile only its own company's ledgers; the
     * classification derived from the (shared) parentGroup text must never leak or merge across
     * the companyId boundary this use case is always explicitly scoped by.
     */
    @Test
    fun `identical ledger name, alias, and parent group across two companies never leak into each other's seed set`() = runTest {
        val port = CompanyScopedFakeLedgerSnapshotPort(
            mapOf(
                "co-A" to listOf(ledger("guid:shared", "Shared Traders", "Sundry Debtors", alias = "9876543210")),
                "co-B" to listOf(ledger("guid:shared", "Shared Traders", "Sundry Debtors", alias = "9876543210")),
            ),
        )
        val repository = FakePartyRepository()
        val useCase = ReconcilePartiesFromLedgersUseCase(port, repository)

        useCase("co-A")
        val seedsForA = repository.lastSeeds
        useCase("co-B")
        val seedsForB = repository.lastSeeds

        assertEquals(1, seedsForA?.size)
        assertEquals(1, seedsForB?.size)
        assertEquals(listOf("co-A", "co-B"), port.requestedCompanyIds)
        // Every seed carries the same identity-bearing fields either run would produce (same
        // ledgerId/name/alias/classification, by natural-key construction) -- the real, separate
        // guarantee this test exists to prove is that B's invocation used ONLY B's own port
        // response (never A's, never a merged/union set), verified structurally below.
        assertEquals(seedsForA!!.single().ledgerId, seedsForB!!.single().ledgerId)
        assertEquals(1, port.callCountFor("co-A"))
        assertEquals(1, port.callCountFor("co-B"))
    }

    /** A ledger present only in one company must never be visible to the other company's run. */
    @Test
    fun `a ledger unique to one company never appears in the other company's seed set`() = runTest {
        val port = CompanyScopedFakeLedgerSnapshotPort(
            mapOf(
                "co-A" to listOf(ledger("guid:a-only", "A-Only Traders", "Sundry Debtors")),
                "co-B" to listOf(ledger("guid:b-only", "B-Only Traders", "Sundry Creditors")),
            ),
        )
        val repository = FakePartyRepository()
        val useCase = ReconcilePartiesFromLedgersUseCase(port, repository)

        useCase("co-A")
        assertEquals(listOf("guid:a-only"), repository.lastSeeds!!.map { it.ledgerId })

        useCase("co-B")
        assertEquals(listOf("guid:b-only"), repository.lastSeeds!!.map { it.ledgerId })
    }
}

private class FakeLedgerSnapshotPort(private val ledgers: List<Ledger>) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = ledgers
}

/** Company-aware fake — unlike [FakeLedgerSnapshotPort], only ever returns the ledgers registered
 * for the exact [companyId] passed in, structurally ruling out any cross-company leakage at this
 * fake's own boundary (the real [com.budcom.android.feature.masterdata.ledger.data.repository.LedgerSnapshotPortImpl]
 * is equally scoped, via a `companyId`-filtered Room query). */
private class CompanyScopedFakeLedgerSnapshotPort(
    private val byCompany: Map<String, List<Ledger>>,
) : LedgerSnapshotPort {
    val requestedCompanyIds = mutableListOf<String>()

    override suspend fun getCachedLedgers(companyId: String): List<Ledger> {
        requestedCompanyIds += companyId
        return byCompany[companyId].orEmpty()
    }

    fun callCountFor(companyId: String): Int = requestedCompanyIds.count { it == companyId }
}

private class FakePartyRepository : PartyRepository {
    var lastSeeds: List<EligibleLedgerSeed>? = null

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = null
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = null
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun searchParties(
        companyId: String,
        query: String,
        classification: PartyClassification?,
        page: Int,
        pageSize: Int,
    ) = PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = emptyList()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = emptyList()
    override suspend fun getSourceLinksForCompany(companyId: String): List<com.budcom.android.feature.party.domain.model.PartySourceLink> = emptyList()
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = emptyMap()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = emptyList()
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?) =
        FieldProvenanceState.BudcomOnlyPending
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?) =
        FieldProvenanceState.ConfirmedFromTally

    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> {
        lastSeeds = seeds
        return seeds.map {
            Party(
                companyId = companyId,
                partyId = "party-${it.ledgerId}",
                displayName = it.ledgerName,
                classification = it.classification,
                primaryPhone = null,
                primaryPhoneNormalized = null,
                primaryEmail = null,
                addressLine1 = null,
                addressCity = null,
                addressState = null,
                addressPincode = null,
                gstin = null,
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
    }

    override suspend fun applyLedgerContactDetailsBulk(
        companyId: String,
        items: List<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails>,
    ): com.budcom.android.feature.party.domain.model.BulkContactSeedResult = error("unused")

    override suspend fun createProspect(
        companyId: String,
        draft: com.budcom.android.feature.party.domain.model.ProspectDraft,
    ): Party = error("unused")
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): com.budcom.android.feature.party.domain.model.PartySourceLink? = error("unused")
    override suspend fun upsertContactPerson(
        companyId: String,
        partyId: String,
        contactPersonId: String?,
        name: String,
        designation: String?,
        mobile: String?,
        whatsappNumber: String?,
        email: String?,
        isPrimary: Boolean,
    ): PartyContactPerson = error("unused")
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String): Unit = error("unused")
    override suspend fun getAllTags(): List<Tag> = error("unused")
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = error("unused")
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String): Unit = error("unused")
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String): Unit = error("unused")
    override suspend fun addNote(
        companyId: String,
        partyId: String,
        body: String,
        linkedVoucherId: String?,
        type: com.budcom.android.feature.party.domain.model.NoteType,
        dueAt: Long?,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.PartyNote = error("unused")
    override suspend fun editNote(
        companyId: String,
        noteId: String,
        body: String,
        type: com.budcom.android.feature.party.domain.model.NoteType,
        dueAt: Long?,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.PartyNote? = error("unused")
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): com.budcom.android.feature.party.domain.model.PartyNote? =
        error("unused")
    override suspend fun deleteNote(companyId: String, noteId: String): Unit = error("unused")
    override suspend fun createIssue(companyId: String, partyId: String, title: String): com.budcom.android.feature.party.domain.model.PartyIssue =
        error("unused")
    override suspend fun resolveIssue(companyId: String, issueId: String): com.budcom.android.feature.party.domain.model.PartyIssue? = error("unused")
    override suspend fun reopenIssue(companyId: String, issueId: String): com.budcom.android.feature.party.domain.model.PartyIssue? = error("unused")
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<com.budcom.android.feature.party.domain.model.PartyIssue> =
        error("unused")
    override suspend fun getIssueActivitySummary(
        companyId: String,
        partyId: String,
    ): Map<String, com.budcom.android.feature.party.domain.model.IssueActivitySummary> = error("unused")
    override suspend fun getNotesForParty(
        companyId: String,
        partyId: String,
        page: Int,
        pageSize: Int,
    ): com.budcom.android.feature.party.domain.model.PartyNotePage = error("unused")
    override suspend fun getTimelineForParty(
        companyId: String,
        partyId: String,
        page: Int,
        pageSize: Int,
        issueId: String?,
    ): com.budcom.android.feature.party.domain.model.TimelineEntryPage = error("unused")
    override suspend fun getExportCandidates(
        companyId: String,
        partyId: String,
    ): List<com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate> = error("unused")
    override suspend fun recordExport(
        companyId: String,
        partyId: String,
        outputFileName: String,
        fieldNames: List<String>,
    ): com.budcom.android.feature.party.domain.model.PartyExportEvent = error("unused")
    override suspend fun reconcileExportedFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyRawValue: String?,
    ): FieldProvenanceState = error("unused")
    override suspend fun getExportHistory(
        companyId: String,
        partyId: String,
        limit: Int,
    ): List<com.budcom.android.feature.party.domain.model.PartyExportEvent> = error("unused")
}
