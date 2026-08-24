package com.budcom.android.feature.masterdata.stockitem.domain.port

import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem

/**
 * Stable public read-only lookup port for Stock Items, distinct from [SearchStockItemsPort]'s
 * paged text search. Added for MVP-1.4 Catalogue (docs/architecture/
 * BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md §5/§14): Catalogue links a product to a Stock Item by
 * its stable id and must resolve that Stock Item's Tally-authoritative display fields (name, unit,
 * HSN, GST, parent group) live at read time rather than mirroring a copy, and must be able to
 * sweep every locally-cached Stock Item for a company to detect rename/disappearance/reappearance.
 * Deliberately local-cache-only (no remote fetch) — Catalogue only ever needs whatever the
 * existing Stock Item sync has already cached, never a fresh network call of its own.
 *
 * Cross-feature consumers must use this port rather than the Stock Item repository
 * implementation or its local data source directly.
 */
interface StockItemLookupPort {
    suspend fun findById(companyId: String, stockItemId: String): StockItem?
    suspend fun listAllForCompany(companyId: String): List<StockItem>
}
