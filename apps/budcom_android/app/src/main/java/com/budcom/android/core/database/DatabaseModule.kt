package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import com.budcom.android.feature.company.data.local.CompanyDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementDao
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemDao
import com.budcom.android.feature.voucher.data.local.VoucherDao
import com.budcom.android.core.connection.data.local.PairedConnectorDao
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyNoteDao
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.TagDao
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
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
}
