/**
 * Relay Service entrypoint -- did not exist before this controlled-pilot operationalization package
 * (see this package's own discovery notes: `buildRelayService` was only ever exercised in tests with
 * fully-injected fakes). Wires the REAL Postgres repository (`PostgresRelayRepository` already
 * existed, production-ready, just never actually launched), a REAL acceptance-evidence signer, and
 * the REAL `PilotAuthorityVerifier` (see that module's own doc comment for exactly what it does and
 * does not prove) against Trust's real HTTP verification-keys endpoint and Trust's shared Postgres
 * authority tables.
 */
import { buildRelayService } from './http/app.js';
import { readRelayServiceConfig } from './config.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
import { PostgresRelayRepository } from './persistence/relay-repository.js';
import { LocalFileRelayAcceptanceSigner } from './persistence/local-relay-signer.js';
import { SignedRelayAcceptanceIssuer } from './application/acceptance-evidence.js';
import { PartitionedRelayIngressLimiter } from './application/relay-protections.js';
import { PilotAuthorityVerifier, relayAcknowledgementVerifier, relayMailboxVerifier, relaySubmissionVerifier } from './application/pilot-authority-verifier.js';
import { HttpTrustVerificationKeyFetcher } from './application/http-verification-key-fetcher.js';
import { PostgresTrustAuthoritySnapshotReader } from '../../trust/src/persistence/postgres-authority-snapshot-reader.js';
import { PostgresRelayReplayGuard } from './persistence/postgres-relay-replay-guard.js';

const config = readRelayServiceConfig();
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
// Idempotent (see migrations.ts's own version-registry check) -- safe regardless of whether Trust
// has already run migrations against this same database, so operator launch order never matters.
await runMigrations(database);
const repository = new PostgresRelayRepository(database);
const issuer = new SignedRelayAcceptanceIssuer(new LocalFileRelayAcceptanceSigner(config.relayId, 'P256-SHA256-v1', config.relayAcceptanceKeyPath));
const ingressLimiter = new PartitionedRelayIngressLimiter(config.ingressMaxRequestsPerWindow, config.ingressWindowMs);
const verifierCore = new PilotAuthorityVerifier(
  new HttpTrustVerificationKeyFetcher(config.trustBaseUrl),
  new PostgresTrustAuthoritySnapshotReader(database),
  new PostgresRelayReplayGuard(database),
);

const app = buildRelayService({
  repository, issuer, ingressLimiter,
  verifier: relaySubmissionVerifier(verifierCore),
  mailboxVerifier: relayMailboxVerifier(verifierCore),
  acknowledgementVerifier: relayAcknowledgementVerifier(verifierCore),
});
app.addHook('onClose', async () => database.close());

let stopping = false;
async function stop(signal: string): Promise<void> {
  if (stopping) return;
  stopping = true;
  app.log.info({ signal }, 'relay_service_stopping');
  await app.close();
}
process.once('SIGINT', () => void stop('SIGINT'));
process.once('SIGTERM', () => void stop('SIGTERM'));
app.log.info({ relayId: config.relayId, trustBaseUrl: config.trustBaseUrl, trustIssuerId: config.trustIssuerId }, 'relay_service_ready');
await app.listen({ host: config.host, port: config.port });
