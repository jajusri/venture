package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort
import com.budcom.android.feature.masterdata.stockitem.domain.usecase.LoadStockItemsUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockItemLookupPortImpl @Inject constructor(
    private val local: StockItemLocalDataSource,
    private val loadStockItems: LoadStockItemsUseCase,
) : StockItemLookupPort {
    override suspend fun findById(companyId: String, stockItemId: String): StockItem? =
        local.findById(companyId, stockItemId)

    override suspend fun listAllForCompany(companyId: String): List<StockItem> =
        local.listAllForCompany(companyId)

    /** [companyId] is accepted for API symmetry with every other method here, but the underlying
     * [LoadStockItemsUseCase] call targets whichever company is currently selected (it has no
     * per-call company parameter of its own) — safe because Catalogue itself is always scoped to
     * that same currently-selected company. The default, unfiltered, page-1 query is what triggers
     * [com.budcom.android.feature.masterdata.stockitem.data.repository.StockItemRepositoryImpl]'s
     * full-snapshot warm (see its `persistSuccessfulPage`/`warmFullSnapshot`), not a single page. */
    override suspend fun warmStockItemCache(companyId: String) {
        loadStockItems(StockItemQuery())
    }
}
