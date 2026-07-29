package com.budcom.android.feature.company.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached Connector company discovery metadata (not session SoR).
 */
@Entity(tableName = "cached_companies")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val financialYear: String?,
    val booksFrom: String?,
    val baseCurrency: String?,
)

/**
 * Singleton row holding the last successful company-discovery envelope.
 */
@Entity(tableName = "company_discovery_meta")
data class CompanyDiscoveryMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val schemaVersion: String,
    val dataFreshnessAt: String,
    val contractVersion: String,
    val status: String,
    val tallyReachable: Boolean,
    val dataQualityStatus: String?,
    val dataQualityReason: String?,
    val reason: String?,
    val cachedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
