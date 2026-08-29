import { createPrivateKey, createPublicKey, generateKeyPairSync, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import type { RelayAcceptanceSigner } from '../application/acceptance-evidence.js';

/**
 * CONTROLLED-PILOT / LOCAL-DEV SIGNING KEY for Relay's own acceptance evidence -- see
 * `../../../trust/src/persistence/local-signer.ts`'s doc comment; the same limitations apply here
 * (no HSM, no rotation, generated on first use, must never be committed/logged).
 */
export class LocalFileRelayAcceptanceSigner implements RelayAcceptanceSigner {
  private readonly privateKeyPem: string;

  constructor(private readonly relayId: string, private readonly profile: string, keyPath: string) {
    if (existsSync(keyPath)) {
      this.privateKeyPem = readFileSync(keyPath, 'utf8');
    } else {
      const keyPair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
      this.privateKeyPem = keyPair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
      mkdirSync(dirname(keyPath), { recursive: true });
      writeFileSync(keyPath, this.privateKeyPem, { mode: 0o600 });
    }
    // Exercises the public half once so a misconfigured/corrupt key file fails fast at startup.
    createPublicKey(createPrivateKey(this.privateKeyPem));
  }

  sign(payload: Uint8Array): Promise<{ readonly relayId: string; readonly profile: string; readonly evidence: Uint8Array }> {
    return Promise.resolve({ relayId: this.relayId, profile: this.profile, evidence: sign('sha256', payload, this.privateKeyPem) });
  }
}
