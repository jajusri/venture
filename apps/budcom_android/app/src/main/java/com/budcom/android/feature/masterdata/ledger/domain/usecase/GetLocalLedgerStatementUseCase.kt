package com.budcom.android.feature.masterdata.ledger.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementInventoryRow
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementRow
import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodDefaults
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementCoverage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementItemDetail
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementItemLine
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first replacement for the network-fetched Ledger statement (BUDCOM MVP-1 Ledger
 * relaunch): resolves a [LedgerPeriodSelection] to a concrete date range, reads movements
 * straight out of the already-synced `cached_vouchers`/`cached_voucher_ledger_lines` tables via
 * [LedgerMovementDao], and computes opening/running/closing balances entirely in Kotlin —
 * `suspend`, but never touches the network. Room is the sole source of truth here, matching
 * [com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl]'s established
 * cache-only-vs-explicit-refresh split: this use case is the "cache-only" side; refreshing the
 * underlying data is the normal Voucher sync's job (see [RefreshLedgerCoverageUseCase]), not
 * this one's.
 *
 * Balance sign convention (internal to this computation only — never surfaced): Dr is positive,
 * Cr is negative. This is an independent, self-consistent convention for the local computation;
 * it does not need to bit-for-bit match the Connector's own (superseded, network-only)
 * `getLedgerStatement()` algorithm, since the two no longer share a balance computation.
 */
