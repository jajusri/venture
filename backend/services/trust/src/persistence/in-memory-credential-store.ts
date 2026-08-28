import type { CredentialIssuanceStore, IssuedBusinessDeviceCredential } from '../application/issue-credential.js';

/**
 * Process-lifetime-only `CredentialIssuanceStore`. No `trust_issued_credential` table exists in the
 * current migration set (`backend/packages/persistence/src/migrations.ts`) -- issued credentials
 * have never had a durable home in this codebase. Adding one is a genuine schema change (a new
 * migration), which this controlled-pilot operationalization work deliberately does not add (see
 * the final report's PRODUCTION PROVISIONING BLOCKER section).
 *
 * This is safe to leave in-memory for now because a credential is a short-lived (bounded to at most
 * 24h, enforced by `BusinessDeviceCredentialIssuer`'s own constructor), self-contained, signed
 * bearer proof -- `findByIntent`'s only job is avoiding a duplicate issuance for the exact same
 * intentId within a single process run. Losing that idempotency guard across a process restart is
 * not a security issue (a fresh issuance for the same intent is still a valid, correctly-authorized
 * credential -- it merely isn't "the same one"), only a minor operational nicety.
 */
export class InMemoryCredentialIssuanceStore implements CredentialIssuanceStore {
  private readonly issued = new Map<string, IssuedBusinessDeviceCredential>();

  findByIntent(businessId: string, deviceId: string, intentId: string): Promise<IssuedBusinessDeviceCredential | null> {
    return Promise.resolve(this.issued.get(this.key(businessId, deviceId, intentId)) ?? null);
  }
  record(intentId: string, credential: IssuedBusinessDeviceCredential): Promise<IssuedBusinessDeviceCredential> {
    this.issued.set(this.key(credential.claims.businessId, credential.claims.deviceId, intentId), credential);
    return Promise.resolve(credential);
  }
  private key(businessId: string, deviceId: string, intentId: string): string { return `${businessId}::${deviceId}::${intentId}`; }
}
