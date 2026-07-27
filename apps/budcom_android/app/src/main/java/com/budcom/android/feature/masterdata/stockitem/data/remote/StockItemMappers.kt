package com.budcom.android.feature.masterdata.stockitem.data.remote

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

internal fun StockItemListResponseDto.toDomain(): StockItemPage = StockItemPage(
    items = items.filterNot { it.isDeleted }.map { it.toDomain() },
    pagination = MasterDataPagination(
        page = pagination.page,
        pageSize = pagination.pageSize,
        totalItems = pagination.totalItems,
        totalPages = pagination.totalPages,
    ),
    dataFreshnessAt = dataFreshnessAt,
)

internal fun StockItemSummaryDto.toDomain(): StockItem = StockItem(
    id = id,
    name = name,
    alias = alias?.takeIf { it.isNotBlank() },
    parentGroup = parentGroup?.takeIf { it.isNotBlank() },
    category = category?.takeIf { it.isNotBlank() },
    baseUnit = baseUnit?.takeIf { it.isNotBlank() },
    partNumber = partNumber?.takeIf { it.isNotBlank() },
    hsnCode = hsnCode?.takeIf { it.isNotBlank() },
    gstRate = gstRate?.takeIf { it.isNotBlank() },
    status = status.toStockItemStatus(),
    closingBalance = closingBalance?.toDomain(),
    dataQuality = dataQuality.toStockItemDataQuality(),
    syncedAt = syncedAt,
)

internal fun StockNormalizedAmountDto.toDomain(): StockMoneyAmount = StockMoneyAmount(
    amount = amount,
    currencyCode = currencyCode,
    side = when (side.lowercase()) {
        "cr" -> StockAmountSide.Cr
        else -> StockAmountSide.Dr
    },
)

internal fun String.toStockItemStatus(): StockItemStatus = when (lowercase()) {
    "active" -> StockItemStatus.Active
    "inactive" -> StockItemStatus.Inactive
    else -> StockItemStatus.Unknown
}

internal fun String.toStockItemDataQuality(): StockItemDataQuality = when (lowercase()) {
    "complete" -> StockItemDataQuality.Complete
    "incomplete" -> StockItemDataQuality.Incomplete
    else -> StockItemDataQuality.Incomplete
}

internal fun StockItemQuery.toApiSortBy(): String = when (sortBy) {
    StockItemSortBy.Name -> "name"
    StockItemSortBy.ParentGroup -> "parentGroup"
    StockItemSortBy.Category -> "category"
    StockItemSortBy.BaseUnit -> "baseUnit"
    StockItemSortBy.SyncedAt -> "syncedAt"
}

internal fun StockItemQuery.toApiSortDirection(): String = when (sortDirection) {
    StockItemSortDirection.Asc -> "asc"
    StockItemSortDirection.Desc -> "desc"
}

internal fun StockItemQuery.normalizedText(): String? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.take(MasterDataBrowserDefaults.MAX_QUERY_LENGTH)
