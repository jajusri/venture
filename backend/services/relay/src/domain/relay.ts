type Brand<Value, Name extends string> = Value & { readonly __brand: Name };
export type RelayEnvelopeId = Brand<string, 'RelayEnvelopeId'>;
export type MailboxId = Brand<string, 'MailboxId'>;
export type RelayAcceptanceId = Brand<string, 'RelayAcceptanceId'>;
export type RelayCursor = Brand<string, 'RelayCursor'>;

export function relayIdentifier<Name extends string>(value: string, name: Name): Brand<string, Name> {
  const normalized = value.trim();
  if (!normalized || normalized.length > 128) throw new Error(`${name} must contain 1-128 characters`);
  return normalized as Brand<string, Name>;
}

export interface RecipientRoutingKey {
  readonly businessId: string;
  readonly mailboxId: MailboxId;
}

/** Relay transports the authenticated envelope bytes; it never reconstructs the canonical object. */
export interface RelaySubmission {
  readonly envelopeId: RelayEnvelopeId;
  readonly protocolVersion: number;
  readonly objectType: string;
  readonly objectId: string;
  readonly objectVersion: number;
  readonly senderBusinessId: string;
  readonly senderActorId: string;
  readonly senderDeviceId: string;
  readonly recipient: RecipientRoutingKey;
  readonly authenticatedEnvelope: Uint8Array;
  readonly commercialContent: string;
  readonly commercialContentType: string;
  readonly commercialContentVersion: number;
  readonly idempotencyKey: string;
  readonly submittedAt: Date;
}

export interface RelayAcceptance {
  readonly acceptanceId: RelayAcceptanceId;
  readonly envelopeId: RelayEnvelopeId;
  readonly objectType: string;
  readonly objectId: string;
  readonly objectVersion: number;
  readonly senderBusinessId: string;
  readonly recipientBusinessId: string;
  readonly acceptedAt: Date;
  readonly status: 'relay_accepted';
  readonly relayId: string;
  readonly evidenceProfile: string;
  readonly evidence: Uint8Array;
}

export type RelayDeliveryStatus = 'relay_accepted' | 'delivered' | 'rejected';
export interface RelayDeliveryRecord {
  readonly envelopeId: RelayEnvelopeId;
  readonly recipient: RecipientRoutingKey;
  readonly status: RelayDeliveryStatus;
  readonly mailboxSequence: number;
  readonly createdAt: Date;
  readonly acknowledgedAt?: Date;
}

export interface RelayAcknowledgement {
  readonly envelopeId: RelayEnvelopeId;
  readonly recipientBusinessId: string;
  readonly recipientDeviceId: string;
  readonly receivedAt: Date;
}

export type RelayFailureKind = 'temporary' | 'permanent' | 'overloaded';
export interface RelayFailure { readonly kind: RelayFailureKind; readonly code: string; readonly retryAfterMs?: number }

export interface RelayMailboxEntry {
  readonly envelopeId: RelayEnvelopeId;
  readonly mailboxSequence: number;
  readonly objectType: string;
  readonly objectId: string;
  readonly objectVersion: number;
  readonly senderBusinessId: string;
  readonly senderActorId: string;
  readonly senderDeviceId: string;
  readonly status: RelayDeliveryStatus;
  readonly acceptedAt: Date;
  readonly acceptanceId: RelayAcceptanceId;
  readonly authenticatedEnvelope: Uint8Array;
  readonly commercialContent?: string | null;
  readonly commercialContentType?: string | null;
  readonly commercialContentVersion?: number | null;
}

export interface RelayMailboxPage {
  readonly recipient: RecipientRoutingKey;
  readonly items: readonly RelayMailboxEntry[];
  readonly nextCursor: RelayCursor | null;
}

export function encodeRelayCursor(sequence: number): RelayCursor {
  if (!Number.isInteger(sequence) || sequence < 0) throw new Error('Relay cursor sequence must be a non-negative integer');
  return relayIdentifier(String(sequence), 'RelayCursor');
}

export function decodeRelayCursor(cursor: RelayCursor): number {
  const value = Number(cursor);
  if (!Number.isInteger(value) || value < 0) throw new Error('Relay cursor must decode to a non-negative integer');
  return value;
}

export function validateRelaySubmission(value: RelaySubmission): void {
  if (value.protocolVersion !== 1) throw new Error('Unsupported relay protocol version');
  if (!value.objectType.trim() || !value.objectId.trim() || value.objectVersion < 1) throw new Error('Valid canonical object reference is required');
  if (!value.senderBusinessId.trim() || !value.senderActorId.trim() || !value.senderDeviceId.trim()) throw new Error('Authenticated sender binding is required');
  if (!value.recipient.businessId.trim() || !value.recipient.mailboxId.trim()) throw new Error('Recipient routing is required');
  if (!value.idempotencyKey.trim() || value.idempotencyKey.length > 128) throw new Error('Bounded idempotency key is required');
  if (value.authenticatedEnvelope.length === 0 || value.authenticatedEnvelope.length > 256 * 1024) throw new Error('Authenticated envelope exceeds relay bounds');
  if (value.commercialContentType !== ORDER_SNAPSHOT_CONTENT_TYPE || value.commercialContentVersion !== ORDER_SNAPSHOT_CONTENT_VERSION) {
    throw new Error('Supported commercial content type and version are required');
  }
  const contentBytes = Buffer.byteLength(value.commercialContent, 'utf8');
  if (contentBytes === 0) throw new Error('Authenticated commercial content is required');
  if (contentBytes > MAX_COMMERCIAL_CONTENT_BYTES) throw new Error('Commercial content exceeds relay bounds');
}

export const ORDER_SNAPSHOT_CONTENT_TYPE = 'application/vnd.budcom.order-snapshot+json';
/** Current production commercial wire version (Codex-confirmed 2026-08-29): Android's
 * `OrderVersionSnapshot.CURRENT_CONTRACT_VERSION` is 3 and carries explicit `buyerBusinessId`/
 * `sellerBusinessId` fields absent from v2. Relay treats this content as opaque bytes-plus-metadata
 * (it never parses/reconstructs the canonical object -- see this file's own top doc comment), so
 * bumping this constant does not require Relay to understand buyer/seller roles; it only changes
 * which version this gate accepts as current. v2 is deliberately NOT accepted here anymore -- see
 * `relay-domain.test.ts`'s explicit rejection tests. */
export const ORDER_SNAPSHOT_CONTENT_VERSION = 3;
export const MAX_COMMERCIAL_CONTENT_BYTES = 24_576;
