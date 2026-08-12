package com.budcom.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.company.data.local.CompanyDiscoveryMetaEntity
import com.budcom.android.feature.company.data.local.CompanyEntity
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerEntity
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementEntity
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementTransactionEntity
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemEntity
import com.budcom.android.feature.voucher.data.local.*
import com.budcom.android.core.connection.data.local.PairedConnectorDao
import com.budcom.android.core.connection.data.local.PairedConnectorEntity

@Database(
    entities = [
        CompanyEntity::class,
        CompanyDiscoveryMetaEntity::class,
        LedgerEntity::class,
        StockItemEntity::class,
        VoucherEntity::class,
        VoucherDetailEntity::class,
        VoucherLedgerLineEntity::class,
        VoucherInventoryLineEntity::class,
        VoucherCacheMetaEntity::class,
        PairedConnectorEntity::class,
        LedgerStatementEntity::class,
        LedgerStatementTransactionEntity::class,
    ],
    version = DatabaseConstants.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun voucherDao(): VoucherDao
    abstract fun pairedConnectorDao(): PairedConnectorDao
    abstract fun ledgerStatementDao(): LedgerStatementDao
    abstract fun ledgerMovementDao(): LedgerMovementDao
}
