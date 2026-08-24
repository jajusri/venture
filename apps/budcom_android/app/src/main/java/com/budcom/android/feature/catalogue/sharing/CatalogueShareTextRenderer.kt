package com.budcom.android.feature.catalogue.sharing

import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode

/**
 * Renders a Published-only catalogue scope as a plain-text share file.
 *
 * Deliberately plain text rather than a PDF for this MVP-1.4 pass — unlike the Ledger statement's
 * tabular structure (which had a directly reusable rendering shape), a product catalogue's layout
 * has no existing precedent in this codebase, and this environment has no way to visually verify a
 * hand-rolled PDF pagination/wrapping algorithm. A plain-text file is trivially correct (verified
 * here by ordinary string-content unit tests), still satisfies "generate a file, hand it to the OS
 * share sheet" (architecture §11), and the [CatalogueShareCoordinator] contract this renders
 * through does not change if a PDF renderer replaces this later. Named explicitly as a deliberate
 * simplification, not a silently lowered bar — see the MVP-1.4 implementation report.
 *
 * Pure function, no Android dependency — reads exclusively from the [CataloguePublishedSnapshot]
 * list the caller supplies, never from Draft/Review data (that guarantee is enforced by the
 * caller only ever fetching Published snapshots, see [CatalogueShareCoordinator]'s own doc comment).
 */
object CatalogueShareTextRenderer {

    fun render(businessName: String?, scope: CatalogueShareScope, snapshots: List<CataloguePublishedSnapshot>): String = buildString {
        appendLine(businessName?.trim()?.takeIf(String::isNotBlank) ?: "BUDCOM Catalogue")
        appendLine(
            when (scope) {
                CatalogueShareScope.FullCatalogue -> "Full Catalogue"
                is CatalogueShareScope.Category -> "Category: ${scope.name}"
            },
        )
        appendLine("=".repeat(40))
        appendLine()
        snapshots.forEach { snapshot ->
            appendLine(snapshot.displayName)
            snapshot.customerFacingCategory?.takeIf(String::isNotBlank)?.let { appendLine("Category: $it") }
            appendLine(priceLine(snapshot))
            snapshot.description?.trim()?.takeIf(String::isNotBlank)?.let { appendLine(it) }
            appendLine("-".repeat(40))
            appendLine()
        }
    }

    private fun priceLine(snapshot: CataloguePublishedSnapshot): String = when (snapshot.priceDisplayMode) {
        PriceDisplayMode.ContactForPrice -> "Price: Contact for price"
        PriceDisplayMode.Open -> {
            val amount = snapshot.resolvedPriceAmount
            if (amount.isNullOrBlank()) "Price: Contact for price" else "Price: $amount ${snapshot.resolvedPriceCurrencyCode.orEmpty()}".trim()
        }
    }
}
