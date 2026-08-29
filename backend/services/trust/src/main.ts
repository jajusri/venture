import { buildTrustService } from './app.js';
import { readTrustServiceConfig } from './config.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
import { BusinessDeviceCredentialIssuer } from './application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from './persistence/in-memory-credential-store.js';
import { ManagedSigningKeyRegistry } from './persistence/managed-signing-key-registry.js';
import { DuplicateActiveSigningKeyError, PostgresIssuerSigningKeyStore } from './persistence/postgres-issuer-signing-key-store.js';
import { PostgresEnrollmentGrantStore } from './persistence/postgres-enrollment-grant-store.js';
import { PostgresTrustAuthoritySnapshotReader } from './persistence/postgres-authority-snapshot-reader.js';
import { RotatingTrustCredentialSigner } from './application/signer-rotation.js';
import { VerificationKeyDirectory } from './application/verification-keys.js';

const config = readTrustServiceConfig();
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
await runMigrations(database);

// Server-authoritative issuer signing-key lifecycle (round 6): Postgres tracks status
// (active/retired/revoked), a local file per key_id holds the actual private key material -- see
// `ManagedSigningKeyRegistry`'s own doc comment for exactly why this split, and why it is safe for
// a single-process Trust deployment. Auto-bootstraps the FIRST key on a fresh install using the
// existing `issuerKeyId` config value, preserving the pre-existing "just works on first run" pilot
// ergonomics `LocalFileTrustCredentialSigner` alone used to provide -- this is the process's own
// one-time initialization, not a caller self-selecting authority (key-lifecycle invariant 7):
// subsequent rotation/retirement/revocation happens only through the operator CLI
// (`scripts/manage-signing-keys.ts`), never automatically.
const signingKeyStore = new PostgresIssuerSigningKeyStore(database);
const signingKeyRegistry = new ManagedSigningKeyRegistry(signingKeyStore, config.issuerId, config.issuerKeyDir);
try {
  await signingKeyRegistry.bootstrap(config.issuerKeyId, 'P256-SHA256-v1', new Date());
} catch (error) {
  if (!(error instanceof DuplicateActiveSigningKeyError)) throw error;
  await signingKeyRegistry.refresh();
}
const signer = new RotatingTrustCredentialSigner(() => signingKeyRegistry.handles());

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
const active = (await signingKeyRegistry.describe()).find((key) => key.status === 'active');
app.log.info({ issuerId: config.issuerId, activeIssuerKeyId: active?.keyId }, 'trust_service_issuer_ready');
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
