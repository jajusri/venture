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
import com.budcom.android.feature.businessprofile.data.local.BusinessProfileDao
import com.budcom.android.feature.businessprofile.data.local.BusinessProfileEntity
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyContactPersonEntity
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyEntity
import com.budcom.android.feature.party.data.local.PartyExportEventDao
import com.budcom.android.feature.party.data.local.PartyExportEventEntity
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceEntity
import com.budcom.android.feature.party.data.local.PartyIssueDao
import com.budcom.android.feature.party.data.local.PartyIssueEntity
import com.budcom.android.feature.party.data.local.PartyNoteDao
import com.budcom.android.feature.party.data.local.PartyNoteEntity
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartySourceLinkEntity
import com.budcom.android.feature.party.data.local.PartyTagCrossRefEntity
import com.budcom.android.feature.party.data.local.PartyTimelineDao
import com.budcom.android.feature.party.data.local.TagDao
import com.budcom.android.feature.party.data.local.TagEntity

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
        PartyEntity::class,
        PartySourceLinkEntity::class,
        PartyFieldProvenanceEntity::class,
        PartyContactPersonEntity::class,
        TagEntity::class,
        PartyTagCrossRefEntity::class,
        PartyNoteEntity::class,
        PartyExportEventEntity::class,
        PartyIssueEntity::class,
        BusinessProfileEntity::class,
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
    abstract fun partyDao(): PartyDao
    abstract fun partySourceLinkDao(): PartySourceLinkDao
    abstract fun partyFieldProvenanceDao(): PartyFieldProvenanceDao
    abstract fun partyContactPersonDao(): PartyContactPersonDao
    abstract fun tagDao(): TagDao
    abstract fun partyNoteDao(): PartyNoteDao
    abstract fun partyExportEventDao(): PartyExportEventDao
    abstract fun partyIssueDao(): PartyIssueDao
    abstract fun partyTimelineDao(): PartyTimelineDao
    abstract fun businessProfileDao(): BusinessProfileDao
}
