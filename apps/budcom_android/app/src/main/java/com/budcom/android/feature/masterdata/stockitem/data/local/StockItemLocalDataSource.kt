package com.budcom.android.feature.masterdata.stockitem.data.local

import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockAmountSide
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemSortBy
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemSortDirection
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockMoneyAmount
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

interface StockItemLocalDataSource {
    suspend fun hasCache(companyId: String): Boolean
    suspend fun upsert(companyId: String, items: List<StockItem>, dataFreshnessAt: String?)
    suspend fun replaceAll(companyId: String, items: List<StockItem>, dataFreshnessAt: String?)
    suspend fun query(companyId: String, query: StockItemQuery): StockItemPage?
    suspend fun findById(companyId: String, id: String): StockItem?
    suspend fun listAllForCompany(companyId: String): List<StockItem>
}

@Singleton
class RoomStockItemLocalDataSource @Inject constructor(
    private val stockItemDao: StockItemDao,
) : StockItemLocalDataSource {

    override suspend fun hasCache(companyId: String): Boolean = stockItemDao.countForCompany(companyId) > 0

    override suspend fun upsert(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        if (items.isEmpty()) return
        stockItemDao.upsertAll(items.map { it.toEntity(companyId, dataFreshnessAt) })
    }

    override suspend fun replaceAll(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        stockItemDao.replaceAllForCompany(
            companyId = companyId,
            entities = items.map { it.toEntity(companyId, dataFreshnessAt) },
        )
    }

    override suspend fun query(companyId: String, query: StockItemQuery): StockItemPage? {
        if (!hasCache(companyId)) return null
        val normalized = query.text?.trim()?.takeIf { it.isNotEmpty() }
            ?.take(MasterDataBrowserDefaults.MAX_QUERY_LENGTH)
        val page = query.page.coerceAtLeast(1)
        val pageSize = query.pageSize.coerceIn(1, 100)
        val totalItems = stockItemDao.countMatching(companyId, normalized)
        val totalPages = if (totalItems == 0) 0 else ceil(totalItems / pageSize.toDouble()).toInt()
        val entities = stockItemDao.queryPage(
            companyId = companyId,
            query = normalized,
            sortBy = query.sortBy.toCacheSortKey(),
            ascending = if (query.sortDirection == StockItemSortDirection.Asc) 1 else 0,
            limit = pageSize,
            offset = (page - 1) * pageSize,
        )
        val freshness = entities.firstOrNull()?.dataFreshnessAt
        return StockItemPage(
            items = entities.map { it.toDomain() },
            pagination = MasterDataPagination(
                page = page,
                pageSize = pageSize,
                totalItems = totalItems,
                totalPages = totalPages,
            ),
            dataFreshnessAt = freshness,
        )
    }

    override suspend fun findById(companyId: String, id: String): StockItem? =
        stockItemDao.findById(companyId, id)?.toDomain()

    override suspend fun listAllForCompany(companyId: String): List<StockItem> =
        stockItemDao.findAllForCompany(companyId).map { it.toDomain() }
}

internal fun StockItem.toEntity(companyId: String, dataFreshnessAt: String?): StockItemEntity = StockItemEntity(
    companyId = companyId,
    id = id,
    name = name,
    alias = alias,
    parentGroup = parentGroup,
    category = category,
    baseUnit = baseUnit,
    partNumber = partNumber,
    hsnCode = hsnCode,
    gstRate = gstRate,
    status = status.name.lowercase(),
    closingAmount = closingBalance?.amount,
    closingCurrencyCode = closingBalance?.currencyCode,
    closingSide = closingBalance?.side?.name?.lowercase(),
    dataQuality = dataQuality.name.lowercase(),
    syncedAt = syncedAt,
    dataFreshnessAt = dataFreshnessAt,
)

internal fun StockItemEntity.toDomain(): StockItem = StockItem(
    id = id,
    name = name,
    alias = alias,
    parentGroup = parentGroup,
    category = category,
    baseUnit = baseUnit,
    partNumber = partNumber,
    hsnCode = hsnCode,
    gstRate = gstRate,
    status = status.toStockStatus(),
    closingBalance = toMoneyAmount(),
    dataQuality = dataQuality.toStockDataQuality(),
    syncedAt = syncedAt,
)

private fun StockItemEntity.toMoneyAmount(): StockMoneyAmount? {
    val amount = closingAmount ?: return null
    val currency = closingCurrencyCode ?: return null
    val side = when (closingSide?.lowercase()) {
        "cr" -> StockAmountSide.Cr
        "dr" -> StockAmountSide.Dr
        else -> return null
    }
    return StockMoneyAmount(amount = amount, currencyCode = currency, side = side)
}

private fun String.toStockStatus(): StockItemStatus = when (lowercase()) {
    "active" -> StockItemStatus.Active
    "inactive" -> StockItemStatus.Inactive
    else -> StockItemStatus.Unknown
}

private fun String.toStockDataQuality(): StockItemDataQuality = when (lowercase()) {
    "complete" -> StockItemDataQuality.Complete
    "incomplete" -> StockItemDataQuality.Incomplete
    else -> StockItemDataQuality.Incomplete
}

private fun StockItemSortBy.toCacheSortKey(): String = when (this) {
    StockItemSortBy.Name -> "name"
    StockItemSortBy.ParentGroup -> "parentGroup"
    StockItemSortBy.Category -> "category"
    StockItemSortBy.BaseUnit -> "baseUnit"
    StockItemSortBy.SyncedAt -> "syncedAt"
}
