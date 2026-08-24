package com.budcom.android.feature.catalogue.sharing

import com.budcom.android.feature.catalogue.domain.model.CataloguePriceState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.resolveCataloguePriceState

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

    /** Mid-MVP-1.4 lock: a viewer must always see one of three explicit states -- an actual price,
     * an honest "no price yet," or the seller's deliberate "Contact for price." The pre-lock
     * version of this function silently collapsed an Open-mode product with no amount entered into
     * "Contact for price," which is exactly the state confusion the lock forbids (Example B/C). */
    private fun priceLine(snapshot: CataloguePublishedSnapshot): String =
        when (val state = resolveCataloguePriceState(snapshot.priceDisplayMode, snapshot.resolvedPriceAmount, snapshot.resolvedPriceCurrencyCode)) {
            is CataloguePriceState.ActualPrice -> "Price: ${state.amount} ${state.currencyCode.orEmpty()}".trim()
            CataloguePriceState.NoPriceSupplied -> "Price: Not supplied yet"
            CataloguePriceState.ContactForPrice -> "Price: Contact for price"
        }
}
