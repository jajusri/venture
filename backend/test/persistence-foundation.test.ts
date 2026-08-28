import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { runMigrations } from '../packages/persistence/src/migrations.js';
import { readTrustServiceConfig } from '../services/trust/src/config.js';

class RecordingDatabase implements Database {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    return Promise.resolve({ rows: [], rowCount: 0 });
  }
  transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { return work(this); }
  close(): Promise<void> { return Promise.resolve(); }
}

describe('PostgreSQL persistence foundation', () => {
  it('fails closed when the required database URL is absent', () => {
    expect(() => readTrustServiceConfig({})).toThrow('BUDCOM_TRUST_DATABASE_URL is required');
  });

  it('runs additive migrations and parameterizes registry writes', async () => {
    const database = new RecordingDatabase();
    await runMigrations(database);
    expect(database.calls.some((call) => call.sql.includes('CREATE TABLE IF NOT EXISTS'))).toBe(true);
    const registryWrites = database.calls.filter((call) => call.sql.startsWith('INSERT INTO trust_schema_migration'));
    expect(registryWrites.map((call) => call.parameters)).toEqual([[1, 'migration_registry'], [2, 'trust_authority_state'], [3, 'relay_durable_mailbox'], [4, 'relay_acceptance_evidence'], [5, 'relay_authenticated_commercial_content']]);
  });
});
