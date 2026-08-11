package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
import com.budcom.android.feature.voucher.data.local.VoucherDao
import com.budcom.android.core.connection.data.local.PairedConnectorDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt providers for Room [AppDatabase] and feature DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DatabaseConstants.NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    @Provides
    fun provideCompanyDao(db: AppDatabase): CompanyDao = db.companyDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()

    @Provides
    fun provideStockItemDao(db: AppDatabase): StockItemDao = db.stockItemDao()

    @Provides
    fun providePairedConnectorDao(db: AppDatabase): PairedConnectorDao = db.pairedConnectorDao()

    @Provides
    fun provideVoucherDao(db: AppDatabase): VoucherDao = db.voucherDao()

    @Provides
    fun provideLedgerStatementDao(db: AppDatabase): LedgerStatementDao = db.ledgerStatementDao()

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `paired_connectors` (" +
                    "`connectorId` TEXT NOT NULL, `friendlyName` TEXT NOT NULL, " +
                    "`lastKnownHost` TEXT NOT NULL, `lastKnownPort` INTEGER NOT NULL, " +
                    "`lastConnectedAtEpochMillis` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`connectorId`))",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_vouchers` (" +
                    "`companyId` TEXT NOT NULL, `voucherId` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `number` TEXT, `partyName` TEXT, `referenceNumber` TEXT, " +
                    "`amountValue` TEXT, `amountSide` TEXT, `status` TEXT NOT NULL, `dataQuality` TEXT NOT NULL, " +
                    "`lastSyncedAt` INTEGER NOT NULL, PRIMARY KEY(`companyId`, `voucherId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_voucher_details` (" +
                    "`companyId` TEXT NOT NULL, `voucherId` TEXT NOT NULL, `effectiveDate` TEXT, " +
                    "`narration` TEXT, `lastSyncedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `voucherId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_voucher_ledger_lines` (" +
                    "`companyId` TEXT NOT NULL, `voucherId` TEXT NOT NULL, `lineNumber` INTEGER NOT NULL, " +
                    "`ledgerName` TEXT NOT NULL, `amountValue` TEXT NOT NULL, `amountSide` TEXT, " +
                    "`isDeemedPositive` INTEGER, PRIMARY KEY(`companyId`, `voucherId`, `lineNumber`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_voucher_inventory_lines` (" +
                    "`companyId` TEXT NOT NULL, `voucherId` TEXT NOT NULL, `lineNumber` INTEGER NOT NULL, " +
                    "`itemName` TEXT NOT NULL, `quantity` TEXT, `rate` TEXT, `amountValue` TEXT, " +
                    "`amountSide` TEXT, PRIMARY KEY(`companyId`, `voucherId`, `lineNumber`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `voucher_cache_meta` (" +
                    "`companyId` TEXT NOT NULL, `lastSyncedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`))",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_ledger_statements` (" +
                    "`companyId` TEXT NOT NULL, `ledgerId` TEXT NOT NULL, `periodFrom` TEXT NOT NULL, " +
                    "`periodTo` TEXT NOT NULL, `ledgerName` TEXT NOT NULL, `parentGroup` TEXT, " +
                    "`openingAmount` TEXT, `openingSide` TEXT, `closingAmount` TEXT, `closingSide` TEXT, " +
                    "`transactionsComplete` INTEGER NOT NULL, `balanceAvailable` INTEGER NOT NULL, " +
                    "`syncedFrom` TEXT, `syncedTo` TEXT, `coverageMessage` TEXT, `lastSyncedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `ledgerId`, `periodFrom`, `periodTo`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_ledger_statement_transactions` (" +
                    "`companyId` TEXT NOT NULL, `ledgerId` TEXT NOT NULL, `periodFrom` TEXT NOT NULL, " +
                    "`periodTo` TEXT NOT NULL, `lineIndex` INTEGER NOT NULL, `voucherId` TEXT NOT NULL, " +
                    "`date` TEXT NOT NULL, `voucherType` TEXT NOT NULL, `voucherNumber` TEXT, " +
                    "`referenceNumber` TEXT, `narration` TEXT, `debit` TEXT, `credit` TEXT, " +
                    "`runningAmount` TEXT, `runningSide` TEXT, " +
                    "PRIMARY KEY(`companyId`, `ledgerId`, `periodFrom`, `periodTo`, `lineIndex`))",
            )
        }
    }
}
