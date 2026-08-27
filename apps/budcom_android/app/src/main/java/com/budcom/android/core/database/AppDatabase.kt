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
import com.budcom.android.feature.catalogue.data.local.BranchDao
import com.budcom.android.feature.catalogue.data.local.BranchEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetDao
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueCustomFieldDao
import com.budcom.android.feature.catalogue.data.local.CatalogueCustomFieldEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideDao
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkEntity
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsDao
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsEntity
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
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantDao
import com.budcom.android.feature.transaction.data.local.CanonicalOrderDao
import com.budcom.android.feature.transaction.data.local.CanonicalOrderEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderLineEntity
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantEntity
import com.budcom.android.feature.transaction.data.local.CommercialTransactionDao
import com.budcom.android.feature.transaction.data.local.CommercialTransactionEntity
import com.budcom.android.feature.transaction.data.local.EstimatePoDao
import com.budcom.android.feature.transaction.data.local.EstimatePoEntity
import com.budcom.android.feature.transaction.data.local.EstimatePoLineItemDao
import com.budcom.android.feature.transaction.data.local.EstimatePoLineItemEntity
import com.budcom.android.feature.transaction.data.local.LedgerIntentDao
import com.budcom.android.feature.transaction.data.local.LedgerIntentEntity
import com.budcom.android.feature.transaction.data.local.PaymentEventDao
import com.budcom.android.feature.transaction.data.local.PaymentEventEntity
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryDao
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryEntity
import com.budcom.android.feature.transaction.data.local.TermsAcknowledgmentDao
import com.budcom.android.feature.transaction.data.local.TermsAcknowledgmentEntity

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
        CatalogueProductEntity::class,
        CatalogueProductSourceLinkEntity::class,
        BranchEntity::class,
        CatalogueOverrideEntity::class,
        CataloguePublishedSnapshotEntity::class,
        CatalogueAssetEntity::class,
        CatalogueSettingsEntity::class,
        CatalogueCustomFieldEntity::class,
        EstimatePoEntity::class,
        EstimatePoLineItemEntity::class,
        SellerInboxEntryEntity::class,
        CommercialTransactionEntity::class,
        TermsAcknowledgmentEntity::class,
        PaymentEventEntity::class,
        LedgerIntentEntity::class,
        CatalogueAccessGrantEntity::class,
        CanonicalOrderEntity::class,
        CanonicalOrderLineEntity::class,
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
    abstract fun catalogueProductDao(): CatalogueProductDao
    abstract fun catalogueProductSourceLinkDao(): CatalogueProductSourceLinkDao
    abstract fun branchDao(): BranchDao
    abstract fun catalogueOverrideDao(): CatalogueOverrideDao
    abstract fun cataloguePublishedSnapshotDao(): CataloguePublishedSnapshotDao
    abstract fun catalogueAssetDao(): CatalogueAssetDao
    abstract fun catalogueSettingsDao(): CatalogueSettingsDao
    abstract fun catalogueCustomFieldDao(): CatalogueCustomFieldDao
    abstract fun estimatePoDao(): EstimatePoDao
    abstract fun estimatePoLineItemDao(): EstimatePoLineItemDao
    abstract fun sellerInboxEntryDao(): SellerInboxEntryDao
    abstract fun commercialTransactionDao(): CommercialTransactionDao
    abstract fun termsAcknowledgmentDao(): TermsAcknowledgmentDao
    abstract fun paymentEventDao(): PaymentEventDao
    abstract fun ledgerIntentDao(): LedgerIntentDao
    abstract fun catalogueAccessGrantDao(): CatalogueAccessGrantDao
    abstract fun canonicalOrderDao(): CanonicalOrderDao
}
