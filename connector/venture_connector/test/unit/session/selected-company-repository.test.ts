import { afterEach, describe, expect, it } from 'vitest';

import { createLogger, type StructuredLogEntry } from '../../../src/infrastructure/logging/logger.js';
import { SelectedCompanyRepository } from '../../../src/services/session/selected-company-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

function testLogger(): { logger: ReturnType<typeof createLogger>; entries: StructuredLogEntry[] } {
  const entries: StructuredLogEntry[] = [];
  const logger = createLogger({ service: 'test', level: 'debug', sink: (entry) => entries.push(entry) });
  return { logger, entries };
}

describe('SelectedCompanyRepository (TD-013)', () => {
  afterEach(async () => {
    await cleanupTestSqliteStorage();
  });

  it('returns null when nothing has ever been persisted', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    expect(repository.load()).toBeNull();
  });

  it('persists a selection and loads it back with the same identity and timestamp', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    repository.save({ id: 'estimation', name: 'ESTIMATION' }, '2026-08-08T00:00:00.000Z');
    const loaded = repository.load();

    expect(loaded).toEqual({
      company: { id: 'estimation', name: 'ESTIMATION' },
      selectedAt: '2026-08-08T00:00:00.000Z',
    });
  });

  it('a second save replaces the first persisted selection rather than appending', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    repository.save({ id: 'estimation', name: 'ESTIMATION' }, '2026-08-08T00:00:00.000Z');
    repository.save({ id: 'acme', name: 'Acme Traders' }, '2026-08-08T01:00:00.000Z');

    expect(repository.load()).toEqual({
      company: { id: 'acme', name: 'Acme Traders' },
      selectedAt: '2026-08-08T01:00:00.000Z',
    });
  });

  it('clear() removes a persisted selection', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    repository.save({ id: 'estimation', name: 'ESTIMATION' }, '2026-08-08T00:00:00.000Z');
    repository.clear();

    expect(repository.load()).toBeNull();
  });

  it('a new repository instance sharing the same database sees a prior save (restart continuity)', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const writer = new SelectedCompanyRepository(() => storage.getBundle().database, logger);
    writer.save({ id: 'estimation', name: 'ESTIMATION' }, '2026-08-08T00:00:00.000Z');

    const reader = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    expect(reader.load()).toEqual({
      company: { id: 'estimation', name: 'ESTIMATION' },
      selectedAt: '2026-08-08T00:00:00.000Z',
    });
  });

  it('fails safe (returns null, logs a non-sensitive reason, never throws) when persisted value is not valid JSON', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger, entries } = testLogger();
    const db = storage.getBundle().database.getDatabase();
    db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(
      'selected_company',
      'not-json{{{',
    );
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    expect(repository.load()).toBeNull();
    const warning = entries.find((entry) => entry.message === 'selected_company_state_unavailable');
    expect(warning).toBeDefined();
    expect(JSON.stringify(warning)).not.toContain('estimation');
  });

  it('fails safe when persisted JSON is well-formed but missing required fields', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger, entries } = testLogger();
    const db = storage.getBundle().database.getDatabase();
    db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(
      'selected_company',
      JSON.stringify({ version: 1, id: 'estimation' }),
    );
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    expect(repository.load()).toBeNull();
    expect(entries.some((entry) => entry.message === 'selected_company_state_corrupt')).toBe(true);
  });

  it('fails safe on an unrecognized schema version instead of trusting stale data', async () => {
    const { storage } = await createTestSqliteStorage();
    const { logger } = testLogger();
    const db = storage.getBundle().database.getDatabase();
    db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(
      'selected_company',
      JSON.stringify({ version: 99, id: 'estimation', name: 'ESTIMATION', selectedAt: '2026-08-08T00:00:00.000Z' }),
    );
    const repository = new SelectedCompanyRepository(() => storage.getBundle().database, logger);

    expect(repository.load()).toBeNull();
  });

  it('load/save/clear never throw when the database is unavailable, and a reason is logged', async () => {
    const { logger, entries } = testLogger();
    const repository = new SelectedCompanyRepository(() => {
      throw new Error('database not started');
    }, logger);

    expect(repository.load()).toBeNull();
    expect(() => repository.save({ id: 'estimation', name: 'ESTIMATION' }, '2026-08-08T00:00:00.000Z')).not.toThrow();
    expect(() => repository.clear()).not.toThrow();
    expect(entries.length).toBeGreaterThanOrEqual(3);
    expect(entries.every((entry) => !JSON.stringify(entry).includes('estimation'))).toBe(true);
  });
});
