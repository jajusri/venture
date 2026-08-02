package com.budcom.android.feature.voucher.data.local

import androidx.room.Entity

@Entity(tableName = "cached_vouchers", primaryKeys = ["companyId", "voucherId"])
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

@Entity(tableName = "cached_voucher_ledger_lines", primaryKeys = ["companyId", "voucherId", "lineNumber"])
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
