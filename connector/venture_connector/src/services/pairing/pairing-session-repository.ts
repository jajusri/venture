import crypto from 'node:crypto';

import type { SqliteDatabase } from '../../storage/sqlite/sqlite-database.js';

/**
 * Secure local pairing (QR / one-time code) — session model.
 *
 * TRUST MODEL: this is a PHYSICALLY TRUSTED BOOTSTRAP, not an offline-cryptographically-
 * authenticated one. A first-time device has no independently-verifiable trust anchor (public
 * key, prior pairing, pinned fingerprint) to check a signature against — an HMAC keyed by a
 * secret carried in the same payload it signs would be circular (whoever can read the payload
 * can already recompute it) and must never be presented as an authenticity guarantee.
 *
 * Instead: every field the client submits at redemption (connectorId, host, port) is checked
 * for an EXACT match against this session's stored row before the secret is even compared. A
 * mismatch on any of connectorId, host/port, or the secret itself is (a) counted against the
 * same bounded failed_attempts budget and (b) reported to the caller as one generic outcome
 * (see outcomeToHttpFailure in api/routes/pairing.ts) — the external response never discloses
 * which field was wrong, so a caller cannot use distinct responses as an oracle to fuzz a
 * session's stored connectorId/host/port. That plus short expiry, single-use, cancellable, and
 * constant-time secret comparison is the complete security model this repository provides. It
 * does not by itself prove the QR/code was never altered before the human scanned it — only
 * that what the client is presenting now matches what was stored at creation time. The live
 * /health connectorId cross-check against the endpoint actually connected to is the future
 * Android client's responsibility; this repository has no way to perform that on its behalf.
 */

/**
 * QR/short-code payload schema version. Bumped whenever the payload's field set changes in a
 * way older clients couldn't handle. The redeem route rejects any explicitly-supplied
 * schemaVersion other than this constant, so a future breaking payload change fails loudly on
 * old Android builds instead of misbehaving silently.
 */
export const PAIRING_SCHEMA_VERSION = '1';

const DEFAULT_TTL_MS = 120_000;
const MIN_TTL_MS = 30_000;
const MAX_TTL_MS = 300_000;
const MAX_FAILED_ATTEMPTS = 5;
const SHORT_CODE_LENGTH = 8;
// Excludes visually-ambiguous characters (0/O, 1/I/L). Uppercase only, for easy manual entry.
const SHORT_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

export interface PairingSessionRecord {
  readonly pairingSessionId: string;
  readonly connectorId: string;
  readonly connectorName: string;
  readonly host: string;
  readonly port: number;
  readonly schemaVersion: string;
  readonly createdAt: string;
  readonly expiresAt: string;
  readonly redeemedAt: string | null;
  readonly cancelledAt: string | null;
  readonly failedAttempts: number;
}

interface PairingSessionRow {
  pairing_session_id: string;
  connector_id: string;
  connector_name: string;
  host: string;
  port: number;
  schema_version: string;
  secret_hash: string;
  short_code_hash: string;
  failed_attempts: number;
  created_at: string;
  expires_at: string;
  redeemed_at: string | null;
  cancelled_at: string | null;
}

function mapRow(row: PairingSessionRow): PairingSessionRecord {
  return {
    pairingSessionId: row.pairing_session_id,
    connectorId: row.connector_id,
    connectorName: row.connector_name,
    host: row.host,
    port: row.port,
    schemaVersion: row.schema_version,
    createdAt: row.created_at,
    expiresAt: row.expires_at,
    redeemedAt: row.redeemed_at,
    cancelledAt: row.cancelled_at,
    failedAttempts: row.failed_attempts,
  };
}

export interface CreatePairingSessionParams {
  readonly connectorId: string;
  readonly connectorName: string;
  readonly host: string;
  readonly port: number;
  readonly ttlMs?: number;
}

export interface CreatePairingSessionResult {
  readonly schemaVersion: string;
  readonly pairingSessionId: string;
  /** Raw one-time secret — returned once, for the QR payload. Never stored, never logged. */
  readonly secret: string;
  /** Raw one-time short code — returned once, for manual entry. Never stored, never logged. */
  readonly shortCode: string;
  readonly connectorId: string;
  readonly connectorName: string;
  readonly host: string;
  readonly port: number;
  readonly expiresAt: string;
}

/** Fields the QR-path redeemer must present unaltered from what it read in the payload. */
export interface RedeemSessionFields {
  readonly connectorId: string;
  readonly host: string;
  readonly port: number;
}

/**
 * Internal outcome kinds — kept distinct for repository-level tests and auditing (never for
 * external HTTP responses; see outcomeToHttpFailure, which collapses secret_mismatch /
 * connector_id_mismatch / endpoint_mismatch into a single generic external outcome). No variant
 * here ever carries a secret, hash, or raw code.
 */
