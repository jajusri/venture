import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { migrations } from '../packages/persistence/src/migrations.js';
import { hydrateStoredRelayEnvelope, PostgresRelayRepository } from '../services/relay/src/persistence/relay-repository.js';
import { relayIdentifier, type RelayAcceptance, type RelayMailboxEntry, type RelaySubmission } from '../services/relay/src/domain/relay.js';

class RecordingDatabase implements Database {
  calls: { sql: string; parameters: readonly unknown[] }[] = [];
  hydrate = false;
  mailboxRows: StoredRow[] = [];
  get row() {
    return { envelope_id: 'env-1', idempotency_key: 'intent-1', protocol_version: 1, object_type: 'ORDER', object_id: 'order-1',
      object_version: '1', sender_business_id: 'sender', sender_actor_id: 'actor', sender_device_id: 'device',
      recipient_business_id: 'recipient', mailbox_id: 'orders', authenticated_envelope: Buffer.from([1]),
      acceptance_id: 'accept-1', accepted_at: new Date(2), relay_id: 'relay-1', acceptance_evidence_profile: 'test-v1',
      acceptance_evidence: Buffer.from([9]), mailbox_sequence: '7', status: 'relay_accepted', created_at: new Date(2), acknowledged_at: null };
  }
  query<Row extends Record<string, unknown>>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    this.calls.push({ sql, parameters });
    const rows = sql.includes('RETURNING next_sequence') ? [{ mailbox_sequence: '7' }]
      : this.hydrate && sql.includes('FROM relay_envelope') ? [this.row]
      : sql.includes('FROM relay_mailbox_entry') ? this.mailboxRows : [];
    return Promise.resolve({ rows: rows as unknown as Row[], rowCount: rows.length });
  }
  transaction<T>(work: (session: DatabaseSession) => Promise<T>) { return work(this); }
  close() { return Promise.resolve(); }
}
const submission: RelaySubmission = { envelopeId: relayIdentifier('env-1', 'RelayEnvelopeId'), protocolVersion: 1, objectType: 'ORDER', objectId: 'order-1', objectVersion: 1,
  senderBusinessId: 'sender', senderActorId: 'actor', senderDeviceId: 'device', recipient: { businessId: 'recipient', mailboxId: relayIdentifier('orders', 'MailboxId') },
  authenticatedEnvelope: new Uint8Array([1]), idempotencyKey: 'intent-1', submittedAt: new Date(1) };
const acceptance: RelayAcceptance = { acceptanceId: relayIdentifier('accept-1', 'RelayAcceptanceId'), envelopeId: submission.envelopeId,
  objectType: 'ORDER', objectId: 'order-1', objectVersion: 1, senderBusinessId: 'sender', recipientBusinessId: 'recipient',
  acceptedAt: new Date(2), status: 'relay_accepted', relayId: 'relay-1', evidenceProfile: 'test-v1', evidence: new Uint8Array([9]) };

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
    expect(stored.acceptance.evidence).toEqual(acceptance.evidence);
    expect(database.calls.some((call) => call.sql.includes('ON CONFLICT (recipient_business_id, mailbox_id)'))).toBe(true);
    expect(database.calls.some((call) => call.parameters.includes(submission.authenticatedEnvelope))).toBe(true);
    expect(database.calls.some((call) => call.sql.includes('acceptance_evidence') && call.parameters.includes(acceptance.evidence))).toBe(true);
  });
  it('hydrates stored acceptance evidence for idempotent retries', async () => {
    const database = new RecordingDatabase();
    database.hydrate = true;
    const stored = await new PostgresRelayRepository(database).findByIdempotency('sender', 'intent-1');
    expect(stored?.acceptance.acceptanceId).toBe(acceptance.acceptanceId);
    expect(stored?.acceptance.status).toBe('relay_accepted');
    expect(stored?.submission.authenticatedEnvelope).toEqual(submission.authenticatedEnvelope);
    expect(hydrateStoredRelayEnvelope(database.row).delivery.mailboxSequence).toBe(7);
  });
  it('lists mailbox entries with cursor bounds and relay_accepted filter', async () => {
    const database = new RecordingDatabase();
    database.mailboxRows = [
      { ...database.row, envelope_id: 'env-2', mailbox_sequence: '8', object_id: 'order-2' },
    ];
    const entries = await new PostgresRelayRepository(database).listMailboxEntries(
      { businessId: 'recipient', mailboxId: relayIdentifier('orders', 'MailboxId') }, 7, 25,
    );
    expect(entries).toHaveLength(1);
    expect(entries[0]!.mailboxSequence).toBe(8);
    const call = database.calls.find((entry) => entry.sql.includes('FROM relay_mailbox_entry'))!;
    expect(call.sql).toContain("m.status = 'relay_accepted'");
    expect(call.sql).toContain('mailbox_sequence > $3');
    expect(call.parameters).toEqual(['recipient', 'orders', 7, 25]);
  });
});
