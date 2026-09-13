package com.jajusri.venture.feature.party.domain.model

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
        // Prospect is, by definition, a VENTURE-created party never represented by a Tally ledger.
        listOf("Sundry Debtors", "Sundry Creditors", "Bank Accounts", null).forEach { group ->
            assertEquals(false, LedgerPartyEligibilityPolicy.classify(group) == PartyClassification.Prospect)
        }
    }

    // ============================== classifyGroup (TD-035) ==============================

    @Test
    fun `classifyGroup DEBTOR matches the same keyword rule as classify's Customer case`() {
        assertEquals(LedgerGroupClassification.DEBTOR, LedgerPartyEligibilityPolicy.classifyGroup("Sundry Debtors"))
        assertEquals(LedgerGroupClassification.DEBTOR, LedgerPartyEligibilityPolicy.classifyGroup("Local Debtors"))
        assertEquals(LedgerGroupClassification.DEBTOR, LedgerPartyEligibilityPolicy.classifyGroup("SUNDRY DEBTORS"))
    }

    @Test
    fun `classifyGroup CREDITOR matches the same keyword rule as classify's Supplier case`() {
        assertEquals(LedgerGroupClassification.CREDITOR, LedgerPartyEligibilityPolicy.classifyGroup("Sundry Creditors"))
    }

    @Test
    fun `classifyGroup OTHER is distinct from UNKNOWN -- a real, present, non-debtor-creditor group`() {
        assertEquals(LedgerGroupClassification.OTHER, LedgerPartyEligibilityPolicy.classifyGroup("Bank Accounts"))
        assertEquals(LedgerGroupClassification.OTHER, LedgerPartyEligibilityPolicy.classifyGroup("Cash-in-hand"))
        assertEquals(LedgerGroupClassification.OTHER, LedgerPartyEligibilityPolicy.classifyGroup("Fixed Assets"))
        assertEquals(LedgerGroupClassification.OTHER, LedgerPartyEligibilityPolicy.classifyGroup("Duties & Taxes"))
        assertEquals(LedgerGroupClassification.OTHER, LedgerPartyEligibilityPolicy.classifyGroup("Direct Expenses"))
    }

    @Test
    fun `classifyGroup UNKNOWN is distinct from OTHER -- a blank or missing group value`() {
        assertEquals(LedgerGroupClassification.UNKNOWN, LedgerPartyEligibilityPolicy.classifyGroup(null))
        assertEquals(LedgerGroupClassification.UNKNOWN, LedgerPartyEligibilityPolicy.classifyGroup(""))
        assertEquals(LedgerGroupClassification.UNKNOWN, LedgerPartyEligibilityPolicy.classifyGroup("   "))
    }

    @Test
    fun `classify and classifyGroup agree exactly -- OTHER and UNKNOWN both collapse to null in classify`() {
        val cases = listOf(
            "Sundry Debtors", "Sundry Creditors", "Bank Accounts", "Fixed Assets", null, "", "   ",
        )
        for (group in cases) {
            val expectedFromClassify = when (LedgerPartyEligibilityPolicy.classifyGroup(group)) {
                LedgerGroupClassification.DEBTOR -> PartyClassification.Customer
                LedgerGroupClassification.CREDITOR -> PartyClassification.Supplier
                LedgerGroupClassification.OTHER, LedgerGroupClassification.UNKNOWN -> null
            }
            assertEquals(
                "classify(\"$group\") must derive exactly from classifyGroup(\"$group\")",
                expectedFromClassify,
                LedgerPartyEligibilityPolicy.classify(group),
            )
        }
    }
}
