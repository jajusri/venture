package com.jajusri.venture.feature.transaction.sharing

import com.jajusri.venture.feature.transaction.domain.model.EstimatePo
import com.jajusri.venture.feature.transaction.domain.model.EstimatePoStatus
import com.jajusri.venture.feature.transaction.domain.model.PaymentTiming
import com.jajusri.venture.feature.transaction.domain.model.TermsAcknowledgment
import com.jajusri.venture.feature.transaction.domain.model.TransactionDeliveryChannel
import com.jajusri.venture.feature.transaction.domain.model.TransactionEntryPointType
import com.jajusri.venture.feature.transaction.domain.model.TransactionLineItem
import com.jajusri.venture.feature.transaction.domain.model.TransactionSubmissionType
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionShareContentTest {

    private fun ts(millis: Long = 1_000L) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun line(
        name: String = "Widget",
        unit: String? = "Nos",
        quantity: String = "10",
        amount: String? = "1000",
        currency: String? = "INR",
        contactForPrice: Boolean = false,
    ) = TransactionLineItem(
        estimatePoId = "e-1", lineItemId = "l-1", linkedProductId = "product-1", snapshotProductName = name,
        snapshotUnit = unit, snapshotSku = "SKU-1", quantity = quantity, unitPriceAmount = amount,
        unitPriceCurrencyCode = currency, lineTotalAmount = amount, isContactForPrice = contactForPrice,
    )

    private fun estimatePo(
        lineItems: List<TransactionLineItem> = listOf(line()),
        submissionType: TransactionSubmissionType = TransactionSubmissionType.Estimate,
        totalAmount: String = "1000",
    ) = EstimatePo(
        companyId = "co-1", estimatePoId = "e-1", entryPointType = TransactionEntryPointType.Catalogue,
        submissionType = submissionType, deliveryChannel = TransactionDeliveryChannel.WhatsAppShared,
        buyerPartyId = null, totalAmount = totalAmount, currencyCode = "INR", status = EstimatePoStatus.Shared,
        submittedAt = ts(), lineItems = lineItems,
    )

    @Test
    fun `refuses to resolve content for an estimate with no line items`() {
        val result = TransactionShareContent.resolve(estimatePo(lineItems = emptyList()), TransactionSharePriceVisibility.Visible, null, null, null)
        assertTrue(result is TransactionShareResult.Failure)
    }

    @Test
    fun `resolves successfully for a non-empty estimate`() {
        val result = TransactionShareContent.resolve(estimatePo(), TransactionSharePriceVisibility.Visible, "Acme", "Buyer Co", null)
        assertTrue(result is TransactionShareResult.Success)
    }
}

class TransactionShareTextRendererTest {

    private fun ts(millis: Long = 1_000L) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun line(
        name: String = "Widget",
        unit: String? = "Nos",
        quantity: String = "10",
        amount: String? = "1000",
        currency: String? = "INR",
        contactForPrice: Boolean = false,
    ) = TransactionLineItem(
        estimatePoId = "e-1", lineItemId = "l-1", linkedProductId = "product-1", snapshotProductName = name,
        snapshotUnit = unit, snapshotSku = "SKU-1", quantity = quantity, unitPriceAmount = amount,
        unitPriceCurrencyCode = currency, lineTotalAmount = amount, isContactForPrice = contactForPrice,
    )

    private fun estimatePo(
        lineItems: List<TransactionLineItem> = listOf(line()),
        submissionType: TransactionSubmissionType = TransactionSubmissionType.Estimate,
        totalAmount: String = "1000",
    ) = EstimatePo(
        companyId = "co-1", estimatePoId = "e-1", entryPointType = TransactionEntryPointType.Catalogue,
        submissionType = submissionType, deliveryChannel = TransactionDeliveryChannel.WhatsAppShared,
        buyerPartyId = null, totalAmount = totalAmount, currencyCode = "INR", status = EstimatePoStatus.Shared,
        submittedAt = ts(), lineItems = lineItems,
    )

    private fun render(
        estimatePo: EstimatePo = estimatePo(),
        priceVisibility: TransactionSharePriceVisibility = TransactionSharePriceVisibility.Visible,
        terms: TermsAcknowledgment? = null,
    ) = TransactionShareTextRenderer.render(estimatePo, priceVisibility, "Acme Traders", "Buyer Co", terms)

    // ============================== Estimate vs PO ==============================

    @Test
    fun `renders Estimate document type and non-binding disclosure`() {
        val text = render(estimatePo(submissionType = TransactionSubmissionType.Estimate))
        assertTrue(text.contains("Estimate"))
        assertTrue(text.contains("non-binding Estimate"))
        assertFalse(text.contains("Purchase Order"))
    }

    @Test
    fun `renders Purchase Order document type distinctly from Estimate`() {
        val text = render(estimatePo(submissionType = TransactionSubmissionType.PurchaseOrder))
        assertTrue(text.contains("Purchase Order"))
        assertTrue(text.contains("This is a Purchase Order."))
        assertFalse(text.contains("non-binding Estimate"))
    }

    // ============================== the four price states ==============================

