package com.jajusri.venture.feature.catalogue.domain.excel

/**
 * MVP-1.4 Excel interchange contract (architecture §9). Deliberately does not pick, or depend on,
 * any specific spreadsheet file-format library (Apache POI or otherwise) — that is a real
 * dependency decision this planning/implementation pass does not make unilaterally (task
 * instruction: "prefer minimal surface-area changes"; introducing a new third-party parsing
 * dependency is exactly the kind of choice that deserves its own explicit review). What this file
 * *does* implement, fully and testably, is the part that actually carries the locked, safety-
 * relevant decisions: the reserved-name protection, row validation, and stable-identifier
 * create-vs-update matching. A future session wiring an actual `.xlsx`/`.csv` reader only needs to
 * parse rows into [CatalogueExcelRow] — everything downstream of that is already here.
 *
 * **Extensibility discipline (avoiding a brittle schema, §9):** [CatalogueExcelColumns.RESERVED_NAMES]
 * is a membership set, not a fixed column count/order — adding a future native field is one entry
 * here, never a schema/file-format migration.
 */
object CatalogueExcelColumns {
    // Required, minimal viable row (Brainstorm Outcome §8 "quick-start default path").
    const val PRODUCT_NAME = "Product Name"
    const val UNIT = "Unit"

    // Native, Tally-sourced — read-only on import when a Stock Item link is resolved.
    const val SKU = "SKU"
    const val STOCK_ITEM_REFERENCE = "Stock Item Reference"
    const val HSN = "HSN"
    const val GST_RATE = "GST Rate"
    const val STOCK_GROUP = "Stock Group"

    // Native, Catalogue-owned — editable via import.
    const val DESCRIPTION = "Description"
    const val SPECIFICATIONS = "Specifications"
    const val CATEGORY = "Category"
    const val PRICE = "Price"
    const val PRICE_DISPLAY_MODE = "Price Display Mode"

    // Export-only, informational — never writable via import (§9: Publish/Archive transitions
    // happen only through the lifecycle UI, never via Excel re-import).
    const val PUBLICATION_STATE = "Publication State"

    val RESERVED_NAMES: Set<String> = setOf(
        PRODUCT_NAME, UNIT, SKU, STOCK_ITEM_REFERENCE, HSN, GST_RATE, STOCK_GROUP,
        DESCRIPTION, SPECIFICATIONS, CATEGORY, PRICE, PRICE_DISPLAY_MODE, PUBLICATION_STATE,
    )

    /** Column order an export writes, before any owner-defined custom columns are appended
     * (§9's "Export: round-trips every native + custom column"). Required columns first, matching
     * the order an owner would most naturally fill them in by hand. */
    val NATIVE_EXPORT_ORDER: List<String> = listOf(
        PRODUCT_NAME, UNIT, SKU, STOCK_ITEM_REFERENCE, HSN, GST_RATE, STOCK_GROUP,
        DESCRIPTION, SPECIFICATIONS, CATEGORY, PRICE, PRICE_DISPLAY_MODE, PUBLICATION_STATE,
    )

    fun isReserved(columnName: String): Boolean =
        RESERVED_NAMES.any { it.equals(columnName.trim(), ignoreCase = true) }

    /**
     * LOCKED (Brainstorm Outcome §6): "a custom column cannot reuse a native/future-native name;
     * clear rename prompt shown on conflict." This is the check an owner-facing "add a custom
     * Excel column" UI runs against the name they type, before it is ever saved — the collision
     * is meaningful at column-definition time, not when re-parsing an already-written file's
     * header row (a header that matches a reserved name is simply that native column).
     */
    fun validateCustomColumnName(name: String): CustomColumnNameValidation {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CustomColumnNameValidation.Blank
        return if (isReserved(trimmed)) CustomColumnNameValidation.Reserved else CustomColumnNameValidation.Valid
    }
}

sealed interface CustomColumnNameValidation {
    data object Valid : CustomColumnNameValidation
    data object Blank : CustomColumnNameValidation
    /** Rename prompt trigger — the name collides with a native/future-native reserved name. */
    data object Reserved : CustomColumnNameValidation
}

/** One already-parsed spreadsheet row — file-format-agnostic by design (see this file's own doc
 * comment). [rowNumber] is 1-indexed and excludes the header row, matching how a spreadsheet
 * application itself numbers rows for a user-facing error message. */
data class CatalogueExcelRow(
    val rowNumber: Int,
    /** Column header (exactly as it appeared in the file) to cell value, `null`/absent for a
     * blank cell. Header comparison for reserved-name matching is case-insensitive. */
    val cells: Map<String, String?>,
) {
    fun value(column: String): String? = cells.entries.firstOrNull { it.key.equals(column, ignoreCase = true) }?.value?.trim()?.takeIf { it.isNotEmpty() }
}

sealed interface CatalogueExcelRowOutcome {
    val rowNumber: Int
    data class Create(override val rowNumber: Int, val productName: String) : CatalogueExcelRowOutcome
    data class Update(override val rowNumber: Int, val productId: String) : CatalogueExcelRowOutcome
    data class Skipped(override val rowNumber: Int, val reason: String) : CatalogueExcelRowOutcome
}

data class CatalogueExcelImportPreview(
    val outcomes: List<CatalogueExcelRowOutcome>,
    /** Column headers present in the file that are not on [CatalogueExcelColumns.RESERVED_NAMES]
     * — round-tripped opaquely, VENTURE never interprets their content (§9). */
    val customColumnNames: Set<String>,
) {
    val createCount: Int get() = outcomes.count { it is CatalogueExcelRowOutcome.Create }
    val updateCount: Int get() = outcomes.count { it is CatalogueExcelRowOutcome.Update }
    val skipCount: Int get() = outcomes.count { it is CatalogueExcelRowOutcome.Skipped }
}
