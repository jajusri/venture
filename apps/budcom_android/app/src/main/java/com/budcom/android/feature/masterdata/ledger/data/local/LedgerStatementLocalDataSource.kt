package com.budcom.android.feature.masterdata.ledger.data.local

import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementCoverage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import javax.inject.Inject
import javax.inject.Singleton

interface LedgerStatementLocalDataSource {
    suspend fun statement(companyId: String, ledgerId: String, range: LedgerStatementDateRange): LedgerStatement?
    suspend fun store(companyId: String, statement: LedgerStatement, syncedAt: Long)
}

@Singleton
class RoomLedgerStatementLocalDataSource @Inject constructor(
    private val dao: LedgerStatementDao,
) : LedgerStatementLocalDataSource {

    override suspend fun statement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): LedgerStatement? {
        val header = dao.statement(companyId, ledgerId, range.from, range.to) ?: return null
        val transactions = dao.transactions(companyId, ledgerId, range.from, range.to)
        return header.toDomain(transactions)
    }

    override suspend fun store(companyId: String, statement: LedgerStatement, syncedAt: Long) {
        dao.storeStatement(
            statement.toEntity(companyId, syncedAt),
            statement.transactions.mapIndexed { index, transaction ->
                transaction.toEntity(companyId, statement.ledgerId, statement.period, index)
            },
        )
    }
}

private fun LedgerStatement.toEntity(companyId: String, syncedAt: Long): LedgerStatementEntity = LedgerStatementEntity(
    companyId = companyId,
    ledgerId = ledgerId,
    periodFrom = period.from,
    periodTo = period.to,
    ledgerName = ledgerName,
    parentGroup = parentGroup,
    openingAmount = openingBalance?.amount,
    openingSide = openingBalance?.side?.name,
    closingAmount = closingBalance?.amount,
    closingSide = closingBalance?.side?.name,
    transactionsComplete = coverage.transactionsComplete,
    balanceAvailable = coverage.balanceAvailable,
    syncedFrom = coverage.syncedFrom,
    syncedTo = coverage.syncedTo,
    coverageMessage = coverage.message,
    lastSyncedAt = syncedAt,
)

private fun LedgerStatementTransaction.toEntity(
    companyId: String,
    ledgerId: String,
    period: LedgerStatementDateRange,
    lineIndex: Int,
): LedgerStatementTransactionEntity = LedgerStatementTransactionEntity(
    companyId = companyId,
    ledgerId = ledgerId,
    periodFrom = period.from,
    periodTo = period.to,
    lineIndex = lineIndex,
    voucherId = voucherId,
    date = date,
    voucherType = voucherType,
    voucherNumber = voucherNumber,
    referenceNumber = referenceNumber,
    narration = narration,
    debit = debit,
    credit = credit,
    runningAmount = runningBalance?.amount,
    runningSide = runningBalance?.side?.name,
)

private fun LedgerStatementEntity.toDomain(
    transactions: List<LedgerStatementTransactionEntity>,
): LedgerStatement = LedgerStatement(
    ledgerId = ledgerId,
    ledgerName = ledgerName,
    parentGroup = parentGroup,
    period = LedgerStatementDateRange(periodFrom, periodTo),
    openingBalance = amountOf(openingAmount, openingSide),
    closingBalance = amountOf(closingAmount, closingSide),
    transactions = transactions.map { it.toDomain() },
    coverage = LedgerStatementCoverage(
        transactionsComplete = transactionsComplete,
        balanceAvailable = balanceAvailable,
        syncedFrom = syncedFrom,
        syncedTo = syncedTo,
        message = coverageMessage,
    ),
)

private fun LedgerStatementTransactionEntity.toDomain(): LedgerStatementTransaction = LedgerStatementTransaction(
    voucherId = voucherId,
    date = date,
    voucherType = voucherType,
    voucherNumber = voucherNumber,
    referenceNumber = referenceNumber,
    narration = narration,
    debit = debit,
    credit = credit,
    runningBalance = amountOf(runningAmount, runningSide),
)

private fun amountOf(amount: String?, side: String?): LedgerStatementAmount? {
    val value = amount ?: return null
    val resolvedSide = side?.let { runCatching { AmountSide.valueOf(it) }.getOrNull() } ?: return null
    return LedgerStatementAmount(value, resolvedSide)
}
