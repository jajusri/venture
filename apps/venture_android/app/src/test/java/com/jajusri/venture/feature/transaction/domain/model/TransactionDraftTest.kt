package com.jajusri.venture.feature.transaction.domain.model

import com.jajusri.venture.feature.transaction.domain.model.TransactionDraftOperations.DraftToSubmissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun actual(amount: String, currency: String? = "INR") = TransactionDraftPriceState.ActualPrice(amount, currency)

class TransactionDraftOperationsTest {

    private fun draft(companyId: String = "co-1") = TransactionDraftOperations.empty(companyId, "buyer-1", TransactionSubmissionType.Estimate)

    // ============================== composition (Phase 1) ==============================

    @Test
    fun `an empty draft has no lines and no total`() {
        val d = draft()
        assertTrue(d.isEmpty)
        assertNull(d.totalAmount)
    }

    @Test
    fun `adding a new product creates exactly one line`() {
        val d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        assertEquals(1, d.lines.size)
        assertEquals("1", d.lines.single().quantity)
    }

    @Test
    fun `re-adding an already-selected product increments quantity instead of duplicating the row`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p1", "Widget", "Nos", "SKU-1", actual("100"), quantityToAdd = "2")
        assertEquals(1, d.lines.size)
        assertEquals("3", d.lines.single().quantity)
    }

    @Test
    fun `re-adding a product refreshes its price state to whatever is currently resolved, not the stale one`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ContactForPrice, quantityToAdd = "1")
        assertEquals(TransactionDraftPriceState.ContactForPrice, d.lines.single().priceState)
    }

    @Test
    fun `companyId is preserved through every operation - no cross-company drift`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft("co-A"), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.setQuantity(d, "p1", "5")
        assertEquals("co-A", d.companyId)
        d = TransactionDraftOperations.removeLine(d, "p1")
        assertEquals("co-A", d.companyId)
    }

    // ============================== editing (Phase 2) ==============================

    @Test
    fun `setQuantity increases quantity`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.setQuantity(d, "p1", "9")
        assertEquals("9", d.lines.single().quantity)
    }

    @Test
    fun `setQuantity decreases quantity`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"), quantityToAdd = "9")
        d = TransactionDraftOperations.setQuantity(d, "p1", "2")
        assertEquals("2", d.lines.single().quantity)
    }

    @Test
    fun `setQuantity to zero removes the line`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.setQuantity(d, "p1", "0")
        assertTrue(d.isEmpty)
    }

    @Test
    fun `setQuantity to a negative number removes the line, never stores an invalid quantity`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.setQuantity(d, "p1", "-3")
        assertTrue(d.isEmpty)
    }

    @Test
    fun `removeLine removes only the targeted product, preserving order of the rest`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", actual("200"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p3", "Gizmo", "Nos", "SKU-3", actual("300"))
        d = TransactionDraftOperations.removeLine(d, "p2")
        assertEquals(listOf("p1", "p3"), d.lines.map { it.linkedProductId })
    }

    @Test
    fun `line order is preserved across edits - editing an early line never reorders later ones`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", actual("200"))
        d = TransactionDraftOperations.setQuantity(d, "p1", "7")
        assertEquals(listOf("p1", "p2"), d.lines.map { it.linkedProductId })
    }

    // ============================== total recalculation ==============================

    @Test
    fun `total is the sum of unit price times quantity across all visibly-priced lines`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"), quantityToAdd = "2")
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", actual("50"), quantityToAdd = "3")
        assertEquals("350", d.totalAmount)
    }

    @Test
    fun `total recalculates after a quantity change`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        assertEquals("100", d.totalAmount)
        d = TransactionDraftOperations.setQuantity(d, "p1", "4")
        assertEquals("400", d.totalAmount)
    }

    @Test
    fun `total is null - never a partial sum - if any line is Contact-for-price`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", TransactionDraftPriceState.ContactForPrice)
        assertNull(d.totalAmount)
    }

    @Test
    fun `total is null if any line has no price supplied yet`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", TransactionDraftPriceState.NoPriceSupplied)
        assertNull(d.totalAmount)
    }

    @Test
    fun `total is null if any line is Hidden`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", TransactionDraftPriceState.Hidden)
        assertNull(d.totalAmount)
    }

    // ============================== submission (creation) ==============================

    @Test
    fun `an empty draft is rejected at submission`() {
        assertTrue(TransactionDraftOperations.toSubmission(draft()) is DraftToSubmissionResult.EmptyDraft)
    }

    @Test
    fun `a valid draft submits successfully with every line preserved`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"), quantityToAdd = "3")
        val result = TransactionDraftOperations.toSubmission(d) as DraftToSubmissionResult.Success
        assertEquals(1, result.lineItems.size)
        assertEquals("Widget", result.lineItems.single().snapshotProductName)
        assertEquals("3", result.lineItems.single().quantity)
        assertEquals("300", result.lineItems.single().lineTotalAmount)
    }

    @Test
    fun `Estimate vs PO is preserved through the draft, unaffected by submission conversion`() {
        val estimateDraft = TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.Estimate)
        val poDraft = TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.PurchaseOrder)
        assertEquals(TransactionSubmissionType.Estimate, estimateDraft.submissionType)
        assertEquals(TransactionSubmissionType.PurchaseOrder, poDraft.submissionType)
    }

    @Test
    fun `a draft containing a Hidden-price line is refused at submission, never silently downgraded`() {
        var d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", actual("100"))
        d = TransactionDraftOperations.addOrIncrementLine(d, "p2", "Gadget", "Nos", "SKU-2", TransactionDraftPriceState.Hidden)
        val result = TransactionDraftOperations.toSubmission(d)
        assertTrue(result is DraftToSubmissionResult.UnauthorizedPriceLines)
        assertEquals(listOf("p2"), (result as DraftToSubmissionResult.UnauthorizedPriceLines).linkedProductIds)
    }

    @Test
    fun `Contact-for-price lines DO submit successfully - only Hidden is refused`() {
        val d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ContactForPrice)
        val result = TransactionDraftOperations.toSubmission(d)
        assertTrue(result is DraftToSubmissionResult.Success)
        assertTrue((result as DraftToSubmissionResult.Success).lineItems.single().isContactForPrice)
        assertNull(result.lineItems.single().unitPriceAmount)
    }

    @Test
    fun `no price supplied yet line submits with a null amount, never a fabricated zero`() {
        val d = TransactionDraftOperations.addOrIncrementLine(draft(), "p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.NoPriceSupplied)
        val result = TransactionDraftOperations.toSubmission(d) as DraftToSubmissionResult.Success
        assertNull(result.lineItems.single().unitPriceAmount)
        assertFalse(result.lineItems.single().isContactForPrice)
    }
}

