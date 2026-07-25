import fs from 'node:fs';
import path from 'node:path';
import { AsyncLocalStorage } from 'node:async_hooks';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { DatabaseSync } from './node-sqlite.js';
import { nodeSqlite } from './node-sqlite.js';
import {
  LEDGER_COLUMN_UPGRADES,
  MIGRATION_001,
  MIGRATION_002,
  MIGRATION_003,
  MIGRATION_004,
  MIGRATION_005,
  MIGRATION_006,
  MIGRATION_007,
  MIGRATION_008,
  STOCK_ITEM_COLUMN_UPGRADES,
  STORAGE_SCHEMA_VERSION,
} from './schema.js';
export interface SqliteDatabaseOptions {
  readonly databasePath: string;
  readonly readonly?: boolean;
}

interface TransactionStore {
  readonly id: symbol;
}

export class SqliteDatabase {
  private db: DatabaseSync | null = null;
  /**
   * Outermost transaction callbacks on this connection are serialized via an
   * async mutex. Nested joins are scoped with AsyncLocalStorage so unrelated
   * async callers never observe another job's ambient transaction.
   *
   * The BEGIN/COMMIT body is synchronous: node:sqlite DatabaseSync does not
   * allow awaiting between BEGIN and COMMIT on the same connection.
   */
  private static readonly transactionAls = new AsyncLocalStorage<TransactionStore>();
  private transactionMutex: Promise<void> = Promise.resolve();
  private mutexHeld = false;
  private concurrencyTestHook: (() => Promise<void>) | null = null;

  constructor(private readonly options: SqliteDatabaseOptions) {}