@Singleton
class GetLocalLedgerStatementUseCase @Inject constructor(
    private val ledgerDao: LedgerDao,
    private val movementDao: LedgerMovementDao,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Kolkata")),
) {
    suspend operator fun invoke(
        companyId: String,
        ledgerId: String,
        period: LedgerPeriodSelection,
        mode: LedgerStatementMode = LedgerStatementMode.Summary,
    ): AppResult<LedgerStatement> {
        val ledger = ledgerDao.findById(companyId, ledgerId)
            ?: return AppResult.Failure(AppError.Message(LEDGER_NOT_SYNCED_MESSAGE))

        val range = resolveRange(companyId, ledger.name, period)
        val today = LedgerPeriodDefaults.today(clock).toString()
        val syncedDate = runCatching { LocalDate.parse(ledger.syncedAt.take(10)) }.getOrNull()?.toString()
        val anchorClosing = ledger.closingAmount?.let { amount -> ledger.closingSide?.let { side -> amount to side.toAmountSide() } }

        val fetchTo = maxOf(range.to, syncedDate ?: range.to, today).let { if (range.to > it) range.to else it }
        val fetched = movementDao.movementsInRange(companyId, ledger.name, range.from, fetchTo)
        val displayRows = fetched.filter { it.date <= range.to }

        val balanceAvailable = anchorClosing != null && syncedDate != null && range.from <= syncedDate
        val balanceResult: Triple<LedgerStatementAmount?, LedgerStatementAmount?, Map<String, BigDecimal>> = if (balanceAvailable) {
            val (closingAmount, closingSide) = anchorClosing!!
            val netToSynced = fetched.filter { it.date <= syncedDate!! }.sumSigned()
            val openingSigned = closingSide.toSigned(closingAmount) - netToSynced
            val running = LinkedHashMap<String, BigDecimal>()
            var cursor = openingSigned
            for (row in displayRows) {
                cursor += row.signedAmount()
                running["${row.voucherId}#${row.lineNumber}"] = cursor
            }
            val closingSigned = running.values.lastOrNull() ?: openingSigned
            Triple(openingSigned.toStatementAmount(), closingSigned.toStatementAmount(), running)
        } else {
            Triple(null, null, emptyMap())
        }
        val (opening, closing, runningByVoucherLine) = balanceResult

        val earliestSynced = movementDao.earliestSyncedDate(companyId)
        val transactionsComplete = earliestSynced != null && range.from >= earliestSynced

        val displayVoucherIds = displayRows.map { it.voucherId }.distinct()
        val narrations = movementDao.narrations(companyId, displayVoucherIds)
            .associate { it.voucherId to it.narration }
        val itemDetailsByVoucher = if (mode == LedgerStatementMode.Detailed && displayVoucherIds.isNotEmpty()) {
            movementDao.inventoryLinesForVouchers(companyId, displayVoucherIds)
                .groupBy { it.voucherId }
                .mapValues { (_, lines) -> lines.toItemDetail() }
        } else {
            emptyMap()
        }

        val messages = mutableListOf<String>()
        if (!transactionsComplete) {
            messages += if (earliestSynced == null) {
                "No Voucher data has been synced locally yet for this company. Sync to populate this ledger's statement."
            } else {
                "Local data only reaches back to $earliestSynced. Sync older history to complete this period."
            }
        }
        if (!balanceAvailable) {
            messages += if (anchorClosing == null) {
                "Opening/closing balance unavailable: this ledger's balance has not been synced locally yet."
            } else {
                "Opening/closing balance unavailable: the selected period starts before this ledger's last balance sync ($syncedDate)."
            }
        }

        // A voucher can, in principle, contribute more than one ledger line to this same ledger
        // (a split entry) — each still needs its own accounting row for a correct running balance,
        // but the item block must appear at most once per voucher, never repeated across those
        // rows, so only the first displayed row for a given voucherId carries it.
        val voucherIdsWithItemDetailAttached = mutableSetOf<String>()
        val transactions = displayRows.map { row ->
            val itemDetail = itemDetailsByVoucher[row.voucherId]
                ?.takeIf { voucherIdsWithItemDetailAttached.add(row.voucherId) }
            LedgerStatementTransaction(
                voucherId = row.voucherId,
                date = row.date,
                voucherType = row.voucherType,
                voucherNumber = row.voucherNumber,
                referenceNumber = row.referenceNumber,
                narration = narrations[row.voucherId],
                debit = if (row.amountSide?.toAmountSide() == AmountSide.Dr) row.amountValue else null,
                credit = if (row.amountSide?.toAmountSide() == AmountSide.Cr) row.amountValue else null,
                runningBalance = runningByVoucherLine["${row.voucherId}#${row.lineNumber}"]?.toStatementAmount(),
                itemDetail = itemDetail,
            )
        }

        return AppResult.Success(
            LedgerStatement(
                ledgerId = ledger.id,
                ledgerName = ledger.name,
                ledgerAlias = ledger.alias,
                parentGroup = ledger.parentGroup,
                period = LedgerStatementDateRange(range.from, range.to),
                openingBalance = opening,
                closingBalance = closing,
                transactions = transactions,
                coverage = LedgerStatementCoverage(
                    transactionsComplete = transactionsComplete,
                    balanceAvailable = balanceAvailable,
                    syncedFrom = earliestSynced,
                    syncedTo = syncedDate,
                    message = messages.takeIf { it.isNotEmpty() }?.joinToString(" "),
                ),
            ),
        )
    }

    /**
     * Resolves [period] to a concrete `[from, to]`. [LedgerPeriodSelection.Last7Sales] counts
     * only `voucherType = 'Sales'` (Receipts/Payments/Journals/etc. never consume the count, per
     * the locked default-scope contract) and anchors `from` at the earliest of the last (up to)
     * [LedgerPeriodDefaults.DEFAULT_LAST_SALES_COUNT] Sales vouchers found; a ledger with fewer
     * than that many Sales vouchers uses all of them, and a ledger with none at all falls back to
     * its own earliest locally-known movement (any type) so the period is never fabricated wider
     * than what's actually cached.
     */
    private suspend fun resolveRange(
        companyId: String,
        ledgerName: String,
        period: LedgerPeriodSelection,
    ): LedgerStatementDateRange {
        val today = LedgerPeriodDefaults.today(clock).toString()
        return when (period) {
            is LedgerPeriodSelection.Last7Sales -> {
                val lastSales = movementDao.lastSalesMovements(companyId, ledgerName, LedgerPeriodDefaults.DEFAULT_LAST_SALES_COUNT)
                val from = lastSales.minOfOrNull { it.date }
                    ?: movementDao.movementsInRange(companyId, ledgerName, EARLIEST_POSSIBLE_DATE, today).minOfOrNull { it.date }
                    ?: today
                LedgerStatementDateRange(from, today)
            }
            is LedgerPeriodSelection.Today -> LedgerPeriodDefaults.todayRange(clock)
            is LedgerPeriodSelection.ThisMonth -> LedgerPeriodDefaults.thisMonth(clock)
            is LedgerPeriodSelection.LastMonth -> LedgerPeriodDefaults.lastMonth(clock)
            is LedgerPeriodSelection.CurrentFinancialYear -> LedgerPeriodDefaults.currentFinancialYear(clock)
            is LedgerPeriodSelection.PreviousFinancialYear -> LedgerPeriodDefaults.previousFinancialYear(clock)
            is LedgerPeriodSelection.Last30Days -> LedgerPeriodDefaults.last30Days(clock)
            is LedgerPeriodSelection.Custom -> LedgerStatementDateRange(period.from, period.to)
        }
    }

    private fun List<LedgerMovementRow>.sumSigned(): BigDecimal = fold(BigDecimal.ZERO) { acc, row -> acc + row.signedAmount() }

    private fun LedgerMovementRow.signedAmount(): BigDecimal {
        val value = amountValue.toBigDecimalOrNull() ?: return BigDecimal.ZERO
        return amountSide?.toAmountSide()?.toSigned(value) ?: BigDecimal.ZERO
    }

    private fun AmountSide.toSigned(amount: String): BigDecimal =
        toSigned(amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)

    private fun AmountSide.toSigned(amount: BigDecimal): BigDecimal = if (this == AmountSide.Dr) amount else amount.negate()

    private fun BigDecimal.toStatementAmount(): LedgerStatementAmount =
        if (this.signum() >= 0) LedgerStatementAmount(this.toPlainString(), AmountSide.Dr)
        else LedgerStatementAmount(this.negate().toPlainString(), AmountSide.Cr)

    private fun String.toAmountSide(): AmountSide = when (lowercase()) {
        "cr", "credit" -> AmountSide.Cr
        else -> AmountSide.Dr
    }

    /**
     * Maps one voucher's already-synced inventory lines (in [LedgerMovementInventoryRow.lineNumber]
     * order, from the batched query) into a display-ready [LedgerStatementItemDetail]. [totalLabel]
     * sums the lines' own amounts purely for this commercial-detail display — it is never compared
     * against or substituted for the voucher-level [LedgerStatementTransaction.debit]/[credit],
     * which always come from the existing authoritative ledger-line data untouched.
     */
    private fun List<LedgerMovementInventoryRow>.toItemDetail(): LedgerStatementItemDetail {
        val items = map { line ->
            LedgerStatementItemLine(
                itemName = line.itemName,
                quantityLabel = line.quantity?.takeIf(String::isNotBlank),
                // Tally's stored rate is a compound display string (e.g. "26.00/Nos"), not a
                // plain decimal — must be preserved verbatim, never parsed/reformatted. Matches
                // the existing precedent in VoucherDetailsUiState's own rate mapping.
                rateLabel = line.rate?.takeIf(String::isNotBlank),
                amountLabel = line.amountValue?.toBigDecimalOrNull()?.let(::formatInr),
            )
        }
        val total = fold(BigDecimal.ZERO) { acc, line ->
            acc + (line.amountValue?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
        return LedgerStatementItemDetail(items = items, totalLabel = formatInr(total))
    }

    companion object {
        const val LEDGER_NOT_SYNCED_MESSAGE = "This ledger has not been synced locally yet. Sync Ledgers to view its statement."
        private const val EARLIEST_POSSIBLE_DATE = "0001-01-01"
    }
}

/** `₹#,##0.00` — used only for the new Detailed-mode item commercial-detail block; the existing
 * voucher-level Debit/Credit/Balance formatting is untouched and deliberately not changed here. */
private fun formatInr(amount: BigDecimal): String {
    val format = DecimalFormat("₹#,##0.00")
    return format.format(amount.setScale(2, RoundingMode.HALF_UP))
}
