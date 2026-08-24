package com.budcom.android.feature.catalogue.domain.excel

/**
 * Pure, file-format-agnostic validation of already-parsed [CatalogueExcelRow]s (architecture §9).
 * Never mutates anything — [preview] is the mandatory import-preview step ("shows create/update/
 * skip counts and every flagged row's reason, never a silent bulk-apply") a caller must show
 * before committing anything to [com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository].
 */
object CatalogueExcelValidator {

    /**
     * @param resolveExistingProductId given a Stock Item Reference or SKU, returns the Catalogue
     *   Product already linked to it, if any — this is what prevents duplicate-on-round-trip
     *   (PDL-020 §4). Caller-supplied so this stays a pure function independent of Room/Hilt.
     */
    fun preview(
        rows: List<CatalogueExcelRow>,
        resolveExistingProductId: (identifier: String) -> String?,
    ): CatalogueExcelImportPreview {
        val customColumnNames = rows
            .flatMap { it.cells.keys }
            .filterNot(CatalogueExcelColumns::isReserved)
            .toSet()

        // Duplicate rows within one file (same identifier twice): last-row-wins, matching this
        // codebase's own "fail honestly, per-item, never a silent partial success" convention.
        // A row's own identity for this purpose is Stock Item Reference, then SKU, then Product
        // Name -- the first present is used, exactly mirroring create-vs-update resolution below.
        val lastRowNumberByIdentity = mutableMapOf<String, Int>()
        rows.forEach { row ->
            row.identity()?.let { lastRowNumberByIdentity[it] = row.rowNumber }
        }

        val outcomes = rows.map { row ->
            val identity = row.identity()
            val isSuperseded = identity != null && lastRowNumberByIdentity[identity] != row.rowNumber
            when {
                isSuperseded -> CatalogueExcelRowOutcome.Skipped(
                    row.rowNumber,
                    "Duplicate row for the same product later in this file (row ${lastRowNumberByIdentity[identity]} wins)",
                )
                else -> row.classify(resolveExistingProductId)
            }
        }

        return CatalogueExcelImportPreview(outcomes, customColumnNames)
    }

    /** A row's dedup/matching identity: Stock Item Reference, then SKU, then Product Name (the
     * only identifiers a spreadsheet row can carry — a Catalogue-issued Product ID column is
     * intentionally not part of this contract yet, per architecture §22 item 4's own open flag on
     * SKU/Product-ID policy). */
    private fun CatalogueExcelRow.identity(): String? =
        value(CatalogueExcelColumns.STOCK_ITEM_REFERENCE)
            ?: value(CatalogueExcelColumns.SKU)
            ?: value(CatalogueExcelColumns.PRODUCT_NAME)

    private fun CatalogueExcelRow.classify(resolveExistingProductId: (String) -> String?): CatalogueExcelRowOutcome {
        val productName = value(CatalogueExcelColumns.PRODUCT_NAME)
        val unit = value(CatalogueExcelColumns.UNIT)
        if (productName == null) return CatalogueExcelRowOutcome.Skipped(rowNumber, "Missing required column: ${CatalogueExcelColumns.PRODUCT_NAME}")
        if (unit == null) return CatalogueExcelRowOutcome.Skipped(rowNumber, "Missing required column: ${CatalogueExcelColumns.UNIT}")

        val priceDisplayMode = value(CatalogueExcelColumns.PRICE_DISPLAY_MODE)
        val price = value(CatalogueExcelColumns.PRICE)
        if (priceDisplayMode?.equals("Open", ignoreCase = true) == true && price != null && price.toBigDecimalOrNull() == null) {
            return CatalogueExcelRowOutcome.Skipped(rowNumber, "Price is not a valid number: \"$price\"")
        }

        val identity = value(CatalogueExcelColumns.STOCK_ITEM_REFERENCE) ?: value(CatalogueExcelColumns.SKU)
        val existingProductId = identity?.let(resolveExistingProductId)
        return if (existingProductId != null) {
            CatalogueExcelRowOutcome.Update(rowNumber, existingProductId)
        } else {
            CatalogueExcelRowOutcome.Create(rowNumber, productName)
        }
    }

    private fun String.toBigDecimalOrNull(): java.math.BigDecimal? = runCatching { java.math.BigDecimal(this) }.getOrNull()
}