  open(): DatabaseSync {
    if (this.db) {
      return this.db;
    }

    const dir = path.dirname(this.options.databasePath);
    fs.mkdirSync(dir, { recursive: true });

    try {
      this.db = new nodeSqlite.DatabaseSync(this.options.databasePath, {
        readOnly: this.options.readonly ?? false,
      });
      this.db.exec('PRAGMA foreign_keys = ON;');
      this.db.exec('PRAGMA journal_mode = WAL;');
      this.db.exec('PRAGMA synchronous = NORMAL;');
      this.db.exec('PRAGMA busy_timeout = 5000;');
      this.runMigrations();
      return this.db;
    } catch (error) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Unable to open local SQLite database. The storage file may be corrupted or inaccessible.',
        503,
        { cause: error instanceof Error ? error.message : String(error) },
      );
    }
  }

  getDatabase(): DatabaseSync {
    return this.open();
  }

  close(): void {
    if (this.db) {
      this.db.close();
      this.db = null;
    }
  }

  isOpen(): boolean {
    return this.db !== null;
  }

  /**
   * Testing only: awaited after mutex acquisition and before BEGIN.
   * Used to prove unrelated callers queue instead of joining.
   */
  setConcurrencyTestHook(hook: (() => Promise<void>) | null): void {
    this.concurrencyTestHook = hook;
  }

  /**
   * True only inside the current async execution's owning transaction callback.
   * Unrelated concurrent callers must not see another job's transaction as active.
   */
  isInTransaction(): boolean {
    return SqliteDatabase.transactionAls.getStore() !== undefined;
  }

  /**
   * Runs work in a single BEGIN IMMEDIATE / COMMIT boundary.
   * Outermost callbacks are serialized on this connection (async mutex).
   * Nested calls from the same async context join the owner (no nested BEGIN).
   * Unrelated async callers never join; they wait for the owner to finish.
   *
   * `fn` must be synchronous: DatabaseSync transactions cannot span awaits.
   */
  async runInTransaction<T>(fn: () => T): Promise<T> {
    if (SqliteDatabase.transactionAls.getStore()) {
      return fn();
    }

    let release!: () => void;
    const previous = this.transactionMutex;
    this.transactionMutex = new Promise<void>((resolve) => {
      release = resolve;
    });
    await previous;

    this.mutexHeld = true;
    try {
      if (this.concurrencyTestHook) {
        await this.concurrencyTestHook();
      }
      return this.executeSynchronousTransaction(fn);
    } finally {
      this.mutexHeld = false;
      release();
    }
  }

  /**
   * Synchronous transaction for standalone repository writes (e.g. migration).
   * Joins an ambient ALS transaction when present.
   * Refuses to start while an async transaction slot is held, so SQL cannot
   * interleave on the shared DatabaseSync connection.
   */
  runInTransactionSync<T>(fn: () => T): T {
    if (SqliteDatabase.transactionAls.getStore()) {
      return fn();
    }
    if (this.mutexHeld) {
      throw new Error(
        'Cannot start a synchronous SQLite transaction while another async transaction holds this connection.',
      );
    }
    return this.executeSynchronousTransaction(fn);
  }

  private executeSynchronousTransaction<T>(fn: () => T): T {
    const db = this.getDatabase();
    const store: TransactionStore = { id: Symbol('sqlite-tx') };
    return SqliteDatabase.transactionAls.run(store, () => {
      db.exec('BEGIN IMMEDIATE');
      try {
        const result = fn();
        db.exec('COMMIT');
        return result;
      } catch (error) {
        try {
          db.exec('ROLLBACK');
        } catch {
          // Connection may already be aborted.
        }
        throw error;
      }
    });
  }

  private runMigrations(): void {
    const db = this.db!;
    let currentVersion = 0;
    try {
      const applied = db
        .prepare('SELECT MAX(version) AS version FROM schema_migrations')
        .get() as { version: number | null } | undefined;
      currentVersion = applied?.version ?? 0;
    } catch {
      currentVersion = 0;
    }

    if (currentVersion >= STORAGE_SCHEMA_VERSION) {
      return;
    }

    db.exec('BEGIN IMMEDIATE;');
    try {
      const now = new Date().toISOString();
      if (currentVersion < 1) {
        db.exec(MIGRATION_001);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(1, now);
      }
      if (currentVersion < 2) {
        db.exec(MIGRATION_002);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(2, now);
      }
      if (currentVersion < 3) {
        this.ensureStockItemColumns(db);
        db.exec(MIGRATION_003);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(3, now);
      }
      if (currentVersion < 4) {
        db.exec(MIGRATION_004);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(4, now);
      }
      if (currentVersion < 5) {
        this.ensureLedgerColumns(db);
        db.exec(MIGRATION_005);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(5, now);
      }
      if (currentVersion < 6) {
        db.exec(MIGRATION_006);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(6, now);
      }
      if (currentVersion < 7) {
        this.ensureXmlImportAttemptReservationColumn(db);
        db.exec(MIGRATION_007);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(7, now);
      }
      if (currentVersion < 8) {
        db.exec(MIGRATION_008);
        db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(8, now);
      }
      db.exec('COMMIT;');
    } catch (error) {
      db.exec('ROLLBACK;');
      throw new AppError(
        ErrorCodes.INTERNAL_ERROR,
        'SQLite schema migration failed and was rolled back.',
        500,
        { cause: error instanceof Error ? error.message : String(error) },
      );
    }
  }

  private ensureXmlImportAttemptReservationColumn(db: DatabaseSync): void {
    const tableExists = db
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'xml_import_attempts'")
      .get() as { name: string } | undefined;
    if (!tableExists) {
      return;
    }
    const columns = new Set(
      (db.prepare('PRAGMA table_info(xml_import_attempts)').all() as Array<{ name: string }>).map(
        (row) => row.name,
      ),
    );
    if (!columns.has('reservation_status')) {
      db.exec(
        "ALTER TABLE xml_import_attempts ADD COLUMN reservation_status TEXT NOT NULL DEFAULT 'released'",
      );
    }
  }

  private ensureLedgerColumns(db: DatabaseSync): void {
    const tableExists = db
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'ledgers'")
      .get() as { name: string } | undefined;
    if (!tableExists) {
      return;
    }
    const columns = new Set(
      (db.prepare('PRAGMA table_info(ledgers)').all() as Array<{ name: string }>).map((row) => row.name),
    );
    for (const upgrade of LEDGER_COLUMN_UPGRADES) {
      if (!columns.has(upgrade.name)) {
        db.exec(upgrade.ddl);
      }
    }
  }

  private ensureStockItemColumns(db: DatabaseSync): void {
    const tableExists = db
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'stock_items'")
      .get() as { name: string } | undefined;
    if (!tableExists) {
      return;
    }
    const columns = new Set(
      (db.prepare('PRAGMA table_info(stock_items)').all() as Array<{ name: string }>).map((row) => row.name),
    );
    for (const upgrade of STOCK_ITEM_COLUMN_UPGRADES) {
      if (!columns.has(upgrade.name)) {
        db.exec(upgrade.ddl);
      }
    }
  }
}
