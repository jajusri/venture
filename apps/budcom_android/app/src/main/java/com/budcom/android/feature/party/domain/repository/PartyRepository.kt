package com.budcom.android.feature.party.domain.repository

import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate

/**
 * Local-first, bounded/paged Party read+write surface for later Connect UI (MVP-1.1-B+) and for
 * this milestone's own seeding/tests. Never performs a live Tally/Connector call — Party data is
 * either seeded from already-synced local Ledger data or edited directly in BUDCOM.
 */
interface PartyRepository {
    suspend fun getPartyById(companyId: String, partyId: String): Party?

    /** Resolves the Party linked to a Tally ledger id (the same `guid:`/`name:`-prefixed id
     * already used as [com.budcom.android.feature.masterdata.ledger.domain.model.Ledger.id]). */
    suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party?

    suspend fun listByClassification(
        companyId: String,
        classification: PartyClassification,
        page: Int,
        pageSize: Int,
    ): PartyPage

    /** Matches on display name or normalized phone. [classification] narrows to one section
     * (e.g. Customers-tab search never surfaces a Prospect) — null searches every classification. */
    suspend fun searchParties(
        companyId: String,
        query: String,
        classification: PartyClassification?,
        page: Int,
        pageSize: Int,
    ): PartyPage

    suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson>

    suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag>

    /** Every source link for a company in one bounded read — for Connect's list-enrichment join
     * (resolving each row's linked ledger for balance/deep-link display), never per-row. */
    suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink>

    /** Every Party's tags for a company, grouped by partyId, in one bounded read — for Connect's
     * list-enrichment join, never per-row per party. */
    suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>>

    suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance>

    /** A BUDCOM-side edit to a Tally-compatible field. Always lands as [FieldProvenanceState.BudcomOnlyPending]
     * — never becomes confirmed merely because the user entered it (architecture §5/§6.2). */
    suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState

    /** Models "a later Tally sync returned this exact field" — the only path that can promote a
     * field to [FieldProvenanceState.ConfirmedFromTally]. A non-null pending BUDCOM value that
     * disagrees with [tallyValue] becomes [FieldProvenanceState.Conflict] instead of being
     * silently overwritten either direction. */
    suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState

    /**
     * Seeds/updates one [Party] per eligible ledger, keyed deterministically by the ledger's
     * stable id so the same ledger always maps to the same `partyId` and a rename never creates a
     * new Party. Idempotent — safe to call repeatedly with the same input. Also applies the
     * exact-10-digit Alias-phone rule non-destructively (architecture §8).
     */
    suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party>

    // ---- MVP-1.1-C: Prospects, contact persons, tags, notes ----

    /** Creates a BUDCOM-native Prospect: a Party with no Tally source link at all. Works fully
     * offline. Duplicate-looking names/phones are never auto-merged (architecture §5.4/§5.5) —
     * always creates a genuinely new Party with its own stable id. */
    suspend fun createProspect(companyId: String, draft: ProspectDraft): Party

    /** Single source link for one Party, if any (contrast with [getSourceLinksForCompany], the
     * bulk company-wide read) — used to resolve the target Tally ledger for XML export and to
     * decide whether accounting-context UI should show at all. */
    suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink?

    /** Creates a new contact person when [contactPersonId] is null, otherwise edits the existing
     * one in place. Setting [isPrimary] true first demotes any other primary contact for the same
     * Party, so at most one contact is ever primary. Never mutates [Party.primaryPhone]. */
    suspend fun upsertContactPerson(
        companyId: String,
        partyId: String,
        contactPersonId: String?,
        name: String,
        designation: String?,
        mobile: String?,
        whatsappNumber: String?,
        email: String?,
        isPrimary: Boolean,
    ): PartyContactPerson

    suspend fun deleteContactPerson(companyId: String, contactPersonId: String)

    /** Every tag in the global (not company-scoped) tag vocabulary — for an "add existing tag"
     * picker. */
    suspend fun getAllTags(): List<Tag>

    /** Returns the existing tag with this exact name under [parentTagId] if one exists, otherwise
     * creates it — never creates a duplicate. */
    suspend fun createOrGetTag(name: String, parentTagId: String?): Tag

    suspend fun assignTag(companyId: String, partyId: String, tagId: String)

    suspend fun unassignTag(companyId: String, partyId: String, tagId: String)

    suspend fun addNote(companyId: String, partyId: String, body: String, linkedVoucherId: String?): PartyNote

    suspend fun editNote(companyId: String, noteId: String, body: String): PartyNote?

    suspend fun deleteNote(companyId: String, noteId: String)

    /** Bounded, newest-first, indexed — never a full scan. */
    suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage

    // ---- MVP-1.1-D: Tally XML enrichment round-trip ----

    /** One review row per [com.budcom.android.feature.party.domain.model.TallyExportFieldMapping]-
     * eligible field, joining its current provenance with a display label. */
    suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate>

    /**
     * Records a successful XML export: writes a lightweight audit row (field *names* only, never
     * values) and transitions every included field's provenance to [FieldProvenanceState.Exported]
     * with `lastExportedAt` set. Never marks the whole Party confirmed — this only ever touches
     * the fields actually included in this export.
     */
    suspend fun recordExport(companyId: String, partyId: String, outputFileName: String, fieldNames: List<String>): PartyExportEvent

    /**
     * Reconciles one previously-exported field against a freshly-fetched live Tally value, using
     * field-appropriate canonical comparison (phone=normalized, email=case/trim-insensitive,
     * address=exact post-trim, GSTIN=case-insensitive) rather than raw string equality — so a
     * merely-differently-formatted match is not misreported as a conflict. A blank/unavailable
     * [tallyRawValue] never downgrades an already-`Exported` field to "unknown" (TD-027 honesty:
     * "not yet re-synced" is not the same as "failed").
     */
    suspend fun reconcileExportedFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyRawValue: String?): FieldProvenanceState

    /** Bounded, newest-first — the lightweight export audit trail for one Party. */
    suspend fun getExportHistory(companyId: String, partyId: String, limit: Int = 20): List<PartyExportEvent>
}
