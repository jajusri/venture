import type { Database } from '../../../../packages/persistence/src/database.js';
import { relayIdentifier, type MailboxId, type RelayAcceptance, type RelayDeliveryRecord, type RelayEnvelopeId, type RelaySubmission } from '../domain/relay.js';

export interface StoredRelayEnvelope { readonly submission: RelaySubmission; readonly acceptance: RelayAcceptance; readonly delivery: RelayDeliveryRecord }
export interface RelayRepository {
  findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null>;
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope>;
}

interface StoredRow extends Record<string, unknown> {
  envelope_id: string; idempotency_key: string; protocol_version: number; object_type: string; object_id: string;
  object_version: string | number; sender_business_id: string; sender_actor_id: string; sender_device_id: string;
  recipient_business_id: string; mailbox_id: string; authenticated_envelope: Buffer | Uint8Array;
  acceptance_id: string; accepted_at: Date | string; relay_id: string; acceptance_evidence_profile: string;
  acceptance_evidence: Buffer | Uint8Array; mailbox_sequence: string | number; status: string;
  created_at: Date | string; acknowledged_at: Date | string | null;
}

function asDate(value: Date | string): Date { return value instanceof Date ? value : new Date(value); }
function asBytes(value: Buffer | Uint8Array): Uint8Array { return Uint8Array.from(value); }

export function hydrateStoredRelayEnvelope(row: StoredRow): StoredRelayEnvelope {
  const envelopeId = relayIdentifier(row.envelope_id, 'RelayEnvelopeId') as RelayEnvelopeId;
  const recipient = { businessId: row.recipient_business_id, mailboxId: relayIdentifier(row.mailbox_id, 'MailboxId') as MailboxId };
  const acceptedAt = asDate(row.accepted_at);
  const submission: RelaySubmission = {
    envelopeId, protocolVersion: Number(row.protocol_version), objectType: row.object_type, objectId: row.object_id,
    objectVersion: Number(row.object_version), senderBusinessId: row.sender_business_id, senderActorId: row.sender_actor_id,
    senderDeviceId: row.sender_device_id, recipient, authenticatedEnvelope: asBytes(row.authenticated_envelope),
    idempotencyKey: row.idempotency_key, submittedAt: acceptedAt,
  };
  const acceptance: RelayAcceptance = {
    acceptanceId: relayIdentifier(row.acceptance_id, 'RelayAcceptanceId'), envelopeId, objectType: row.object_type,
    objectId: row.object_id, objectVersion: Number(row.object_version), senderBusinessId: row.sender_business_id,
    recipientBusinessId: row.recipient_business_id, acceptedAt, status: 'relay_accepted', relayId: row.relay_id,
    evidenceProfile: row.acceptance_evidence_profile, evidence: asBytes(row.acceptance_evidence),
  };
  const delivery: RelayDeliveryRecord = {
    envelopeId, recipient, status: row.status === 'delivered' ? 'delivered' : row.status === 'rejected' ? 'rejected' : 'relay_accepted',
    mailboxSequence: Number(row.mailbox_sequence), createdAt: asDate(row.created_at),
    ...(row.acknowledged_at ? { acknowledgedAt: asDate(row.acknowledged_at) } : {}),
  };
  return { submission, acceptance, delivery };
}

export class PostgresRelayRepository implements RelayRepository {
  constructor(private readonly database: Database) {}
  async findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null> {
    const result = await this.database.query<StoredRow>(`SELECT e.envelope_id, e.idempotency_key, e.protocol_version, e.object_type, e.object_id,
      e.object_version, e.sender_business_id, e.sender_actor_id, e.sender_device_id, e.recipient_business_id, e.mailbox_id,
      e.authenticated_envelope, e.acceptance_id, e.accepted_at, e.relay_id, e.acceptance_evidence_profile, e.acceptance_evidence,
      m.mailbox_sequence, m.status, m.created_at, m.acknowledged_at
      FROM relay_envelope e JOIN relay_mailbox_entry m ON m.envelope_id = e.envelope_id
      WHERE e.sender_business_id = $1 AND e.idempotency_key = $2 LIMIT 1`, [senderBusinessId, idempotencyKey]);
    if (result.rowCount === 0) return null;
    return hydrateStoredRelayEnvelope(result.rows[0]!);
  }
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope> {
    return this.database.transaction(async (tx) => {
      const sequence = await tx.query<{ mailbox_sequence: string }>(`INSERT INTO relay_mailbox_checkpoint(recipient_business_id, mailbox_id, next_sequence)
        VALUES ($1, $2, 2) ON CONFLICT (recipient_business_id, mailbox_id) DO UPDATE
        SET next_sequence = relay_mailbox_checkpoint.next_sequence + 1 RETURNING next_sequence - 1 AS mailbox_sequence`,
      [submission.recipient.businessId, submission.recipient.mailboxId]);
      const mailboxSequence = Number(sequence.rows[0]!.mailbox_sequence);
      await tx.query(`INSERT INTO relay_envelope(envelope_id, idempotency_key, protocol_version, object_type, object_id, object_version,
        sender_business_id, sender_actor_id, sender_device_id, recipient_business_id, mailbox_id, authenticated_envelope, acceptance_id, accepted_at,
        relay_id, acceptance_evidence_profile, acceptance_evidence)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)`, [submission.envelopeId, submission.idempotencyKey,
        submission.protocolVersion, submission.objectType, submission.objectId, submission.objectVersion, submission.senderBusinessId,
        submission.senderActorId, submission.senderDeviceId, submission.recipient.businessId, submission.recipient.mailboxId,
        submission.authenticatedEnvelope, acceptance.acceptanceId, acceptance.acceptedAt, acceptance.relayId, acceptance.evidenceProfile, acceptance.evidence]);
      await tx.query(`INSERT INTO relay_mailbox_entry(recipient_business_id, mailbox_id, mailbox_sequence, envelope_id, status, created_at)
        VALUES ($1,$2,$3,$4,'relay_accepted',$5)`, [submission.recipient.businessId, submission.recipient.mailboxId,
        mailboxSequence, submission.envelopeId, acceptance.acceptedAt]);
      return { submission, acceptance, delivery: { envelopeId: submission.envelopeId, recipient: submission.recipient,
        status: 'relay_accepted', mailboxSequence, createdAt: acceptance.acceptedAt } };
    });
  }
}
