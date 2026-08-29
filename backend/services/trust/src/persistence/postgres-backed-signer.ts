import { join } from 'node:path';
import type { TrustCredentialSigner, TrustSignerIdentity } from '../application/issue-credential.js';
import { validateSigningKeyId } from '../domain/signing-key-id.js';
import { LocalFileTrustCredentialSigner } from './local-signer.js';
import type { PostgresIssuerSigningKeyStore } from './postgres-issuer-signing-key-store.js';

/**
 * The REAL production issuance signer (round 7, Codex Critical Fix 1). Deliberately holds NO
 * lifecycle state cached between calls -- WHO CAN CONSTRUCT OR OBTAIN THIS AUTHORITY? PostgreSQL,
 * freshly, at the moment of EVERY issuance, never a long-lived in-memory snapshot. This is why it
 * bypasses `ManagedSigningKeyRegistry.handles()`/`RotatingTrustCredentialSigner` entirely for real
 * issuance: that combination's synchronous, cached `handles()` snapshot is exactly what let an
 * independent operator CLI's rotate/revoke go unnoticed by an already-running server process (the
 * CLI has no way to refresh a snapshot it does not own, and a long-running server was never
 * refreshing it on its own either). Those two classes remain valid, tested, generic utilities --
 * `ManagedSigningKeyRegistry` is still exactly right for the CLI's own short-lived MUTATION
 * operations (bootstrap/rotate/retire/revoke) -- they must simply never again drive a long-running
 * server process's actual per-request signing decision.
 *
 * Runtime requires EXACTLY ONE active key at the moment of signing. The database's own partial
 * unique index enforces AT MOST one active row per issuer -- it does not by itself guarantee
 * exactly one (zero is possible, e.g. after a revocation with no replacement yet), so this class
 * independently checks the count and fails closed on either zero OR more than one (a corrupted/
 * defensive-test state must never be resolved by picking an arbitrary key).
 *
 * The active row's local private-key file is loaded STRICTLY (`allowGenerate: false` -- see
 * `local-signer.ts`): if it is missing or unparseable, this throws rather than silently generating
 * a replacement keypair, which would desynchronize the signer from the public key already published
 * at `GET /v1/trust/issuers/.../verification-keys`. No automatic fallback signer of any kind.
 */
export class PostgresBackedTrustCredentialSigner implements TrustCredentialSigner {
  constructor(
    private readonly store: PostgresIssuerSigningKeyStore,
    private readonly issuerId: string,
    private readonly issuerKeyDir: string,
  ) {}

  async sign(buildPayload: (identity: TrustSignerIdentity) => Uint8Array): Promise<{ readonly issuerId: string; readonly issuerKeyId: string; readonly profile: string; readonly signature: Uint8Array }> {
    const active = (await this.store.describe(this.issuerId)).filter((key) => key.status === 'active');
    if (active.length !== 1) {
      throw new Error(`Trust signing is unavailable: found ${active.length} active issuer keys for ${this.issuerId} (exactly one authoritative active key is required at issuance time)`);
    }
    const keyId = validateSigningKeyId(active[0]!.keyId);
    const signer = new LocalFileTrustCredentialSigner(
      { keyPath: join(this.issuerKeyDir, `${keyId}.pem`), issuerId: this.issuerId, issuerKeyId: keyId },
      { allowGenerate: false },
    );
    return signer.sign(buildPayload);
  }
}
