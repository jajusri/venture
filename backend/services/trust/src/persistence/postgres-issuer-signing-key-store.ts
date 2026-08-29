import type { Database } from '../../../../packages/persistence/src/database.js';
import type { TrustVerificationKey, VerificationKeyStatus, VerificationKeyStore } from '../application/verification-keys.js';

/**
 * Server-authoritative issuer signing-key LIFECYCLE store (round 6 key-lifecycle invariant 6:
 * "Key status must be server-authoritative"). Holds ONLY public/metadata columns -- `public_key_pem`
 * is public verification material by design, never a secret. The private key itself never touches
 * this table or any database row; see `managed-signing-key-registry.ts` for where the corresponding
 * local-file private key is loaded, keyed by the SAME `key_id` this store tracks.
 *
 * "Exactly one active key per issuer" (invariant 1) is enforced primarily by
 * `trust_issuer_signing_key_one_active_idx`, a PARTIAL UNIQUE INDEX on `(issuer_id) WHERE status =
 * 'active'` (migration v8) -- a second concurrent attempt to mark a row active for the same issuer
 * fails atomically at the database level. `rotate()` additionally takes `SELECT ... FOR UPDATE` on
 * the current active row so a concurrent rotation blocks on the SAME physical row rather than
 * racing to insert two "new" active rows past the index (see that method's own doc comment for the
 * exact interleaving this guarantees).
 */

export interface IssuerSigningKeyDescriptor {
  readonly issuerId: string; readonly keyId: string; readonly status: VerificationKeyStatus; readonly profile: string;
  readonly createdAt: Date; readonly activatedAt: Date | null; readonly retiredAt: Date | null; readonly revokedAt: Date | null;
}

export class DuplicateActiveSigningKeyError extends Error {
  constructor(issuerId: string) { super(`An active issuer signing key already exists for issuer ${issuerId} -- rotate instead of bootstrapping again`); }
}
export class UnknownSigningKeyError extends Error {
  constructor(issuerId: string, keyId: string) { super(`No issuer signing key found for issuer=${issuerId} key=${keyId}`); }
}
export class NoActiveSigningKeyError extends Error {
  constructor(issuerId: string) { super(`No active issuer signing key found for issuer=${issuerId} -- bootstrap one first`); }
}
export class SigningKeyLifecycleConflictError extends Error {}

interface SigningKeyRow extends Record<string, unknown> {
  readonly issuer_id: string; readonly key_id: string; readonly status: string; readonly profile: string; readonly public_key_pem: string;
  readonly created_at: Date; readonly activated_at: Date | null; readonly retired_at: Date | null; readonly revoked_at: Date | null;
}
const SIGNING_KEY_COLUMNS = 'issuer_id, key_id, status, profile, public_key_pem, created_at, activated_at, retired_at, revoked_at';

function toVerificationKey(row: SigningKeyRow): TrustVerificationKey {
  return { issuerId: row.issuer_id, issuerKeyId: row.key_id, profile: row.profile, publicKey: row.public_key_pem, validFrom: row.created_at, status: row.status as VerificationKeyStatus };
}
function toDescriptor(row: SigningKeyRow): IssuerSigningKeyDescriptor {
  return { issuerId: row.issuer_id, keyId: row.key_id, status: row.status as VerificationKeyStatus, profile: row.profile, createdAt: row.created_at, activatedAt: row.activated_at, retiredAt: row.retired_at, revokedAt: row.revoked_at };
}

export class PostgresIssuerSigningKeyStore implements VerificationKeyStore {
  constructor(private readonly database: Database) {}

  /** Every non-expired key regardless of status -- a revoked or retired key must remain VISIBLE
   * (with its real status) so a verifier can see and reject it; only `VerificationKeyDirectory`'s
   * own temporal filtering decides what "non-expired" means. Mirrors the pre-existing, unmodified
   * behavior this store replaces (a single hardcoded closure that also never filtered by status). */
  async listForIssuer(issuerId: string): Promise<readonly TrustVerificationKey[]> {
    const result = await this.database.query<SigningKeyRow>(`SELECT ${SIGNING_KEY_COLUMNS} FROM trust_issuer_signing_key WHERE issuer_id = $1 ORDER BY created_at`, [issuerId]);
    return result.rows.map(toVerificationKey);
  }

  /** Full lifecycle listing for the operator CLI -- never includes private key material (there is
   * none in this table) and deliberately omits the public PEM too (verbose, not needed for a status
   * listing; available via `listForIssuer` if ever needed). */
  async describe(issuerId: string): Promise<readonly IssuerSigningKeyDescriptor[]> {
    const result = await this.database.query<SigningKeyRow>(`SELECT ${SIGNING_KEY_COLUMNS} FROM trust_issuer_signing_key WHERE issuer_id = $1 ORDER BY created_at`, [issuerId]);
    return result.rows.map(toDescriptor);
  }

  /** The FIRST key ever created for an issuer. Fails if an active key already exists (use `rotate`
   * instead) -- this is a deliberate pre-check inside the transaction, not just reliance on the
   * partial unique index, so the operator gets a clear, specific error rather than a raw constraint
   * violation. */
  async bootstrap(issuerId: string, keyId: string, profile: string, publicKeyPem: string, now: Date): Promise<void> {
    return this.database.transaction(async (tx) => {
      const existingActive = await tx.query('SELECT 1 FROM trust_issuer_signing_key WHERE issuer_id = $1 AND status = $2', [issuerId, 'active']);
      if (existingActive.rowCount > 0) throw new DuplicateActiveSigningKeyError(issuerId);
      await tx.query(
        `INSERT INTO trust_issuer_signing_key(issuer_id, key_id, status, profile, public_key_pem, created_at, activated_at) VALUES ($1,$2,'active',$3,$4,$5,$5)`,
        [issuerId, keyId, profile, publicKeyPem, now],
      );
    });
  }

