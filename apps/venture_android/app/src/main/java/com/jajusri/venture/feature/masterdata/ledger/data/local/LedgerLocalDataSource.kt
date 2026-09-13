package com.jajusri.venture.feature.masterdata.ledger.data.local

import com.jajusri.venture.core.util.AliasSearchClassifier
import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.ledger.domain.model.AmountSide
import com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerSortBy
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerSortDirection
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatus
import com.jajusri.venture.feature.masterdata.ledger.domain.model.MoneyAmount
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

interface LedgerLocalDataSource {
    suspend fun hasCache(companyId: String): Boolean
    suspend fun upsert(companyId: String, items: List<Ledger>, dataFreshnessAt: String?)
    suspend fun replaceAll(companyId: String, items: List<Ledger>, dataFreshnessAt: String?)
    suspend fun query(companyId: String, query: LedgerQuery): LedgerPage?
}

@Singleton
class RoomLedgerLocalDataSource @Inject constructor(
    private val ledgerDao: LedgerDao,
) : LedgerLocalDataSource {

    override suspend fun hasCache(companyId: String): Boolean = ledgerDao.countForCompany(companyId) > 0

    override suspend fun upsert(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
        if (items.isEmpty()) return
        ledgerDao.upsertAll(items.map { it.toEntity(companyId, dataFreshnessAt) })
    }

    override suspend fun replaceAll(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
        ledgerDao.replaceAllForCompany(
            companyId = companyId,
            entities = items.map { it.toEntity(companyId, dataFreshnessAt) },
        )
    }

    override suspend fun query(companyId: String, query: LedgerQuery): LedgerPage? {
        if (!hasCache(companyId)) return null
        val normalized = query.text?.trim()?.takeIf { it.isNotEmpty() }
            ?.take(MasterDataBrowserDefaults.MAX_QUERY_LENGTH)
        val page = query.page.coerceAtLeast(1)
        val pageSize = query.pageSize.coerceIn(1, 100)
        val totalItems = ledgerDao.countMatching(companyId, normalized)
        val totalPages = if (totalItems == 0) 0 else ceil(totalItems / pageSize.toDouble()).toInt()
        val entities = ledgerDao.queryPage(
            companyId = companyId,
            query = normalized,
            sortBy = query.sortBy.toCacheSortKey(),
            ascending = if (query.sortDirection == LedgerSortDirection.Asc) 1 else 0,
            limit = pageSize,
            offset = (page - 1) * pageSize,
            exactAliasFirst = if (AliasSearchClassifier.isShortNumericAlias(normalized)) 1 else 0,
        )
        val freshness = entities.firstOrNull()?.dataFreshnessAt
        return LedgerPage(
            items = entities.map { it.toDomain() },
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = totalPages,
            dataFreshnessAt = freshness,
        )
    }
}

internal fun Ledger.toEntity(companyId: String, dataFreshnessAt: String?): LedgerEntity = LedgerEntity(
    companyId = companyId,
    id = id,
    name = name,
    alias = alias,
    parentGroup = parentGroup,
    status = status.name.lowercase(),
    closingAmount = closingBalance?.amount,
    closingCurrencyCode = closingBalance?.currencyCode,
    closingSide = closingBalance?.side?.name?.lowercase(),
    dataQuality = dataQuality.name.lowercase(),
    syncedAt = syncedAt,
    dataFreshnessAt = dataFreshnessAt,
)

internal fun LedgerEntity.toDomain(): Ledger = Ledger(
    id = id,
    name = name,
    alias = alias,
    parentGroup = parentGroup,
    status = status.toLedgerStatus(),
    closingBalance = toMoneyAmount(),
    dataQuality = dataQuality.toLedgerDataQuality(),
    syncedAt = syncedAt,
)

private fun LedgerEntity.toMoneyAmount(): MoneyAmount? {
    val amount = closingAmount ?: return null
    val currency = closingCurrencyCode ?: return null
    val side = when (closingSide?.lowercase()) {
        "cr" -> AmountSide.Cr
        "dr" -> AmountSide.Dr
        else -> return null
    }
    return MoneyAmount(amount = amount, currencyCode = currency, side = side)
}

private fun String.toLedgerStatus(): LedgerStatus = when (lowercase()) {
    "active" -> LedgerStatus.Active
    "inactive" -> LedgerStatus.Inactive
    "reserved" -> LedgerStatus.Reserved
    else -> LedgerStatus.Unknown
}

private fun String.toLedgerDataQuality(): LedgerDataQuality = when (lowercase()) {
    "complete" -> LedgerDataQuality.Complete
    "partial" -> LedgerDataQuality.Partial
    "invalid" -> LedgerDataQuality.Invalid
    else -> LedgerDataQuality.Partial
}

private fun LedgerSortBy.toCacheSortKey(): String = when (this) {
    LedgerSortBy.Name -> "name"
    LedgerSortBy.ParentGroup -> "parentGroup"
    LedgerSortBy.ClosingBalance -> "closingBalance"
    LedgerSortBy.SyncedAt -> "syncedAt"
}
