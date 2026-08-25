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

    /**
     * Deliberately the one exception to this port's local-cache-only rule above. TD-050
     * (2026-08-24 live validation): the Sync screen's "Sync Now" for Stock Items only updates the
     * Connector's own database — it never populates this port's Room cache. Only a pull through
     * [com.budcom.android.feature.masterdata.stockitem.domain.usecase.LoadStockItemsUseCase]
     * (the same one the Stock Items browser already performs) does that. A Catalogue user who
     * never separately opened that browser would otherwise hit an empty/stale [listAllForCompany]
     * with no indication why — this exists so Catalogue's "Link from stock"/"Link all" flow can
     * guarantee freshness itself rather than depending on the user having taken an unrelated,
     * easily-missed action first. Failure (offline, Connector unreachable) is swallowed here and
     * simply leaves the existing cache as-is, exactly like every other degrade-to-cache path in
     * this codebase — never blocks or fails the caller.
     */
    suspend fun warmStockItemCache(companyId: String)
}
