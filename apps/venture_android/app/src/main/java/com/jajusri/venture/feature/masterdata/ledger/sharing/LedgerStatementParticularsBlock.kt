package com.jajusri.venture.feature.masterdata.ledger.sharing

import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementTransaction

/**
 * The complete Particulars-column content for one transaction, in vertical stacking order:
 * voucher identity heading, then (only for an item-bearing voucher) the item mini-table and its
 * total, then narration/reference. Pure data — no Android/graphics dependency — so the exact
 * content this task requires (all items present, correct qty/rate/amount, no fabricated items for
 * a non-item voucher, narration placement) is directly unit-testable without a PDF-capable
 * runtime. Wrapping/pagination/drawing is the PDF renderer's job, not this one's.
 */
internal data class ParticularsBlock(
    val headingText: String,
    val itemHeaderLabels: List<String>?,
    val itemRows: List<ItemRowText>,
    val totalText: String?,
    val narrationText: String?,
) {
    val hasItems: Boolean get() = itemRows.isNotEmpty()
}

internal data class ItemRowText(val name: String, val quantity: String, val rate: String, val amount: String)

private val ITEM_HEADER_LABELS = listOf("Item Name", "Qty", "Rate", "Amount")

/**
 * Builds [transaction]'s Particulars block. Only ever produces item rows when
 * [LedgerStatementTransaction.itemDetail] is non-null and non-empty — a non-item voucher
 * (Receipt/Payment/Contra/Journal, or a voucher whose item detail wasn't attached to this
 * particular row per [com.jajusri.venture.feature.masterdata.ledger.domain.usecase.GetLocalLedgerStatementUseCase]'s
 * one-block-per-voucher rule) always yields `itemRows = emptyList()` and `itemHeaderLabels = null`
 * — never a fabricated table. Every item present in [LedgerStatementTransaction.itemDetail] is
 * included; nothing is dropped or truncated here.
 */
internal fun buildParticularsBlock(transaction: LedgerStatementTransaction): ParticularsBlock {
    val head = listOfNotNull(
        transaction.voucherType.takeIf(String::isNotBlank),
        transaction.voucherNumber?.takeIf(String::isNotBlank)?.let { "No. $it" },
    ).joinToString(" · ")
    val ref = transaction.referenceNumber?.takeIf(String::isNotBlank)?.let { "Ref: $it" }
    val narrationParts = listOfNotNull(ref, transaction.narration?.takeIf(String::isNotBlank))
    val narrationText = narrationParts.joinToString(" — ").takeIf { it.isNotBlank() }

    val detail = transaction.itemDetail?.takeIf { it.items.isNotEmpty() }
    val itemRows = detail?.items.orEmpty().map { line ->
        ItemRowText(
            name = line.itemName,
            quantity = line.quantityLabel.orEmpty(),
            rate = line.rateLabel.orEmpty(),
            amount = line.amountLabel.orEmpty(),
        )
    }

    return ParticularsBlock(
        headingText = head.ifBlank { "—" },
        itemHeaderLabels = ITEM_HEADER_LABELS.takeIf { itemRows.isNotEmpty() },
        itemRows = itemRows,
        totalText = detail?.totalLabel?.let { "Total  $it" },
        narrationText = narrationText,
    )
}
