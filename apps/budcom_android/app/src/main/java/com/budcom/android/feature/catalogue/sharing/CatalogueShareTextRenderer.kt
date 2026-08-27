package com.budcom.android.feature.catalogue.sharing

import com.budcom.android.feature.catalogue.domain.model.CataloguePriceState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.resolveCataloguePriceState

/**
 * Renders the same Published-only catalogue scope as a text representation for compatibility with
 * the existing content-layer tests. Android sharing uses [CataloguePdfRenderer] for the generated
 * artifact; this pure renderer remains useful for checking the canonical content and price rules.
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
