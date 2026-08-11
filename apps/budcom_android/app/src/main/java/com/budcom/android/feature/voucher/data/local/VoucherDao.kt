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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDetails(rows: List<VoucherDetailEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLedgerLines(rows: List<VoucherLedgerLineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInventoryLines(rows: List<VoucherInventoryLineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMeta(row: VoucherCacheMetaEntity)
    @Query("DELETE FROM cached_voucher_ledger_lines WHERE companyId = :companyId AND voucherId = :voucherId") suspend fun deleteLedgerLines(companyId: String, voucherId: String)
    @Query("DELETE FROM cached_voucher_inventory_lines WHERE companyId = :companyId AND voucherId = :voucherId") suspend fun deleteInventoryLines(companyId: String, voucherId: String)

    @Query("SELECT voucherId FROM cached_vouchers WHERE companyId = :companyId AND date BETWEEN :from AND :to AND voucherId NOT IN (:keepIds)")
    suspend fun staleVoucherIdsInScope(companyId: String, from: String, to: String, keepIds: List<String>): List<String>
    @Query("SELECT voucherId FROM cached_vouchers WHERE companyId = :companyId AND date BETWEEN :from AND :to")
    suspend fun voucherIdsInScope(companyId: String, from: String, to: String): List<String>
    @Query("DELETE FROM cached_vouchers WHERE companyId = :companyId AND voucherId IN (:voucherIds)")
    suspend fun deleteVouchersById(companyId: String, voucherIds: List<String>)
    @Query("DELETE FROM cached_voucher_details WHERE companyId = :companyId AND voucherId IN (:voucherIds)")
    suspend fun deleteDetailsById(companyId: String, voucherIds: List<String>)
    @Query("DELETE FROM cached_voucher_ledger_lines WHERE companyId = :companyId AND voucherId IN (:voucherIds)")
    suspend fun deleteLedgerLinesById(companyId: String, voucherIds: List<String>)
    @Query("DELETE FROM cached_voucher_inventory_lines WHERE companyId = :companyId AND voucherId IN (:voucherIds)")
    suspend fun deleteInventoryLinesById(companyId: String, voucherIds: List<String>)

    /**
     * Scope-bounded full replacement for one authoritative company+date-window refresh: [rows]
     * is treated as the COMPLETE truth for [scopeFrom]..[scopeTo] (matching the Connector's own
     * windowed-refresh contract), so any previously-cached voucher in that scope absent from
     * [rows] — cancelled, back-dated out of range, or a stale duplicate under a changed derived
     * id — is pruned along with its detail/line rows, never left as a phantom. Vouchers outside
     * the scope are untouched. The whole operation is one Room [Transaction], so observers only
     * ever see the prior complete state or the next complete state, never a partial mix.
     */
    @Transaction
    suspend fun storeList(
        companyId: String,
        rows: List<VoucherEntity>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    ) {
        val staleIds = if (rows.isEmpty()) {
            voucherIdsInScope(companyId, scopeFrom, scopeTo)
        } else {
            staleVoucherIdsInScope(companyId, scopeFrom, scopeTo, rows.map { it.voucherId })
        }
        if (staleIds.isNotEmpty()) {
            deleteVouchersById(companyId, staleIds)
            deleteDetailsById(companyId, staleIds)
            deleteLedgerLinesById(companyId, staleIds)
            deleteInventoryLinesById(companyId, staleIds)
        }
        if (rows.isNotEmpty()) upsertVouchers(rows)
        upsertMeta(VoucherCacheMetaEntity(companyId, syncedAt))
    }

    /**
     * Same authoritative-window replacement as [storeList], but also atomically persists the
     * complete per-Voucher detail (ledger/inventory lines, narration, effectiveDate) that came
     * back in the same offline-complete sync response — so every Voucher in [rows] is immediately
     * usable (list, detail, Share/PDF) with no follow-up per-Voucher download. Old ledger/
     * inventory lines for every fresh id are cleared before re-inserting (not just upserted): a
     * Voucher whose line count shrank between syncs must not keep phantom trailing rows, matching
     * the same clear-then-insert pattern [storeDetails] already uses for a single Voucher.
     */
    @Transaction
    suspend fun storeListWithDetails(
        companyId: String,
        rows: List<VoucherEntity>,
        details: List<VoucherDetailEntity>,
        ledgerLines: List<VoucherLedgerLineEntity>,
        inventoryLines: List<VoucherInventoryLineEntity>,
        syncedAt: Long,
        scopeFrom: String,
        scopeTo: String,
    ) {
        val staleIds = if (rows.isEmpty()) {
            voucherIdsInScope(companyId, scopeFrom, scopeTo)
        } else {
            staleVoucherIdsInScope(companyId, scopeFrom, scopeTo, rows.map { it.voucherId })
        }
        if (staleIds.isNotEmpty()) {
            deleteVouchersById(companyId, staleIds)
            deleteDetailsById(companyId, staleIds)
            deleteLedgerLinesById(companyId, staleIds)
            deleteInventoryLinesById(companyId, staleIds)
        }
        if (rows.isNotEmpty()) {
            val freshIds = rows.map { it.voucherId }
            upsertVouchers(rows)
            deleteLedgerLinesById(companyId, freshIds)
            deleteInventoryLinesById(companyId, freshIds)
            upsertDetails(details)
            if (ledgerLines.isNotEmpty()) upsertLedgerLines(ledgerLines)
            if (inventoryLines.isNotEmpty()) upsertInventoryLines(inventoryLines)
        }
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
