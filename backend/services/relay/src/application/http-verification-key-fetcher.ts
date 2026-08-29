import type { TrustVerificationKeyFetcher } from './pilot-authority-verifier.js';

/**
 * Fetches an issuer's verification keys from Trust's real, already-existing
 * `GET /v1/trust/issuers/:issuerId/verification-keys` route (wired live in `main.ts` once this
 * package's changes land) and caches the result briefly -- this is the "cached verification key"
 * design Android's own naming (`IssuerVerificationKeyCache`) already implies is the intended shape,
 * completed here on the Relay side rather than invented from scratch.
 */
export class HttpTrustVerificationKeyFetcher implements TrustVerificationKeyFetcher {
  private readonly cache = new Map<string, { readonly expiresAt: number; readonly keys: readonly { readonly issuerKeyId: string; readonly publicKey: string; readonly status: string }[] }>();

  constructor(private readonly trustBaseUrl: string, private readonly cacheTtlMs = 60_000, private readonly now: () => number = Date.now) {}

  async fetch(issuerId: string, issuerKeyId: string): Promise<string | null> {
    const cached = this.cache.get(issuerId);
    const keys = cached && cached.expiresAt > this.now() ? cached.keys : await this.refresh(issuerId);
    const match = keys.find((key) => key.issuerKeyId === issuerKeyId);
    return match && match.status === 'active' ? match.publicKey : null;
  }

  private async refresh(issuerId: string): Promise<readonly { readonly issuerKeyId: string; readonly publicKey: string; readonly status: string }[]> {
    const response = await fetch(`${this.trustBaseUrl}/v1/trust/issuers/${encodeURIComponent(issuerId)}/verification-keys`);
    if (!response.ok) return [];
    const body = (await response.json()) as { readonly keys?: readonly { readonly issuerKeyId: string; readonly publicKey: string; readonly status: string }[] };
    const keys = body.keys ?? [];
    this.cache.set(issuerId, { expiresAt: this.now() + this.cacheTtlMs, keys });
    return keys;
  }
}
