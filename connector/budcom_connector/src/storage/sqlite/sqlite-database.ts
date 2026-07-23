import fs from 'node:fs';
import path from 'node:path';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { DatabaseSync } from './node-sqlite.js';
import { nodeSqlite } from './node-sqlite.js';
import { MIGRATION_001, STORAGE_SCHEMA_VERSION } from './schema.js';
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
    let appliedVersion: number | undefined;
    try {
      const applied = db
        .prepare('SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1')
        .get() as { version: number } | undefined;
      appliedVersion = applied?.version;
    } catch {
      appliedVersion = undefined;
    }

    if (appliedVersion === STORAGE_SCHEMA_VERSION) {
      return;
    }

    db.exec('BEGIN IMMEDIATE;');
    try {
      db.exec(MIGRATION_001);
      db.prepare('INSERT OR REPLACE INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
        STORAGE_SCHEMA_VERSION,
        new Date().toISOString(),
      );
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
}