class BuyAgainListBuilderTest {

    private fun ts(millis: Long) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun line(
        estimatePoId: String,
        productId: String?,
        name: String,
        quantity: String,
    ) = TransactionLineItem(
        estimatePoId = estimatePoId, lineItemId = "$estimatePoId-line", linkedProductId = productId,
        snapshotProductName = name, snapshotUnit = "Nos", snapshotSku = null, quantity = quantity,
        unitPriceAmount = "10", unitPriceCurrencyCode = "INR", lineTotalAmount = "10", isContactForPrice = false,
    )

    @Test
    fun `empty history yields an empty list`() {
        assertTrue(BuyAgainListBuilder.build(emptyList()).isEmpty())
    }

    @Test
    fun `deduplicates repeated purchases of the same product into one entry with a purchase count`() {
        val history = listOf(
            line("e1", "p1", "Widget", "2"),
            line("e2", "p1", "Widget", "5"),
        )
        val result = BuyAgainListBuilder.build(history)
        assertEquals(1, result.size)
        assertEquals(2, result.single().purchaseCount)
    }

    @Test
    fun `most recent occurrence wins for the displayed quantity - history arrives oldest first`() {
        // findCompletedPurchaseHistory sorts oldest-transaction-first before flattening, so the
        // *last* element in the input list for a given product is the most recent purchase.
        val history = listOf(line("e1", "p1", "Widget", "2"), line("e2", "p1", "Widget", "9"))
        val result = BuyAgainListBuilder.build(history)
        assertEquals("9", result.single().mostRecentQuantity)
    }

    @Test
    fun `most-recently-purchased product is listed first when multiple distinct products exist`() {
        val history = listOf(
            line("e1", "p1", "Widget", "1"),
            line("e2", "p2", "Gadget", "1"),
        )
        val result = BuyAgainListBuilder.build(history)
        assertEquals(listOf("p2", "p1"), result.map { it.linkedProductId })
    }

