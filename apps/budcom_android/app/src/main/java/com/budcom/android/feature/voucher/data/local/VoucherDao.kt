package com.budcom.android.feature.voucher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VoucherDao {
    @Query("SELECT * FROM cached_vouchers WHERE companyId = :companyId")
    suspend fun vouchers(companyId: String): List<VoucherEntity>
    @Query("SELECT * FROM cached_vouchers WHERE companyId = :companyId AND voucherId = :voucherId")
    suspend fun voucher(companyId: String, voucherId: String): VoucherEntity?
    @Query("SELECT * FROM cached_voucher_details WHERE companyId = :companyId AND voucherId = :voucherId")
    suspend fun detail(companyId: String, voucherId: String): VoucherDetailEntity?
    @Query("SELECT * FROM cached_voucher_ledger_lines WHERE companyId = :companyId AND voucherId = :voucherId ORDER BY lineNumber")
    suspend fun ledgerLines(companyId: String, voucherId: String): List<VoucherLedgerLineEntity>
    @Query("SELECT * FROM cached_voucher_inventory_lines WHERE companyId = :companyId AND voucherId = :voucherId ORDER BY lineNumber")
    suspend fun inventoryLines(companyId: String, voucherId: String): List<VoucherInventoryLineEntity>
    @Query("SELECT * FROM voucher_cache_meta WHERE companyId = :companyId")
    suspend fun meta(companyId: String): VoucherCacheMetaEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVouchers(rows: List<VoucherEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVoucher(row: VoucherEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDetail(row: VoucherDetailEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLedgerLines(rows: List<VoucherLedgerLineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInventoryLines(rows: List<VoucherInventoryLineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMeta(row: VoucherCacheMetaEntity)
    @Query("DELETE FROM cached_voucher_ledger_lines WHERE companyId = :companyId AND voucherId = :voucherId") suspend fun deleteLedgerLines(companyId: String, voucherId: String)
    @Query("DELETE FROM cached_voucher_inventory_lines WHERE companyId = :companyId AND voucherId = :voucherId") suspend fun deleteInventoryLines(companyId: String, voucherId: String)

    @Transaction suspend fun storeList(companyId: String, rows: List<VoucherEntity>, syncedAt: Long) {
        if (rows.isNotEmpty()) upsertVouchers(rows)
        upsertMeta(VoucherCacheMetaEntity(companyId, syncedAt))
    }
    @Transaction suspend fun storeDetails(header: VoucherEntity, detail: VoucherDetailEntity, ledger: List<VoucherLedgerLineEntity>, inventory: List<VoucherInventoryLineEntity>) {
        upsertVoucher(header); upsertDetail(detail)
        deleteLedgerLines(header.companyId, header.voucherId); deleteInventoryLines(header.companyId, header.voucherId)
        if (ledger.isNotEmpty()) upsertLedgerLines(ledger)
        if (inventory.isNotEmpty()) upsertInventoryLines(inventory)
        upsertMeta(VoucherCacheMetaEntity(header.companyId, detail.lastSyncedAt))
    }
}
