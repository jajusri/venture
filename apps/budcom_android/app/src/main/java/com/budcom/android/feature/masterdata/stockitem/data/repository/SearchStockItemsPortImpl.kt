package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchStockItemsPortImpl @Inject constructor(
    private val repository: StockItemRepository,
) : SearchStockItemsPort {
    override suspend fun search(query: StockItemQuery): AppResult<StockItemPage> =
        repository.loadStockItems(query)
}
