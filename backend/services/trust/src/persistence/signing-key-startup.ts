import type { ManagedSigningKeyRegistry } from './managed-signing-key-registry.js';
import type { PostgresIssuerSigningKeyStore } from './postgres-issuer-signing-key-store.js';

export type SigningKeyStartupOutcome = 'bootstrapped_fresh' | 'using_existing_active' | 'blocked_recovery_required';

/**
 * The ACTUAL production startup decision (round 7, Codex Critical Fix 2) -- `main.ts` calls this
 * exact function, and so does every test proving it; there is no separate, untested copy of this
 * logic anywhere.
 *
 * WHO CAN CONSTRUCT OR OBTAIN THIS AUTHORITY? Auto-bootstrap is safe ONLY for a genuinely fresh
 * issuer -- zero lifecycle rows EVER, proven by `store.describe()` returning nothing at all -- NOT
 * merely "zero currently active," which could just as easily mean every key was deliberately
 * revoked. A restart must never silently manufacture replacement authority after revocation: that
 * is exactly the attack this function exists to close. When history exists but nothing is active,
 * this returns `blocked_recovery_required` and does NOTHING else -- no key is created, no key is
 * reactivated. Recovering from that state always requires an explicit, privileged operator action
 * (`manage-signing-keys.ts bootstrap`/`rotate`), never something startup does on its own.
 */
export async function initializeIssuerSigningKeyOnStartup(
  store: PostgresIssuerSigningKeyStore,
  registry: ManagedSigningKeyRegistry,
  issuerId: string,
  defaultKeyId: string,
  profile: string,
  now: Date,
): Promise<SigningKeyStartupOutcome> {
  const existing = await store.describe(issuerId);
  if (existing.length === 0) {
    await registry.bootstrap(defaultKeyId, profile, now);
    return 'bootstrapped_fresh';
  }
  if (existing.some((key) => key.status === 'active')) return 'using_existing_active';
  return 'blocked_recovery_required';
}
