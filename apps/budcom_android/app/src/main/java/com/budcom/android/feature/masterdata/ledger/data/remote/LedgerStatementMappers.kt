package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementCoverage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction

internal fun LedgerStatementEnvelopeDto.toDomain(): LedgerStatement = statement.toDomain()

internal fun LedgerStatementDto.toDomain(): LedgerStatement = LedgerStatement(
    ledgerId = ledgerId,
    ledgerName = ledgerName,
    parentGroup = parentGroup?.takeIf { it.isNotBlank() },
    period = LedgerStatementDateRange(period.from, period.to),
    openingBalance = openingBalance?.toDomain(),
    closingBalance = closingBalance?.toDomain(),
    transactions = transactions.map { it.toDomain() },
    coverage = coverage.toDomain(),
)

internal fun LedgerStatementAmountDto.toDomain(): LedgerStatementAmount =
    LedgerStatementAmount(amount = amount, side = side.toStatementAmountSide())

internal fun LedgerStatementTransactionDto.toDomain(): LedgerStatementTransaction = LedgerStatementTransaction(
    voucherId = voucherId,
    date = date,
    voucherType = voucherType,
    voucherNumber = voucherNumber?.takeIf { it.isNotBlank() },
    referenceNumber = referenceNumber?.takeIf { it.isNotBlank() },
    narration = narration?.takeIf { it.isNotBlank() },
    debit = debit,
    credit = credit,
    runningBalance = runningBalance?.toDomain(),
)

internal fun LedgerStatementCoverageDto.toDomain(): LedgerStatementCoverage = LedgerStatementCoverage(
    transactionsComplete = transactionsComplete,
    balanceAvailable = balanceAvailable,
    syncedFrom = syncedFrom,
    syncedTo = syncedTo,
    message = message?.takeIf { it.isNotBlank() },
)

internal fun String.toStatementAmountSide(): AmountSide = when (lowercase()) {
    "credit", "cr" -> AmountSide.Cr
    else -> AmountSide.Dr
}