  /**
   * Atomic rotation (key-lifecycle Gate 4): retires the current active key and installs a new one
   * as active, in ONE transaction. `SELECT ... FOR UPDATE` locks the current active row for the
   * transaction's duration -- a concurrent second `rotate()` call targeting the SAME issuer blocks
   * on that exact row until this transaction commits or rolls back, then re-evaluates its own
   * `WHERE status = 'active'` against the now-updated row. If this transaction committed first, that
   * row is now 'retired', so the second call's SELECT returns zero rows and it fails closed with
   * `NoActiveSigningKeyError` -- it can never proceed to insert a second concurrent "new" active key.
   * The `retired.rowCount !== 1` check below is defensive (mirrors this codebase's existing
   * `PostgresDeviceRegistrationStore`-style "should not happen given the lock above" guards).
   */
  async rotate(issuerId: string, newKeyId: string, profile: string, newPublicKeyPem: string, now: Date): Promise<{ readonly retiredKeyId: string }> {
    return this.database.transaction(async (tx) => {
      const current = await tx.query<SigningKeyRow>(`SELECT ${SIGNING_KEY_COLUMNS} FROM trust_issuer_signing_key WHERE issuer_id = $1 AND status = 'active' FOR UPDATE`, [issuerId]);
      if (current.rowCount === 0) throw new NoActiveSigningKeyError(issuerId);
      const currentKeyId = current.rows[0]!.key_id;
      const retired = await tx.query(`UPDATE trust_issuer_signing_key SET status = 'retired', retired_at = $1 WHERE issuer_id = $2 AND key_id = $3 AND status = 'active'`, [now, issuerId, currentKeyId]);
      if (retired.rowCount !== 1) throw new SigningKeyLifecycleConflictError('Concurrent rotation conflict: the active key changed underneath this rotation');
      await tx.query(
        `INSERT INTO trust_issuer_signing_key(issuer_id, key_id, status, profile, public_key_pem, created_at, activated_at) VALUES ($1,$2,'active',$3,$4,$5,$5)`,
        [issuerId, newKeyId, profile, newPublicKeyPem, now],
      );
      return { retiredKeyId: currentKeyId };
    });
  }

  /** Only valid FROM 'active' -- retiring implies "was in active service, now voluntarily taken out
   * of it." A retired or already-revoked key cannot be retired again (deterministic conflict, not
   * silent idempotency -- explicit operator feedback per key-lifecycle Gate 7's own requirement). */
  async retire(issuerId: string, keyId: string, now: Date): Promise<void> {
    return this.database.transaction(async (tx) => {
      const existing = await tx.query<SigningKeyRow>(`SELECT ${SIGNING_KEY_COLUMNS} FROM trust_issuer_signing_key WHERE issuer_id = $1 AND key_id = $2 FOR UPDATE`, [issuerId, keyId]);
      if (existing.rowCount === 0) throw new UnknownSigningKeyError(issuerId, keyId);
      if (existing.rows[0]!.status !== 'active') throw new SigningKeyLifecycleConflictError(`Cannot retire issuer key ${keyId}: it is not currently active (status: ${existing.rows[0]!.status})`);
      await tx.query(`UPDATE trust_issuer_signing_key SET status = 'retired', retired_at = $1 WHERE issuer_id = $2 AND key_id = $3`, [now, issuerId, keyId]);
    });
  }

  /**
   * Valid from 'active' OR 'retired' -- a compromise can be discovered after a key has already been
   * rotated out. Deliberately NOT required to happen only after a successor is already active
   * (key-lifecycle Gate 5's own conditional: "if revoking the current signer requires selecting
   * another active signer first, enforce that invariant" -- this design does not require it,
   * because refusing to revoke a KNOWN-COMPROMISED key merely because no replacement is ready yet
   * would be backwards for incident response). Revoking the current active key directly leaves ZERO
   * active keys, which correctly makes `RotatingTrustCredentialSigner` refuse ALL new issuance
   * ("Exactly one active Trust signing key is required") until an operator activates a
   * replacement -- an issuance outage, not "continuing to issue with a revoked signer." Revoking an
   * already-revoked key is a deterministic conflict, not silent idempotency (same reasoning as
   * `retire`).
   */
  async revoke(issuerId: string, keyId: string, now: Date): Promise<void> {
    return this.database.transaction(async (tx) => {
      const existing = await tx.query<SigningKeyRow>(`SELECT ${SIGNING_KEY_COLUMNS} FROM trust_issuer_signing_key WHERE issuer_id = $1 AND key_id = $2 FOR UPDATE`, [issuerId, keyId]);
      if (existing.rowCount === 0) throw new UnknownSigningKeyError(issuerId, keyId);
      if (existing.rows[0]!.status === 'revoked') throw new SigningKeyLifecycleConflictError(`Issuer key ${keyId} is already revoked`);
      await tx.query(`UPDATE trust_issuer_signing_key SET status = 'revoked', revoked_at = $1 WHERE issuer_id = $2 AND key_id = $3`, [now, issuerId, keyId]);
    });
  }
}