export type RedeemPairingSessionOutcome =
  | {
      readonly kind: 'redeemed';
      readonly pairingSessionId: string;
      readonly connectorId: string;
      readonly connectorName: string;
    }
  | { readonly kind: 'not_found' }
  | { readonly kind: 'expired' }
  | { readonly kind: 'already_redeemed' }
  | { readonly kind: 'cancelled' }
  | { readonly kind: 'too_many_attempts' }
  | { readonly kind: 'secret_mismatch' }
  | { readonly kind: 'connector_id_mismatch' }
  | { readonly kind: 'endpoint_mismatch' };

function sha256Hex(value: string): string {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex');
}

function generateSecret(): string {
  return crypto.randomBytes(32).toString('base64url');
}

function generateShortCode(): string {
  const bytes = crypto.randomBytes(SHORT_CODE_LENGTH);
  let code = '';
  for (let i = 0; i < SHORT_CODE_LENGTH; i += 1) {
    code += SHORT_CODE_ALPHABET[bytes[i] % SHORT_CODE_ALPHABET.length];
  }
  return code;
}

function clampTtlMs(ttlMs: number | undefined): number {
  const value = ttlMs ?? DEFAULT_TTL_MS;
  return Math.min(MAX_TTL_MS, Math.max(MIN_TTL_MS, value));
}

