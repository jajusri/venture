import { randomUUID } from 'node:crypto';
import { relayIdentifier, validateRelaySubmission, type RelaySubmission } from '../domain/relay.js';
import type { RelayRepository, StoredRelayEnvelope } from '../persistence/relay-repository.js';

export interface VerifiedRelayAuthority {
  readonly protocolVersion: number;
  readonly envelopeId: string;
  readonly senderBusinessId: string;
  readonly senderActorId: string;
  readonly senderDeviceId: string;
  readonly recipientBusinessId: string;
  readonly mailboxId: string;
  readonly envelopeIntegrityValid: boolean;
  readonly credentialValid: boolean;
  readonly authorityScope: ReadonlySet<string>;
}
export interface RelaySubmissionVerifier { verify(submission: RelaySubmission): Promise<VerifiedRelayAuthority> }

function sameSubmission(left: RelaySubmission, right: RelaySubmission): boolean {
  return left.envelopeId === right.envelopeId && left.objectType === right.objectType && left.objectId === right.objectId &&
    left.objectVersion === right.objectVersion && left.senderBusinessId === right.senderBusinessId &&
    left.senderActorId === right.senderActorId && left.senderDeviceId === right.senderDeviceId &&
    left.recipient.businessId === right.recipient.businessId && left.recipient.mailboxId === right.recipient.mailboxId &&
    Buffer.from(left.authenticatedEnvelope).equals(right.authenticatedEnvelope);
}

export class AcceptRelaySubmission {
  constructor(private readonly repository: RelayRepository, private readonly verifier: RelaySubmissionVerifier,
    private readonly now: () => Date = () => new Date(), private readonly newId: () => string = randomUUID) {}

  async execute(submission: RelaySubmission): Promise<StoredRelayEnvelope> {
    validateRelaySubmission(submission);
    const existing = await this.repository.findByIdempotency(submission.senderBusinessId, submission.idempotencyKey);
    if (existing) {
      if (!sameSubmission(existing.submission, submission)) throw new Error('Conflicting relay submission idempotency key');
      return existing;
    }
    const authority = await this.verifier.verify(submission);
    if (!authority.credentialValid || !authority.envelopeIntegrityValid || !authority.authorityScope.has('send_orders')) throw new Error('Authenticated relay authority rejected');
    if (authority.protocolVersion !== submission.protocolVersion || authority.envelopeId !== submission.envelopeId ||
      authority.senderBusinessId !== submission.senderBusinessId || authority.senderActorId !== submission.senderActorId ||
      authority.senderDeviceId !== submission.senderDeviceId || authority.recipientBusinessId !== submission.recipient.businessId ||
      authority.mailboxId !== submission.recipient.mailboxId) throw new Error('Authenticated relay binding mismatch');
    const acceptedAt = this.now();
    return this.repository.persist(submission, { acceptanceId: relayIdentifier(this.newId(), 'RelayAcceptanceId'),
      envelopeId: submission.envelopeId, acceptedAt, status: 'relay_accepted' });
  }
}
