import { buildTrustService } from './app.js';
import { readTrustServiceConfig } from './config.js';
const app = buildTrustService();
const config = readTrustServiceConfig();
let stopping = false;
async function stop(signal: string): Promise<void> {
  if (stopping) return;
  stopping = true;
  app.log.info({ signal }, 'trust_service_stopping');
  await app.close();
}
process.once('SIGINT', () => void stop('SIGINT'));
process.once('SIGTERM', () => void stop('SIGTERM'));
await app.listen(config);
