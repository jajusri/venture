import type { RelayAcknowledgementSubmission } from '../application/record-acknowledgement.js';
import { relayIdentifier } from '../domain/relay.js';
import { RelayServiceError } from '../errors.js';

export interface RelayAcknowledgementBody {
  readonly envelopeId?: unknown;
  readonly recipientBusinessId?: unknown;
  readonly recipientActorId?: unknown;
  readonly recipientDeviceId?: unknown;
  readonly receivedAt?: unknown;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError('invalid_acknowledgement', `${field} is required`, 400);
  return value;
}

export function mapRelayAcknowledgementBody(body: RelayAcknowledgementBody, fallback: Date): RelayAcknowledgementSubmission {
  const receivedAt = typeof body.receivedAt === 'string' ? new Date(body.receivedAt) : fallback;
  if (Number.isNaN(receivedAt.getTime())) throw new RelayServiceError('invalid_acknowledgement', 'receivedAt is invalid', 400);
  return {
    envelopeId: relayIdentifier(requiredString(body.envelopeId, 'envelopeId'), 'RelayEnvelopeId'),
    recipientBusinessId: requiredString(body.recipientBusinessId, 'recipientBusinessId'),
    recipientActorId: requiredString(body.recipientActorId, 'recipientActorId'),
    recipientDeviceId: requiredString(body.recipientDeviceId, 'recipientDeviceId'),
    receivedAt,
  };
}
