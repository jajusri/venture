import { relayIdentifier, type RelaySubmission } from '../domain/relay.js';
import { RelayServiceError } from '../errors.js';
import { decodeBase64Envelope } from './base64-envelope.js';

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
  readonly commercialContent?: unknown;
  readonly commercialContentType?: unknown;
  readonly commercialContentVersion?: unknown;
  readonly submittedAt?: unknown;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError('invalid_submission', `${field} is required`, 400);
  return value;
}

export function mapRelaySubmissionBody(body: RelaySubmissionBody, submittedAtFallback: Date): RelaySubmission {
  const submittedAt = typeof body.submittedAt === 'string' ? new Date(body.submittedAt) : submittedAtFallback;
  if (Number.isNaN(submittedAt.getTime())) throw new RelayServiceError('invalid_submission', 'submittedAt is invalid', 400);
  const objectVersion = Number(body.objectVersion);
  if (!Number.isInteger(objectVersion)) throw new RelayServiceError('invalid_submission', 'objectVersion is required', 400);
  const commercialContentVersion = Number(body.commercialContentVersion);
  if (!Number.isInteger(commercialContentVersion)) throw new RelayServiceError('invalid_submission', 'commercialContentVersion is required', 400);
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
    authenticatedEnvelope: decodeBase64Envelope(body.authenticatedEnvelope, 'invalid_submission', 'authenticatedEnvelope'),
    commercialContent: requiredString(body.commercialContent, 'commercialContent'),
    commercialContentType: requiredString(body.commercialContentType, 'commercialContentType'),
    commercialContentVersion,
    idempotencyKey: requiredString(body.idempotencyKey, 'idempotencyKey'),
    submittedAt,
  };
}
