package com.jajusri.venture.feature.masterdata.stockitem.domain.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery

interface StockItemRepository {
    suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage>
}
