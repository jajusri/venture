import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { loadConfig } from '../../../src/config/index.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { createTallyMockFetch } from '../../helpers/mock-fetch.js';

describe('TallyConnectionManager', () => {
  it('pings Tally successfully', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const manager = new TallyConnectionManager({
      config: loadConfig({ env: 'test', tallyRetryMaxAttempts: 1 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config: loadConfig({ env: 'test' }),
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });

    await manager.start();
    await expect(manager.ping()).resolves.toBe(true);
    expect(manager.getState()).toBe('connected');
    await manager.stop();
  });

  it('returns false when ping fails', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const manager = new TallyConnectionManager({
      config: loadConfig({ env: 'test', tallyRetryMaxAttempts: 1 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config: loadConfig({ env: 'test' }),
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });

    await manager.start();
    await expect(manager.ping()).resolves.toBe(false);
    expect(manager.getDiagnostics().failedRequests).toBeGreaterThan(0);
    await manager.stop();
  });

  it('records diagnostics snapshot', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true });
    const manager = new TallyConnectionManager({
      config: loadConfig({ env: 'test', tallyRetryMaxAttempts: 1 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config: loadConfig({ env: 'test' }),
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });

    await manager.start();
    await manager.ping();
    const diagnostics = manager.getDiagnostics();
    expect(diagnostics.totalRequests).toBe(1);
    expect(diagnostics.host).toBe('localhost');
    expect(diagnostics.port).toBe(9000);
    await manager.stop();
  });
});
