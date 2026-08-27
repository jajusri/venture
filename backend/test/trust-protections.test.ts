import { describe, expect, it } from 'vitest';
import { TrustProtectionGate, type PartitionedTrustRateLimiter, type ReplayEvidenceStore, type TrustSecurityAuditSink } from '../services/trust/src/application/trust-protections.js';
class ReplayStore implements ReplayEvidenceStore { readonly keys = new Set<string>(); reserve(partition: string, key: string) { const value = `${partition}:${key}`; if (this.keys.has(value)) return Promise.resolve('duplicate' as const); this.keys.add(value); return Promise.resolve('reserved' as const); } }
describe('bounded Trust protections', () => {
  it('partitions replay evidence by business device and action', async () => {
    const limiter: PartitionedTrustRateLimiter = { consume: () => Promise.resolve({ allowed: true }) }; const replay = new ReplayStore(); const audit: TrustSecurityAuditSink = { record: () => Promise.resolve() };
    const gate = new TrustProtectionGate(limiter, replay, audit); const base = { action: 'credential_issuance' as const, businessId: 'business-1', deviceId: 'device-1', now: new Date(1) };
    expect(await gate.authorize(base, 'intent-1')).toBe('allowed'); expect(await gate.authorize(base, 'intent-1')).toBe('duplicate');
    expect(await gate.authorize({ ...base, businessId: 'business-2' }, 'intent-1')).toBe('allowed');
  });
  it('fails closed and emits a bounded audit event when limited', async () => {
    const events: string[] = []; const gate = new TrustProtectionGate({ consume: () => Promise.resolve({ allowed: false, retryAfterMs: 1000 }) }, new ReplayStore(), { record: (event) => { events.push(event.kind); return Promise.resolve(); } });
    await expect(gate.authorize({ action: 'authority_change', businessId: 'business-1', actorId: 'actor-1', now: new Date(1) })).rejects.toThrow('rate limited');
    expect(events).toEqual(['rate_limited_authority_change']);
  });
});
