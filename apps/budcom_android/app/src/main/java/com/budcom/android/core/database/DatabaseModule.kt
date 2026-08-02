package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
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
    ).addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    fun provideCompanyDao(db: AppDatabase): CompanyDao = db.companyDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()

    @Provides
    fun provideStockItemDao(db: AppDatabase): StockItemDao = db.stockItemDao()

    @Provides
    fun providePairedConnectorDao(db: AppDatabase): PairedConnectorDao = db.pairedConnectorDao()

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
}
