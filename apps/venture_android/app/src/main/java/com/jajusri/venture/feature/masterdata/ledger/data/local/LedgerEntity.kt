package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_ledgers",
    primaryKeys = ["companyId", "id"],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "name"]),
    ],
)
data class LedgerEntity(
    val companyId: String,
    val id: String,
    val name: String,
    val alias: String?,
    val parentGroup: String?,
    val status: String,
    val closingAmount: String?,
    val closingCurrencyCode: String?,
    val closingSide: String?,
    val dataQuality: String,
    val syncedAt: String,
    val dataFreshnessAt: String?,
)
