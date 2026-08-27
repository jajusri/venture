import { describe, expect, it } from 'vitest';
import type { Database, DatabaseSession, QueryResult } from '../packages/persistence/src/database.js';
import { runMigrations } from '../packages/persistence/src/migrations.js';
import { BusinessDeviceCredentialIssuer, type CredentialIssuanceStore, type IssuedBusinessDeviceCredential, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { AuthorityScope, identifier, type BusinessMembership, type RegisteredBusinessDevice } from '../services/trust/src/domain/authority.js';

class FailingDatabase implements Database {
  rolledBack = false;
  query<Row extends Record<string, unknown>>(): Promise<QueryResult<Row>> { return Promise.reject(new Error('database temporarily unavailable')); }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> { try { return await work(this); } catch (error) { this.rolledBack = true; throw error; } }
  close() { return Promise.resolve(); }
}
class DurableIssuanceStore implements CredentialIssuanceStore {
  value: IssuedBusinessDeviceCredential | null = null;
  findByIntent() { return Promise.resolve(this.value); }
  record(_intent: string, value: IssuedBusinessDeviceCredential) { this.value = value; return Promise.resolve(value); }
}
const businessId = identifier('business-1', 'BusinessId'); const actorId = identifier('actor-1', 'ActorId'); const membershipId = identifier('member-1', 'MembershipId');
const scope = new AuthorityScope(['issue_credentials', 'send_orders']);
const membership: BusinessMembership = { membershipId, businessId, actorId, status: 'active', authorityScope: scope, authorityEpoch: { value: 1 }, createdAt: new Date(1), modifiedAt: new Date(1) };
const device: RegisteredBusinessDevice = { businessId, actorId, membershipId, deviceId: identifier('device-1', 'DeviceId'), deviceKeyId: identifier('key-1', 'DeviceKeyId'), deviceKeyVersion: 1, publicKey: new Uint8Array([1]), publicKeyFingerprint: 'fp', status: 'active', authorityEpoch: { value: 1 }, createdAt: new Date(1) };
const input = { business: { businessId, status: 'active' as const }, membership, device, requestedScope: new AuthorityScope(['send_orders']), intentId: 'intent-1' };
const signer: TrustCredentialSigner = { sign: (build) => { const identity = { issuerId: 'issuer', issuerKeyId: 'key', profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([1]) }); } };

describe('Trust Service failure and recovery', () => {
  it('fails closed and rolls back when PostgreSQL is unavailable', async () => {
    const database = new FailingDatabase(); await expect(runMigrations(database)).rejects.toThrow('temporarily unavailable'); expect(database.rolledBack).toBe(true);
  });
  it('preserves idempotent issuance across service restart', async () => {
    const store = new DurableIssuanceStore(); const first = await new BusinessDeviceCredentialIssuer(store, signer, 60_000, () => new Date(10)).issue(input);
    const afterRestart = await new BusinessDeviceCredentialIssuer(store, signer, 60_000, () => new Date(20)).issue(input);
    expect(afterRestart.claims.credentialId).toBe(first.claims.credentialId); expect(afterRestart.claims.issuedAt).toEqual(first.claims.issuedAt);
  });
  it('does not persist an unsigned credential when secure signing fails', async () => {
    const store = new DurableIssuanceStore(); const failedSigner: TrustCredentialSigner = { sign: () => Promise.reject(new Error('secure signer unavailable')) };
    await expect(new BusinessDeviceCredentialIssuer(store, failedSigner, 60_000).issue(input)).rejects.toThrow('signer unavailable'); expect(store.value).toBeNull();
  });
});
