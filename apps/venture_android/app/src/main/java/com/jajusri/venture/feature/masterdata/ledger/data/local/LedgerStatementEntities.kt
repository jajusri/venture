package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Entity

@Entity(
    tableName = "cached_ledger_statements",
    primaryKeys = ["companyId", "ledgerId", "periodFrom", "periodTo"],
)
data class LedgerStatementEntity(
    val companyId: String,
    val ledgerId: String,
    val periodFrom: String,
    val periodTo: String,
    val ledgerName: String,
    val parentGroup: String?,
    val openingAmount: String?,
    val openingSide: String?,
    val closingAmount: String?,
    val closingSide: String?,
    val transactionsComplete: Boolean,
    val balanceAvailable: Boolean,
    val syncedFrom: String?,
    val syncedTo: String?,
    val coverageMessage: String?,
    val lastSyncedAt: Long,
)

/** [lineIndex] preserves the Connector's own authoritative accounting order (0-based). */
@Entity(
    tableName = "cached_ledger_statement_transactions",
    primaryKeys = ["companyId", "ledgerId", "periodFrom", "periodTo", "lineIndex"],
)
data class LedgerStatementTransactionEntity(
    val companyId: String,
    val ledgerId: String,
    val periodFrom: String,
    val periodTo: String,
    val lineIndex: Int,
    val voucherId: String,
    val date: String,
    val voucherType: String,
    val voucherNumber: String?,
    val referenceNumber: String?,
    val narration: String?,
    val debit: String?,
    val credit: String?,
    val runningAmount: String?,
    val runningSide: String?,
)
