import { relayIdentifier, type RelaySubmission } from '../domain/relay.js';
import { RelayServiceError } from '../errors.js';

export interface RelaySubmissionBody {
  readonly protocolVersion?: unknown;
  readonly envelopeId?: unknown;
  readonly idempotencyKey?: unknown;
  readonly objectType?: unknown;
  readonly objectId?: unknown;
  readonly objectVersion?: unknown;
  readonly senderBusinessId?: unknown;
  readonly senderActorId?: unknown;
  readonly senderDeviceId?: unknown;
  readonly recipientBusinessId?: unknown;
  readonly mailboxId?: unknown;
  readonly authenticatedEnvelope?: unknown;
  readonly submittedAt?: unknown;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError('invalid_submission', `${field} is required`, 400);
  return value;
}

function decodeEnvelope(value: unknown): Uint8Array {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError('invalid_submission', 'authenticatedEnvelope is required', 400);
  try {
    return Uint8Array.from(Buffer.from(value, 'base64'));
  } catch {
    throw new RelayServiceError('invalid_submission', 'authenticatedEnvelope must be base64', 400);
  }
}

export function mapRelaySubmissionBody(body: RelaySubmissionBody, submittedAtFallback: Date): RelaySubmission {
  const submittedAt = typeof body.submittedAt === 'string' ? new Date(body.submittedAt) : submittedAtFallback;
  if (Number.isNaN(submittedAt.getTime())) throw new RelayServiceError('invalid_submission', 'submittedAt is invalid', 400);
  const objectVersion = Number(body.objectVersion);
  if (!Number.isInteger(objectVersion)) throw new RelayServiceError('invalid_submission', 'objectVersion is required', 400);
  return {
    envelopeId: relayIdentifier(requiredString(body.envelopeId, 'envelopeId'), 'RelayEnvelopeId'),
    protocolVersion: Number(body.protocolVersion),
    objectType: requiredString(body.objectType, 'objectType'),
    objectId: requiredString(body.objectId, 'objectId'),
    objectVersion,
    senderBusinessId: requiredString(body.senderBusinessId, 'senderBusinessId'),
    senderActorId: requiredString(body.senderActorId, 'senderActorId'),
    senderDeviceId: requiredString(body.senderDeviceId, 'senderDeviceId'),
    recipient: {
      businessId: requiredString(body.recipientBusinessId, 'recipientBusinessId'),
      mailboxId: relayIdentifier(requiredString(body.mailboxId, 'mailboxId'), 'MailboxId'),
    },
    authenticatedEnvelope: decodeEnvelope(body.authenticatedEnvelope),
    idempotencyKey: requiredString(body.idempotencyKey, 'idempotencyKey'),
    submittedAt,
  };
}
