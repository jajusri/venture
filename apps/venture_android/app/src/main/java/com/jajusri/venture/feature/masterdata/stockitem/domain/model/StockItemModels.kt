package com.jajusri.venture.feature.masterdata.stockitem.domain.model

import com.jajusri.venture.feature.masterdata.domain.model.MasterDataPagination

/**
 * Stock item list query matching Connector `GET /stock-items` search parameters.
 */
data class StockItemQuery(
    val text: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50,
    val sortBy: StockItemSortBy = StockItemSortBy.Name,
    val sortDirection: StockItemSortDirection = StockItemSortDirection.Asc,
)

enum class StockItemSortBy {
    Name,
    ParentGroup,
    Category,
    BaseUnit,
    SyncedAt,
}

enum class StockItemSortDirection {
    Asc,
    Desc,
}

data class StockMoneyAmount(
    val amount: String,
    val currencyCode: String,
    val side: StockAmountSide,
)

enum class StockAmountSide {
    Dr,
    Cr,
}

enum class StockItemStatus {
    Active,
    Inactive,
    Unknown,
}

enum class StockItemDataQuality {
    Complete,
    Incomplete,
}

/**
 * Domain stock item row for browser lists (from Connector StockItemSummary).
 */
data class StockItem(
    val id: String,
    val name: String,
    val alias: String?,
    val parentGroup: String?,
    val category: String?,
    val baseUnit: String?,
    val partNumber: String?,
    val hsnCode: String?,
    val gstRate: String?,
    val status: StockItemStatus,
    val closingBalance: StockMoneyAmount?,
    val dataQuality: StockItemDataQuality,
    val syncedAt: String,
)

data class StockItemPage(
    val items: List<StockItem>,
    val pagination: MasterDataPagination,
    val dataFreshnessAt: String?,
)
