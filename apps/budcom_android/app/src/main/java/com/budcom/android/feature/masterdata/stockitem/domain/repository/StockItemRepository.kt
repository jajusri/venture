package com.budcom.android.feature.masterdata.stockitem.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery

interface StockItemRepository {
    suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage>
}
