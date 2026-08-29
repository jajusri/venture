import { join } from 'node:path';
import type { ManagedSignerHandle } from '../application/signer-rotation.js';
import { LocalFileTrustCredentialSigner } from './local-signer.js';
import { PostgresIssuerSigningKeyStore, type IssuerSigningKeyDescriptor } from './postgres-issuer-signing-key-store.js';

/**
 * Bridges Postgres-authoritative key-lifecycle STATUS (`PostgresIssuerSigningKeyStore`) to
 * `RotatingTrustCredentialSigner`'s existing, unmodified, ALREADY-CERTIFIED contract
 * (`signer-rotation.ts`): a synchronous `handles(): readonly ManagedSignerHandle[]` supplier,
 * because signing itself must not depend on Trust's own database being reachable at sign-time.
 *
 * The in-memory snapshot this class holds is refreshed from Postgres at Trust startup and after
 * EVERY mutating operation performed THROUGH this registry (bootstrap/rotate/retire/revoke) --
 * never mutated independently of what was actually just committed. This is safe for a single-process
 * Trust deployment (the only kind this pilot runs): no other process can mutate
 * `trust_issuer_signing_key` concurrently except through this same registry's own methods, so the
 * in-memory snapshot can never drift from what Postgres actually holds as of this process's own last
 * write. (A future multi-instance deployment would need each instance to also poll/refresh
 * independently -- out of scope here, not a regression from anything that exists today.)
 *
 * Each `ManagedSignerHandle.signer` is a `LocalFileTrustCredentialSigner` -- private key material
 * itself is NEVER read from or written to Postgres, only from a local file per key_id (unchanged
 * security posture from the pre-existing single-key pattern; see that class's own doc comment for
 * why: no HSM/KMS available in this environment, so file-mode-0600 local storage is the accepted
 * pilot-grade protection, now extended to multiple keys instead of just one).
 */
export class ManagedSigningKeyRegistry {
  private handlesSnapshot: readonly ManagedSignerHandle[] = [];

  constructor(
    private readonly store: PostgresIssuerSigningKeyStore,
    private readonly issuerId: string,
    private readonly issuerKeyDir: string,
  ) {}

  /** Synchronous, per `RotatingTrustCredentialSigner`'s existing contract -- returns whatever was
   * loaded by the most recent `refresh()` (called once at startup by `main.ts`, and again after
   * every successful mutation below). */
  handles(): readonly ManagedSignerHandle[] { return this.handlesSnapshot; }

  async refresh(): Promise<void> {
    const descriptors = await this.store.describe(this.issuerId);
    this.handlesSnapshot = descriptors.map((descriptor) => ({
      signer: new LocalFileTrustCredentialSigner({ keyPath: this.keyPathFor(descriptor.keyId), issuerId: this.issuerId, issuerKeyId: descriptor.keyId }),
      status: descriptor.status,
    }));
  }

  async describe(): Promise<readonly IssuerSigningKeyDescriptor[]> { return this.store.describe(this.issuerId); }

  /** Generates (or, on a safe retry, loads) the local private key file for `keyId` FIRST, then
   * records it in Postgres as the issuer's first active key. If the Postgres write fails (e.g. an
   * active key already exists), the local file may already have been created -- harmless (still
   * file-mode-0600, unreferenced by any Postgres row) and safely reusable if the operator retries
   * with the same `keyId`, since `LocalFileTrustCredentialSigner` loads an existing file rather than
   * regenerating it. */
  async bootstrap(keyId: string, profile: string, now: Date): Promise<void> {
    const signer = new LocalFileTrustCredentialSigner({ keyPath: this.keyPathFor(keyId), issuerId: this.issuerId, issuerKeyId: keyId });
    const publicKeyPem = signer.publicVerificationKey(now).publicKey;
    await this.store.bootstrap(this.issuerId, keyId, profile, publicKeyPem, now);
    await this.refresh();
  }

  /** Atomic Postgres rotation (see `PostgresIssuerSigningKeyStore.rotate`'s own doc comment for the
   * exact concurrency guarantee), followed by a registry refresh so `handles()` reflects the new
   * active key immediately -- no restart required. */
  async rotate(newKeyId: string, profile: string, now: Date): Promise<{ readonly retiredKeyId: string }> {
    const signer = new LocalFileTrustCredentialSigner({ keyPath: this.keyPathFor(newKeyId), issuerId: this.issuerId, issuerKeyId: newKeyId });
    const publicKeyPem = signer.publicVerificationKey(now).publicKey;
    const result = await this.store.rotate(this.issuerId, newKeyId, profile, publicKeyPem, now);
    await this.refresh();
    return result;
  }

  async retire(keyId: string, now: Date): Promise<void> {
    await this.store.retire(this.issuerId, keyId, now);
    await this.refresh();
  }

  async revoke(keyId: string, now: Date): Promise<void> {
    await this.store.revoke(this.issuerId, keyId, now);
    await this.refresh();
  }

  private keyPathFor(keyId: string): string { return join(this.issuerKeyDir, `${keyId}.pem`); }
}
