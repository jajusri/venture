import type { Database } from '../../../../packages/persistence/src/database.js';
import type { RelayAcknowledgementSubmission } from '../application/record-acknowledgement.js';
import { relayIdentifier, type MailboxId, type RecipientRoutingKey, type RelayAcceptance, type RelayAcknowledgement, type RelayDeliveryRecord, type RelayEnvelopeId, type RelayMailboxEntry, type RelaySubmission } from '../domain/relay.js';

export interface StoredRelayEnvelope { readonly submission: RelaySubmission; readonly acceptance: RelayAcceptance; readonly delivery: RelayDeliveryRecord }
export interface RelayRepository {
  findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null>;
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope>;
  listMailboxEntries(recipient: RecipientRoutingKey, afterSequence: number | null, limit: number): Promise<RelayMailboxEntry[]>;
  recordAcknowledgement(submission: RelayAcknowledgementSubmission, recordedAt: Date): Promise<RelayAcknowledgement>;
}

interface StoredRow extends Record<string, unknown> {
  envelope_id: string; idempotency_key: string; protocol_version: number; object_type: string; object_id: string;
  object_version: string | number; sender_business_id: string; sender_actor_id: string; sender_device_id: string;
  recipient_business_id: string; mailbox_id: string; authenticated_envelope: Buffer | Uint8Array;
  commercial_content?: string | null; commercial_content_type?: string | null; commercial_content_version?: number | null;
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
    commercialContent: row.commercial_content ?? '', commercialContentType: row.commercial_content_type ?? '',
    commercialContentVersion: row.commercial_content_version == null ? 0 : Number(row.commercial_content_version),
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

function hydrateMailboxEntry(row: StoredRow): RelayMailboxEntry {
  return {
    envelopeId: relayIdentifier(row.envelope_id, 'RelayEnvelopeId') as RelayEnvelopeId,
    mailboxSequence: Number(row.mailbox_sequence),
    objectType: row.object_type,
    objectId: row.object_id,
    objectVersion: Number(row.object_version),
    senderBusinessId: row.sender_business_id,
    senderActorId: row.sender_actor_id,
    senderDeviceId: row.sender_device_id,
    status: row.status === 'delivered' ? 'delivered' : row.status === 'rejected' ? 'rejected' : 'relay_accepted',
    acceptedAt: asDate(row.accepted_at),
    acceptanceId: relayIdentifier(row.acceptance_id, 'RelayAcceptanceId'),
    authenticatedEnvelope: asBytes(row.authenticated_envelope),
    commercialContent: row.commercial_content ?? null,
    commercialContentType: row.commercial_content_type ?? null,
    commercialContentVersion: row.commercial_content_version == null ? null : Number(row.commercial_content_version),
  };
}

export class PostgresRelayRepository implements RelayRepository {
  constructor(private readonly database: Database) {}
  async findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null> {
    const result = await this.database.query<StoredRow>(`SELECT e.envelope_id, e.idempotency_key, e.protocol_version, e.object_type, e.object_id,
      e.object_version, e.sender_business_id, e.sender_actor_id, e.sender_device_id, e.recipient_business_id, e.mailbox_id,
      e.authenticated_envelope, e.commercial_content, e.commercial_content_type, e.commercial_content_version,
      e.acceptance_id, e.accepted_at, e.relay_id, e.acceptance_evidence_profile, e.acceptance_evidence,
      m.mailbox_sequence, m.status, m.created_at, m.acknowledged_at
      FROM relay_envelope e JOIN relay_mailbox_entry m ON m.envelope_id = e.envelope_id
      WHERE e.sender_business_id = $1 AND e.idempotency_key = $2 LIMIT 1`, [senderBusinessId, idempotencyKey]);
    if (result.rowCount === 0) return null;
    return hydrateStoredRelayEnvelope(result.rows[0]!);
  }
  async listMailboxEntries(recipient: RecipientRoutingKey, afterSequence: number | null, limit: number): Promise<RelayMailboxEntry[]> {
    const result = await this.database.query<StoredRow>(`SELECT e.envelope_id, e.idempotency_key, e.protocol_version, e.object_type, e.object_id,
      e.object_version, e.sender_business_id, e.sender_actor_id, e.sender_device_id, e.recipient_business_id, e.mailbox_id,
      e.authenticated_envelope, e.commercial_content, e.commercial_content_type, e.commercial_content_version,
      e.acceptance_id, e.accepted_at, e.relay_id, e.acceptance_evidence_profile, e.acceptance_evidence,
      m.mailbox_sequence, m.status, m.created_at, m.acknowledged_at
      FROM relay_mailbox_entry m JOIN relay_envelope e ON e.envelope_id = m.envelope_id
      WHERE m.recipient_business_id = $1 AND m.mailbox_id = $2 AND m.status = 'relay_accepted'
      AND ($3::bigint IS NULL OR m.mailbox_sequence > $3)
      ORDER BY m.mailbox_sequence ASC LIMIT $4`,
    [recipient.businessId, recipient.mailboxId, afterSequence, limit]);
    return result.rows.map((row) => hydrateMailboxEntry(row));
  }
  recordAcknowledgement(submission: RelayAcknowledgementSubmission, recordedAt: Date): Promise<RelayAcknowledgement> {
    return this.database.transaction(async (tx) => {
      const mailbox = await tx.query<{ recipient_business_id: string; mailbox_id: string }>(
        `SELECT recipient_business_id, mailbox_id FROM relay_mailbox_entry WHERE envelope_id = $1 AND recipient_business_id = $2 LIMIT 1`,
        [submission.envelopeId, submission.recipientBusinessId],
      );
      if (mailbox.rowCount === 0) throw new Error('Relay mailbox entry not found');
      const existing = await tx.query<{ envelope_id: string }>(
        `SELECT envelope_id FROM relay_delivery_acknowledgement WHERE envelope_id = $1 LIMIT 1`,
        [submission.envelopeId],
      );
      if (existing.rowCount > 0) {
        return {
          envelopeId: submission.envelopeId,
          recipientBusinessId: submission.recipientBusinessId,
          recipientDeviceId: submission.recipientDeviceId,
          receivedAt: submission.receivedAt,
        };
      }
      await tx.query(
        `INSERT INTO relay_delivery_acknowledgement(envelope_id, recipient_business_id, recipient_device_id, received_at, recorded_at)
         VALUES ($1,$2,$3,$4,$5)`,
        [submission.envelopeId, submission.recipientBusinessId, submission.recipientDeviceId, submission.receivedAt, recordedAt],
      );
      await tx.query(
        `UPDATE relay_mailbox_entry SET status = 'delivered', acknowledged_at = $2
         WHERE envelope_id = $1 AND recipient_business_id = $3`,
        [submission.envelopeId, submission.receivedAt, submission.recipientBusinessId],
      );
      return {
        envelopeId: submission.envelopeId,
        recipientBusinessId: submission.recipientBusinessId,
        recipientDeviceId: submission.recipientDeviceId,
        receivedAt: submission.receivedAt,
      };
    });
  }
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope> {
    return this.database.transaction(async (tx) => {
      const sequence = await tx.query<{ mailbox_sequence: string }>(`INSERT INTO relay_mailbox_checkpoint(recipient_business_id, mailbox_id, next_sequence)
        VALUES ($1, $2, 2) ON CONFLICT (recipient_business_id, mailbox_id) DO UPDATE
        SET next_sequence = relay_mailbox_checkpoint.next_sequence + 1 RETURNING next_sequence - 1 AS mailbox_sequence`,
      [submission.recipient.businessId, submission.recipient.mailboxId]);
      const mailboxSequence = Number(sequence.rows[0]!.mailbox_sequence);
      await tx.query(`INSERT INTO relay_envelope(envelope_id, idempotency_key, protocol_version, object_type, object_id, object_version,
        sender_business_id, sender_actor_id, sender_device_id, recipient_business_id, mailbox_id, authenticated_envelope,
        commercial_content, commercial_content_type, commercial_content_version, acceptance_id, accepted_at,
        relay_id, acceptance_evidence_profile, acceptance_evidence)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)`, [submission.envelopeId, submission.idempotencyKey,
        submission.protocolVersion, submission.objectType, submission.objectId, submission.objectVersion, submission.senderBusinessId,
        submission.senderActorId, submission.senderDeviceId, submission.recipient.businessId, submission.recipient.mailboxId,
        submission.authenticatedEnvelope, submission.commercialContent, submission.commercialContentType, submission.commercialContentVersion,
        acceptance.acceptanceId, acceptance.acceptedAt, acceptance.relayId, acceptance.evidenceProfile, acceptance.evidence]);
      await tx.query(`INSERT INTO relay_mailbox_entry(recipient_business_id, mailbox_id, mailbox_sequence, envelope_id, status, created_at)
        VALUES ($1,$2,$3,$4,'relay_accepted',$5)`, [submission.recipient.businessId, submission.recipient.mailboxId,
        mailboxSequence, submission.envelopeId, acceptance.acceptedAt]);
      return { submission, acceptance, delivery: { envelopeId: submission.envelopeId, recipient: submission.recipient,
        status: 'relay_accepted', mailboxSequence, createdAt: acceptance.acceptedAt } };
    });
  }
}
