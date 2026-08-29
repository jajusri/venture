import { relayIdentifier, type RelayAcknowledgement, type RelayEnvelopeId } from '../domain/relay.js';
import type { RelayRepository } from '../persistence/relay-repository.js';

export interface RelayAcknowledgementSubmission {
  readonly envelopeId: RelayEnvelopeId;
  readonly recipientBusinessId: string;
  readonly recipientActorId: string;
  readonly recipientDeviceId: string;
  readonly receivedAt: Date;
  /** Canonical authenticated-request bytes (Trust credential + device signature over this
   * acknowledgement's own security-relevant fields, including the target envelopeId) -- see
   * `devtools/pilot-envelope.ts`'s `AuthenticatedRelayRequestWire` doc comment. Required:
   * identifier-only acknowledgements carry no possession proof and must be rejected (Codex STOP 1). */
  readonly authenticatedRequest: Uint8Array;
}

export interface VerifiedAcknowledgementAuthority {
  readonly recipientBusinessId: string;
  readonly recipientActorId: string;
  readonly recipientDeviceId: string;
  /** The envelopeId the verified signature actually covered -- cross-checked below against what
   * the caller claims to be acknowledging, so a valid signature for message A can never be replayed
   * to acknowledge a different message B (Codex STOP 1 / architecture Phase 4). */
  readonly envelopeId: string;
  readonly credentialValid: boolean;
  readonly authorityScope: ReadonlySet<string>;
}

export interface RelayAcknowledgementVerifier { verify(submission: RelayAcknowledgementSubmission): Promise<VerifiedAcknowledgementAuthority> }

export class RecordRelayAcknowledgement {
  constructor(private readonly repository: RelayRepository, private readonly verifier: RelayAcknowledgementVerifier, private readonly now: () => Date = () => new Date()) {}

  async execute(submission: RelayAcknowledgementSubmission): Promise<RelayAcknowledgement> {
    if (!submission.recipientBusinessId.trim() || !submission.recipientActorId.trim() || !submission.recipientDeviceId.trim()) {
      throw new Error('Authenticated recipient binding is required');
    }
    const authority = await this.verifier.verify(submission);
    if (!authority.credentialValid || !authority.authorityScope.has('receive_orders')) throw new Error('Authenticated relay authority rejected');
    if (authority.recipientBusinessId !== submission.recipientBusinessId || authority.recipientActorId !== submission.recipientActorId ||
      authority.recipientDeviceId !== submission.recipientDeviceId || authority.envelopeId !== submission.envelopeId) {
      throw new Error('Authenticated relay binding mismatch');
    }
    return this.repository.recordAcknowledgement(submission, this.now());
  }
}

export function relayAcknowledgementSubmission(input: {
  envelopeId: string;
  recipientBusinessId: string;
  recipientActorId: string;
  recipientDeviceId: string;
  receivedAt: Date;
  authenticatedRequest: Uint8Array;
}): RelayAcknowledgementSubmission {
  return {
    envelopeId: relayIdentifier(input.envelopeId, 'RelayEnvelopeId'),
    recipientBusinessId: input.recipientBusinessId,
    recipientActorId: input.recipientActorId,
    recipientDeviceId: input.recipientDeviceId,
    receivedAt: input.receivedAt,
    authenticatedRequest: input.authenticatedRequest,
  };
}
