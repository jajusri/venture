package com.budcom.android.feature.catalogue.domain.model

/**
 * Pure, deterministic implementation of the LOCKED override-resolution order (architecture §8):
 *
 *   ITEM → BRANCH → STOCK-GROUP → CATALOGUE-WIDE
 *
 * First match wins; no merging. Deliberately a plain function over an in-memory list rather than
 * a DAO query — the precedence chain is business logic, not persistence, and this shape is what
 * makes the exhaustive precedence-combination test matrix (architecture §20) possible without a
 * database.
 */
object CatalogueOverrideResolver {

    /**
     * @param rows every override row for this company + [attribute] only (the caller is
     *   responsible for that pre-filter — this function does not filter by companyId/attribute
     *   itself, so it stays trivially reusable/testable against a synthetic row set).
     * @param productId the product being resolved for (ITEM level).
     * @param branchId the current branch context, or `null` if none selected (BRANCH level is
     *   skipped when `null`).
     * @param stockGroupKey the linked Stock Item's stock-group key, or `null` for a Manual product
     *   or one whose source is currently unavailable (STOCK-GROUP level is skipped when `null`).
     */
    fun resolve(
        rows: List<CatalogueOverrideRow>,
        productId: String,
        branchId: String?,
        stockGroupKey: String?,
    ): CatalogueOverrideRow? {
        rows.firstOrNull { it.scope == CatalogueOverrideScope.Item(productId) }?.let { return it }
        if (branchId != null) {
            rows.firstOrNull { it.scope == CatalogueOverrideScope.Branch(branchId) }?.let { return it }
        }
        if (stockGroupKey != null) {
            rows.firstOrNull { it.scope == CatalogueOverrideScope.StockGroup(stockGroupKey) }?.let { return it }
        }
        return rows.firstOrNull { it.scope == CatalogueOverrideScope.CatalogueWide }
    }
}
