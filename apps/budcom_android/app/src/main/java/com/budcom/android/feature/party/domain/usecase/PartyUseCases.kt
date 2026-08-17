package com.budcom.android.feature.party.domain.usecase

import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.LedgerPartyEligibilityPolicy
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import javax.inject.Inject

class GetPartyByIdUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String): Party? = repository.getPartyById(companyId, partyId)
}

class GetPartyForLedgerUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, ledgerId: String): Party? = repository.getPartyForLedger(companyId, ledgerId)
}

class ListPartiesByClassificationUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(
        companyId: String,
        classification: PartyClassification,
        page: Int = 1,
        pageSize: Int = 50,
    ): PartyPage = repository.listByClassification(companyId, classification, page, pageSize)
}

class SearchPartiesUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(
        companyId: String,
        query: String,
        classification: PartyClassification? = null,
        page: Int = 1,
        pageSize: Int = 50,
    ): PartyPage = repository.searchParties(companyId, query, classification, page, pageSize)
}

class GetContactPersonsUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String): List<PartyContactPerson> =
        repository.getContactPersons(companyId, partyId)
}

class GetTagsForPartyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String): List<Tag> = repository.getTagsForParty(companyId, partyId)
}

class GetPartySourceLinksForCompanyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String): List<PartySourceLink> = repository.getSourceLinksForCompany(companyId)
}

class GetPartyTagsForCompanyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String): Map<String, List<Tag>> = repository.getTagsForCompany(companyId)
}

class GetFieldProvenanceUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String): List<PartyFieldProvenance> =
        repository.getFieldProvenance(companyId, partyId)
}

class UpdateBudcomOnlyFieldUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState =
        repository.updateBudcomOnlyField(companyId, partyId, fieldName, value)
}

class ConfirmFieldFromTallyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState =
        repository.confirmFieldFromTally(companyId, partyId, fieldName, tallyValue)
}

/**
 * Orchestrates MVP-1.1-A Party seeding: reads whatever ledgers are already locally cached for the
 * company (no network call of its own — [LedgerSnapshotPort] is local-only), filters to those
 * eligible per [LedgerPartyEligibilityPolicy], and reconciles them into Parties. Idempotent and
 * safe to call repeatedly, e.g. after every successful Ledger sync.
 */
class ReconcilePartiesFromLedgersUseCase @Inject constructor(
    private val ledgerSnapshotPort: LedgerSnapshotPort,
    private val repository: PartyRepository,
) {
    suspend operator fun invoke(companyId: String): List<Party> {
        val eligible = ledgerSnapshotPort.getCachedLedgers(companyId).mapNotNull { ledger ->
            val classification = LedgerPartyEligibilityPolicy.classify(ledger.parentGroup) ?: return@mapNotNull null
            EligibleLedgerSeed(
                ledgerId = ledger.id,
                ledgerName = ledger.name,
                alias = ledger.alias,
                classification = classification,
            )
        }
        if (eligible.isEmpty()) return emptyList()
        return repository.reconcilePartiesFromEligibleLedgers(companyId, eligible)
    }
}

// ---- MVP-1.1-C: Prospects, contact persons, tags, notes ----

class CreateProspectUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, draft: ProspectDraft): Party = repository.createProspect(companyId, draft)
}

class GetSourceLinkForPartyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String): PartySourceLink? =
        repository.getSourceLinkForParty(companyId, partyId)
}

class UpsertContactPersonUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(
        companyId: String,
        partyId: String,
        contactPersonId: String?,
        name: String,
        designation: String?,
        mobile: String?,
        whatsappNumber: String?,
        email: String?,
        isPrimary: Boolean,
    ): PartyContactPerson = repository.upsertContactPerson(
        companyId, partyId, contactPersonId, name, designation, mobile, whatsappNumber, email, isPrimary,
    )
}

class DeleteContactPersonUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, contactPersonId: String) = repository.deleteContactPerson(companyId, contactPersonId)
}

class GetAllTagsUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(): List<Tag> = repository.getAllTags()
}

class CreateOrGetTagUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(name: String, parentTagId: String?): Tag = repository.createOrGetTag(name, parentTagId)
}

class AssignTagUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, tagId: String) = repository.assignTag(companyId, partyId, tagId)
}

class UnassignTagUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, tagId: String) = repository.unassignTag(companyId, partyId, tagId)
}

class AddNoteUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, body: String, linkedVoucherId: String?): PartyNote =
        repository.addNote(companyId, partyId, body, linkedVoucherId)
}

class EditNoteUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, noteId: String, body: String): PartyNote? = repository.editNote(companyId, noteId, body)
}

class DeleteNoteUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, noteId: String) = repository.deleteNote(companyId, noteId)
}

class GetNotesForPartyUseCase @Inject constructor(private val repository: PartyRepository) {
    suspend operator fun invoke(companyId: String, partyId: String, page: Int = 1, pageSize: Int = 20): PartyNotePage =
        repository.getNotesForParty(companyId, partyId, page, pageSize)
}
