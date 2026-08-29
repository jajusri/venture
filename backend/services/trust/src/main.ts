import { buildTrustService } from './app.js';
import { readTrustServiceConfig } from './config.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
import { BusinessDeviceCredentialIssuer } from './application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from './persistence/in-memory-credential-store.js';
import { LocalFileTrustCredentialSigner } from './persistence/local-signer.js';
import { PostgresEnrollmentGrantStore } from './persistence/postgres-enrollment-grant-store.js';
import { VerificationKeyDirectory } from './application/verification-keys.js';

const config = readTrustServiceConfig();
// Self-seeded from this process's own signer -- see LocalFileTrustCredentialSigner's doc comment
// for why this needs no separate persisted table (there is exactly one active key per process).
const signer = new LocalFileTrustCredentialSigner({ keyPath: config.issuerKeyPath, issuerId: config.issuerId, issuerKeyId: config.issuerKeyId });
const verificationKeyIssuedAt = new Date();
const verificationKeys = new VerificationKeyDirectory({
  listForIssuer: (issuerId) => Promise.resolve(issuerId === config.issuerId ? [signer.publicVerificationKey(verificationKeyIssuedAt)] : []),
});
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
const enrollmentGrantStore = new PostgresEnrollmentGrantStore(database);
// In-memory credential-issuance idempotency, same accepted limitation as every other caller of
// `BusinessDeviceCredentialIssuer` today (see `InMemoryCredentialIssuanceStore`'s own doc comment
// and `consume-enrollment-grant.ts`'s KNOWN LIMITATION note) -- no `trust_issued_credential` table
// exists yet in any code path, not one newly introduced for enrollment.
const credentialIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 60 * 60 * 1000);
const app = buildTrustService({ verificationKeys, enrollment: { store: enrollmentGrantStore, credentialIssuer } });
await runMigrations(database);
app.addHook('onClose', async () => database.close());
app.log.info({ issuerId: config.issuerId, issuerKeyId: config.issuerKeyId }, 'trust_service_issuer_ready');
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
