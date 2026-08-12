package com.budcom.android.feature.voucher.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * `index_cached_vouchers_companyId_date` and `index_cached_vouchers_companyId_status` (added in
 * schema v5) exist to serve the local-first Ledger statement's Last-7-Sales/date-period/coverage
 * queries in [com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao] without
 * a full company table scan — see that DAO's doc comment for the query shapes these back.
 */
@Entity(
    tableName = "cached_vouchers",
    primaryKeys = ["companyId", "voucherId"],
    indices = [
        Index(value = ["companyId", "date"]),
        Index(value = ["companyId", "status"]),
    ],
)
data class VoucherEntity(
    val companyId: String, val voucherId: String, val date: String, val type: String,
    val number: String?, val partyName: String?, val referenceNumber: String?,
    val amountValue: String?, val amountSide: String?, val status: String, val dataQuality: String,
    val lastSyncedAt: Long,
)

@Entity(tableName = "cached_voucher_details", primaryKeys = ["companyId", "voucherId"])
data class VoucherDetailEntity(
    val companyId: String, val voucherId: String, val effectiveDate: String?,
    val narration: String?, val lastSyncedAt: Long,
)

/** `index_cached_voucher_ledger_lines_companyId_ledgerName` (schema v5) — see [VoucherEntity]'s
 * doc comment; this is the join key the Ledger statement filters movements by. */
@Entity(
    tableName = "cached_voucher_ledger_lines",
    primaryKeys = ["companyId", "voucherId", "lineNumber"],
    indices = [Index(value = ["companyId", "ledgerName"])],
)
data class VoucherLedgerLineEntity(
    val companyId: String, val voucherId: String, val lineNumber: Int, val ledgerName: String,
    val amountValue: String, val amountSide: String?, val isDeemedPositive: Boolean?,
)

@Entity(tableName = "cached_voucher_inventory_lines", primaryKeys = ["companyId", "voucherId", "lineNumber"])
data class VoucherInventoryLineEntity(
    val companyId: String, val voucherId: String, val lineNumber: Int, val itemName: String,
    val quantity: String?, val rate: String?, val amountValue: String?, val amountSide: String?,
)

@Entity(tableName = "voucher_cache_meta", primaryKeys = ["companyId"])
data class VoucherCacheMetaEntity(val companyId: String, val lastSyncedAt: Long)
