package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import com.budcom.android.BuildConfig
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
import com.budcom.android.feature.voucher.data.local.VoucherDao
import com.budcom.android.core.connection.data.local.PairedConnectorDao
import com.budcom.android.feature.businessprofile.data.local.BusinessProfileDao
import com.budcom.android.feature.catalogue.data.local.BranchDao
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetDao
import com.budcom.android.feature.catalogue.data.local.CatalogueCustomFieldDao
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsDao
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyExportEventDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyIssueDao
import com.budcom.android.feature.party.data.local.PartyNoteDao
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartyTimelineDao
import com.budcom.android.feature.party.data.local.TagDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Hilt providers for Room [AppDatabase] and feature DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * TD-041 root-cause instrumentation (debug builds only, gated by [BuildConfig.DEBUG] so it
     * never ships to release): every SQL statement Room executes that touches `cached_parties`, or
     * that marks a transaction boundary, is logged with the executing thread name -- the exact
     * detail needed to see whether a reconciliation write and a Connect read were ever interleaved
     * on separate connections/transactions at the moment a read came back transiently empty. Runs
     * on its own single-thread executor so the callback itself never adds contention to Room's own
     * query/transaction executors.
     */
    private val td041SqlLogExecutor = Executors.newSingleThreadExecutor()

    private val td041RelevantSqlMarkers = listOf("cached_parties", "TRANSACTION", "PRAGMA")

    private fun logTd041Query(sqlQuery: String, bindArgs: List<Any?>) {
        if (td041RelevantSqlMarkers.none { sqlQuery.contains(it, ignoreCase = true) }) return
        timber.log.Timber.tag("TD041_SQL").d(
            "thread=${Thread.currentThread().name} sql=$sqlQuery args=$bindArgs",
        )
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DatabaseConstants.NAME,
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
            MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
        )
        if (BuildConfig.DEBUG) {
            builder.setQueryCallback(::logTd041Query, td041SqlLogExecutor)
        }
        return builder.build()
    }

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

    @Provides
    fun provideLedgerMovementDao(db: AppDatabase): LedgerMovementDao = db.ledgerMovementDao()

    @Provides
    fun providePartyDao(db: AppDatabase): PartyDao = db.partyDao()

    @Provides
    fun providePartySourceLinkDao(db: AppDatabase): PartySourceLinkDao = db.partySourceLinkDao()

    @Provides
    fun providePartyFieldProvenanceDao(db: AppDatabase): PartyFieldProvenanceDao = db.partyFieldProvenanceDao()

    @Provides
    fun providePartyContactPersonDao(db: AppDatabase): PartyContactPersonDao = db.partyContactPersonDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun providePartyNoteDao(db: AppDatabase): PartyNoteDao = db.partyNoteDao()

    @Provides
    fun providePartyExportEventDao(db: AppDatabase): PartyExportEventDao = db.partyExportEventDao()

    @Provides
    fun providePartyIssueDao(db: AppDatabase): PartyIssueDao = db.partyIssueDao()

    @Provides
    fun providePartyTimelineDao(db: AppDatabase): PartyTimelineDao = db.partyTimelineDao()

    @Provides
    fun provideBusinessProfileDao(db: AppDatabase): BusinessProfileDao = db.businessProfileDao()

    @Provides
    fun provideCatalogueProductDao(db: AppDatabase): CatalogueProductDao = db.catalogueProductDao()

    @Provides
    fun provideCatalogueProductSourceLinkDao(db: AppDatabase): CatalogueProductSourceLinkDao = db.catalogueProductSourceLinkDao()

    @Provides
    fun provideBranchDao(db: AppDatabase): BranchDao = db.branchDao()

    @Provides
    fun provideCatalogueOverrideDao(db: AppDatabase): CatalogueOverrideDao = db.catalogueOverrideDao()

    @Provides
    fun provideCataloguePublishedSnapshotDao(db: AppDatabase): CataloguePublishedSnapshotDao = db.cataloguePublishedSnapshotDao()

    @Provides
    fun provideCatalogueAssetDao(db: AppDatabase): CatalogueAssetDao = db.catalogueAssetDao()

    @Provides
    fun provideCatalogueSettingsDao(db: AppDatabase): CatalogueSettingsDao = db.catalogueSettingsDao()

    @Provides
    fun provideCatalogueCustomFieldDao(db: AppDatabase): CatalogueCustomFieldDao = db.catalogueCustomFieldDao()

    @Provides
    fun provideEstimatePoDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.EstimatePoDao = db.estimatePoDao()

    @Provides
    fun provideEstimatePoLineItemDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.EstimatePoLineItemDao = db.estimatePoLineItemDao()

    @Provides
    fun provideSellerInboxEntryDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.SellerInboxEntryDao = db.sellerInboxEntryDao()

    @Provides
    fun provideCommercialTransactionDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.CommercialTransactionDao = db.commercialTransactionDao()

    @Provides
    fun provideTermsAcknowledgmentDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.TermsAcknowledgmentDao = db.termsAcknowledgmentDao()

    @Provides
    fun providePaymentEventDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.PaymentEventDao = db.paymentEventDao()

    @Provides
    fun provideLedgerIntentDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.LedgerIntentDao = db.ledgerIntentDao()

    @Provides
    fun provideCatalogueAccessGrantDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantDao = db.catalogueAccessGrantDao()

    @Provides
    fun provideCanonicalOrderDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.CanonicalOrderDao = db.canonicalOrderDao()

    @Provides
    fun provideOrderOutboxDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.OrderOutboxDao = db.orderOutboxDao()

    @Provides
    fun provideStructuredRecipientInboxDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxDao =
        db.structuredRecipientInboxDao()

    @Provides
    fun provideRecipientInboxCursorDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.RecipientInboxCursorDao =
        db.recipientInboxCursorDao()

    @Provides
    fun provideOrderCommercialEventDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.OrderCommercialEventDao =
        db.orderCommercialEventDao()

    @Provides
    fun provideOrderVersionArchiveDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.OrderVersionArchiveDao =
        db.orderVersionArchiveDao()

    @Provides
    fun provideAuthenticatedCounterpartyBindingDao(db: AppDatabase): com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao =
        db.authenticatedCounterpartyBindingDao()

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

    /**
     * Additive-only: three indices supporting the local-first Ledger statement's Last-7-Sales/
     * date-period/coverage queries directly over the existing (already fully-populated, see
     * [com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao]'s doc comment)
     * `cached_vouchers`/`cached_voucher_ledger_lines` tables. No table created, no column added,
     * no row touched — every pre-existing row and every pre-existing query continues to work
     * unchanged; this migration only makes the new Ledger queries fast instead of full-scanning.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_vouchers_companyId_date` " +
                    "ON `cached_vouchers` (`companyId`, `date`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_vouchers_companyId_status` " +
                    "ON `cached_vouchers` (`companyId`, `status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_voucher_ledger_lines_companyId_ledgerName` " +
                    "ON `cached_voucher_ledger_lines` (`companyId`, `ledgerName`)",
            )
        }
    }

    /**
     * Additive-only: adds the six MVP-1.1-A Universal Party Identity tables (Party, its Tally
     * source link, field provenance, contact persons, tags, tag assignments). No existing table
     * is touched, dropped, or destructively recreated — every pre-existing Company/Ledger/
     * StockItem/Voucher/PairedConnector/LedgerStatement row survives unchanged. Party rows
     * themselves are seeded separately by [com.budcom.android.feature.party.domain.usecase.ReconcilePartiesFromLedgersUseCase]
     * (application logic, not migration SQL) — this migration only creates empty structure.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_parties` (" +
                    "`companyId` TEXT NOT NULL, `partyId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`classification` TEXT NOT NULL, `primaryPhone` TEXT, `primaryPhoneNormalized` TEXT, " +
                    "`primaryEmail` TEXT, `addressLine1` TEXT, `addressCity` TEXT, `addressState` TEXT, " +
                    "`addressPincode` TEXT, `gstin` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `partyId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_parties_companyId` ON `cached_parties` (`companyId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_parties_companyId_displayName` " +
                    "ON `cached_parties` (`companyId`, `displayName`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_parties_companyId_classification` " +
                    "ON `cached_parties` (`companyId`, `classification`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_parties_companyId_primaryPhoneNormalized` " +
                    "ON `cached_parties` (`companyId`, `primaryPhoneNormalized`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_source_links` (" +
                    "`companyId` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `externalEntityId` TEXT NOT NULL, " +
                    "`partyId` TEXT NOT NULL, `sourceInstanceId` TEXT NOT NULL, `externalDisplayName` TEXT NOT NULL, " +
                    "`identitySource` TEXT NOT NULL, `lastConfirmedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `sourceType`, `externalEntityId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_source_links_companyId_partyId` " +
                    "ON `party_source_links` (`companyId`, `partyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_field_provenance` (" +
                    "`companyId` TEXT NOT NULL, `partyId` TEXT NOT NULL, `fieldName` TEXT NOT NULL, " +
                    "`state` TEXT NOT NULL, `tallyValue` TEXT, `budcomValue` TEXT, `lastConfirmedAt` INTEGER, " +
                    "`lastExportedAt` INTEGER, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `partyId`, `fieldName`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_field_provenance_companyId_partyId` " +
                    "ON `party_field_provenance` (`companyId`, `partyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_contact_persons` (" +
                    "`companyId` TEXT NOT NULL, `contactPersonId` TEXT NOT NULL, `partyId` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `designation` TEXT, `mobile` TEXT, `mobileNormalized` TEXT, " +
                    "`whatsappNumber` TEXT, `email` TEXT, `isPrimary` INTEGER NOT NULL, `provenance` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `contactPersonId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_contact_persons_companyId_partyId` " +
                    "ON `party_contact_persons` (`companyId`, `partyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_tags` (" +
                    "`tagId` TEXT NOT NULL, `parentTagId` TEXT, `name` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`tagId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_party_tags_parentTagId` ON `party_tags` (`parentTagId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_party_tags_name` ON `party_tags` (`name`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_tag_assignments` (" +
                    "`companyId` TEXT NOT NULL, `partyId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
                    "`assignedAt` INTEGER NOT NULL, PRIMARY KEY(`companyId`, `partyId`, `tagId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_tag_assignments_companyId_tagId` " +
                    "ON `party_tag_assignments` (`companyId`, `tagId`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_party_tag_assignments_tagId` ON `party_tag_assignments` (`tagId`)")
        }
    }

    /**
     * Additive-only: adds the single MVP-1.1-C `party_notes` table (BUDCOM-only Party notes,
     * optionally referencing an existing Voucher by its stable id — never duplicating Voucher
     * data). No existing table is touched.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_notes` (" +
                    "`companyId` TEXT NOT NULL, `noteId` TEXT NOT NULL, `partyId` TEXT NOT NULL, " +
                    "`body` TEXT NOT NULL, `linkedVoucherId` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`companyId`, `noteId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_notes_companyId_partyId_createdAt` " +
                    "ON `party_notes` (`companyId`, `partyId`, `createdAt`)",
            )
        }
    }

    /**
     * Additive-only: adds the single MVP-1.1-D `party_export_events` table — a lightweight audit
     * trail of Tally-enrichment XML exports (export id, timestamp, output filename, exported field
     * *names* only, never raw values). No existing table is touched.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_export_events` (" +
                    "`companyId` TEXT NOT NULL, `exportId` TEXT NOT NULL, `partyId` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `outputFileName` TEXT NOT NULL, `fieldNamesCsv` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `exportId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_export_events_companyId_partyId_createdAt` " +
                    "ON `party_export_events` (`companyId`, `partyId`, `createdAt`)",
            )
        }
    }

    /**
     * Additive-only (MVP-1.2-A): extends `party_notes` with typed/due-date/completion/issue-
     * grouping columns and adds the new `party_issues` table. No existing table is dropped or
     * destructively recreated. `type` is added `NOT NULL DEFAULT 'general'` — every pre-existing
     * `party_notes` row backfills to `'general'` via this default, so no note written before this
     * migration changes behavior. `dueAt`/`completedAt`/`issueId` are nullable and need no default;
     * SQLite implicitly defaults a nullable `ALTER TABLE ... ADD COLUMN` to NULL for existing rows.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `party_notes` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'general'")
            db.execSQL("ALTER TABLE `party_notes` ADD COLUMN `dueAt` INTEGER")
            db.execSQL("ALTER TABLE `party_notes` ADD COLUMN `completedAt` INTEGER")
            db.execSQL("ALTER TABLE `party_notes` ADD COLUMN `issueId` TEXT")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `party_issues` (" +
                    "`companyId` TEXT NOT NULL, `issueId` TEXT NOT NULL, `partyId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`resolvedAt` INTEGER, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `issueId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_party_issues_companyId_partyId_status_createdAt` " +
                    "ON `party_issues` (`companyId`, `partyId`, `status`, `createdAt`)",
            )
        }
    }

    /**
     * Additive-only (MVP-1.3-A): adds the single `business_profile` table — one BUDCOM-owned row
     * per Tally company (PDL-019), never a Party-adjacent or provenance-tracked table. No existing
     * table is touched, dropped, or destructively recreated.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `business_profile` (" +
                    "`companyId` TEXT NOT NULL, `tradingName` TEXT NOT NULL, `legalName` TEXT, " +
                    "`addressLine1` TEXT, `addressCity` TEXT, `addressState` TEXT, `addressPincode` TEXT, " +
                    "`phone` TEXT, `phoneNormalized` TEXT, `email` TEXT, `gstin` TEXT, `website` TEXT, " +
                    "`description` TEXT, `logoAssetPath` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`companyId`))",
            )
        }
    }

    /**
     * Additive-only (MVP-1.4 Catalogue, architecture §19): adds the seven new Catalogue tables.
     * No existing table is touched, dropped, or destructively recreated — every pre-existing row
     * in every prior table survives unchanged. Deliberately no `cached_stock_items` column is
     * added or touched here: Catalogue always resolves Tally-owned fields via a live join on
     * `catalogue_product_source_link.externalStockItemId`, never a mirrored copy (see
     * `CatalogueEntities.kt`'s own doc comment). An existing install with no Catalogue data simply
     * has seven empty tables until first used.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_product` (" +
                    "`companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                    "`linkedStockItemId` TEXT, `sku` TEXT, `displayNameOverride` TEXT, `description` TEXT, " +
                    "`specifications` TEXT, `customerFacingCategory` TEXT, `priceDisplayMode` TEXT NOT NULL, " +
                    "`manualPriceAmount` TEXT, `manualPriceCurrencyCode` TEXT, `lifecycleState` TEXT NOT NULL, " +
                    "`sourceAvailable` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `createdAtSource` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `updatedAtSource` TEXT NOT NULL, `archivedAt` INTEGER, " +
                    "`archivedAtSource` TEXT, PRIMARY KEY(`companyId`, `productId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalogue_product_companyId` ON `catalogue_product` (`companyId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_product_companyId_lifecycleState` " +
                    "ON `catalogue_product` (`companyId`, `lifecycleState`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_product_companyId_linkedStockItemId` " +
                    "ON `catalogue_product` (`companyId`, `linkedStockItemId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_product_source_link` (" +
                    "`companyId` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `externalStockItemId` TEXT NOT NULL, " +
                    "`productId` TEXT NOT NULL, `lastConfirmedAt` INTEGER NOT NULL, `lastConfirmedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `sourceType`, `externalStockItemId`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_catalogue_product_source_link_companyId_productId` " +
                    "ON `catalogue_product_source_link` (`companyId`, `productId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_branch` (" +
                    "`companyId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `createdAtSource` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `updatedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `branchId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalogue_branch_companyId` ON `catalogue_branch` (`companyId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_override` (" +
                    "`companyId` TEXT NOT NULL, `scopeType` TEXT NOT NULL, `scopeKey` TEXT NOT NULL, " +
                    "`attributeName` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`updatedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `scopeType`, `scopeKey`, `attributeName`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_override_companyId_attributeName` " +
                    "ON `catalogue_override` (`companyId`, `attributeName`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_published_snapshot` (" +
                    "`companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`description` TEXT, `specifications` TEXT, `customerFacingCategory` TEXT, " +
                    "`priceDisplayMode` TEXT NOT NULL, `resolvedPriceAmount` TEXT, `resolvedPriceCurrencyCode` TEXT, " +
                    "`primaryAssetId` TEXT, `publishedAt` INTEGER NOT NULL, `publishedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `productId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_published_snapshot_companyId` " +
                    "ON `catalogue_published_snapshot` (`companyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_asset` (" +
                    "`companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `assetId` TEXT NOT NULL, " +
                    "`isPrimary` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `filePath` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `createdAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `productId`, `assetId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_asset_companyId_productId` " +
                    "ON `catalogue_asset` (`companyId`, `productId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_settings` (" +
                    "`companyId` TEXT NOT NULL, `isPublic` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`updatedAtSource` TEXT NOT NULL, PRIMARY KEY(`companyId`))",
            )
        }
    }

    /**
     * Additive-only (MVP-1.4 Catalogue Excel contract completion, architecture §9): adds the single
     * `catalogue_custom_field` table so an owner-defined Excel custom column's per-product value
     * actually persists and round-trips on export, not just its name. No existing table touched.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_custom_field` (" +
                    "`companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `columnName` TEXT NOT NULL, " +
                    "`value` TEXT, `updatedAt` INTEGER NOT NULL, `updatedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `productId`, `columnName`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_custom_field_companyId_productId` " +
                    "ON `catalogue_custom_field` (`companyId`, `productId`)",
            )
        }
    }

    /**
     * Additive-only (TD-047 resolution): adds a single nullable `manualUnit` column to
     * `catalogue_product`. Existing rows backfill to `NULL` (SQLite's implicit default for a
     * nullable `ALTER TABLE ... ADD COLUMN`, same precedent as `MIGRATION_8_9`'s `dueAt`/
     * `completedAt`/`issueId`) — every pre-existing product, Manual or Tally-linked, is completely
     * unaffected until an owner explicitly sets a Manual product's Unit. This column is populated
     * only for a `source = 'MANUAL'` row ([CatalogueRepositoryImpl] enforces this); a Tally-linked
     * product's Unit remains exclusively resolved from the live Stock Item join, never this column.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `catalogue_product` ADD COLUMN `manualUnit` TEXT")
        }
    }

    /**
     * Transaction Mode foundation (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md §14):
     * eight new, additive-only tables. No existing table (`cached_parties`, `cached_ledgers`,
     * `cached_stock_items`, any `catalogue_*` table) is touched — mirrors the exact
     * `MIGRATION_10_11` precedent of bundling every new table for one cohesive feature landing
     * together into a single migration.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_estimate_po` (" +
                    "`companyId` TEXT NOT NULL, `estimatePoId` TEXT NOT NULL, `entryPointType` TEXT NOT NULL, " +
                    "`submissionType` TEXT NOT NULL, `deliveryChannel` TEXT NOT NULL, `buyerPartyId` TEXT, " +
                    "`totalAmount` TEXT NOT NULL, `currencyCode` TEXT, `status` TEXT NOT NULL, " +
                    "`submittedAt` INTEGER NOT NULL, `submittedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `estimatePoId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_estimate_po_companyId` ON `txn_estimate_po` (`companyId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_estimate_po_companyId_buyerPartyId` " +
                    "ON `txn_estimate_po` (`companyId`, `buyerPartyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_estimate_po_line_item` (" +
                    "`companyId` TEXT NOT NULL, `estimatePoId` TEXT NOT NULL, `lineItemId` TEXT NOT NULL, " +
                    "`linkedProductId` TEXT, `snapshotProductName` TEXT NOT NULL, `snapshotUnit` TEXT, " +
                    "`snapshotSku` TEXT, `quantity` TEXT NOT NULL, `unitPriceAmount` TEXT, " +
                    "`unitPriceCurrencyCode` TEXT, `lineTotalAmount` TEXT, `isContactForPrice` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `estimatePoId`, `lineItemId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_estimate_po_line_item_companyId_estimatePoId` " +
                    "ON `txn_estimate_po_line_item` (`companyId`, `estimatePoId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_seller_inbox_entry` (" +
                    "`companyId` TEXT NOT NULL, `inboxEntryId` TEXT NOT NULL, `estimatePoId` TEXT NOT NULL, " +
                    "`state` TEXT NOT NULL, `acknowledgedAt` INTEGER, `acknowledgedAtSource` TEXT, " +
                    "`changeRequestNote` TEXT, `respondedAt` INTEGER, `respondedAtSource` TEXT, " +
                    "`convertedTransactionId` TEXT, PRIMARY KEY(`companyId`, `inboxEntryId`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_seller_inbox_entry_companyId_estimatePoId` " +
                    "ON `txn_seller_inbox_entry` (`companyId`, `estimatePoId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_seller_inbox_entry_companyId_state` " +
                    "ON `txn_seller_inbox_entry` (`companyId`, `state`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `commercial_transaction` (" +
                    "`companyId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `estimatePoId` TEXT NOT NULL, " +
                    "`buyerPartyId` TEXT NOT NULL, `state` TEXT NOT NULL, `totalAmount` TEXT NOT NULL, " +
                    "`currencyCode` TEXT, `acceptedAt` INTEGER NOT NULL, `acceptedAtSource` TEXT NOT NULL, " +
                    "`completedAt` INTEGER, `completedAtSource` TEXT, " +
                    "PRIMARY KEY(`companyId`, `transactionId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_commercial_transaction_companyId` ON `commercial_transaction` (`companyId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_commercial_transaction_companyId_buyerPartyId` " +
                    "ON `commercial_transaction` (`companyId`, `buyerPartyId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_commercial_transaction_companyId_state` " +
                    "ON `commercial_transaction` (`companyId`, `state`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_terms_acknowledgment` (" +
                    "`companyId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `paymentTiming` TEXT NOT NULL, " +
                    "`creditDays` INTEGER, `partialAdvancePercent` TEXT, `partialBalanceTiming` TEXT, " +
                    "`amount` TEXT NOT NULL, `currencyCode` TEXT, `note` TEXT, `proposedAt` INTEGER NOT NULL, " +
                    "`proposedAtSource` TEXT NOT NULL, `buyerConfirmedAt` INTEGER, `buyerConfirmedAtSource` TEXT, " +
                    "`sellerConfirmedAt` INTEGER, `sellerConfirmedAtSource` TEXT, " +
                    "PRIMARY KEY(`companyId`, `transactionId`))",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_payment_event` (" +
                    "`companyId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `paymentEventId` TEXT NOT NULL, " +
                    "`installmentSequence` INTEGER NOT NULL, `buyerClaimStatus` TEXT NOT NULL, " +
                    "`buyerClaimedAmount` TEXT NOT NULL, `currencyCode` TEXT, `buyerClaimedAt` INTEGER NOT NULL, " +
                    "`buyerClaimedAtSource` TEXT NOT NULL, `sellerConfirmed` INTEGER NOT NULL, " +
                    "`sellerConfirmedAt` INTEGER, `sellerConfirmedAtSource` TEXT, `sellerDiscrepancyNote` TEXT, " +
                    "PRIMARY KEY(`companyId`, `transactionId`, `paymentEventId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_payment_event_companyId_transactionId` " +
                    "ON `txn_payment_event` (`companyId`, `transactionId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_ledger_intent` (" +
                    "`companyId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `buyerPartyId` TEXT NOT NULL, " +
                    "`chosenLedgerGroup` TEXT NOT NULL, `promotedProspectAt` INTEGER, `promotedProspectAtSource` TEXT, " +
                    "`recordedAt` INTEGER NOT NULL, `recordedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `transactionId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_ledger_intent_companyId_buyerPartyId` " +
                    "ON `txn_ledger_intent` (`companyId`, `buyerPartyId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalogue_access_grant` (" +
                    "`companyId` TEXT NOT NULL, `grantId` TEXT NOT NULL, `buyerPartyId` TEXT NOT NULL, " +
                    "`priceVisibility` TEXT NOT NULL, `grantedAt` INTEGER NOT NULL, `grantedAtSource` TEXT NOT NULL, " +
                    "`expiresAt` INTEGER, `revokedAt` INTEGER, `revokedAtSource` TEXT, " +
                    "PRIMARY KEY(`companyId`, `grantId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalogue_access_grant_companyId_buyerPartyId` " +
                    "ON `catalogue_access_grant` (`companyId`, `buyerPartyId`)",
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order` (" +
                    "`companyId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `creationKey` TEXT NOT NULL, " +
                    "`sellerCompanyId` TEXT NOT NULL, `buyerPartyId` TEXT, `state` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, `submissionType` TEXT NOT NULL, `note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `createdAtSource` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `orderId`))",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_order_companyId_creationKey` ON `txn_order` (`companyId`, `creationKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_order_companyId_buyerPartyId` ON `txn_order` (`companyId`, `buyerPartyId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order_line` (" +
                    "`companyId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `lineId` TEXT NOT NULL, " +
                    "`linkedProductId` TEXT, `snapshotProductName` TEXT NOT NULL, `snapshotUnit` TEXT, " +
                    "`snapshotSku` TEXT, `quantity` TEXT NOT NULL, `unitPriceAmount` TEXT, " +
                    "`unitPriceCurrencyCode` TEXT, `priceState` TEXT NOT NULL, `lineTotalAmount` TEXT, " +
                    "PRIMARY KEY(`companyId`, `orderId`, `lineId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_order_line_companyId_orderId` ON `txn_order_line` (`companyId`, `orderId`)")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order_outbox` (" +
                    "`companyId` TEXT NOT NULL, `envelopeId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, " +
                    "`objectType` TEXT NOT NULL, `orderId` TEXT NOT NULL, `orderVersion` INTEGER NOT NULL, " +
                    "`senderCompanyId` TEXT NOT NULL, `recipientPartyId` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`createdAtSource` TEXT NOT NULL, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                    "`lastAttemptAt` INTEGER, `lastAttemptAtSource` TEXT, `lastError` TEXT, " +
                    "PRIMARY KEY(`companyId`, `envelopeId`))",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_order_outbox_companyId_idempotencyKey` ON `txn_order_outbox` (`companyId`, `idempotencyKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_order_outbox_companyId_orderId` ON `txn_order_outbox` (`companyId`, `orderId`)")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_recipient_inbox` (" +
                    "`companyId` TEXT NOT NULL, `envelopeId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, " +
                    "`objectType` TEXT NOT NULL, `objectId` TEXT NOT NULL, `objectVersion` INTEGER NOT NULL, " +
                    "`senderBusinessId` TEXT NOT NULL, `senderActorId` TEXT NOT NULL, `senderDeviceId` TEXT NOT NULL, " +
                    "`mailboxId` TEXT NOT NULL, `mailboxSequence` INTEGER NOT NULL, `acceptanceId` TEXT NOT NULL, " +
                    "`acceptedAt` INTEGER NOT NULL, `acceptedAtSource` TEXT NOT NULL, `ingestedAt` INTEGER NOT NULL, " +
                    "`ingestedAtSource` TEXT NOT NULL, `transportState` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `envelopeId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_recipient_inbox_companyId_mailboxId_mailboxSequence` " +
                    "ON `txn_recipient_inbox` (`companyId`, `mailboxId`, `mailboxSequence`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_recipient_inbox_companyId_objectType_objectId` " +
                    "ON `txn_recipient_inbox` (`companyId`, `objectType`, `objectId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_recipient_inbox_cursor` (" +
                    "`companyId` TEXT NOT NULL, `mailboxId` TEXT NOT NULL, `cursor` TEXT, " +
                    "PRIMARY KEY(`companyId`, `mailboxId`))",
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order_commercial_event` (" +
                    "`companyId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, " +
                    "`orderId` TEXT NOT NULL, `orderVersion` INTEGER NOT NULL, `eventType` TEXT NOT NULL, " +
                    "`actorBusinessId` TEXT NOT NULL, `actorId` TEXT NOT NULL, `actorDeviceId` TEXT, " +
                    "`counterpartyBusinessId` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `occurredAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `eventId`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_order_commercial_event_companyId_idempotencyKey` " +
                    "ON `txn_order_commercial_event` (`companyId`, `idempotencyKey`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_order_commercial_event_companyId_orderId_orderVersion_eventType` " +
                    "ON `txn_order_commercial_event` (`companyId`, `orderId`, `orderVersion`, `eventType`)",
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn_order_commercial_event` ADD COLUMN `authorityEpoch` INTEGER")
            db.execSQL("ALTER TABLE `txn_order_commercial_event` ADD COLUMN `authorityScopeFingerprint` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order_version_archive` (" +
                    "`companyId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                    "`creationKey` TEXT NOT NULL, `sellerCompanyId` TEXT NOT NULL, `buyerPartyId` TEXT, " +
                    "`state` TEXT NOT NULL, `source` TEXT NOT NULL, `submissionType` TEXT NOT NULL, `note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `createdAtSource` TEXT NOT NULL, `supersedesVersion` INTEGER, " +
                    "`revisionReason` TEXT, `archivedAt` INTEGER NOT NULL, `archivedAtSource` TEXT NOT NULL, " +
                    "PRIMARY KEY(`companyId`, `orderId`, `version`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_order_version_archive_companyId_orderId` " +
                    "ON `txn_order_version_archive` (`companyId`, `orderId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `txn_order_line_version_archive` (" +
                    "`companyId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `version` INTEGER NOT NULL, `lineId` TEXT NOT NULL, " +
                    "`linkedProductId` TEXT, `snapshotProductName` TEXT NOT NULL, `snapshotUnit` TEXT, `snapshotSku` TEXT, " +
                    "`quantity` TEXT NOT NULL, `unitPriceAmount` TEXT, `unitPriceCurrencyCode` TEXT, `priceState` TEXT NOT NULL, " +
                    "`lineTotalAmount` TEXT, PRIMARY KEY(`companyId`, `orderId`, `version`, `lineId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_txn_order_line_version_archive_companyId_orderId_version` " +
                    "ON `txn_order_line_version_archive` (`companyId`, `orderId`, `version`)",
            )
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn_order_outbox` ADD COLUMN `recipientBusinessId` TEXT")
            db.execSQL("ALTER TABLE `txn_order_outbox` ADD COLUMN `commercialContentType` TEXT")
            db.execSQL("ALTER TABLE `txn_order_outbox` ADD COLUMN `commercialContentVersion` INTEGER")
            db.execSQL("ALTER TABLE `txn_order_outbox` ADD COLUMN `commercialContentCanonical` TEXT")
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `authenticated_counterparty_binding` (" +
                    "`localBusinessId` TEXT NOT NULL, `partyId` TEXT NOT NULL, `counterpartyBusinessId` TEXT NOT NULL, " +
                    "`verifiedActorId` TEXT NOT NULL, `verifiedDeviceId` TEXT NOT NULL, `authorityEpoch` INTEGER NOT NULL, " +
                    "`verificationReference` TEXT NOT NULL, `status` TEXT NOT NULL, `verifiedAtEpochMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`localBusinessId`, `partyId`))",
            )
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn_order` ADD COLUMN `buyerBusinessId` TEXT")
            db.execSQL("ALTER TABLE `txn_order` ADD COLUMN `sellerBusinessId` TEXT")
            db.execSQL("ALTER TABLE `txn_order_version_archive` ADD COLUMN `buyerBusinessId` TEXT")
            db.execSQL("ALTER TABLE `txn_order_version_archive` ADD COLUMN `sellerBusinessId` TEXT")
        }
    }
}
