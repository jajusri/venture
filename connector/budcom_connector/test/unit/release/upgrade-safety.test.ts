import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { assessUpgradeSafety } from '../../../src/release/upgrade-safety.js';
import { isAutoUpdatePermitted, ReleaseMode, resolveConnectorReleaseMode } from '../../../src/release/release-mode.js';
import { STORAGE_SCHEMA_VERSION } from '../../../src/storage/sqlite/schema.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';

describe('upgrade safety contract', () => {
  it('reports current schema startup', () => {
    expect(assessUpgradeSafety(STORAGE_SCHEMA_VERSION).kind).toBe('current');
  });

  it('reports valid upgrade path', () => {
    expect(assessUpgradeSafety(0).kind).toBe('upgrade_required');
  });

  it('fails closed on future schema version', () => {
    const outcome = assessUpgradeSafety(STORAGE_SCHEMA_VERSION + 1);
    expect(outcome.kind).toBe('future_schema');
  });

  it('blocks downgrade incompatibility semantics', () => {
    const outcome = assessUpgradeSafety(STORAGE_SCHEMA_VERSION + 5, STORAGE_SCHEMA_VERSION);
    expect(outcome.kind).toBe('future_schema');
  });
});

describe('connector release mode', () => {
  it('rejects unknown release mode', () => {
    expect(() => resolveConnectorReleaseMode('invalid')).toThrow(/Unknown release mode/);
  });

  it('disables auto-update in controlled pilot', () => {
    expect(isAutoUpdatePermitted(ReleaseMode.ControlledPilot)).toBe(false);
  });
});

describe('sqlite migration safety integration', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  it('rejects database with future schema version', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-schema-future-'));
    tempDirs.push(dir);
    const dbPath = path.join(dir, 'budcom-ledger.db');
    const db = new SqliteDatabase({ databasePath: dbPath });
    db.open();
    db.getDatabase().prepare(
      'INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)',
    ).run(STORAGE_SCHEMA_VERSION + 3, new Date().toISOString());
    db.close();

    const reopened = new SqliteDatabase({ databasePath: dbPath });
    expect(() => reopened.open()).toThrow(/newer than this connector supports/);
    reopened.close();
  });

  it('reopens idempotently after successful migration baseline', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-schema-current-'));
    tempDirs.push(dir);
    const dbPath = path.join(dir, 'budcom-ledger.db');
    const first = new SqliteDatabase({ databasePath: dbPath });
    first.open();
    first.close();
    const second = new SqliteDatabase({ databasePath: dbPath });
    expect(() => second.open()).not.toThrow();
    second.close();
  });
});