/** Fixed-length-buffer constant-time comparison of two hex-encoded SHA-256 digests. */
function constantTimeHashesMatch(presentedHex: string, storedHex: string): boolean {
  const a = Buffer.from(presentedHex, 'hex');
  const b = Buffer.from(storedHex, 'hex');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

export class PairingSessionRepository {
  constructor(private readonly db: SqliteDatabase) {}

  /**
   * Creates a new pairing session for the given (always self-supplied, never caller-supplied)
   * Connector identity and reachable endpoint. Exactly one session is active per connectorId:
   * any still-active prior session for the same connector is cancelled first, so the Desktop
   * UI's "one QR at a time" behavior is a real, enforced invariant rather than just a UI
   * convention. Also opportunistically prunes expired-and-never-redeemed sessions (no
   * background timer needed — cleanup piggybacks on the natural "pairing again" moment).
   */
  create(params: CreatePairingSessionParams): CreatePairingSessionResult {
    const now = new Date();
    const ttlMs = clampTtlMs(params.ttlMs);
    const expiresAt = new Date(now.getTime() + ttlMs).toISOString();
    const nowIso = now.toISOString();

    const pairingSessionId = crypto.randomUUID();
    const secret = generateSecret();
    const shortCode = generateShortCode();

    const database = this.db.getDatabase();
    database.exec('BEGIN IMMEDIATE;');
    try {
      database
        .prepare(
          `DELETE FROM pairing_sessions
           WHERE redeemed_at IS NULL AND cancelled_at IS NULL AND expires_at <= ?`,
        )
        .run(nowIso);

      database
        .prepare(
          `UPDATE pairing_sessions SET cancelled_at = ?
           WHERE connector_id = ? AND redeemed_at IS NULL AND cancelled_at IS NULL AND expires_at > ?`,
        )
        .run(nowIso, params.connectorId, nowIso);

      database
        .prepare(
          `INSERT INTO pairing_sessions
             (pairing_session_id, connector_id, connector_name, host, port, schema_version,
              secret_hash, short_code_hash, failed_attempts, created_at, expires_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)`,
        )
        .run(
          pairingSessionId,
          params.connectorId,
          params.connectorName,
          params.host,
          params.port,
          PAIRING_SCHEMA_VERSION,
          sha256Hex(secret),
          sha256Hex(shortCode),
          nowIso,
          expiresAt,
        );
      database.exec('COMMIT;');
    } catch (error) {
      database.exec('ROLLBACK;');
      throw error;
    }

    return {
      schemaVersion: PAIRING_SCHEMA_VERSION,
      pairingSessionId,
      secret,
      shortCode,
      connectorId: params.connectorId,
      connectorName: params.connectorName,
      host: params.host,
      port: params.port,
      expiresAt,
    };
  }

  /**
   * Redeems by pairing session ID + secret (the QR path). `fields` must exactly match what was
   * stored at creation time. A connectorId or host/port mismatch is checked before the secret,
   * and — like a wrong-secret guess — is counted against this session's failed_attempts budget
   * and surfaced to the caller as the same generic outcome, so distinguishing "which field was
   * wrong" is never observable externally (see the class-level trust-model note).
   */
  redeemBySessionId(
    pairingSessionId: string,
    secret: string,
    fields: RedeemSessionFields,
  ): RedeemPairingSessionOutcome {
    const row = this.db
      .getDatabase()
      .prepare('SELECT * FROM pairing_sessions WHERE pairing_session_id = ?')
      .get(pairingSessionId) as PairingSessionRow | undefined;

    if (!row) return { kind: 'not_found' };
    if (row.cancelled_at) return { kind: 'cancelled' };
    if (row.redeemed_at) return { kind: 'already_redeemed' };
    if (new Date(row.expires_at).getTime() <= Date.now()) return { kind: 'expired' };
    if (row.failed_attempts >= MAX_FAILED_ATTEMPTS) return { kind: 'too_many_attempts' };

    if (row.connector_id !== fields.connectorId) {
      this.incrementFailedAttempts(row.pairing_session_id);
      return { kind: 'connector_id_mismatch' };
    }
    if (row.host !== fields.host || row.port !== fields.port) {
      this.incrementFailedAttempts(row.pairing_session_id);
      return { kind: 'endpoint_mismatch' };
    }

    return this.compareSecretAndRedeem(row, secret, 'secret_hash');
  }

  /**
   * Redeems by short code alone (the manual-entry path, no camera). There is at most one
   * active session per Connector, so the short code alone is enough to locate it. No
   * connectorId/host/port fields are expected here — a human typing an 8-character code has no
   * way to also transcribe those. Note this path requires the caller to already know or have
   * selected the Connector's endpoint by some other means (e.g. a prior discovery pass); the
   * short code alone cannot locate a Connector on the network the way the QR payload's embedded
   * host/port can.
   */
  redeemByShortCode(shortCode: string): RedeemPairingSessionOutcome {
    const row = this.db
      .getDatabase()
      .prepare(
        `SELECT * FROM pairing_sessions
         WHERE redeemed_at IS NULL AND cancelled_at IS NULL
         ORDER BY created_at DESC LIMIT 1`,
      )
      .get() as PairingSessionRow | undefined;

    if (!row) return { kind: 'not_found' };
    if (new Date(row.expires_at).getTime() <= Date.now()) return { kind: 'expired' };
    if (row.failed_attempts >= MAX_FAILED_ATTEMPTS) return { kind: 'too_many_attempts' };

    return this.compareSecretAndRedeem(row, shortCode, 'short_code_hash');
  }

  private incrementFailedAttempts(pairingSessionId: string): void {
    this.db
      .getDatabase()
      .prepare('UPDATE pairing_sessions SET failed_attempts = failed_attempts + 1 WHERE pairing_session_id = ?')
      .run(pairingSessionId);
  }

  private compareSecretAndRedeem(
    row: PairingSessionRow,
    presented: string,
    hashColumn: 'secret_hash' | 'short_code_hash',
  ): RedeemPairingSessionOutcome {
    const presentedHash = sha256Hex(presented);
    const storedHash = hashColumn === 'secret_hash' ? row.secret_hash : row.short_code_hash;
    const matches = constantTimeHashesMatch(presentedHash, storedHash);

    if (!matches) {
      this.incrementFailedAttempts(row.pairing_session_id);
      return { kind: 'secret_mismatch' };
    }

    const now = new Date().toISOString();
    const result = this.db
      .getDatabase()
      .prepare(
        `UPDATE pairing_sessions SET redeemed_at = ?
         WHERE pairing_session_id = ? AND redeemed_at IS NULL AND cancelled_at IS NULL`,
      )
      .run(now, row.pairing_session_id);

    if ((result.changes as number) === 0) {
      // Raced with a concurrent redeem/cancel between the read above and this write.
      return { kind: 'already_redeemed' };
    }

    return {
      kind: 'redeemed',
      pairingSessionId: row.pairing_session_id,
      connectorId: row.connector_id,
      connectorName: row.connector_name,
    };
  }

  cancel(pairingSessionId: string): boolean {
    const result = this.db
      .getDatabase()
      .prepare(
        `UPDATE pairing_sessions SET cancelled_at = ?
         WHERE pairing_session_id = ? AND redeemed_at IS NULL AND cancelled_at IS NULL`,
      )
      .run(new Date().toISOString(), pairingSessionId);
    return (result.changes as number) > 0;
  }

  get(pairingSessionId: string): PairingSessionRecord | null {
    const row = this.db
      .getDatabase()
      .prepare('SELECT * FROM pairing_sessions WHERE pairing_session_id = ?')
      .get(pairingSessionId) as PairingSessionRow | undefined;
    return row ? mapRow(row) : null;
  }

  /** Public, non-sensitive status snapshot — never includes secret_hash/short_code_hash. */
  getStatus(pairingSessionId: string): Omit<PairingSessionRecord, 'failedAttempts'> | null {
    const record = this.get(pairingSessionId);
    if (!record) return null;
    return {
      pairingSessionId: record.pairingSessionId,
      connectorId: record.connectorId,
      connectorName: record.connectorName,
      host: record.host,
      port: record.port,
      schemaVersion: record.schemaVersion,
      createdAt: record.createdAt,
      expiresAt: record.expiresAt,
      redeemedAt: record.redeemedAt,
      cancelledAt: record.cancelledAt,
    };
  }
}
