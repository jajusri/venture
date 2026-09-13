package com.jajusri.venture.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jajusri.venture.feature.company.data.local.CompanyDao
import com.jajusri.venture.feature.company.data.local.CompanyDiscoveryMetaEntity
import com.jajusri.venture.feature.company.data.local.CompanyEntity
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerDao
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerEntity
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerStatementDao
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerStatementEntity
import com.jajusri.venture.feature.masterdata.ledger.data.local.LedgerStatementTransactionEntity
import com.jajusri.venture.feature.masterdata.stockitem.data.local.StockItemDao
import com.jajusri.venture.feature.masterdata.stockitem.data.local.StockItemEntity
import com.jajusri.venture.feature.voucher.data.local.*
import com.jajusri.venture.core.connection.data.local.PairedConnectorDao
import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.feature.businessprofile.data.local.BusinessProfileDao
import com.jajusri.venture.feature.businessprofile.data.local.BusinessProfileEntity
import com.jajusri.venture.feature.catalogue.data.local.BranchDao
import com.jajusri.venture.feature.catalogue.data.local.BranchEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueAssetDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueAssetEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueCustomFieldDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueCustomFieldEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueOverrideDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueOverrideEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductSourceLinkEntity
import com.jajusri.venture.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.jajusri.venture.feature.catalogue.data.local.CataloguePublishedSnapshotEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueSettingsDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueSettingsEntity
import com.jajusri.venture.feature.party.data.local.PartyContactPersonDao
import com.jajusri.venture.feature.party.data.local.PartyContactPersonEntity
import com.jajusri.venture.feature.party.data.local.PartyDao
import com.jajusri.venture.feature.party.data.local.PartyEntity
import com.jajusri.venture.feature.party.data.local.PartyExportEventDao
import com.jajusri.venture.feature.party.data.local.PartyExportEventEntity
import com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceDao
import com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceEntity
import com.jajusri.venture.feature.party.data.local.PartyIssueDao
import com.jajusri.venture.feature.party.data.local.PartyIssueEntity
import com.jajusri.venture.feature.party.data.local.PartyNoteDao
import com.jajusri.venture.feature.party.data.local.PartyNoteEntity
import com.jajusri.venture.feature.party.data.local.PartySourceLinkDao
import com.jajusri.venture.feature.party.data.local.PartySourceLinkEntity
import com.jajusri.venture.feature.party.data.local.PartyTagCrossRefEntity
import com.jajusri.venture.feature.party.data.local.PartyTimelineDao
import com.jajusri.venture.feature.party.data.local.TagDao
import com.jajusri.venture.feature.party.data.local.TagEntity
import com.jajusri.venture.feature.transaction.data.local.CatalogueAccessGrantDao
import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderDao
import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderEntity
import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderLineEntity
import com.jajusri.venture.feature.transaction.data.local.OrderDeliveryEnvelopeEntity
import com.jajusri.venture.feature.transaction.data.local.OrderOutboxDao
import com.jajusri.venture.feature.transaction.data.local.OrderCommercialEventEntity
import com.jajusri.venture.feature.transaction.data.local.OrderCommercialEventDao
import com.jajusri.venture.feature.transaction.data.local.OrderVersionArchiveEntity
import com.jajusri.venture.feature.transaction.data.local.OrderVersionLineArchiveEntity
import com.jajusri.venture.feature.transaction.data.local.OrderVersionArchiveDao
import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorDao
import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorEntity
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxDao
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxEntity
import com.jajusri.venture.feature.transaction.data.local.CatalogueAccessGrantEntity
import com.jajusri.venture.feature.transaction.data.local.CommercialTransactionDao
import com.jajusri.venture.feature.transaction.data.local.CommercialTransactionEntity
import com.jajusri.venture.feature.transaction.data.local.EstimatePoDao
import com.jajusri.venture.feature.transaction.data.local.EstimatePoEntity
import com.jajusri.venture.feature.transaction.data.local.EstimatePoLineItemDao
import com.jajusri.venture.feature.transaction.data.local.EstimatePoLineItemEntity
import com.jajusri.venture.feature.transaction.data.local.LedgerIntentDao
import com.jajusri.venture.feature.transaction.data.local.LedgerIntentEntity
import com.jajusri.venture.feature.transaction.data.local.PaymentEventDao
import com.jajusri.venture.feature.transaction.data.local.PaymentEventEntity
import com.jajusri.venture.feature.transaction.data.local.SellerInboxEntryDao
import com.jajusri.venture.feature.transaction.data.local.SellerInboxEntryEntity
import com.jajusri.venture.feature.transaction.data.local.TermsAcknowledgmentDao
import com.jajusri.venture.feature.transaction.data.local.TermsAcknowledgmentEntity
import com.jajusri.venture.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao
import com.jajusri.venture.feature.transaction.data.local.AuthenticatedCounterpartyBindingEntity

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
        OrderDeliveryEnvelopeEntity::class,
        StructuredRecipientInboxEntity::class,
        RecipientInboxCursorEntity::class,
        OrderCommercialEventEntity::class,
        OrderVersionArchiveEntity::class,
        OrderVersionLineArchiveEntity::class,
        AuthenticatedCounterpartyBindingEntity::class,
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
    abstract fun orderOutboxDao(): OrderOutboxDao
    abstract fun structuredRecipientInboxDao(): StructuredRecipientInboxDao
    abstract fun recipientInboxCursorDao(): RecipientInboxCursorDao
    abstract fun orderCommercialEventDao(): OrderCommercialEventDao
    abstract fun orderVersionArchiveDao(): OrderVersionArchiveDao
    abstract fun authenticatedCounterpartyBindingDao(): AuthenticatedCounterpartyBindingDao
}
