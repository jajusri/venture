package com.budcom.android.feature.party.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerPartyEligibilityPolicyTest {

    @Test
    fun `a debtor group classifies as Customer`() {
        assertEquals(PartyClassification.Customer, LedgerPartyEligibilityPolicy.classify("Sundry Debtors"))
        assertEquals(PartyClassification.Customer, LedgerPartyEligibilityPolicy.classify("Local Debtors"))
    }

    @Test
    fun `a creditor group classifies as Supplier`() {
        assertEquals(PartyClassification.Supplier, LedgerPartyEligibilityPolicy.classify("Sundry Creditors"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(PartyClassification.Customer, LedgerPartyEligibilityPolicy.classify("SUNDRY DEBTORS"))
    }

    @Test
    fun `unrelated groups are not seeded`() {
        assertNull(LedgerPartyEligibilityPolicy.classify("Bank Accounts"))
        assertNull(LedgerPartyEligibilityPolicy.classify("Cash-in-hand"))
        assertNull(LedgerPartyEligibilityPolicy.classify("Fixed Assets"))
        assertNull(LedgerPartyEligibilityPolicy.classify("Duties & Taxes"))
        assertNull(LedgerPartyEligibilityPolicy.classify("Direct Expenses"))
    }

    @Test
    fun `null or blank group is not seeded`() {
        assertNull(LedgerPartyEligibilityPolicy.classify(null))
        assertNull(LedgerPartyEligibilityPolicy.classify(""))
        assertNull(LedgerPartyEligibilityPolicy.classify("   "))
    }

    @Test
    fun `a Tally ledger is never classified as Prospect`() {
        // Prospect is, by definition, a BUDCOM-created party never represented by a Tally ledger.
        listOf("Sundry Debtors", "Sundry Creditors", "Bank Accounts", null).forEach { group ->
            assertEquals(false, LedgerPartyEligibilityPolicy.classify(group) == PartyClassification.Prospect)
        }
    }
}
