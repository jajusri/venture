package com.jajusri.venture.feature.masterdata.stockitem.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.jajusri.venture.feature.masterdata.stockitem.domain.repository.StockItemRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchStockItemsPortImpl @Inject constructor(
    private val repository: StockItemRepository,
) : SearchStockItemsPort {
    override suspend fun search(query: StockItemQuery): AppResult<StockItemPage> =
        repository.loadStockItems(query)
}
