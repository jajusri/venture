import { describe, expect, it } from 'vitest';
import type { DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { migrations } from '../packages/persistence/src/migrations.js';
import { AuthorityRepository } from '../services/trust/src/persistence/authority-repository.js';
import { identifier } from '../services/trust/src/domain/authority.js';

class RecordingSession implements DatabaseSession {
  readonly calls: { sql: string; parameters: readonly unknown[] }[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters }); return Promise.resolve({ rows: [], rowCount: 0 });
  }
}
describe('trust authority persistence', () => {
  it('defines additive bounded lookup indexes', () => {
    const sql = migrations.find((migration) => migration.version === 2)!.sql;
    expect(sql).toContain('UNIQUE (business_id, actor_id)');
    expect(sql).toContain('trust_membership_business_status_idx');
    expect(sql).toContain('trust_device_business_status_idx');
    expect(sql).toContain('trust_device_epoch_idx');
  });
  it('uses parameterized point lookups rather than scans', async () => {
    const session = new RecordingSession(); const repository = new AuthorityRepository(session);
    await repository.findMembership(identifier('business-1', 'BusinessId'), identifier('actor-1', 'ActorId'));
    await repository.findActiveDevice(identifier('business-1', 'BusinessId'), identifier('device-1', 'DeviceId'));
    expect(session.calls[0]?.parameters).toEqual(['business-1', 'actor-1']);
    expect(session.calls[1]?.sql).toContain('LIMIT 1');
    expect(session.calls.every((call) => call.sql.includes('WHERE'))).toBe(true);
  });
});
