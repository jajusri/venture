package com.budcom.android.feature.party.domain.model

/**
 * Decides which Tally ledgers are eligible to become a BUDCOM [Party] during MVP-1.1-A seeding,
 * and what classification they get.
 *
 * Not every Tally ledger is a Customer/Prospect — architecture §"Party seeding" is explicit about
 * this. No repository-locked eligibility rule exists yet for MVP-1.1 (this is genuinely the first
 * implementation), so this policy applies a conservative, explicit, testable default rather than
 * blocking on a product decision: a ledger whose immediate Tally parent group name contains
 * "debtor" seeds a [PartyClassification.Customer]; "creditor" seeds a [PartyClassification.Supplier].
 * Every other group (Bank, Cash, Fixed Assets, Duties & Taxes, Capital Account, expense/income
 * ledgers, etc.) is left unseeded in 1.1-A.
 *
 * Known limitation, documented rather than silently assumed away: this matches only the ledger's
 * immediate `parentGroup` string (what the Connector already extracts), not a full Tally group
 * hierarchy walk — a ledger filed under a custom sub-group whose own name doesn't contain
 * "debtor"/"creditor" (e.g. a user-created "Local Debtors" is matched, but "AP Region 1" filed
 * under Sundry Debtors is not) will not be auto-seeded. Acceptable for a foundation milestone with
 * no UI yet; revisit once real seeded-Party evidence from a live company exists (PDL-012).
 *
 * [PartyClassification.Prospect] is never produced here — a Prospect is, by definition (Connect
 * spec §4.1), a BUDCOM-created party not yet represented by an accounting ledger, so a Tally
 * ledger can never itself be the origin of a Prospect.
 */
object LedgerPartyEligibilityPolicy {

    private val CUSTOMER_GROUP_KEYWORDS = listOf("debtor")
    private val SUPPLIER_GROUP_KEYWORDS = listOf("creditor")

    fun classify(parentGroup: String?): PartyClassification? {
        val group = parentGroup?.lowercase().orEmpty()
        if (group.isBlank()) return null
        return when {
            CUSTOMER_GROUP_KEYWORDS.any { group.contains(it) } -> PartyClassification.Customer
            SUPPLIER_GROUP_KEYWORDS.any { group.contains(it) } -> PartyClassification.Supplier
            else -> null
        }
    }
}
