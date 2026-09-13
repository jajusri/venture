package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Dao
import androidx.room.Query

/** One joined row: a `cached_voucher_ledger_lines` line plus its parent `cached_vouchers` header,
 * for exactly one ledger. Mirrors the Connector's own `VoucherLedgerMovement` join shape. */
data class LedgerMovementRow(
    val voucherId: String,
    val date: String,
    val voucherType: String,
    val voucherNumber: String?,
    val referenceNumber: String?,
    val lineNumber: Int,
    val amountValue: String,
    val amountSide: String?,
)

/**
 * Reads Ledger movements directly from the existing, already-fully-populated Voucher cache
 * (`cached_vouchers` join `cached_voucher_ledger_lines`, filtered by `ledgerName`) — no separate
 * Ledger-movement table exists or is needed. `VoucherRepositoryImpl.refreshVouchers()`
 * unconditionally forces `includeDetails = true` on every normal window sync/reconciliation, so
 * every synced Voucher's ledger lines are already local before this DAO ever runs a query, and
 * `VoucherDao.storeListWithDetails()`'s scope-bounded clear-then-insert already gives edited/
 * moved/cancelled-then-resynced Vouchers correct, phantom-free reconciliation for free — this DAO
 * only reads, it never writes.
 *
 * Every query is scoped by `companyId` first and filtered to `status = 'Active'` (see
 * [com.jajusri.venture.feature.voucher.domain.model.VoucherStatus]) so a Cancelled Voucher never
 * contributes to a balance or a Last-7-Sales count. Ledger identity is matched by `ledgerName`
 * (exact, case-sensitive) — the same join key the Connector's own `findLedgerMovements` uses,
 * since Tally's own voucher-line export has no stable per-line ledger id, only a name.
 */
@Dao
interface LedgerMovementDao {

    /**
     * The last [limit] distinct (voucherId, date) Sales vouchers touching this ledger, most
     * recent first — the seed for the Last-7-Sales default period. Receipts/Payments/Journals/
     * etc. are excluded by the `voucherType = 'Sales'` filter, so they never consume the count.
     */
    @Query(
        """
        SELECT DISTINCT h.voucherId AS voucherId, h.date AS date, h.type AS voucherType,
               h.number AS voucherNumber, h.referenceNumber AS referenceNumber,
               e.lineNumber AS lineNumber, e.amountValue AS amountValue, e.amountSide AS amountSide
        FROM cached_voucher_ledger_lines e
        JOIN cached_vouchers h ON h.companyId = e.companyId AND h.voucherId = e.voucherId
        WHERE e.companyId = :companyId AND e.ledgerName = :ledgerName
          AND h.status = 'Active' AND h.type = 'Sales'
        GROUP BY h.voucherId
        ORDER BY h.date DESC, h.voucherId DESC
        LIMIT :limit
        """,
    )
    suspend fun lastSalesMovements(companyId: String, ledgerName: String, limit: Int): List<LedgerMovementRow>

    /**
     * Every active-status movement for this ledger within [from]..[to] inclusive, in accounting
     * order (date ascending; `voucherId` is a stable but not Tally-authoritative same-day
     * tiebreak — the Connector's own `voucher_key`/`voucher_retain_key` staging-order fields are
     * not currently exposed to Android by the Voucher list/detail API, see Phase 1 audit note in
     * the accompanying report).
     */
    @Query(
        """
        SELECT h.voucherId AS voucherId, h.date AS date, h.type AS voucherType,
               h.number AS voucherNumber, h.referenceNumber AS referenceNumber,
               e.lineNumber AS lineNumber, e.amountValue AS amountValue, e.amountSide AS amountSide
        FROM cached_voucher_ledger_lines e
        JOIN cached_vouchers h ON h.companyId = e.companyId AND h.voucherId = e.voucherId
        WHERE e.companyId = :companyId AND e.ledgerName = :ledgerName
          AND h.status = 'Active' AND h.date BETWEEN :from AND :to
        ORDER BY h.date ASC, h.voucherId ASC, e.lineNumber ASC
        """,
    )
    suspend fun movementsInRange(companyId: String, ledgerName: String, from: String, to: String): List<LedgerMovementRow>

    /** Narration for a set of vouchers, keyed by voucherId — populated by the same normal Voucher
     * sync (`cached_voucher_details`), read separately since it's a 1-row-per-voucher table. */
    @Query("SELECT voucherId, narration FROM cached_voucher_details WHERE companyId = :companyId AND voucherId IN (:voucherIds)")
    suspend fun narrations(companyId: String, voucherIds: List<String>): List<VoucherNarrationRow>

    /**
     * Inventory (item) lines for a set of vouchers in one batched query — the Detailed-statement
     * equivalent of [narrations]'s existing batching pattern, so rendering N transactions never
     * costs N Room round trips. A voucher absent from the result has no inventory lines at all
     * (a non-item voucher such as Receipt/Payment/Contra/Journal) — callers must not fabricate
     * rows for it.
     */
    @Query(
        "SELECT voucherId, lineNumber, itemName, quantity, rate, amountValue, amountSide " +
            "FROM cached_voucher_inventory_lines WHERE companyId = :companyId AND voucherId IN (:voucherIds) " +
            "ORDER BY voucherId, lineNumber",
    )
    suspend fun inventoryLinesForVouchers(companyId: String, voucherIds: List<String>): List<LedgerMovementInventoryRow>

    /**
     * Earliest Voucher date currently cached for this company, across all ledgers — the coverage
     * floor used to decide whether a requested period's start predates anything we can prove was
     * synced. This is an approximation, not the Connector's authoritative per-snapshot
     * date_from/date_to metadata (Android has no local equivalent of that yet): it can only prove
     * a gap exists (`from < floor`), never prove full internal completeness of a range that starts
     * at or after the floor. Documented as a known limitation in the accompanying report.
     */
    @Query("SELECT MIN(date) FROM cached_vouchers WHERE companyId = :companyId")
    suspend fun earliestSyncedDate(companyId: String): String?
}

data class VoucherNarrationRow(val voucherId: String, val narration: String?)

data class LedgerMovementInventoryRow(
    val voucherId: String,
    val lineNumber: Int,
    val itemName: String,
    val quantity: String?,
    val rate: String?,
    val amountValue: String?,
    val amountSide: String?,
)
