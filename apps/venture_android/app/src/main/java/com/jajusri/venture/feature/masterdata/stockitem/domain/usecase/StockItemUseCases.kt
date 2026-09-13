package com.jajusri.venture.feature.masterdata.stockitem.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.repository.StockItemRepository
import javax.inject.Inject

class LoadStockItemsUseCase @Inject constructor(
    private val repository: StockItemRepository,
) {
    suspend operator fun invoke(query: StockItemQuery): AppResult<StockItemPage> =
        repository.loadStockItems(query)
}

class RefreshStockItemsUseCase @Inject constructor(
    private val repository: StockItemRepository,
) {
    suspend operator fun invoke(query: StockItemQuery): AppResult<StockItemPage> =
        repository.loadStockItems(query)
}
