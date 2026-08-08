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

  it('reports "disconnected", not "connecting", immediately after start() when nothing has been attempted yet', async () => {
    // Regression test for a live incident: /diagnostics/connection showed state:'connecting'
    // for an entire Connector session (totalRequests/failedRequests/pool counters all stayed
    // at 0 the whole time — confirmed live, not simulated) because nothing had ever called
    // ping()/exchange(). 'connecting' misleadingly implies an attempt is in progress and
    // should resolve soon; 'disconnected' accurately says "nothing attempted, not connected".
    const { fetchImpl, calls } = createTallyMockFetch({ pingOk: true });
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

    expect(manager.getState()).toBe('disconnected');
    expect(manager.getState()).not.toBe('connecting');
    // Confirms the label change did not add any proactive Tally contact — start() must stay
    // a pure local state change, matching the existing "no Tally access at bare startup"
    // invariant covered by voucher-hardening.test.ts's "reports local health and readiness
    // without contacting Tally".
    const diagnostics = manager.getDiagnostics();
    expect(diagnostics.totalRequests).toBe(0);
    expect(diagnostics.failedRequests).toBe(0);
    expect(calls.length).toBe(0);

    await manager.stop();
  });

  it('still reaches "connected" via an explicit ping() after start(), unaffected by the new initial state', async () => {
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
    expect(manager.getState()).toBe('disconnected');
    await expect(manager.ping()).resolves.toBe(true);
    expect(manager.getState()).toBe('connected');
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
