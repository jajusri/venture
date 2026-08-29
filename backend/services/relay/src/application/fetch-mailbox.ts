import {
  decodeRelayCursor,
  encodeRelayCursor,
  relayIdentifier,
  type MailboxId,
  type RecipientRoutingKey,
  type RelayCursor,
  type RelayMailboxEntry,
  type RelayMailboxPage,
} from '../domain/relay.js';
import type { RelayRepository } from '../persistence/relay-repository.js';

export const DEFAULT_MAILBOX_PAGE_SIZE = 25;
export const MAX_MAILBOX_PAGE_SIZE = 50;

export interface RelayMailboxFetch {
  readonly recipient: RecipientRoutingKey;
  readonly recipientActorId: string;
  readonly recipientDeviceId: string;
  readonly cursor: RelayCursor | null;
  readonly limit: number;
  /** Canonical authenticated-request bytes (Trust credential + device signature over this fetch's
   * own security-relevant fields) -- see `devtools/pilot-envelope.ts`'s
   * `AuthenticatedRelayRequestWire` doc comment. Required: identifier-only fetches carry no
   * possession proof and must be rejected (Codex STOP 1). */
  readonly authenticatedRequest: Uint8Array;
}

export interface VerifiedMailboxAuthority {
  readonly recipientBusinessId: string;
  readonly mailboxId: MailboxId;
  readonly recipientActorId: string;
  readonly recipientDeviceId: string;
  readonly credentialValid: boolean;
  readonly authorityScope: ReadonlySet<string>;
}

export interface RelayMailboxVerifier { verify(fetch: RelayMailboxFetch): Promise<VerifiedMailboxAuthority> }

export function normalizeMailboxPageSize(limit: number): number {
  if (!Number.isInteger(limit) || limit < 1) return DEFAULT_MAILBOX_PAGE_SIZE;
  return Math.min(limit, MAX_MAILBOX_PAGE_SIZE);
}

export class FetchRecipientMailbox {
  constructor(private readonly repository: RelayRepository, private readonly verifier: RelayMailboxVerifier) {}

  async execute(fetch: RelayMailboxFetch): Promise<RelayMailboxPage> {
    if (!fetch.recipient.businessId.trim() || !fetch.recipient.mailboxId.trim()) throw new Error('Recipient routing is required');
    if (!fetch.recipientActorId.trim() || !fetch.recipientDeviceId.trim()) throw new Error('Authenticated recipient binding is required');
    const authority = await this.verifier.verify(fetch);
    if (!authority.credentialValid || !authority.authorityScope.has('receive_orders')) throw new Error('Authenticated relay authority rejected');
    if (authority.recipientBusinessId !== fetch.recipient.businessId || authority.mailboxId !== fetch.recipient.mailboxId ||
      authority.recipientActorId !== fetch.recipientActorId || authority.recipientDeviceId !== fetch.recipientDeviceId) {
      throw new Error('Authenticated relay binding mismatch');
    }
    const limit = normalizeMailboxPageSize(fetch.limit);
    const afterSequence = fetch.cursor ? decodeRelayCursor(fetch.cursor) : null;
    const items = await this.repository.listMailboxEntries(fetch.recipient, afterSequence, limit + 1);
    const pageItems = items.slice(0, limit);
    const nextCursor = items.length > limit && pageItems.length > 0
      ? encodeRelayCursor(pageItems[pageItems.length - 1]!.mailboxSequence)
      : null;
    return { recipient: fetch.recipient, items: pageItems, nextCursor };
  }
}

export function relayMailboxFetch(input: {
  recipientBusinessId: string;
  mailboxId: string;
  recipientActorId: string;
  recipientDeviceId: string;
  authenticatedRequest: Uint8Array;
  cursor?: string | null;
  limit?: number;
}): RelayMailboxFetch {
  return {
    recipient: {
      businessId: input.recipientBusinessId,
      mailboxId: relayIdentifier(input.mailboxId, 'MailboxId'),
    },
    recipientActorId: input.recipientActorId,
    recipientDeviceId: input.recipientDeviceId,
    authenticatedRequest: input.authenticatedRequest,
    cursor: input.cursor?.trim() ? relayIdentifier(input.cursor.trim(), 'RelayCursor') : null,
    limit: input.limit ?? DEFAULT_MAILBOX_PAGE_SIZE,
  };
}
