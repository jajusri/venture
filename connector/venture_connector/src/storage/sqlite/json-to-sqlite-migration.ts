import fs from 'node:fs';
import path from 'node:path';

import type { LedgerDetails } from '../../erp/ledger/ledger-domain.js';
import type { LedgerRepositoryPort } from '../../services/ledger/ledger-repository.interface.js';
import type { SqliteDatabase } from './sqlite-database.js';

export interface JsonMigrationReport {
  readonly status: 'none' | 'pending' | 'completed' | 'failed';
  readonly companies: readonly JsonCompanyMigrationResult[];
  readonly startedAt: string;
  readonly completedAt: string | null;
}

export interface JsonCompanyMigrationResult {
  readonly companyId: string;
  readonly status: 'completed' | 'failed' | 'skipped';
  readonly importedCount: number;
  readonly sourcePath: string;
  readonly backupPath: string | null;
  readonly errorMessage: string | null;
}

interface LegacyStore {
  readonly companyId: string;
  readonly ledgers: Record<string, LedgerDetails>;
  readonly updatedAt?: string;
}

export class JsonToSqliteMigrationService {
  constructor(
    private readonly database: SqliteDatabase,
    private readonly repository: LedgerRepositoryPort,
    private readonly legacyBasePath: string,
    private readonly backupBasePath: string,
  ) {}

  migrateIfNeeded(): JsonMigrationReport {
    const startedAt = new Date().toISOString();
    const db = this.database.getDatabase();
    const completed = db.prepare("SELECT value FROM storage_meta WHERE key = 'json_migration_completed'").get() as
      | { value: string }
      | undefined;
    if (completed?.value === 'true') {
      return { status: 'completed', companies: [], startedAt, completedAt: startedAt };
    }

    if (!fs.existsSync(this.legacyBasePath)) {
      return { status: 'none', companies: [], startedAt, completedAt: startedAt };
    }

    const files = fs
      .readdirSync(this.legacyBasePath)
      .filter((file) => file.endsWith('.json') && isSafeLegacyFileName(file));
    if (files.length === 0) {
      return { status: 'none', companies: [], startedAt, completedAt: startedAt };
    }

    fs.mkdirSync(this.backupBasePath, { recursive: true });
    const companies: JsonCompanyMigrationResult[] = [];

    for (const file of files) {
      const sourcePath = resolveLegacyFile(this.legacyBasePath, file);
      if (!sourcePath) {
        continue;
      }
      const companyId = file.replace(/\.json$/, '');
      const existing = db
        .prepare('SELECT status FROM json_migration_runs WHERE company_id = ?')
        .get(companyId) as { status: string } | undefined;
      if (existing?.status === 'completed') {
        companies.push({
          companyId,
          status: 'skipped',
          importedCount: 0,
          sourcePath,
          backupPath: null,
          errorMessage: null,
        });
        continue;
      }

      db.prepare(
        `INSERT OR REPLACE INTO json_migration_runs
         (company_id, status, source_path, backup_path, imported_count, started_at, completed_at, error_message)
         VALUES (?, 'running', ?, NULL, 0, ?, NULL, NULL)`,
      ).run(companyId, sourcePath, new Date().toISOString());

      try {
        const parsed = JSON.parse(fs.readFileSync(sourcePath, 'utf8')) as LegacyStore;
        const ledgers = Object.values(parsed.ledgers ?? {});
        void this.repository.upsertMany(parsed.companyId ?? companyId, ledgers);
        const importedCount = ledgers.length;
        const sqliteCount = db
          .prepare('SELECT COUNT(*) AS total FROM ledgers WHERE company_id = ?')
          .get(parsed.companyId ?? companyId) as { total: number };
        if (sqliteCount.total < importedCount) {
          throw new Error(`Row count mismatch after import (${sqliteCount.total}/${importedCount}).`);
        }

        const backupPath = path.join(this.backupBasePath, path.basename(file));
        fs.copyFileSync(sourcePath, backupPath);
        db.prepare(
          `UPDATE json_migration_runs SET status = 'completed', imported_count = ?, backup_path = ?, completed_at = ?
           WHERE company_id = ?`,
        ).run(importedCount, backupPath, new Date().toISOString(), companyId);

        companies.push({
          companyId,
          status: 'completed',
          importedCount,
          sourcePath,
          backupPath,
          errorMessage: null,
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        db.prepare(
          `UPDATE json_migration_runs SET status = 'failed', completed_at = ?, error_message = ? WHERE company_id = ?`,
        ).run(new Date().toISOString(), message, companyId);
        companies.push({
          companyId,
          status: 'failed',
          importedCount: 0,
          sourcePath,
          backupPath: null,
          errorMessage: message,
        });
      }
    }

    const failed = companies.some((company) => company.status === 'failed');
    const status = failed ? 'failed' : 'completed';
    if (!failed) {
      db.prepare("INSERT OR REPLACE INTO storage_meta (key, value) VALUES ('json_migration_completed', 'true')").run();
    }

    return {
      status,
      companies,
      startedAt,
      completedAt: new Date().toISOString(),
    };
  }
}

function isSafeLegacyFileName(file: string): boolean {
  const baseName = path.basename(file);
  return baseName === file && !baseName.includes('..') && /^[a-zA-Z0-9._-]+\.json$/.test(baseName);
}

function resolveLegacyFile(basePath: string, file: string): string | null {
  if (!isSafeLegacyFileName(file)) {
    return null;
  }
  const resolved = path.resolve(basePath, file);
  const resolvedBase = path.resolve(basePath);
  if (!resolved.startsWith(resolvedBase + path.sep) && resolved !== resolvedBase) {
    return null;
  }
  return resolved;
}
