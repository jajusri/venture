package com.budcom.android.feature.party.domain.repository

import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.Tag

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
}
