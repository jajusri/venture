import { join } from 'node:path';
import { validateSigningKeyId } from '../domain/signing-key-id.js';
import { LocalFileTrustCredentialSigner } from './local-signer.js';
import { PostgresIssuerSigningKeyStore, type IssuerSigningKeyDescriptor } from './postgres-issuer-signing-key-store.js';

/**
 * Pure MUTATION orchestration for issuer signing-key lifecycle: bootstrap/rotate/retire/revoke each
 * (a) manage the local private-key file for whatever key_id is involved and (b) record the
 * resulting lifecycle change in Postgres (`PostgresIssuerSigningKeyStore`) -- private key material
 * itself is NEVER read from or written to Postgres, only from a local file per key_id (unchanged
 * pilot-grade posture: no HSM/KMS available, file-mode-0600 local storage).
 *
 * ROUND 7 (Codex Critical Fix 1): this class deliberately holds NO cached lifecycle snapshot of any
 * kind -- it previously exposed a synchronous `handles(): ManagedSignerHandle[]` accessor feeding
 * `RotatingTrustCredentialSigner` for real credential issuance, refreshed only when a mutation was
 * performed THROUGH this exact instance. That was safe only under the false assumption that nothing
 * else could ever mutate `trust_issuer_signing_key` -- but the operator CLI (`manage-signing-keys.ts`)
 * runs as an entirely separate process with its OWN registry instance, so a real rotate/revoke
 * committed there was invisible to an already-running Trust server's cached snapshot, which kept
 * signing with stale authority until the server happened to restart. That accessor and the
 * `RotatingTrustCredentialSigner` wiring it fed have been removed here entirely -- not merely
 * stopped being called -- so the pattern cannot be silently reintroduced by a future caller reaching
 * for "the obvious existing method." Real issuance now goes through
 * `PostgresBackedTrustCredentialSigner`, which re-reads Postgres fresh on every single `sign()`
 * call and never caches anything across calls. `RotatingTrustCredentialSigner`/`ManagedSignerHandle`
 * (`signer-rotation.ts`) remain valid, independently-tested, generic utilities in their own right --
 * unmodified, and simply no longer wired to this registry.
 */
export class ManagedSigningKeyRegistry {
  constructor(
    private readonly store: PostgresIssuerSigningKeyStore,
    private readonly issuerId: string,
    private readonly issuerKeyDir: string,
  ) {}

  async describe(): Promise<readonly IssuerSigningKeyDescriptor[]> { return this.store.describe(this.issuerId); }

  /** Generates (or, on a safe retry, loads) the local private key file for `keyId` FIRST, then
   * records it in Postgres as the issuer's first active key. If the Postgres write fails (e.g. an
   * active key already exists), the local file may already have been created -- harmless (still
   * file-mode-0600, unreferenced by any Postgres row) and safely reusable if the operator retries
   * with the same `keyId`, since `LocalFileTrustCredentialSigner` loads an existing file rather than
   * regenerating it. */
  async bootstrap(keyId: string, profile: string, now: Date): Promise<void> {
    const validatedKeyId = validateSigningKeyId(keyId);
    const signer = new LocalFileTrustCredentialSigner({ keyPath: this.keyPathFor(validatedKeyId), issuerId: this.issuerId, issuerKeyId: validatedKeyId });
    const publicKeyPem = signer.publicVerificationKey(now).publicKey;
    await this.store.bootstrap(this.issuerId, validatedKeyId, profile, publicKeyPem, now);
  }

  /** Atomic Postgres rotation -- see `PostgresIssuerSigningKeyStore.rotate`'s own doc comment for
   * the exact concurrency guarantee. Any Trust server process (including this one, if it happens to
   * be the same process) picks up the new active key on its NEXT issuance automatically, since real
   * issuance always re-reads Postgres fresh -- no registry refresh step exists or is needed. */
  async rotate(newKeyId: string, profile: string, now: Date): Promise<{ readonly retiredKeyId: string }> {
    const validatedKeyId = validateSigningKeyId(newKeyId);
    const signer = new LocalFileTrustCredentialSigner({ keyPath: this.keyPathFor(validatedKeyId), issuerId: this.issuerId, issuerKeyId: validatedKeyId });
    const publicKeyPem = signer.publicVerificationKey(now).publicKey;
    return this.store.rotate(this.issuerId, validatedKeyId, profile, publicKeyPem, now);
  }

  async retire(keyId: string, now: Date): Promise<void> {
    await this.store.retire(this.issuerId, validateSigningKeyId(keyId), now);
  }

  async revoke(keyId: string, now: Date): Promise<void> {
    await this.store.revoke(this.issuerId, validateSigningKeyId(keyId), now);
  }

  private keyPathFor(keyId: string): string { return join(this.issuerKeyDir, `${validateSigningKeyId(keyId)}.pem`); }
}
