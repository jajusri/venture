package com.jajusri.venture.feature.masterdata.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LedgerStatementDao {
    @Query(
        "SELECT * FROM cached_ledger_statements " +
            "WHERE companyId = :companyId AND ledgerId = :ledgerId AND periodFrom = :from AND periodTo = :to",
    )
    suspend fun statement(companyId: String, ledgerId: String, from: String, to: String): LedgerStatementEntity?

    @Query(
        "SELECT * FROM cached_ledger_statement_transactions " +
            "WHERE companyId = :companyId AND ledgerId = :ledgerId AND periodFrom = :from AND periodTo = :to " +
            "ORDER BY lineIndex",
    )
    suspend fun transactions(
        companyId: String,
        ledgerId: String,
        from: String,
        to: String,
    ): List<LedgerStatementTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatement(row: LedgerStatementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(rows: List<LedgerStatementTransactionEntity>)

    @Query(
        "DELETE FROM cached_ledger_statement_transactions " +
            "WHERE companyId = :companyId AND ledgerId = :ledgerId AND periodFrom = :from AND periodTo = :to",
    )
    suspend fun deleteTransactions(companyId: String, ledgerId: String, from: String, to: String)

    /**
     * [transactions] is treated as the complete, authoritative truth for this exact
     * (ledger, period) scope — old rows are cleared before inserting, matching the Voucher
     * clear-then-insert pattern, so a statement whose transaction count shrank between syncs
     * (e.g. a voucher cancelled in Tally since the last refresh) never keeps a phantom trailing
     * row. One Room [Transaction]: observers only ever see the prior complete statement or the
     * next complete one, never a partial mix.
     */
    @Transaction
    suspend fun storeStatement(statement: LedgerStatementEntity, transactions: List<LedgerStatementTransactionEntity>) {
        deleteTransactions(statement.companyId, statement.ledgerId, statement.periodFrom, statement.periodTo)
        upsertStatement(statement)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
    }
}
