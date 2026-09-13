import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { sampleLedgerDetails } from '../../helpers/ledger-fixtures.js';
import { JsonToSqliteMigrationService } from '../../../src/storage/sqlite/json-to-sqlite-migration.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import { SqliteLedgerRepository } from '../../../src/storage/sqlite/sqlite-ledger-repository.js';
import { cleanupTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

const tempDirs: string[] = [];

const openDatabases: import('../../../src/storage/sqlite/sqlite-database.js').SqliteDatabase[] = [];

afterEach(async () => {
  await cleanupTestSqliteStorage();
  for (const db of openDatabases.splice(0)) {
    db.close();
  }
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // ignore Windows file lock cleanup races
    }
  }
});

function sampleLedger(id: string, name: string) {
  return sampleLedgerDetails({ id, name, normalizedName: name.toLowerCase(), identitySource: 'name' });
}

function createMigrationFixture(): {
  readonly basePath: string;
  readonly legacyPath: string;
  readonly backupPath: string;
  readonly dbPath: string;
} {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-json-migrate-'));
  tempDirs.push(basePath);
  const legacyPath = path.join(basePath, 'ledgers');
  const backupPath = path.join(basePath, 'ledgers-backup');
  fs.mkdirSync(legacyPath, { recursive: true });
  fs.writeFileSync(
    path.join(legacyPath, 'company-1.json'),
    JSON.stringify({
      companyId: 'company-1',
      ledgers: {
        cash: sampleLedger('cash', 'Cash'),
        bank: sampleLedger('bank', 'Bank'),
      },
      updatedAt: '2026-01-01T00:00:00.000Z',
    }),
  );
  return {
    basePath,
    legacyPath,
    backupPath,
    dbPath: path.join(basePath, 'venture-ledger.db'),
  };
}

describe('JsonToSqliteMigrationService', () => {
  it('returns none when no legacy JSON exists', () => {
    const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-empty-migrate-'));
    tempDirs.push(basePath);
    const db = new SqliteDatabase({ databasePath: path.join(basePath, 'venture-ledger.db') });
    openDatabases.push(db);
    const repository = new SqliteLedgerRepository(db);
    const migration = new JsonToSqliteMigrationService(
      db,
      repository,
      path.join(basePath, 'ledgers'),
      path.join(basePath, 'ledgers-backup'),
    );
    const report = migration.migrateIfNeeded();
    expect(report.status).toBe('none');
  });

  it('imports legacy JSON and marks migration complete', () => {
    const fixture = createMigrationFixture();
    const db = new SqliteDatabase({ databasePath: fixture.dbPath });
    openDatabases.push(db);
    const repository = new SqliteLedgerRepository(db);
    const migration = new JsonToSqliteMigrationService(
      db,
      repository,
      fixture.legacyPath,
      fixture.backupPath,
    );
    const report = migration.migrateIfNeeded();
    expect(report.status).toBe('completed');
    expect(report.companies[0]?.importedCount).toBe(2);
    expect(fs.existsSync(path.join(fixture.backupPath, 'company-1.json'))).toBe(true);

    const rerun = migration.migrateIfNeeded();
    expect(rerun.status).toBe('completed');
    expect(rerun.companies).toHaveLength(0);
  });

  it('skips duplicate company import on resume', () => {
    const fixture = createMigrationFixture();
    const db = new SqliteDatabase({ databasePath: fixture.dbPath });
    openDatabases.push(db);
    const repository = new SqliteLedgerRepository(db);
    const migration = new JsonToSqliteMigrationService(
      db,
      repository,
      fixture.legacyPath,
      fixture.backupPath,
    );
    migration.migrateIfNeeded();
    db.getDatabase().prepare("DELETE FROM storage_meta WHERE key = 'json_migration_completed'").run();
    const resume = migration.migrateIfNeeded();
    expect(resume.companies[0]?.status).toBe('skipped');
  });

  it('records failure for malformed JSON without deleting source', () => {
    const fixture = createMigrationFixture();
    fs.writeFileSync(path.join(fixture.legacyPath, 'bad-co.json'), '{not-json');
    const db = new SqliteDatabase({ databasePath: fixture.dbPath });
    openDatabases.push(db);
    const repository = new SqliteLedgerRepository(db);
    const migration = new JsonToSqliteMigrationService(
      db,
      repository,
      fixture.legacyPath,
      fixture.backupPath,
    );
    const report = migration.migrateIfNeeded();
    expect(report.status).toBe('failed');
    expect(report.companies.some((company) => company.status === 'failed')).toBe(true);
    expect(fs.existsSync(path.join(fixture.legacyPath, 'bad-co.json'))).toBe(true);
  });

  it('ignores legacy JSON filenames with traversal or unsafe characters', () => {
    const fixture = createMigrationFixture();
    fs.writeFileSync(path.join(fixture.legacyPath, '..escape.json'), '{}');
    fs.writeFileSync(
      path.join(fixture.legacyPath, 'safe-co.json'),
      JSON.stringify({
        companyId: 'safe-co',
        ledgers: {
          cash: sampleLedger('cash', 'Cash'),
        },
        updatedAt: '2026-01-01T00:00:00.000Z',
      }),
    );
    const db = new SqliteDatabase({ databasePath: fixture.dbPath });
    openDatabases.push(db);
    const repository = new SqliteLedgerRepository(db);
    const migration = new JsonToSqliteMigrationService(
      db,
      repository,
      fixture.legacyPath,
      fixture.backupPath,
    );
    const report = migration.migrateIfNeeded();
    expect(report.companies.some((company) => company.companyId.includes('escape'))).toBe(false);
    expect(report.companies.some((company) => company.companyId === 'safe-co')).toBe(true);
  });
});
