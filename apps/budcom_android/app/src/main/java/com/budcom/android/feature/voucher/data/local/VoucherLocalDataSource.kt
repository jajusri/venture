package com.budcom.android.feature.voucher.data.local

import com.budcom.android.feature.voucher.domain.model.*
import javax.inject.Inject

interface VoucherLocalDataSource {
    /**
     * [scopeFrom]/[scopeTo] must be the COMPLETE authoritative range [items] represents (the
     * refresh query's own date range) — any previously-cached voucher in that scope absent from
     * [items] is pruned as part of this same transactional replacement, never left as a stale
     * phantom entry.
     */
    suspend fun storeList(
        companyId: String,
        items: List<VoucherSummary>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    )
    /**
     * Same authoritative-window replacement as [storeList], but [items] carry complete detail
     * data (ledger/inventory lines, narration, effectiveDate) persisted atomically alongside the
     * summary rows — every Voucher in scope becomes immediately usable offline with no follow-up
     * per-Voucher download.
     */
    suspend fun storeListWithDetails(
        companyId: String,
        items: List<VoucherDetails>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    )
    suspend fun storeDetails(companyId: String, details: VoucherDetails, syncedAt: Long)
    suspend fun list(query: VoucherQuery): VoucherPage?
    suspend fun details(companyId: String, voucherId: String): VoucherDetails?
    /** Best-effort header lookup, available even when full details have not been downloaded yet. */
    suspend fun summary(companyId: String, voucherId: String): VoucherSummary?
}

class RoomVoucherLocalDataSource @Inject constructor(private val dao: VoucherDao) : VoucherLocalDataSource {
    override suspend fun storeList(
        companyId: String,
        items: List<VoucherSummary>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    ) = dao.storeList(
        companyId,
        items.distinctBy { it.identity.id }.map { it.entity(companyId, syncedAt) },
        syncedAt,
        scopeFrom,
        scopeTo,
    )

    override suspend fun storeListWithDetails(
        companyId: String,
        items: List<VoucherDetails>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    ) {
        val distinct = items.distinctBy { it.summary.identity.id }
        dao.storeListWithDetails(
            companyId,
            distinct.map { it.summary.entity(companyId, syncedAt) },
            distinct.map { VoucherDetailEntity(companyId, it.summary.identity.id, it.effectiveDate, it.narration, syncedAt) },
            distinct.flatMap { details ->
                val id = details.summary.identity.id
                details.ledgerEntries.map { VoucherLedgerLineEntity(companyId, id, it.lineNumber, it.ledgerName, it.amount.value, it.amount.side?.name, it.isDeemedPositive) }
            },
            distinct.flatMap { details ->
                val id = details.summary.identity.id
                details.inventoryEntries.map { VoucherInventoryLineEntity(companyId, id, it.lineNumber, it.itemName, it.quantity, it.rate, it.amount?.value, it.amount?.side?.name) }
            },
            syncedAt,
            scopeFrom,
            scopeTo,
        )
    }

    override suspend fun storeDetails(companyId: String, details: VoucherDetails, syncedAt: Long) {
        val id = details.summary.identity.id
        dao.storeDetails(
            details.summary.entity(companyId, syncedAt),
            VoucherDetailEntity(companyId, id, details.effectiveDate, details.narration, syncedAt),
            details.ledgerEntries.map { VoucherLedgerLineEntity(companyId, id, it.lineNumber, it.ledgerName, it.amount.value, it.amount.side?.name, it.isDeemedPositive) },
            details.inventoryEntries.map { VoucherInventoryLineEntity(companyId, id, it.lineNumber, it.itemName, it.quantity, it.rate, it.amount?.value, it.amount?.side?.name) },
        )
    }

    override suspend fun list(query: VoucherQuery): VoucherPage? {
        val meta = dao.meta(query.companyId) ?: return null
        val needle = query.searchText?.trim()?.lowercase()
        val filtered = dao.vouchers(query.companyId).asSequence()
            .filter { it.date in query.dateRange.from..query.dateRange.to }
            .filter { query.voucherType.isNullOrBlank() || it.type.equals(query.voucherType, true) }
            .filter { query.voucherNumber.isNullOrBlank() || it.number?.contains(query.voucherNumber!!, true) == true }
            .filter { query.partyName.isNullOrBlank() || it.partyName?.contains(query.partyName!!, true) == true }
            .filter { needle.isNullOrBlank() || listOf(it.number, it.partyName, it.referenceNumber, it.type).any { value -> value?.lowercase()?.contains(needle) == true } }
            .sortedWith(query.comparator()).toList()
        val size = query.pageSize.coerceIn(1, 100); val page = query.page.coerceAtLeast(1)
        val start = ((page - 1) * size).coerceAtMost(filtered.size)
        val items = filtered.drop(start).take(size).map { it.domain() }
        val pages = if (filtered.isEmpty()) 0 else (filtered.size + size - 1) / size
        return VoucherPage(query.companyId, items, page, size, filtered.size, pages, VoucherCacheState.Offline, meta.lastSyncedAt)
    }

    override suspend fun details(companyId: String, voucherId: String): VoucherDetails? {
        val header = dao.voucher(companyId, voucherId) ?: return null
        val detail = dao.detail(companyId, voucherId) ?: return null
        return VoucherDetails(
            header.domain(), detail.effectiveDate, detail.narration,
            dao.ledgerLines(companyId, voucherId).map { VoucherLedgerLine(it.lineNumber, it.ledgerName, VoucherMoney(it.amountValue, it.amountSide.side()), it.isDeemedPositive) },
            dao.inventoryLines(companyId, voucherId).map { VoucherInventoryLine(it.lineNumber, it.itemName, it.quantity, it.rate, it.amountValue?.let { value -> VoucherMoney(value, it.amountSide.side()) }) },
            VoucherCacheState.Offline, detail.lastSyncedAt,
        )
    }

    override suspend fun summary(companyId: String, voucherId: String): VoucherSummary? =
        dao.voucher(companyId, voucherId)?.domain()
}

private fun VoucherSummary.entity(companyId: String, syncedAt: Long) = VoucherEntity(companyId, identity.id, date, type, number, partyName, referenceNumber, amount?.value, amount?.side?.name, status.name, dataQuality.name, syncedAt)
private fun VoucherEntity.domain() = VoucherSummary(VoucherIdentity(voucherId), date, type, number, partyName, referenceNumber, amountValue?.let { VoucherMoney(it, amountSide.side()) }, enumValue(status, VoucherStatus.Unknown), enumValue(dataQuality, VoucherDataQuality.Incomplete))
private fun String?.side() = this?.let { runCatching { VoucherMoneySide.valueOf(it) }.getOrNull() }
private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T) = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
private fun VoucherQuery.comparator(): Comparator<VoucherEntity> {
    val base = when (sort.field) {
        VoucherSortField.Date -> compareBy<VoucherEntity> { it.date }
        VoucherSortField.VoucherNumber -> compareBy { it.number.orEmpty() }
        VoucherSortField.Amount -> compareBy { it.amountValue?.toBigDecimalOrNull() }
    }
    return if (sort.direction == VoucherSortDirection.Desc) base.reversed() else base
}
