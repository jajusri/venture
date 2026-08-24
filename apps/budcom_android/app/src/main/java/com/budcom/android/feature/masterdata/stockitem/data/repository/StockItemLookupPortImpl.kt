package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockItemLookupPortImpl @Inject constructor(
    private val local: StockItemLocalDataSource,
) : StockItemLookupPort {
    override suspend fun findById(companyId: String, stockItemId: String): StockItem? =
        local.findById(companyId, stockItemId)

    override suspend fun listAllForCompany(companyId: String): List<StockItem> =
        local.listAllForCompany(companyId)
}
