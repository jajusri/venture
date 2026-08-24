package com.budcom.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the version 1 -> 2 migration (adding `paired_connectors` for the Connector identity
 * milestone) preserves every pre-existing cached company/master-data row and never falls back
 * to a destructive recreation.
 *
 * This test exists specifically because an earlier version of this migration accidentally
 * shipped with `fallbackToDestructiveMigration()` and no explicit migration — which would have
 * silently wiped every installed user's local cache on the first upgrade that bumped the schema
 * version for an entirely unrelated feature. A clean-database unit test can never catch that
 * class of defect (there is nothing to destroy), so this instrumentation test starts from a
 * populated version-1 database, exactly like a real installed app would have on disk.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingRowsAndAddsPairedConnectorsOnly() {
        // Arrange: a version-1 database containing the pre-pairing schema, populated with
        // representative rows exactly as a real installed app would have on disk today.
        var db = helper.createDatabase(testDbName, 1)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_companies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`financialYear` TEXT, `booksFrom` TEXT, `baseCurrency` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `company_discovery_meta` (`id` INTEGER NOT NULL, " +
                "`schemaVersion` TEXT NOT NULL, `dataFreshnessAt` TEXT NOT NULL, `contractVersion` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `tallyReachable` INTEGER NOT NULL, `dataQualityStatus` TEXT, " +
                "`dataQualityReason` TEXT, `reason` TEXT, `cachedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_ledgers` (`companyId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `alias` TEXT, `parentGroup` TEXT, `status` TEXT NOT NULL, " +
                "`closingAmount` TEXT, `closingCurrencyCode` TEXT, `closingSide` TEXT, `dataQuality` TEXT NOT NULL, " +
                "`syncedAt` TEXT NOT NULL, `dataFreshnessAt` TEXT, PRIMARY KEY(`companyId`, `id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_stock_items` (`companyId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `alias` TEXT, `parentGroup` TEXT, `category` TEXT, `baseUnit` TEXT, " +
                "`partNumber` TEXT, `hsnCode` TEXT, `gstRate` TEXT, `status` TEXT NOT NULL, `closingAmount` TEXT, " +
                "`closingCurrencyCode` TEXT, `closingSide` TEXT, `dataQuality` TEXT NOT NULL, `syncedAt` TEXT NOT NULL, " +
                "`dataFreshnessAt` TEXT, PRIMARY KEY(`companyId`, `id`))",
        )

        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO company_discovery_meta (id, schemaVersion, dataFreshnessAt, contractVersion, status, " +
                "tallyReachable, dataQualityStatus, dataQualityReason, reason, cachedAtEpochMs) " +
                "VALUES (1, '1.0', '2026-01-01T00:00:00Z', '1.0', 'OK', 1, NULL, NULL, NULL, 1735689600000)",
        )
        db.execSQL(
            "INSERT INTO cached_ledgers (companyId, id, name, alias, parentGroup, status, closingAmount, " +
                "closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt) " +
                "VALUES ('acme-001', 'ledger-1', 'Cash', NULL, 'Current Assets', 'ACTIVE', '1000.00', 'INR', " +
                "'DEBIT', 'GOOD', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO cached_stock_items (companyId, id, name, alias, parentGroup, category, baseUnit, " +
                "partNumber, hsnCode, gstRate, status, closingAmount, closingCurrencyCode, closingSide, " +
                "dataQuality, syncedAt, dataFreshnessAt) VALUES ('acme-001', 'item-1', 'Widget', NULL, NULL, " +
                "NULL, 'PCS', NULL, NULL, NULL, 'ACTIVE', '10', NULL, NULL, 'GOOD', '2026-01-01T00:00:00Z', " +
                "'2026-01-01T00:00:00Z')",
        )
        db.close()

        // Act: run the real production migration (the exact object DatabaseModule wires into
        // Room.databaseBuilder().addMigrations(...) — not a re-implementation of it).
        db = helper.runMigrationsAndValidate(testDbName, 2, false, DatabaseModule.MIGRATION_1_2)

        // Assert: pre-existing rows survived — this is the direct proof that no destructive
        // recreation occurred. fallbackToDestructiveMigration() would have dropped everything
        // before this point, and every one of these queries would return no rows.
        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_ledgers WHERE companyId = 'acme-001' AND id = 'ledger-1'").use { cursor ->
            assertTrue("existing ledger row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_stock_items WHERE companyId = 'acme-001' AND id = 'item-1'").use { cursor ->
            assertTrue("existing stock item row must survive the migration", cursor.moveToFirst())
            assertEquals("Widget", cursor.getString(0))
        }
        db.query("SELECT schemaVersion FROM company_discovery_meta WHERE id = 1").use { cursor ->
            assertTrue("existing discovery-meta row must survive the migration", cursor.moveToFirst())
            assertEquals("1.0", cursor.getString(0))
        }

        // Assert: paired_connectors exists with exactly the expected schema (matches
        // PairedConnectorEntity field-for-field).
        val columns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`paired_connectors`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "connectorId" to "TEXT",
                "friendlyName" to "TEXT",
                "lastKnownHost" to "TEXT",
                "lastKnownPort" to "INTEGER",
                "lastConnectedAtEpochMillis" to "INTEGER",
                "createdAtEpochMillis" to "INTEGER",
            ),
            columns,
        )

        // Assert: the new table is genuinely usable — insert and read back a real row.
        db.execSQL(
            "INSERT INTO paired_connectors (connectorId, friendlyName, lastKnownHost, lastKnownPort, " +
                "lastConnectedAtEpochMillis, createdAtEpochMillis) VALUES ('conn-abc123', 'Test Connector', " +
                "'192.168.50.10', 8080, 1735689600000, 1735689600000)",
        )
        db.query(
            "SELECT friendlyName, lastKnownPort FROM paired_connectors WHERE connectorId = 'conn-abc123'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Connector", cursor.getString(0))
            assertEquals(8080, cursor.getInt(1))
        }

        db.close()
    }

    /**
     * Proves the version 2 -> 3 migration (adding the voucher-cache tables) preserves every
     * pre-existing company/master-data/paired-connector row and never falls back to a
     * destructive recreation.
     *
     * Starts from a real version-2 database built from the committed `2.json` schema — the
     * same starting point a real installed app would have on disk after the version 1 -> 2
     * migration — rather than re-declaring the version-2 tables by hand.
     */
    @Test
    fun migrate2To3_preservesExistingRowsAndAddsVoucherCacheTablesOnly() {
        val db23DbName = "migration-test-db-2-3"

        // Arrange: a real version-2 database (built from the committed schema/2.json), populated
        // with representative rows exactly as a real installed app would have on disk today.
        var db = helper.createDatabase(db23DbName, 2)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO company_discovery_meta (id, schemaVersion, dataFreshnessAt, contractVersion, status, " +
                "tallyReachable, dataQualityStatus, dataQualityReason, reason, cachedAtEpochMs) " +
                "VALUES (1, '1.0', '2026-01-01T00:00:00Z', '1.0', 'OK', 1, NULL, NULL, NULL, 1735689600000)",
        )
        db.execSQL(
            "INSERT INTO cached_ledgers (companyId, id, name, alias, parentGroup, status, closingAmount, " +
                "closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt) " +
                "VALUES ('acme-001', 'ledger-1', 'Cash', NULL, 'Current Assets', 'ACTIVE', '1000.00', 'INR', " +
                "'DEBIT', 'GOOD', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO cached_stock_items (companyId, id, name, alias, parentGroup, category, baseUnit, " +
                "partNumber, hsnCode, gstRate, status, closingAmount, closingCurrencyCode, closingSide, " +
                "dataQuality, syncedAt, dataFreshnessAt) VALUES ('acme-001', 'item-1', 'Widget', NULL, NULL, " +
                "NULL, 'PCS', NULL, NULL, NULL, 'ACTIVE', '10', NULL, NULL, 'GOOD', '2026-01-01T00:00:00Z', " +
                "'2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO paired_connectors (connectorId, friendlyName, lastKnownHost, lastKnownPort, " +
                "lastConnectedAtEpochMillis, createdAtEpochMillis) VALUES ('conn-abc123', 'Test Connector', " +
                "'192.168.50.10', 8080, 1735689600000, 1735689600000)",
        )
        db.close()

        // Act: run the real production migration (the exact object DatabaseModule wires into
        // Room.databaseBuilder().addMigrations(...) — not a re-implementation of it).
        db = helper.runMigrationsAndValidate(db23DbName, 3, false, DatabaseModule.MIGRATION_2_3)

        // Assert: every pre-existing row survived — direct proof no destructive recreation
        // occurred.
        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_ledgers WHERE companyId = 'acme-001' AND id = 'ledger-1'").use { cursor ->
            assertTrue("existing ledger row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_stock_items WHERE companyId = 'acme-001' AND id = 'item-1'").use { cursor ->
            assertTrue("existing stock item row must survive the migration", cursor.moveToFirst())
            assertEquals("Widget", cursor.getString(0))
        }
        db.query("SELECT schemaVersion FROM company_discovery_meta WHERE id = 1").use { cursor ->
            assertTrue("existing discovery-meta row must survive the migration", cursor.moveToFirst())
            assertEquals("1.0", cursor.getString(0))
        }
        db.query("SELECT friendlyName FROM paired_connectors WHERE connectorId = 'conn-abc123'").use { cursor ->
            assertTrue("existing paired-connector row must survive the migration", cursor.moveToFirst())
            assertEquals("Test Connector", cursor.getString(0))
        }

        // Assert: every voucher-cache table exists with exactly the expected schema (matches
        // the voucher Room entities field-for-field).
        fun columnsOf(table: String): Map<String, String> {
            val columns = mutableMapOf<String, String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val typeIdx = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
                }
            }
            return columns
        }

        assertEquals(
            mapOf(
                "companyId" to "TEXT",
                "voucherId" to "TEXT",
                "date" to "TEXT",
                "type" to "TEXT",
                "number" to "TEXT",
                "partyName" to "TEXT",
                "referenceNumber" to "TEXT",
                "amountValue" to "TEXT",
                "amountSide" to "TEXT",
                "status" to "TEXT",
                "dataQuality" to "TEXT",
                "lastSyncedAt" to "INTEGER",
            ),
            columnsOf("cached_vouchers"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT",
                "voucherId" to "TEXT",
                "effectiveDate" to "TEXT",
                "narration" to "TEXT",
                "lastSyncedAt" to "INTEGER",
            ),
            columnsOf("cached_voucher_details"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT",
                "voucherId" to "TEXT",
                "lineNumber" to "INTEGER",
                "ledgerName" to "TEXT",
                "amountValue" to "TEXT",
                "amountSide" to "TEXT",
                "isDeemedPositive" to "INTEGER",
            ),
            columnsOf("cached_voucher_ledger_lines"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT",
                "voucherId" to "TEXT",
                "lineNumber" to "INTEGER",
                "itemName" to "TEXT",
                "quantity" to "TEXT",
                "rate" to "TEXT",
                "amountValue" to "TEXT",
                "amountSide" to "TEXT",
            ),
            columnsOf("cached_voucher_inventory_lines"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT",
                "lastSyncedAt" to "INTEGER",
            ),
            columnsOf("voucher_cache_meta"),
        )

        // Assert: every voucher-cache table is genuinely usable — insert and read back a real
        // row in each, covering summary, detail, ledger line, inventory line and cache-meta.
        db.execSQL(
            "INSERT INTO cached_vouchers (companyId, voucherId, date, type, number, partyName, " +
                "referenceNumber, amountValue, amountSide, status, dataQuality, lastSyncedAt) VALUES " +
                "('acme-001', 'v-1', '2026-01-15', 'Sales', 'S-1', 'Beta Traders', 'PO-99', '500.00', " +
                "'DEBIT', 'Active', 'Complete', 1736899200000)",
        )
        db.query(
            "SELECT partyName FROM cached_vouchers WHERE companyId = 'acme-001' AND voucherId = 'v-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Beta Traders", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO cached_voucher_details (companyId, voucherId, effectiveDate, narration, " +
                "lastSyncedAt) VALUES ('acme-001', 'v-1', '2026-01-15', 'Sale of widgets', 1736899200000)",
        )
        db.query(
            "SELECT narration FROM cached_voucher_details WHERE companyId = 'acme-001' AND voucherId = 'v-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Sale of widgets", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO cached_voucher_ledger_lines (companyId, voucherId, lineNumber, ledgerName, " +
                "amountValue, amountSide, isDeemedPositive) VALUES " +
                "('acme-001', 'v-1', 1, 'Sales Account', '500.00', 'CREDIT', 1)",
        )
        db.query(
            "SELECT ledgerName FROM cached_voucher_ledger_lines WHERE companyId = 'acme-001' " +
                "AND voucherId = 'v-1' AND lineNumber = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Sales Account", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO cached_voucher_inventory_lines (companyId, voucherId, lineNumber, itemName, " +
                "quantity, rate, amountValue, amountSide) VALUES " +
                "('acme-001', 'v-1', 1, 'Widget', '5', '100.00', '500.00', 'DEBIT')",
        )
        db.query(
            "SELECT itemName FROM cached_voucher_inventory_lines WHERE companyId = 'acme-001' " +
                "AND voucherId = 'v-1' AND lineNumber = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Widget", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO voucher_cache_meta (companyId, lastSyncedAt) VALUES ('acme-001', 1736899200000)",
        )
        db.query("SELECT lastSyncedAt FROM voucher_cache_meta WHERE companyId = 'acme-001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1736899200000L, cursor.getLong(0))
        }

        db.close()
    }

    /**
     * Proves the version 3 -> 4 migration (adding the Ledger-statement cache tables) preserves
     * every pre-existing row and never falls back to a destructive recreation — the same class of
     * proof as [migrate1To2_preservesExistingRowsAndAddsPairedConnectorsOnly] and
     * [migrate2To3_preservesExistingRowsAndAddsVoucherCacheTablesOnly], now for the MVP-1 Ledger
     * statement feature.
     *
     * Starts from a real version-3 database built from the committed `3.json` schema, populated
     * with representative rows exactly as a real installed app would have on disk today.
     */
    @Test
    fun migrate3To4_preservesExistingRowsAndAddsLedgerStatementTablesOnly() {
        val db34DbName = "migration-test-db-3-4"

        var db = helper.createDatabase(db34DbName, 3)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_ledgers (companyId, id, name, alias, parentGroup, status, closingAmount, " +
                "closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt) " +
                "VALUES ('acme-001', 'ledger-1', 'Cash', NULL, 'Current Assets', 'ACTIVE', '1000.00', 'INR', " +
                "'DEBIT', 'GOOD', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO cached_vouchers (companyId, voucherId, date, type, number, partyName, " +
                "referenceNumber, amountValue, amountSide, status, dataQuality, lastSyncedAt) VALUES " +
                "('acme-001', 'v-1', '2026-01-15', 'Sales', 'S-1', 'Beta Traders', 'PO-99', '500.00', " +
                "'DEBIT', 'Active', 'Complete', 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db34DbName, 4, false, DatabaseModule.MIGRATION_3_4)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_ledgers WHERE companyId = 'acme-001' AND id = 'ledger-1'").use { cursor ->
            assertTrue("existing ledger row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }
        db.query("SELECT partyName FROM cached_vouchers WHERE companyId = 'acme-001' AND voucherId = 'v-1'").use { cursor ->
            assertTrue("existing voucher row must survive the migration", cursor.moveToFirst())
            assertEquals("Beta Traders", cursor.getString(0))
        }

        fun columnsOf(table: String): Map<String, String> {
            val columns = mutableMapOf<String, String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val typeIdx = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
                }
            }
            return columns
        }

        assertEquals(
            mapOf(
                "companyId" to "TEXT", "ledgerId" to "TEXT", "periodFrom" to "TEXT", "periodTo" to "TEXT",
                "ledgerName" to "TEXT", "parentGroup" to "TEXT", "openingAmount" to "TEXT", "openingSide" to "TEXT",
                "closingAmount" to "TEXT", "closingSide" to "TEXT", "transactionsComplete" to "INTEGER",
                "balanceAvailable" to "INTEGER", "syncedFrom" to "TEXT", "syncedTo" to "TEXT",
                "coverageMessage" to "TEXT", "lastSyncedAt" to "INTEGER",
            ),
            columnsOf("cached_ledger_statements"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "ledgerId" to "TEXT", "periodFrom" to "TEXT", "periodTo" to "TEXT",
                "lineIndex" to "INTEGER", "voucherId" to "TEXT", "date" to "TEXT", "voucherType" to "TEXT",
                "voucherNumber" to "TEXT", "referenceNumber" to "TEXT", "narration" to "TEXT", "debit" to "TEXT",
                "credit" to "TEXT", "runningAmount" to "TEXT", "runningSide" to "TEXT",
            ),
            columnsOf("cached_ledger_statement_transactions"),
        )

        db.execSQL(
            "INSERT INTO cached_ledger_statements (companyId, ledgerId, periodFrom, periodTo, ledgerName, " +
                "parentGroup, openingAmount, openingSide, closingAmount, closingSide, transactionsComplete, " +
                "balanceAvailable, syncedFrom, syncedTo, coverageMessage, lastSyncedAt) VALUES " +
                "('acme-001', 'ledger-1', '2026-07-01', '2026-07-31', 'Cash', 'Current Assets', '0', 'Dr', " +
                "'500', 'Dr', 1, 1, '2026-07-01', '2026-08-10', NULL, 1736899200000)",
        )
        db.query(
            "SELECT closingAmount FROM cached_ledger_statements WHERE companyId = 'acme-001' AND ledgerId = 'ledger-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("500", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 4 -> 5 migration (three indices supporting the local-first Ledger
     * statement's queries over the existing Voucher cache) is purely additive: every pre-existing
     * row in every table survives untouched, and no table is created, dropped, or destructively
     * recreated — this migration adds indices only, never a `fallbackToDestructiveMigration()`.
     */
    @Test
    fun migrate4To5_preservesExistingRowsAndAddsIndicesOnly() {
        val db45DbName = "migration-test-db-4-5"

        var db = helper.createDatabase(db45DbName, 4)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_ledgers (companyId, id, name, alias, parentGroup, status, closingAmount, " +
                "closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt) " +
                "VALUES ('acme-001', 'ledger-1', 'Cash', NULL, 'Current Assets', 'ACTIVE', '1000.00', 'INR', " +
                "'DEBIT', 'GOOD', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO cached_vouchers (companyId, voucherId, date, type, number, partyName, " +
                "referenceNumber, amountValue, amountSide, status, dataQuality, lastSyncedAt) VALUES " +
                "('acme-001', 'v-1', '2026-01-15', 'Sales', 'S-1', 'Beta Traders', 'PO-99', '500.00', " +
                "'DEBIT', 'Active', 'Complete', 1736899200000)",
        )
        db.execSQL(
            "INSERT INTO cached_voucher_ledger_lines (companyId, voucherId, lineNumber, ledgerName, " +
                "amountValue, amountSide, isDeemedPositive) VALUES " +
                "('acme-001', 'v-1', 1, 'Cash', '500.00', 'CREDIT', 1)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db45DbName, 5, false, DatabaseModule.MIGRATION_4_5)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_ledgers WHERE companyId = 'acme-001' AND id = 'ledger-1'").use { cursor ->
            assertTrue("existing ledger row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }
        db.query("SELECT partyName FROM cached_vouchers WHERE companyId = 'acme-001' AND voucherId = 'v-1'").use { cursor ->
            assertTrue("existing voucher row must survive the migration", cursor.moveToFirst())
            assertEquals("Beta Traders", cursor.getString(0))
        }
        db.query(
            "SELECT ledgerName FROM cached_voucher_ledger_lines WHERE companyId = 'acme-001' AND voucherId = 'v-1'",
        ).use { cursor ->
            assertTrue("existing ledger-line row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }

        // Assert: the three new indices exist with exactly the expected name/table/uniqueness.
        val indexNames = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name IN ('cached_vouchers', 'cached_voucher_ledger_lines')")
            .use { cursor ->
                while (cursor.moveToNext()) indexNames.add(cursor.getString(0))
            }
        assertTrue("date index must exist", indexNames.contains("index_cached_vouchers_companyId_date"))
        assertTrue("status index must exist", indexNames.contains("index_cached_vouchers_companyId_status"))
        assertTrue(
            "ledgerName index must exist",
            indexNames.contains("index_cached_voucher_ledger_lines_companyId_ledgerName"),
        )

        // Assert: the indexed queries the local-first Ledger statement depends on are genuinely
        // usable post-migration (not just present in sqlite_master).
        db.query(
            "SELECT voucherId FROM cached_vouchers WHERE companyId = 'acme-001' AND date = '2026-01-15'",
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        db.query(
            "SELECT ledgerName FROM cached_voucher_ledger_lines WHERE companyId = 'acme-001' AND ledgerName = 'Cash'",
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }

        db.close()
    }

    /**
     * Proves the version 5 -> 6 migration (adding the six MVP-1.1-A Universal Party Identity
     * tables) preserves every pre-existing row and never falls back to a destructive recreation
     * — the same class of proof as every migration test above, now for Party/PartySourceLink/
     * PartyFieldProvenance/PartyContactPerson/Tag/PartyTagAssignment. Party rows themselves are
     * never created by the migration (seeding is application logic, not SQL) — this test proves
     * only that the structure exists and is genuinely usable.
     */
    @Test
    fun migrate5To6_preservesExistingRowsAndAddsPartyTablesOnly() {
        val db56DbName = "migration-test-db-5-6"

        var db = helper.createDatabase(db56DbName, 5)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_ledgers (companyId, id, name, alias, parentGroup, status, closingAmount, " +
                "closingCurrencyCode, closingSide, dataQuality, syncedAt, dataFreshnessAt) " +
                "VALUES ('acme-001', 'guid:cash', 'Cash', NULL, 'Current Assets', 'ACTIVE', '1000.00', 'INR', " +
                "'DEBIT', 'GOOD', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO cached_vouchers (companyId, voucherId, date, type, number, partyName, " +
                "referenceNumber, amountValue, amountSide, status, dataQuality, lastSyncedAt) VALUES " +
                "('acme-001', 'v-1', '2026-01-15', 'Sales', 'S-1', 'Beta Traders', 'PO-99', '500.00', " +
                "'DEBIT', 'Active', 'Complete', 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db56DbName, 6, false, DatabaseModule.MIGRATION_5_6)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_ledgers WHERE companyId = 'acme-001' AND id = 'guid:cash'").use { cursor ->
            assertTrue("existing ledger row must survive the migration", cursor.moveToFirst())
            assertEquals("Cash", cursor.getString(0))
        }
        db.query("SELECT partyName FROM cached_vouchers WHERE companyId = 'acme-001' AND voucherId = 'v-1'").use { cursor ->
            assertTrue("existing voucher row must survive the migration", cursor.moveToFirst())
            assertEquals("Beta Traders", cursor.getString(0))
        }

        fun columnsOf(table: String): Map<String, String> {
            val columns = mutableMapOf<String, String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val typeIdx = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
                }
            }
            return columns
        }

        assertEquals(
            mapOf(
                "companyId" to "TEXT", "partyId" to "TEXT", "displayName" to "TEXT", "classification" to "TEXT",
                "primaryPhone" to "TEXT", "primaryPhoneNormalized" to "TEXT", "primaryEmail" to "TEXT",
                "addressLine1" to "TEXT", "addressCity" to "TEXT", "addressState" to "TEXT",
                "addressPincode" to "TEXT", "gstin" to "TEXT", "createdAt" to "INTEGER", "updatedAt" to "INTEGER",
            ),
            columnsOf("cached_parties"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "sourceType" to "TEXT", "externalEntityId" to "TEXT", "partyId" to "TEXT",
                "sourceInstanceId" to "TEXT", "externalDisplayName" to "TEXT", "identitySource" to "TEXT",
                "lastConfirmedAt" to "INTEGER",
            ),
            columnsOf("party_source_links"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "partyId" to "TEXT", "fieldName" to "TEXT", "state" to "TEXT",
                "tallyValue" to "TEXT", "budcomValue" to "TEXT", "lastConfirmedAt" to "INTEGER",
                "lastExportedAt" to "INTEGER", "updatedAt" to "INTEGER",
            ),
            columnsOf("party_field_provenance"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "contactPersonId" to "TEXT", "partyId" to "TEXT", "name" to "TEXT",
                "designation" to "TEXT", "mobile" to "TEXT", "mobileNormalized" to "TEXT", "whatsappNumber" to "TEXT",
                "email" to "TEXT", "isPrimary" to "INTEGER", "provenance" to "TEXT", "createdAt" to "INTEGER",
                "updatedAt" to "INTEGER",
            ),
            columnsOf("party_contact_persons"),
        )
        assertEquals(
            mapOf("tagId" to "TEXT", "parentTagId" to "TEXT", "name" to "TEXT", "path" to "TEXT", "createdAt" to "INTEGER"),
            columnsOf("party_tags"),
        )
        assertEquals(
            mapOf("companyId" to "TEXT", "partyId" to "TEXT", "tagId" to "TEXT", "assignedAt" to "INTEGER"),
            columnsOf("party_tag_assignments"),
        )

        // Assert: every new table is genuinely usable -- insert and read back a real row, and
        // prove the source-link natural key is what resolves a ledger to its Party.
        db.execSQL(
            "INSERT INTO cached_parties (companyId, partyId, displayName, classification, primaryPhone, " +
                "primaryPhoneNormalized, primaryEmail, addressLine1, addressCity, addressState, addressPincode, " +
                "gstin, createdAt, updatedAt) VALUES ('acme-001', 'party-1', 'Cash', 'customer', NULL, NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.execSQL(
            "INSERT INTO party_source_links (companyId, sourceType, externalEntityId, partyId, sourceInstanceId, " +
                "externalDisplayName, identitySource, lastConfirmedAt) VALUES ('acme-001', 'tally_ledger', " +
                "'guid:cash', 'party-1', 'acme-001', 'Cash', 'guid', 1736899200000)",
        )
        db.query(
            "SELECT partyId FROM party_source_links WHERE companyId = 'acme-001' AND sourceType = 'tally_ledger' " +
                "AND externalEntityId = 'guid:cash'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("party-1", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO party_field_provenance (companyId, partyId, fieldName, state, tallyValue, budcomValue, " +
                "lastConfirmedAt, lastExportedAt, updatedAt) VALUES ('acme-001', 'party-1', 'primaryPhone', " +
                "'confirmed_from_tally', '+919876543210', NULL, 1736899200000, NULL, 1736899200000)",
        )
        db.query(
            "SELECT state FROM party_field_provenance WHERE companyId = 'acme-001' AND partyId = 'party-1' " +
                "AND fieldName = 'primaryPhone'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("confirmed_from_tally", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO party_contact_persons (companyId, contactPersonId, partyId, name, designation, mobile, " +
                "mobileNormalized, whatsappNumber, email, isPrimary, provenance, createdAt, updatedAt) VALUES " +
                "('acme-001', 'cp-1', 'party-1', 'Owner Name', 'Owner', '9876543210', '9876543210', NULL, NULL, " +
                "1, 'budcom_only', 1736899200000, 1736899200000)",
        )
        db.query(
            "SELECT name FROM party_contact_persons WHERE companyId = 'acme-001' AND partyId = 'party-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Owner Name", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO party_tags (tagId, parentTagId, name, path, createdAt) VALUES " +
                "('tag-1', NULL, 'Dealer', 'Dealer', 1736899200000)",
        )
        db.execSQL(
            "INSERT INTO party_tag_assignments (companyId, partyId, tagId, assignedAt) VALUES " +
                "('acme-001', 'party-1', 'tag-1', 1736899200000)",
        )
        db.query(
            "SELECT t.name FROM party_tags t INNER JOIN party_tag_assignments a ON a.tagId = t.tagId " +
                "WHERE a.companyId = 'acme-001' AND a.partyId = 'party-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Dealer", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 6 -> 7 migration (adding the single MVP-1.1-C `party_notes` table)
     * preserves every pre-existing row and never falls back to a destructive recreation.
     */
    @Test
    fun migrate6To7_preservesExistingRowsAndAddsPartyNotesTableOnly() {
        val db67DbName = "migration-test-db-6-7"

        var db = helper.createDatabase(db67DbName, 6)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_parties (companyId, partyId, displayName, classification, primaryPhone, " +
                "primaryPhoneNormalized, primaryEmail, addressLine1, addressCity, addressState, addressPincode, " +
                "gstin, createdAt, updatedAt) VALUES ('acme-001', 'party-1', 'ABC Traders', 'customer', NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db67DbName, 7, false, DatabaseModule.MIGRATION_6_7)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT displayName FROM cached_parties WHERE companyId = 'acme-001' AND partyId = 'party-1'").use { cursor ->
            assertTrue("existing party row must survive the migration", cursor.moveToFirst())
            assertEquals("ABC Traders", cursor.getString(0))
        }

        val columns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`party_notes`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "noteId" to "TEXT", "partyId" to "TEXT", "body" to "TEXT",
                "linkedVoucherId" to "TEXT", "createdAt" to "INTEGER", "updatedAt" to "INTEGER",
            ),
            columns,
        )

        db.execSQL(
            "INSERT INTO party_notes (companyId, noteId, partyId, body, linkedVoucherId, createdAt, updatedAt) " +
                "VALUES ('acme-001', 'note-1', 'party-1', 'Customer says 2 pieces short', 'v-1', " +
                "1736899200000, 1736899200000)",
        )
        db.query(
            "SELECT body FROM party_notes WHERE companyId = 'acme-001' AND partyId = 'party-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Customer says 2 pieces short", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 7 -> 8 migration (adding the single MVP-1.1-D `party_export_events`
     * table) preserves every pre-existing row and never falls back to a destructive recreation.
     */
    @Test
    fun migrate7To8_preservesExistingRowsAndAddsPartyExportEventsTableOnly() {
        val db78DbName = "migration-test-db-7-8"

        var db = helper.createDatabase(db78DbName, 7)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_parties (companyId, partyId, displayName, classification, primaryPhone, " +
                "primaryPhoneNormalized, primaryEmail, addressLine1, addressCity, addressState, addressPincode, " +
                "gstin, createdAt, updatedAt) VALUES ('acme-001', 'party-1', 'ABC Traders', 'customer', NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db78DbName, 8, false, DatabaseModule.MIGRATION_7_8)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT displayName FROM cached_parties WHERE companyId = 'acme-001' AND partyId = 'party-1'").use { cursor ->
            assertTrue("existing party row must survive the migration", cursor.moveToFirst())
            assertEquals("ABC Traders", cursor.getString(0))
        }

        val columns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`party_export_events`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "exportId" to "TEXT", "partyId" to "TEXT", "createdAt" to "INTEGER",
                "outputFileName" to "TEXT", "fieldNamesCsv" to "TEXT",
            ),
            columns,
        )

        db.execSQL(
            "INSERT INTO party_export_events (companyId, exportId, partyId, createdAt, outputFileName, " +
                "fieldNamesCsv) VALUES ('acme-001', 'export-1', 'party-1', 1736899200000, " +
                "'BUDCOM-Tally-Export-ABC-Traders-1.xml', 'primaryEmail,gstin')",
        )
        db.query(
            "SELECT fieldNamesCsv FROM party_export_events WHERE companyId = 'acme-001' AND partyId = 'party-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("primaryEmail,gstin", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 8 -> 9 migration (MVP-1.2-A: typed/due-date/completion/issue-grouping
     * columns on `party_notes`, plus the new `party_issues` table) preserves every pre-existing
     * row and never falls back to a destructive recreation. Critically, this also proves the
     * backward-compatibility claim itself: a `party_notes` row inserted *before* this migration
     * (with none of the four new columns) reads back as `type = 'general'` afterward — the exact
     * guarantee that lets every pre-1.2-A note keep behaving unchanged.
     */
    @Test
    fun migrate8To9_preservesExistingRowsAndAddsTypedNotesAndPartyIssuesTableOnly() {
        val db89DbName = "migration-test-db-8-9"

        var db = helper.createDatabase(db89DbName, 8)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_parties (companyId, partyId, displayName, classification, primaryPhone, " +
                "primaryPhoneNormalized, primaryEmail, addressLine1, addressCity, addressState, addressPincode, " +
                "gstin, createdAt, updatedAt) VALUES ('acme-001', 'party-1', 'ABC Traders', 'customer', NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.execSQL(
            "INSERT INTO party_notes (companyId, noteId, partyId, body, linkedVoucherId, createdAt, updatedAt) " +
                "VALUES ('acme-001', 'note-1', 'party-1', 'Customer says 2 pieces short', NULL, " +
                "1736899200000, 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db89DbName, 9, false, DatabaseModule.MIGRATION_8_9)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT displayName FROM cached_parties WHERE companyId = 'acme-001' AND partyId = 'party-1'").use { cursor ->
            assertTrue("existing party row must survive the migration", cursor.moveToFirst())
            assertEquals("ABC Traders", cursor.getString(0))
        }

        // The backward-compatibility guarantee itself: a note written before this migration, with
        // none of the four new columns, must read back with type backfilled to 'general' and every
        // other new column NULL — never crash, never silently change its body.
        db.query(
            "SELECT body, type, dueAt, completedAt, issueId FROM party_notes " +
                "WHERE companyId = 'acme-001' AND noteId = 'note-1'",
        ).use { cursor ->
            assertTrue("pre-existing note row must survive the migration", cursor.moveToFirst())
            assertEquals("Customer says 2 pieces short", cursor.getString(0))
            assertEquals("general", cursor.getString(1))
            assertTrue("dueAt must backfill to NULL, not 0", cursor.isNull(2))
            assertTrue("completedAt must backfill to NULL", cursor.isNull(3))
            assertTrue("issueId must backfill to NULL", cursor.isNull(4))
        }

        val noteColumns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`party_notes`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                noteColumns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "noteId" to "TEXT", "partyId" to "TEXT", "body" to "TEXT",
                "linkedVoucherId" to "TEXT", "createdAt" to "INTEGER", "updatedAt" to "INTEGER",
                "type" to "TEXT", "dueAt" to "INTEGER", "completedAt" to "INTEGER", "issueId" to "TEXT",
            ),
            noteColumns,
        )

        // A note written *after* the migration, with no type specified at the SQL level, must
        // also backfill to 'general' via the column default — not just pre-existing rows.
        db.execSQL(
            "INSERT INTO party_notes (companyId, noteId, partyId, body, linkedVoucherId, createdAt, updatedAt) " +
                "VALUES ('acme-001', 'note-2', 'party-1', 'Post-migration note', NULL, 1736899300000, 1736899300000)",
        )
        db.query("SELECT type FROM party_notes WHERE companyId = 'acme-001' AND noteId = 'note-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("general", cursor.getString(0))
        }

        val issueColumns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`party_issues`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                issueColumns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "issueId" to "TEXT", "partyId" to "TEXT", "title" to "TEXT",
                "status" to "TEXT", "createdAt" to "INTEGER", "resolvedAt" to "INTEGER", "updatedAt" to "INTEGER",
            ),
            issueColumns,
        )

        db.execSQL(
            "INSERT INTO party_issues (companyId, issueId, partyId, title, status, createdAt, resolvedAt, updatedAt) " +
                "VALUES ('acme-001', 'issue-1', 'party-1', '2 pieces short', 'open', 1736899200000, NULL, 1736899200000)",
        )
        db.query("SELECT title FROM party_issues WHERE companyId = 'acme-001' AND partyId = 'party-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2 pieces short", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 9 -> 10 migration (adding `business_profile` for MVP-1.3-A, PDL-019)
     * preserves every pre-existing row and adds exactly one new, empty table — never a destructive
     * recreation. Also proves the new table is genuinely insert/query-usable and its column set
     * matches the entity definition exactly, the same discipline every migration in this codebase
     * has followed since MIGRATION_5_6.
     */
    @Test
    fun migrate9To10_preservesExistingRowsAndAddsBusinessProfileTableOnly() {
        val db910DbName = "migration-test-db-9-10"

        var db = helper.createDatabase(db910DbName, 9)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_parties (companyId, partyId, displayName, classification, primaryPhone, " +
                "primaryPhoneNormalized, primaryEmail, addressLine1, addressCity, addressState, addressPincode, " +
                "gstin, createdAt, updatedAt) VALUES ('acme-001', 'party-1', 'ABC Traders', 'customer', NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db910DbName, 10, false, DatabaseModule.MIGRATION_9_10)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT displayName FROM cached_parties WHERE companyId = 'acme-001' AND partyId = 'party-1'").use { cursor ->
            assertTrue("existing party row must survive the migration", cursor.moveToFirst())
            assertEquals("ABC Traders", cursor.getString(0))
        }

        val profileColumns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`business_profile`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                profileColumns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
            }
        }
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "tradingName" to "TEXT", "legalName" to "TEXT",
                "addressLine1" to "TEXT", "addressCity" to "TEXT", "addressState" to "TEXT",
                "addressPincode" to "TEXT", "phone" to "TEXT", "phoneNormalized" to "TEXT",
                "email" to "TEXT", "gstin" to "TEXT", "website" to "TEXT", "description" to "TEXT",
                "logoAssetPath" to "TEXT", "createdAt" to "INTEGER", "updatedAt" to "INTEGER",
            ),
            profileColumns,
        )

        // The new table starts genuinely empty -- no fabricated/backfilled profile row for any
        // pre-existing company.
        db.query("SELECT COUNT(*) FROM business_profile").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.execSQL(
            "INSERT INTO business_profile (companyId, tradingName, legalName, addressLine1, addressCity, " +
                "addressState, addressPincode, phone, phoneNormalized, email, gstin, website, description, " +
                "logoAssetPath, createdAt, updatedAt) VALUES ('acme-001', 'Acme Traders', NULL, NULL, NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1736899200000, 1736899200000)",
        )
        db.query("SELECT tradingName FROM business_profile WHERE companyId = 'acme-001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Acme Traders", cursor.getString(0))
        }

        db.close()
    }

    /**
     * Proves the version 10 -> 11 migration (MVP-1.4 Catalogue) preserves every pre-existing row
     * and adds exactly seven new, empty tables — never a destructive recreation. Also proves every
     * new table is genuinely insert/query-usable and its column set matches the entity definition
     * exactly, the same discipline every migration in this codebase has followed since
     * MIGRATION_5_6.
     */
    @Test
    fun migrate10To11_preservesExistingRowsAndAddsCatalogueTablesOnly() {
        val db1011DbName = "migration-test-db-10-11"

        var db = helper.createDatabase(db1011DbName, 10)
        db.execSQL(
            "INSERT INTO cached_companies (id, name, financialYear, booksFrom, baseCurrency) " +
                "VALUES ('acme-001', 'Acme Corp', '2025-26', '2025-04-01', 'INR')",
        )
        db.execSQL(
            "INSERT INTO cached_stock_items (companyId, id, name, alias, parentGroup, category, baseUnit, " +
                "partNumber, hsnCode, gstRate, status, closingAmount, closingCurrencyCode, closingSide, " +
                "dataQuality, syncedAt, dataFreshnessAt) VALUES ('acme-001', 'guid:widget', 'Widget', NULL, " +
                "'Finished Goods', NULL, 'PCS', NULL, NULL, NULL, 'ACTIVE', '10', NULL, NULL, 'GOOD', " +
                "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.close()

        db = helper.runMigrationsAndValidate(db1011DbName, 11, false, DatabaseModule.MIGRATION_10_11)

        db.query("SELECT name FROM cached_companies WHERE id = 'acme-001'").use { cursor ->
            assertTrue("existing company row must survive the migration", cursor.moveToFirst())
            assertEquals("Acme Corp", cursor.getString(0))
        }
        db.query("SELECT name FROM cached_stock_items WHERE companyId = 'acme-001' AND id = 'guid:widget'").use { cursor ->
            assertTrue("existing stock item row must survive the migration", cursor.moveToFirst())
            assertEquals("Widget", cursor.getString(0))
        }

        fun columnsOf(table: String): Map<String, String> {
            val columns = mutableMapOf<String, String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val typeIdx = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx)
                }
            }
            return columns
        }

        assertEquals(
            mapOf(
                "companyId" to "TEXT", "productId" to "TEXT", "source" to "TEXT", "linkedStockItemId" to "TEXT",
                "sku" to "TEXT", "displayNameOverride" to "TEXT", "description" to "TEXT", "specifications" to "TEXT",
                "customerFacingCategory" to "TEXT", "priceDisplayMode" to "TEXT", "manualPriceAmount" to "TEXT",
                "manualPriceCurrencyCode" to "TEXT", "lifecycleState" to "TEXT", "sourceAvailable" to "INTEGER",
                "createdAt" to "INTEGER", "createdAtSource" to "TEXT", "updatedAt" to "INTEGER",
                "updatedAtSource" to "TEXT", "archivedAt" to "INTEGER", "archivedAtSource" to "TEXT",
            ),
            columnsOf("catalogue_product"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "sourceType" to "TEXT", "externalStockItemId" to "TEXT",
                "productId" to "TEXT", "lastConfirmedAt" to "INTEGER", "lastConfirmedAtSource" to "TEXT",
            ),
            columnsOf("catalogue_product_source_link"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "branchId" to "TEXT", "name" to "TEXT", "isActive" to "INTEGER",
                "createdAt" to "INTEGER", "createdAtSource" to "TEXT", "updatedAt" to "INTEGER",
                "updatedAtSource" to "TEXT",
            ),
            columnsOf("catalogue_branch"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "scopeType" to "TEXT", "scopeKey" to "TEXT", "attributeName" to "TEXT",
                "value" to "TEXT", "updatedAt" to "INTEGER", "updatedAtSource" to "TEXT",
            ),
            columnsOf("catalogue_override"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "productId" to "TEXT", "displayName" to "TEXT", "description" to "TEXT",
                "specifications" to "TEXT", "customerFacingCategory" to "TEXT", "priceDisplayMode" to "TEXT",
                "resolvedPriceAmount" to "TEXT", "resolvedPriceCurrencyCode" to "TEXT", "primaryAssetId" to "TEXT",
                "publishedAt" to "INTEGER", "publishedAtSource" to "TEXT",
            ),
            columnsOf("catalogue_published_snapshot"),
        )
        assertEquals(
            mapOf(
                "companyId" to "TEXT", "productId" to "TEXT", "assetId" to "TEXT", "isPrimary" to "INTEGER",
                "sortOrder" to "INTEGER", "filePath" to "TEXT", "createdAt" to "INTEGER", "createdAtSource" to "TEXT",
            ),
            columnsOf("catalogue_asset"),
        )
        assertEquals(
            mapOf("companyId" to "TEXT", "isPublic" to "INTEGER", "updatedAt" to "INTEGER", "updatedAtSource" to "TEXT"),
            columnsOf("catalogue_settings"),
        )

        // Every new table starts genuinely empty -- no fabricated/backfilled row for any
        // pre-existing company or stock item.
        listOf(
            "catalogue_product", "catalogue_product_source_link", "catalogue_branch",
            "catalogue_override", "catalogue_published_snapshot", "catalogue_asset", "catalogue_settings",
        ).forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("$table must start empty", 0, cursor.getInt(0))
            }
        }

        // Every new table is genuinely usable -- insert and read back a real row, including the
        // source-link natural key that resolves a Stock Item to its Catalogue Product.
        db.execSQL(
            "INSERT INTO catalogue_product (companyId, productId, source, linkedStockItemId, sku, " +
                "displayNameOverride, description, specifications, customerFacingCategory, priceDisplayMode, " +
                "manualPriceAmount, manualPriceCurrencyCode, lifecycleState, sourceAvailable, createdAt, " +
                "createdAtSource, updatedAt, updatedAtSource, archivedAt, archivedAtSource) VALUES " +
                "('acme-001', 'prod-1', 'TALLY', 'guid:widget', NULL, NULL, 'A fine widget', NULL, NULL, " +
                "'OPEN', NULL, NULL, 'DRAFT', 1, 1736899200000, 'DEVICE_LOCAL_PROVISIONAL', 1736899200000, " +
                "'DEVICE_LOCAL_PROVISIONAL', NULL, NULL)",
        )
        db.query(
            "SELECT description FROM catalogue_product WHERE companyId = 'acme-001' AND productId = 'prod-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A fine widget", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO catalogue_product_source_link (companyId, sourceType, externalStockItemId, productId, " +
                "lastConfirmedAt, lastConfirmedAtSource) VALUES ('acme-001', 'tally_stock_item', 'guid:widget', " +
                "'prod-1', 1736899200000, 'DEVICE_LOCAL_PROVISIONAL')",
        )
        db.query(
            "SELECT productId FROM catalogue_product_source_link WHERE companyId = 'acme-001' " +
                "AND sourceType = 'tally_stock_item' AND externalStockItemId = 'guid:widget'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("prod-1", cursor.getString(0))
        }

        db.close()
    }
}