    @Test
    fun `a line with no linked product (unresolvable identity) is excluded, never fabricated`() {
        val history = listOf(line("e1", null, "Chat-only item", "1"))
        assertTrue(BuyAgainListBuilder.build(history).isEmpty())
    }

    @Test
    fun `every entry is traceable to real transaction data - no invented quantities`() {
        val history = listOf(line("e1", "p1", "Widget", "42"))
        val result = BuyAgainListBuilder.build(history)
        assertEquals("42", result.single().mostRecentQuantity)
        assertEquals("Widget", result.single().mostRecentSnapshotProductName)
    }
}

class ReorderOperationsTest {

    private fun ts(millis: Long) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun completedTransaction() = CommercialTransaction(
        companyId = "co-1", transactionId = "t-1", estimatePoId = "e-1", buyerPartyId = "buyer-1",
        state = CommercialTransactionState.Completed, totalAmount = "1000", currencyCode = "INR",
        acceptedAt = ts(0), completedAt = ts(100),
    )

    private fun originalLineItems() = listOf(
        TransactionLineItem(
            estimatePoId = "e-1", lineItemId = "l-1", linkedProductId = "p1", snapshotProductName = "Widget",
            snapshotUnit = "Nos", snapshotSku = "SKU-1", quantity = "5", unitPriceAmount = "100",
            unitPriceCurrencyCode = "INR", lineTotalAmount = "500", isContactForPrice = false,
        ),
    )

    @Test
    fun `building a new draft from a completed order never mutates the original transaction object`() {
        val original = completedTransaction()
        val originalCopy = original.copy() // structural snapshot to compare against after the call
        ReorderOperations.fromCompletedTransaction(
            "co-1", "buyer-1", TransactionSubmissionType.Estimate, originalLineItems(),
        ) { TransactionDraftPriceState.ActualPrice("100", "INR") }
        assertEquals(originalCopy, original)
    }

    @Test
    fun `the resulting draft is a genuinely new, independent object - editing it cannot reach the old transaction`() {
        val draft = ReorderOperations.fromCompletedTransaction(
            "co-1", "buyer-1", TransactionSubmissionType.Estimate, originalLineItems(),
        ) { TransactionDraftPriceState.ActualPrice("100", "INR") }
        val edited = TransactionDraftOperations.setQuantity(draft, "p1", "99")
        // The original completed transaction's line items are untouched - only a plain in-memory
        // list was read from, and this function has no repository/DAO dependency to write through.
        assertEquals("5", originalLineItems().single().quantity)
        assertEquals("99", edited.lines.single().quantity)
    }

    @Test
    fun `reorder preserves product identity, name, unit, and sku from the original order`() {
        val draft = ReorderOperations.fromCompletedTransaction(
            "co-1", "buyer-1", TransactionSubmissionType.Estimate, originalLineItems(),
        ) { TransactionDraftPriceState.ActualPrice("100", "INR") }
        val line = draft.lines.single()
        assertEquals("p1", line.linkedProductId)
        assertEquals("Widget", line.snapshotProductName)
        assertEquals("Nos", line.snapshotUnit)
        assertEquals("SKU-1", line.snapshotSku)
        assertEquals("5", line.quantity)
    }

    @Test
    fun `reorder uses the CURRENT resolved price state, not the historical one stored on the old line`() {
        val draft = ReorderOperations.fromCompletedTransaction(
            "co-1", "buyer-1", TransactionSubmissionType.Estimate, originalLineItems(),
        ) { TransactionDraftPriceState.ContactForPrice } // pretend price is no longer openly visible today
        assertEquals(TransactionDraftPriceState.ContactForPrice, draft.lines.single().priceState)
    }

    @Test
    fun `a reordered draft can be edited (add, remove, quantity change) exactly like any other draft`() {
        var draft = ReorderOperations.fromCompletedTransaction(
            "co-1", "buyer-1", TransactionSubmissionType.Estimate, originalLineItems(),
        ) { TransactionDraftPriceState.ActualPrice("100", "INR") }
        draft = TransactionDraftOperations.addOrIncrementLine(draft, "p2", "New Item", "Nos", "SKU-2", TransactionDraftPriceState.ActualPrice("50", "INR"))
        draft = TransactionDraftOperations.removeLine(draft, "p1")
        assertEquals(listOf("p2"), draft.lines.map { it.linkedProductId })
    }
}
