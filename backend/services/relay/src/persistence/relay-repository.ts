import type { Database } from '../../../../packages/persistence/src/database.js';
import type { RelayAcceptance, RelayDeliveryRecord, RelaySubmission } from '../domain/relay.js';

export interface StoredRelayEnvelope { readonly submission: RelaySubmission; readonly acceptance: RelayAcceptance; readonly delivery: RelayDeliveryRecord }
export interface RelayRepository {
  findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null>;
  persist(submission: RelaySubmission, acceptance: RelayAcceptance): Promise<StoredRelayEnvelope>;
}

interface StoredRow extends Record<string, unknown> { envelope_id: string; acceptance_id: string; accepted_at: Date; mailbox_sequence: string }
export class PostgresRelayRepository implements RelayRepository {
  constructor(private readonly database: Database) {}
  async findByIdempotency(senderBusinessId: string, idempotencyKey: string): Promise<StoredRelayEnvelope | null> {
    const result = await this.database.query<StoredRow>(`SELECT e.envelope_id, e.acceptance_id, e.accepted_at, m.mailbox_sequence
      FROM relay_envelope e JOIN relay_mailbox_entry m ON m.envelope_id = e.envelope_id
      WHERE e.sender_business_id = $1 AND e.idempotency_key = $2 LIMIT 1`, [senderBusinessId, idempotencyKey]);
    if (result.rowCount === 0) return null;
    throw new Error('Hydration requires the original authenticated submission');
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
