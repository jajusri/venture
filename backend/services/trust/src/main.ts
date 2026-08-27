import { buildTrustService } from './app.js';
import { readTrustServiceConfig } from './config.js';
import { PostgresDatabase } from '../../../packages/persistence/src/postgres-database.js';
import { runMigrations } from '../../../packages/persistence/src/migrations.js';
const app = buildTrustService();
const config = readTrustServiceConfig();
const database = new PostgresDatabase(config.databaseUrl, config.databasePoolMax);
await runMigrations(database);
app.addHook('onClose', async () => database.close());
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
