import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { migrations } from '../packages/persistence/src/migrations.js';
import { PostgresRelayRepository } from '../services/relay/src/persistence/relay-repository.js';
import { relayIdentifier, type RelayAcceptance, type RelaySubmission } from '../services/relay/src/domain/relay.js';

class RecordingDatabase implements Database {
  calls: { sql: string; parameters: readonly unknown[] }[] = [];
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    const rows = sql.includes('RETURNING next_sequence') ? [{ mailbox_sequence: '7' }] : [];
    return Promise.resolve({ rows: rows as unknown as Row[], rowCount: rows.length });
  }
  transaction<T>(work: (session: DatabaseSession) => Promise<T>) { return work(this); }
  close() { return Promise.resolve(); }
}
const submission: RelaySubmission = { envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
  senderBusinessId: 'sender', senderActorId: 'actor', senderDeviceId: 'device', recipient: { businessId: 'recipient', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1]), idempotencyKey: 'intent-1', submittedAt: new Date(1) };
const acceptance: RelayAcceptance = { acceptanceId: relayIdentifier('accept-1', 'RelayAcceptanceId'), envelopeId: submission.envelopeId, acceptedAt: new Date(2), status: 'relay_accepted' };

describe('relay PostgreSQL persistence', () => {
  it('has additive bounded mailbox schema without a global sequence', () => {
    const sql = migrations.find((migration) => migration.name === 'relay_durable_mailbox')!.sql;
    expect(sql).toContain('PRIMARY KEY (recipient_business_id, mailbox_id, mailbox_sequence)');
    expect(sql).toContain('relay_mailbox_status_cursor_idx');
    expect(sql).not.toContain('SERIAL');
  });
  it('persists acceptance and mailbox entry atomically with a per-mailbox sequence', async () => {
    const database = new RecordingDatabase();
    const stored = await new PostgresRelayRepository(database).persist(submission, acceptance);
    expect(stored.delivery.mailboxSequence).toBe(7);
    expect(database.calls.some((call) => call.sql.includes('ON CONFLICT (recipient_business_id, mailbox_id)'))).toBe(true);
    expect(database.calls.some((call) => call.parameters.includes(submission.authenticatedEnvelope))).toBe(true);
  });
});
