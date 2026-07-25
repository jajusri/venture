import fs from 'node:fs';
import path from 'node:path';

import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ServiceStatus } from '../../core/types.js';
import type { StorageStatus } from '../../erp/ledger/ledger-domain.js';
import type { LocalDatabaseService } from '../../services/interfaces/local-database.js';
import type { LedgerRepositoryPort } from '../../services/ledger/ledger-repository.interface.js';
import type { StockItemRepositoryPort } from '../../services/stock-item/stock-item-repository.interface.js';
import { JsonToSqliteMigrationService, type JsonMigrationReport } from './json-to-sqlite-migration.js';
import { nodeSqlite } from './node-sqlite.js';
import { SqliteLedgerRepository } from './sqlite-ledger-repository.js';
import { SqliteStockItemRepository } from './sqlite-stock-item-repository.js';
import { SqliteDatabase } from './sqlite-database.js';
import { SyncRunRepository } from './sync-run-repository.js';
import { SyncRunRetentionService } from './sync-run-retention-service.js';
import { STORAGE_SCHEMA_VERSION } from './schema.js';

export interface LedgerStorageBundle {
  readonly database: SqliteDatabase;
  readonly ledgerRepository: LedgerRepositoryPort;
  readonly stockItemRepository: StockItemRepositoryPort;
  readonly syncRunRepository: SyncRunRepository;
  readonly migrationReport: JsonMigrationReport;
}

export class SqliteStorageService implements LocalDatabaseService {
  private running = false;
  private bundle: LedgerStorageBundle | null = null;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;
    const databasePath = path.join(this.config.databasePath, 'budcom-ledger.db');
    const database = new SqliteDatabase({ databasePath });
    database.open();
    const ledgerRepository = new SqliteLedgerRepository(database);
    const stockItemRepository = new SqliteStockItemRepository(database);
    const syncRunRepository = new SyncRunRepository(database);
    const migration = new JsonToSqliteMigrationService(
      database,
      ledgerRepository,
      path.join(this.config.databasePath, 'ledgers'),
      path.join(this.config.databasePath, 'ledgers-backup'),
    );
    const migrationReport = migration.migrateIfNeeded();
    const recovered = syncRunRepository.recoverAllAbandonedRuns();
    if (recovered > 0) {
      this.logger.info('sqlite_abandoned_sync_runs_recovered', {
        component: 'sqlite-storage',
        recoveredCount: recovered,
      });
    }
    const retention = new SyncRunRetentionService(
      syncRunRepository,
      {
        maxCount: this.config.syncRunHistoryMaxCount,
        maxAgeDays: this.config.syncRunHistoryMaxAgeDays,
      },
      this.logger,
    );
    retention.prune();
    this.bundle = { database, ledgerRepository, stockItemRepository, syncRunRepository, migrationReport };
    this.running = true;
    this.logger.info('sqlite_storage_started', {
      component: 'sqlite-storage',
      migrationStatus: migrationReport.status,
    });
  }

  async stop(): Promise<void> {
    this.bundle?.database.close();
    this.bundle = null;
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'LocalDatabase',
      running: this.running,
      ready: this.running,
      message: this.bundle ? 'SQLite ready' : 'Stopped',
    };
  }

  getBundle(): LedgerStorageBundle {
    if (!this.bundle) {
      throw new Error('SQLite storage is not running.');
    }
    return this.bundle;
  }

  /**
   * Runs work in one SQLite transaction on the shared connection used by
   * ledger/stock repositories and SyncRunRepository.
   * Used to commit domain upserts with their sync-run checkpoint atomically.
   * Outermost callbacks are serialized; nested joins are ALS-scoped.
   */
  runInTransaction<T>(fn: () => T): Promise<T> {
    return this.getBundle().database.runInTransaction(fn);
  }

  getStorageStatus(): StorageStatus {
    if (!this.bundle) {
      return {
        backend: 'sqlite',
        schemaVersion: STORAGE_SCHEMA_VERSION,
        databaseHealthy: false,
        migrationStatus: 'none',
        message: 'Storage is not running.',
      };
    }
    try {
      const row = this.bundle.database.getDatabase().prepare('PRAGMA integrity_check').get() as
        | { integrity_check: string }
        | undefined;
      const healthy = row?.integrity_check === 'ok';
      return {
        backend: 'sqlite',
        schemaVersion: STORAGE_SCHEMA_VERSION,
        databaseHealthy: healthy,
        migrationStatus: this.bundle.migrationReport.status,
        message: healthy ? null : (row?.integrity_check ?? 'integrity_check failed'),
      };
    } catch (error) {
      return {
        backend: 'sqlite',
        schemaVersion: STORAGE_SCHEMA_VERSION,
        databaseHealthy: false,
        migrationStatus: this.bundle.migrationReport.status,
        message: error instanceof Error ? error.message : String(error),
      };
    }
  }

  runIntegrityCheck(): { ok: boolean; message: string } {
    const db = this.bundle?.database.getDatabase();
    if (!db) {
      return { ok: false, message: 'Storage is not running.' };
    }
    const row = db.prepare('PRAGMA integrity_check').get() as { integrity_check: string };
    const ok = row.integrity_check === 'ok';
    return { ok, message: row.integrity_check };
  }

  createBackup(targetDir: string): { ok: boolean; backupPath: string | null; message: string } {
    if (!this.bundle) {
      return { ok: false, backupPath: null, message: 'Storage is not running.' };
    }
    try {
      const db = this.bundle.database.getDatabase();
      db.exec('PRAGMA wal_checkpoint(FULL)');
      fs.mkdirSync(targetDir, { recursive: true });
      const source = path.join(this.config.databasePath, 'budcom-ledger.db');
      const backupFileName = `budcom-ledger-${Date.now()}.db`;
      const backupPath = path.join(targetDir, backupFileName);
      fs.copyFileSync(source, backupPath);
      const probe = new nodeSqlite.DatabaseSync(backupPath, { readOnly: true });
      try {
        const integrity = probe.prepare('PRAGMA integrity_check').get() as { integrity_check: string };
        if (integrity.integrity_check !== 'ok') {
          fs.rmSync(backupPath, { force: true });
          return {
            ok: false,
            backupPath: null,
            message: `Backup integrity check failed: ${integrity.integrity_check}`,
          };
        }
      } finally {
        probe.close();
      }
      for (const suffix of ['-wal', '-shm']) {
        const sidecar = `${backupPath}${suffix}`;
        if (fs.existsSync(sidecar)) {
          fs.rmSync(sidecar, { force: true });
        }
      }
      return { ok: true, backupPath: backupFileName, message: 'Database backup created.' };
    } catch (error) {
      return {
        ok: false,
        backupPath: null,
        message: error instanceof Error ? error.message : String(error),
      };
    }
  }
}
