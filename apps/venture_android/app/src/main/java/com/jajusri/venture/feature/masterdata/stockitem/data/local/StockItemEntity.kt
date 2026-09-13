package com.jajusri.venture.feature.masterdata.stockitem.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_stock_items",
    primaryKeys = ["companyId", "id"],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "name"]),
    ],
)
data class StockItemEntity(
    val companyId: String,
    val id: String,
    val name: String,
    val alias: String?,
    val parentGroup: String?,
    val category: String?,
    val baseUnit: String?,
    val partNumber: String?,
    val hsnCode: String?,
    val gstRate: String?,
    val status: String,
    val closingAmount: String?,
    val closingCurrencyCode: String?,
    val closingSide: String?,
    val dataQuality: String,
    val syncedAt: String,
    val dataFreshnessAt: String?,
)