    @Test
    fun `state 1 - Open plus price shows the actual amount`() {
        val text = render(estimatePo(lineItems = listOf(line(amount = "1500", currency = "INR"))), priceVisibility = TransactionSharePriceVisibility.Visible)
        assertTrue(text.contains("Price: 1500 INR"))
    }

    @Test
    fun `state 2 - Open plus no price supplied is distinguished from Contact for price`() {
        val text = render(estimatePo(lineItems = listOf(line(amount = null))), priceVisibility = TransactionSharePriceVisibility.Visible)
        assertTrue(text.contains("Price: Not supplied yet"))
        assertFalse(text.contains("Price: Contact for price"))
    }

    @Test
    fun `state 3 - Contact for price line shows Contact for price even though visibility is Visible`() {
        val text = render(estimatePo(lineItems = listOf(line(contactForPrice = true, amount = "9999"))), priceVisibility = TransactionSharePriceVisibility.Visible)
        assertTrue(text.contains("Price: Contact for price"))
        assertFalse(text.contains("9999"))
    }

    @Test
    fun `state 4 - Hidden visibility never leaks a price even when the line has one and is not itself Contact-for-price`() {
        val text = render(
            estimatePo(lineItems = listOf(line(amount = "424242", contactForPrice = false))),
            priceVisibility = TransactionSharePriceVisibility.Hidden,
        )
        assertFalse("a hidden-visibility share must never print the raw amount", text.contains("424242"))
        assertTrue(text.contains("Price: Contact for price"))
    }

    @Test
    fun `no-price-supplied is never collapsed into Contact-for-price, even though Hidden and Contact-for-price share the same text`() {
        // "Not supplied yet" (state 2) must read differently from "Contact for price" (states 3
        // and 4) - those two legitimately render identically on purpose (a recipient should not be
        // able to tell "seller chose Contact-for-price" apart from "you're not authorized right
        // now" from the text alone), but state 2 must never be confused with either of them.
        val noPriceSupplied = render(estimatePo(lineItems = listOf(line(amount = null))), priceVisibility = TransactionSharePriceVisibility.Visible)
            .lines().first { it.startsWith("Price:") }
        val contactForPrice = render(estimatePo(lineItems = listOf(line(contactForPrice = true))), priceVisibility = TransactionSharePriceVisibility.Visible)
            .lines().first { it.startsWith("Price:") }
        val hidden = render(estimatePo(lineItems = listOf(line(amount = "100"))), priceVisibility = TransactionSharePriceVisibility.Hidden)
            .lines().first { it.startsWith("Price:") }

        assertTrue(noPriceSupplied != contactForPrice)
        assertTrue(noPriceSupplied != hidden)
        assertTrue(contactForPrice == hidden) // both legitimately say "Price: Contact for price"
    }

    @Test
    fun `Hidden visibility also hides the total, never partially leaking it`() {
        val text = render(estimatePo(totalAmount = "999999"), priceVisibility = TransactionSharePriceVisibility.Hidden)
        assertFalse(text.contains("999999"))
        assertTrue(text.contains("Total: Contact for price"))
    }

    @Test
    fun `total is shown when every line has a real, visible price`() {
        val text = render(estimatePo(lineItems = listOf(line(amount = "1000")), totalAmount = "1000"))
        assertTrue(text.contains("Total: 1000 INR"))
    }

    @Test
    fun `total falls back to Contact for price if any line is Contact-for-price or missing an amount`() {
        val text = render(estimatePo(lineItems = listOf(line(amount = "500"), line(name = "Gadget", contactForPrice = true))))
        assertTrue(text.contains("Total: Contact for price"))
    }

    // ============================== line item preservation ==============================

    @Test
    fun `every submitted line item appears in the rendered output with its snapshot name, quantity, and unit`() {
        val text = render(
            estimatePo(
                lineItems = listOf(
                    line(name = "Widget A", quantity = "5", unit = "Nos"),
                    line(name = "Widget B", quantity = "3", unit = "Box"),
                ),
            ),
        )
        assertTrue(text.contains("Widget A"))
        assertTrue(text.contains("Qty: 5 Nos"))
        assertTrue(text.contains("Widget B"))
        assertTrue(text.contains("Qty: 3 Box"))
    }

    // ============================== terms (only when explicitly supplied) ==============================

    @Test
    fun `no terms section appears when terms is null - the pre-Accept WhatsApp path has no Terms yet`() {
        val text = render(terms = null)
        assertFalse(text.contains("Terms acknowledged"))
    }

    @Test
    fun `terms section appears when supplied, explicitly labeled not a legal contract`() {
        val terms = TermsAcknowledgment(
            companyId = "co-1", transactionId = "t-1", paymentTiming = PaymentTiming.CreditDays, creditDays = 15,
            partialAdvancePercent = null, partialBalanceTiming = null, amount = "1000", currencyCode = "INR",
            note = "Deliver by Friday", proposedAt = ts(), buyerConfirmedAt = null, sellerConfirmedAt = null,
        )
        val text = render(terms = terms)
        assertTrue(text.contains("Terms acknowledged (not a legal contract):"))
        assertTrue(text.contains("Credit 15 days"))
        assertTrue(text.contains("Deliver by Friday"))
    }
}
