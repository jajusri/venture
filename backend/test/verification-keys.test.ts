import { afterEach, describe, expect, it } from 'vitest';
import { VerificationKeyDirectory } from '../services/trust/src/application/verification-keys.js';
import { buildTrustService } from '../services/trust/src/app.js';
const apps: ReturnType<typeof buildTrustService>[] = []; afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));
describe('public verification material', () => {
  it('exposes cacheable versioned public keys and retains rotation overlap', async () => {
    const directory = new VerificationKeyDirectory({ listForIssuer: () => Promise.resolve([
      { issuerId: 'issuer-1', issuerKeyId: 'old', profile: 'Ed25519-v1', publicKey: 'PUBLIC-OLD', validFrom: new Date(1), status: 'retired' },
      { issuerId: 'issuer-1', issuerKeyId: 'new', profile: 'Ed25519-v1', publicKey: 'PUBLIC-NEW', validFrom: new Date(2), status: 'active' },
    ]) });
    const app = buildTrustService({ verificationKeys: directory, now: () => new Date(10) }); apps.push(app);
    const response = await app.inject({ method: 'GET', url: '/v1/trust/issuers/issuer-1/verification-keys' });
    expect(response.statusCode).toBe(200); expect(response.headers['cache-control']).toContain('max-age');
    const body = response.json<{ keys: { issuerKeyId: string }[] }>();
    expect(body.keys.map((key) => key.issuerKeyId)).toEqual(['old', 'new']);
    expect(response.body).not.toContain('private');
  });
});
