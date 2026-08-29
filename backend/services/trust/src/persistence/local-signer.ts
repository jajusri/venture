import { createPrivateKey, createPublicKey, generateKeyPairSync, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import type { TrustCredentialSigner, TrustSignerIdentity } from '../application/issue-credential.js';
import type { TrustVerificationKey } from '../application/verification-keys.js';

/**
 * CONTROLLED-PILOT / LOCAL-DEV SIGNING KEY ONLY.
 *
 * This is NOT production key management: no HSM, no KMS, no automated rotation, no revocation
 * store. It exists so Trust's application services (`BusinessDeviceCredentialIssuer`, which already
 * defines the exact signing contract this implements) can be exercised for real -- real ECDSA
 * signing, real verification -- during local controlled-pilot development, without inventing any
 * new cryptographic scheme: `sign()` here does exactly what `backend/test/trust-flow.integration.test.ts`
 * already does ad hoc (an EC P-256 keypair + `crypto.sign('sha256', payload, privateKey)`), just
 * persisted to a local file so the same key survives across separate process invocations (the Trust
 * server and the dev-provisioning CLI are separate processes and must sign/verify consistently).
 *
 * The private key file is written with mode 0600 to a path matching the repository's own
 * `*.pem`/`.local/` gitignore patterns -- it must never be committed, logged, or printed. If the
 * file does not exist, a fresh key is generated on first use (convenient for a fresh pilot
 * environment); this means the issuer key rotates implicitly on `rm` of that file, which is exactly
 * the "not durable across environment resets" limitation this class's doc comment exists to flag.
 */
export interface LocalTrustSignerConfig {
  readonly keyPath: string;
  readonly issuerId: string;
  /** Distinguishes key generations without a rotation store -- bumped only if the operator changes
   * it; a fresh generated key otherwise keeps whatever value was already configured. */
  readonly issuerKeyId: string;
}

const PROFILE = 'P256-SHA256-v1';

export class LocalFileTrustCredentialSigner implements TrustCredentialSigner {
  private readonly privateKeyPem: string;
  private readonly publicKeyPem: string;
  private readonly identity: TrustSignerIdentity;

  /**
   * `options.allowGenerate` (default `true`, unchanged for every pre-existing caller) governs what
   * happens when `config.keyPath` does not exist. Round 7 (Codex Critical Fix on missing/corrupt
   * active PEM): the real issuance path (`PostgresBackedTrustCredentialSigner`) constructs with
   * `allowGenerate: false` for whichever key_id PostgreSQL currently reports active -- if that
   * key's local file is missing, silently generating a replacement would desynchronize the signer
   * from the public key already published at the verification-keys endpoint (a fresh keypair's
   * public half would never match what was recorded at bootstrap/rotation time), so issuance must
   * fail closed instead. Administrative key-creation call sites (`ManagedSigningKeyRegistry.bootstrap`/
   * `.rotate`, which are creating a brand-new key_id that has never existed before) keep the default
   * `true` -- generating a new key IS their entire purpose.
   */
  constructor(config: LocalTrustSignerConfig, options: { allowGenerate?: boolean } = {}) {
    const allowGenerate = options.allowGenerate ?? true;
    if (existsSync(config.keyPath)) {
      this.privateKeyPem = readFileSync(config.keyPath, 'utf8');
    } else if (allowGenerate) {
      const keyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
      this.privateKeyPem = keyPair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
      mkdirSync(dirname(config.keyPath), { recursive: true });
      writeFileSync(config.keyPath, this.privateKeyPem, { mode: 0o600 });
    } else {
      throw new Error(`Issuer signing key file not found for key ${config.issuerKeyId}: ${config.keyPath}`);
    }
    this.publicKeyPem = createPublicKey(createPrivateKey(this.privateKeyPem)).export({ type: 'spki', format: 'pem' }).toString();
    this.identity = { issuerId: config.issuerId, issuerKeyId: config.issuerKeyId, profile: PROFILE };
  }

  sign(buildPayload: (identity: TrustSignerIdentity) => Uint8Array): Promise<{ readonly issuerId: string; readonly issuerKeyId: string; readonly profile: string; readonly signature: Uint8Array }> {
    const payload = buildPayload(this.identity);
    const signature = sign('sha256', payload, this.privateKeyPem);
    return Promise.resolve({ ...this.identity, signature });
  }

  /** The public half of this signer's own key, in the exact shape `VerificationKeyStore` publishes
   * -- self-seeded at Trust startup (see `main.ts`), never requiring a separate persisted table
   * since there is exactly one active key per running Trust process. */
  publicVerificationKey(validFrom: Date): TrustVerificationKey {
    return { issuerId: this.identity.issuerId, issuerKeyId: this.identity.issuerKeyId, profile: this.identity.profile, publicKey: this.publicKeyPem, validFrom, status: 'active' };
  }
}

/** True if a key file already exists at `keyPath` -- lets callers (e.g. the dev CLI's `status`
 * command) report "issuer key: present/will be generated on first use" without ever reading it. */
export function localSignerKeyExists(keyPath: string): boolean { return existsSync(keyPath); }
