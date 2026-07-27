package com.budcom.android.feature.masterdata.stockitem.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class StockItemListResponseDto(
    val schemaVersion: String? = null,
    val dataFreshnessAt: String? = null,
    val items: List<StockItemSummaryDto> = emptyList(),
    val pagination: StockItemPaginationDto,
)

@Serializable
data class StockItemPaginationDto(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Serializable
data class StockItemSummaryDto(
    val id: String,
    val name: String,
    val normalizedName: String? = null,
    val parentGroup: String? = null,
    val category: String? = null,
    val baseUnit: String? = null,
    val dataQuality: String,
    val openingBalance: StockNormalizedAmountDto? = null,
    val closingBalance: StockNormalizedAmountDto? = null,
    val hsnCode: String? = null,
    val gstRate: String? = null,
    val guid: String? = null,
    val alterId: String? = null,
    val alias: String? = null,
    val partNumber: String? = null,
    val status: String,
    val sourceSystem: String? = null,
    val isDeleted: Boolean = false,
    val syncedAt: String,
)

@Serializable
data class StockNormalizedAmountDto(
    val amount: String,
    val currencyCode: String,
    val side: String,
)
