package com.jajusri.venture.feature.masterdata.ledger.sharing

import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementItemDetail
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementItemLine
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatementParticularsBlockTest {

    private fun transaction(
        voucherType: String = "Sales",
        voucherNumber: String? = "S-2847",
        referenceNumber: String? = null,
        narration: String? = null,
        itemDetail: LedgerStatementItemDetail? = null,
    ) = LedgerStatementTransaction(
        voucherId = "v1",
        date = "2026-08-12",
        voucherType = voucherType,
        voucherNumber = voucherNumber,
        referenceNumber = referenceNumber,
        narration = narration,
        debit = "39450",
        credit = null,
        runningBalance = null,
        itemDetail = itemDetail,
    )

    @Test
    fun `a non-item voucher never produces fabricated item rows`() {
        val block = buildParticularsBlock(transaction(voucherType = "Receipt", voucherNumber = "R-100", itemDetail = null))
        assertTrue(block.itemRows.isEmpty())
        assertNull(block.itemHeaderLabels)
        assertNull(block.totalText)
    }

    @Test
    fun `an item-bearing voucher includes every item with correct qty rate and amount`() {
        val detail = LedgerStatementItemDetail(
            items = listOf(
                LedgerStatementItemLine("Angle Cock - Flora", "20 Nos", "₹450.00", "₹9,000.00"),
                LedgerStatementItemLine("Pillar Cock - Flora", "10 Nos", "₹780.00", "₹7,800.00"),
                LedgerStatementItemLine("Wall Mixer", "5 Nos", "₹2,450.00", "₹12,250.00"),
            ),
            totalLabel = "₹29,050.00",
        )
        val block = buildParticularsBlock(transaction(itemDetail = detail))

        assertEquals(3, block.itemRows.size)
        assertEquals("Angle Cock - Flora", block.itemRows[0].name)
        assertEquals("20 Nos", block.itemRows[0].quantity)
        assertEquals("₹450.00", block.itemRows[0].rate)
        assertEquals("₹9,000.00", block.itemRows[0].amount)
        assertEquals(listOf("Item Name", "Qty", "Rate", "Amount"), block.itemHeaderLabels)
        assertEquals("Total  ₹29,050.00", block.totalText)
    }

    @Test
    fun `all items are included with no truncation for a large item list`() {
        val items = (1..40).map { n -> LedgerStatementItemLine("Item $n", "$n Nos", "₹10.00", "₹${n * 10}.00") }
        val block = buildParticularsBlock(transaction(itemDetail = LedgerStatementItemDetail(items, "₹8,200.00")))
        assertEquals(40, block.itemRows.size)
    }

    @Test
    fun `narration appears after the item block when available`() {
        val detail = LedgerStatementItemDetail(listOf(LedgerStatementItemLine("Item", "1 Nos", "₹1.00", "₹1.00")), "₹1.00")
        val block = buildParticularsBlock(transaction(narration = "Goods supplied against Order No. 458", itemDetail = detail))
        assertEquals(true, block.narrationText?.contains("Order No. 458"))
    }

    @Test
    fun `narration is absent (never a placeholder) when there is none`() {
        val block = buildParticularsBlock(transaction(narration = null))
        assertNull(block.narrationText)
    }

    @Test
    fun `reference number is included in the narration line when present`() {
        val block = buildParticularsBlock(transaction(voucherType = "Receipt", referenceNumber = "REF-99", narration = null))
        assertEquals("Ref: REF-99", block.narrationText)
    }

    @Test
    fun `the heading always identifies voucher type and number`() {
        val block = buildParticularsBlock(transaction(voucherType = "Purchase", voucherNumber = "P-12"))
        assertEquals("Purchase · No. P-12", block.headingText)
    }
}
