import fs from 'node:fs';
import path from 'node:path';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { DatabaseSync } from './node-sqlite.js';
import { nodeSqlite } from './node-sqlite.js';
import { MIGRATION_001, MIGRATION_002, MIGRATION_003, STOCK_ITEM_COLUMN_UPGRADES, STORAGE_SCHEMA_VERSION } from './schema.js';
export interface SqliteDatabaseOptions {
  readonly databasePath: string;
  readonly readonly?: boolean;
}

export class SqliteDatabase {
  private db: DatabaseSync | null = null;

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
