import { buildTrustService } from './app.js';
import { readTrustServiceConfig } from './config.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
import { BusinessDeviceCredentialIssuer } from './application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from './persistence/in-memory-credential-store.js';
import { ManagedSigningKeyRegistry } from './persistence/managed-signing-key-registry.js';
import { PostgresBackedTrustCredentialSigner } from './persistence/postgres-backed-signer.js';
import { PostgresIssuerSigningKeyStore } from './persistence/postgres-issuer-signing-key-store.js';
import { PostgresEnrollmentGrantStore } from './persistence/postgres-enrollment-grant-store.js';
import { PostgresTrustAuthoritySnapshotReader } from './persistence/postgres-authority-snapshot-reader.js';
import { initializeIssuerSigningKeyOnStartup } from './persistence/signing-key-startup.js';
import { VerificationKeyDirectory } from './application/verification-keys.js';

const config = readTrustServiceConfig();
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
await runMigrations(database);

// Server-authoritative issuer signing-key lifecycle (round 6, corrected round 7). Postgres tracks
// status (active/retired/revoked); a local file per key_id holds the actual private key material.
// `initializeIssuerSigningKeyOnStartup` auto-bootstraps ONLY a genuinely fresh issuer (zero
// lifecycle rows ever) -- an issuer with history but zero active keys (e.g. revoked with no
// replacement yet activated) is left exactly as-is: `blocked_recovery_required`, never silently
// re-bootstrapped. See that function's own doc comment for why this distinction is the entire
// point (Codex Critical Fix 2). Recovery from that state is always an explicit, privileged operator
// action via `scripts/manage-signing-keys.ts`, never something startup does automatically.
const signingKeyStore = new PostgresIssuerSigningKeyStore(database);
const signingKeyRegistry = new ManagedSigningKeyRegistry(signingKeyStore, config.issuerId, config.issuerKeyDir);
const signingKeyStartupOutcome = await initializeIssuerSigningKeyOnStartup(
  signingKeyStore, signingKeyRegistry, config.issuerId, config.issuerKeyId, 'P256-SHA256-v1', new Date(),
);

// Real issuance signer (Codex Critical Fix 1): re-reads Postgres fresh on EVERY sign() call, never
// a cached snapshot -- see `PostgresBackedTrustCredentialSigner`'s own doc comment for exactly why.
// An independent operator CLI rotate/revoke takes effect on this server's very next issuance with
// no restart and no refresh call of any kind, because there is nothing here to refresh.
const signer = new PostgresBackedTrustCredentialSigner(signingKeyStore, config.issuerId, config.issuerKeyDir);

const verificationKeys = new VerificationKeyDirectory(signingKeyStore);
const enrollmentGrantStore = new PostgresEnrollmentGrantStore(database);
// In-memory credential-issuance idempotency, same accepted limitation as every other caller of
// `BusinessDeviceCredentialIssuer` today (see `InMemoryCredentialIssuanceStore`'s own doc comment
// and `consume-enrollment-grant.ts`'s KNOWN LIMITATION note) -- no `trust_issued_credential` table
// exists yet in any code path, not one newly introduced for enrollment.
const credentialIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 60 * 60 * 1000);
const authoritySnapshots = new PostgresTrustAuthoritySnapshotReader(database);
const app = buildTrustService({ verificationKeys, enrollment: { store: enrollmentGrantStore, credentialIssuer }, authoritySnapshots });
app.addHook('onClose', async () => database.close());
app.log.info({ issuerId: config.issuerId, signingKeyStartupOutcome }, 'trust_service_issuer_ready');
if (signingKeyStartupOutcome === 'blocked_recovery_required') {
  app.log.error({ issuerId: config.issuerId }, 'trust_service_issuer_signing_blocked -- issuer signing-key history exists with zero active keys; new credential issuance will fail closed until an operator runs manage-signing-keys.ts bootstrap/rotate to activate a replacement key');
}
let stopping = false;
async function stop(signal: string): Promise<void> {
  if (stopping) return;
  stopping = true;
  app.log.info({ signal }, 'trust_service_stopping');
  await app.close();
}
process.once('SIGINT', () => void stop('SIGINT'));
process.once('SIGTERM', () => void stop('SIGTERM'));
await app.listen({ host: config.host, port: config.port });
