package com.jajusri.venture.feature.party.domain.model

/**
 * Decides which Tally ledgers are eligible to become a VENTURE [Party] during MVP-1.1-A seeding,
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
 * spec §4.1), a VENTURE-created party not yet represented by an accounting ledger, so a Tally
 * ledger can never itself be the origin of a Prospect.
 */
object LedgerPartyEligibilityPolicy {

    private val CUSTOMER_GROUP_KEYWORDS = listOf("debtor")
    private val SUPPLIER_GROUP_KEYWORDS = listOf("creditor")

    /**
     * The lower-level, four-state classification of a raw Tally `parentGroup` value alone,
     * independent of any seeding-policy decision about what to do with it (TD-035). [DEBTOR] and
     * [CREDITOR] match the same fixed keyword rule [classify] has always used. [OTHER] is a real,
     * present group value that matched neither — e.g. "Bank Accounts", "Fixed Assets". [UNKNOWN]
     * is a blank/missing group value. The two are kept distinct because "we know the group and it
     * isn't a debtor/creditor group" is a different fact than "we don't know the group at all" —
     * collapsing them into one `null` (as [classify] still does, for its own unchanged contract)
     * would hide that distinction from anything that needs it (diagnostics, a future Connect
     * "why isn't this ledger a customer" surface, TD-035's own investigation record).
     */
    fun classifyGroup(parentGroup: String?): LedgerGroupClassification {
        val group = parentGroup?.lowercase().orEmpty()
        if (group.isBlank()) return LedgerGroupClassification.UNKNOWN
        return when {
            CUSTOMER_GROUP_KEYWORDS.any { group.contains(it) } -> LedgerGroupClassification.DEBTOR
            SUPPLIER_GROUP_KEYWORDS.any { group.contains(it) } -> LedgerGroupClassification.CREDITOR
            else -> LedgerGroupClassification.OTHER
        }
    }

    /**
     * The existing MVP-1.1-A seeding-policy contract, unchanged: only a [LedgerGroupClassification.DEBTOR]
     * or [LedgerGroupClassification.CREDITOR] group is eligible to seed a Party — [OTHER] and
     * [UNKNOWN] both remain `null` here exactly as before, so [ReconcilePartiesFromLedgersUseCase]'s
     * behavior is byte-for-byte identical to pre-TD-035. Delegates to [classifyGroup] rather than
     * duplicating the keyword rule.
     */
    fun classify(parentGroup: String?): PartyClassification? = when (classifyGroup(parentGroup)) {
        LedgerGroupClassification.DEBTOR -> PartyClassification.Customer
        LedgerGroupClassification.CREDITOR -> PartyClassification.Supplier
        LedgerGroupClassification.OTHER, LedgerGroupClassification.UNKNOWN -> null
    }
}

/**
 * Deterministic, four-state classification of a Tally ledger's raw `parentGroup` text (TD-035).
 * The raw value itself remains available separately (`Ledger.parentGroup`) — this type is only
 * ever the *derived* classification, never a replacement for the source text.
 */
enum class LedgerGroupClassification { DEBTOR, CREDITOR, OTHER, UNKNOWN }
