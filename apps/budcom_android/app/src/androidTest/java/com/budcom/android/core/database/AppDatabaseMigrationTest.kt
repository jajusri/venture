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
}
