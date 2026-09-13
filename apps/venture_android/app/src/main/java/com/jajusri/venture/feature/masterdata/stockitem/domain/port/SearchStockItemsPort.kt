package com.jajusri.venture.feature.masterdata.stockitem.domain.port

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery

/**
 * Stable public read-only search port for Stock Items.
 *
 * Cross-feature consumers (e.g. Universal Search) must use this port rather than
 * the Stock Item repository implementation.
 */
interface SearchStockItemsPort {
    suspend fun search(query: StockItemQuery): AppResult<StockItemPage>
}
