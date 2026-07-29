package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
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
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideCompanyDao(db: AppDatabase): CompanyDao = db.companyDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()

    @Provides
    fun provideStockItemDao(db: AppDatabase): StockItemDao = db.stockItemDao()
}
