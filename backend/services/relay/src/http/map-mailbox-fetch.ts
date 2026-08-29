import type { RelayMailboxFetch } from '../application/fetch-mailbox.js';
import { relayIdentifier } from '../domain/relay.js';
import { RelayServiceError } from '../errors.js';
import { decodeBase64Envelope } from './base64-envelope.js';

export interface RelayMailboxFetchBody {
  readonly recipientBusinessId?: unknown;
  readonly mailboxId?: unknown;
  readonly recipientActorId?: unknown;
  readonly recipientDeviceId?: unknown;
  readonly authenticatedRequest?: unknown;
  readonly cursor?: unknown;
  readonly limit?: unknown;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError('invalid_mailbox_fetch', `${field} is required`, 400);
  return value;
}

export function mapRelayMailboxFetchBody(body: RelayMailboxFetchBody): RelayMailboxFetch {
  const limit = body.limit === undefined ? undefined : Number(body.limit);
  if (body.limit !== undefined && !Number.isInteger(limit)) throw new RelayServiceError('invalid_mailbox_fetch', 'limit must be an integer', 400);
  const cursor = body.cursor === undefined || body.cursor === null
    ? null
    : relayIdentifier(requiredString(body.cursor, 'cursor'), 'RelayCursor');
  return {
    recipient: {
      businessId: requiredString(body.recipientBusinessId, 'recipientBusinessId'),
      mailboxId: relayIdentifier(requiredString(body.mailboxId, 'mailboxId'), 'MailboxId'),
    },
    recipientActorId: requiredString(body.recipientActorId, 'recipientActorId'),
    recipientDeviceId: requiredString(body.recipientDeviceId, 'recipientDeviceId'),
    authenticatedRequest: decodeBase64Envelope(body.authenticatedRequest, 'invalid_mailbox_fetch', 'authenticatedRequest'),
    cursor,
    limit: limit ?? 25,
  };
}
