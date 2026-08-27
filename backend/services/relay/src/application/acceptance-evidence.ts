import { randomUUID } from 'node:crypto';
import { relayIdentifier, type RelayAcceptance, type RelaySubmission } from '../domain/relay.js';

export interface RelayEvidenceSignature { readonly relayId: string; readonly profile: string; readonly evidence: Uint8Array }
export interface RelayAcceptanceSigner { sign(payload: Uint8Array): Promise<RelayEvidenceSignature> }
export interface RelayAcceptanceIssuer { issue(submission: RelaySubmission, acceptedAt: Date): Promise<RelayAcceptance> }

export function acceptanceSigningPayload(value: Omit<RelayAcceptance, 'relayId' | 'evidenceProfile' | 'evidence'>): Uint8Array {
  return Buffer.from([value.acceptanceId, value.envelopeId, value.objectType, value.objectId, value.objectVersion,
    value.senderBusinessId, value.recipientBusinessId, value.acceptedAt.getTime(), value.status].map(String).join('|'), 'utf8');
}

export class SignedRelayAcceptanceIssuer implements RelayAcceptanceIssuer {
  constructor(private readonly signer: RelayAcceptanceSigner, private readonly newId: () => string = randomUUID) {}
  async issue(submission: RelaySubmission, acceptedAt: Date): Promise<RelayAcceptance> {
    const unsigned = { acceptanceId: relayIdentifier(this.newId(), 'RelayAcceptanceId'), envelopeId: submission.envelopeId,
      objectType: submission.objectType, objectId: submission.objectId, objectVersion: submission.objectVersion,
      senderBusinessId: submission.senderBusinessId, recipientBusinessId: submission.recipient.businessId,
      acceptedAt, status: 'relay_accepted' as const };
    const signed = await this.signer.sign(acceptanceSigningPayload(unsigned));
    if (!signed.relayId.trim() || !signed.profile.trim() || signed.evidence.length === 0) throw new Error('Relay acceptance signer failed closed');
    return { ...unsigned, relayId: signed.relayId, evidenceProfile: signed.profile, evidence: signed.evidence };
  }
}
