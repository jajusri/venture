import { describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { loadConfig } from '../../src/config/index.js';
import { AppError } from '../../src/infrastructure/errors/app-error.js';
import { TallyConnectionManager } from '../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../src/tally/xml/request-builder.js';
import { createTallyMockFetch } from '../helpers/mock-fetch.js';

describe('Tally safety integration', () => {
  it('does not retry or reconnect in safe mode after transport failure', async () => {
    let calls = 0;
    const { fetchImpl } = createTallyMockFetch({ pingOk: false });
    const wrappedFetch: typeof fetch = async (...args) => {
      calls += 1;
      return fetchImpl(...args);
    };

    const config = loadConfig({
      env: 'test',
      tallySafeMode: true,
      tallyRetryMaxAttempts: 3,
      tallyAutoReconnect: true,
      tallyCircuitBreakerFailureThreshold: 1,
    });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl: wrappedFetch,
      }),
    });

    await manager.start();
    await expect(manager.ping()).resolves.toBe(false);
    expect(calls).toBe(1);

    const diagnostics = manager.getDiagnostics();
    expect(diagnostics.safeMode).toBe(true);
    expect(diagnostics.runtimeLimits.retryMaxAttempts).toBe(1);
    expect(diagnostics.runtimeLimits.poolMaxConnections).toBe(1);
    expect(diagnostics.runtimeLimits.timeoutMs).toBe(config.tallyTimeoutMs);
    expect(diagnostics.reconnectAttempts).toBe(0);
    expect(diagnostics.circuitState).toBe('open');

    await manager.stop();
  });

  it('maps fetch failures to SERVICE_UNAVAILABLE (503) when Tally crashes', async () => {
    const fetchImpl: typeof fetch = async () => {
      throw new TypeError('fetch failed');
    };
    const config = loadConfig({
      env: 'test',
      tallySafeMode: true,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });

    await manager.start();
    await expect(
      manager.exchange(new TallyXmlRequestBuilder().buildConnectivityCheck(), {
        collectionId: 'License Info',
      }),
    ).rejects.toMatchObject({ statusCode: 503 });

    await manager.stop();
  });

  it('blocks further requests when circuit breaker is open', async () => {
    const fetchImpl: typeof fetch = async () => {
      throw new TypeError('fetch failed');
    };
    const config = loadConfig({
      env: 'test',
      tallySafeMode: true,
      tallyCircuitBreakerFailureThreshold: 1,
    });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });
    const xml = new TallyXmlRequestBuilder().buildConnectivityCheck();

    await manager.start();
    await expect(
      manager.exchange(xml, { collectionId: 'License Info' }),
    ).rejects.toBeInstanceOf(AppError);
    await expect(
      manager.exchange(xml, { collectionId: 'License Info' }),
    ).rejects.toMatchObject({ statusCode: 503, message: /circuit breaker is open/i });

    await manager.stop();
  });

  it('suppresses retries when safe reconnect probe fails', async () => {
    let calls = 0;
    const fetchImpl: typeof fetch = async () => {
      calls += 1;
      throw new TypeError('fetch failed');
    };
    const config = loadConfig({
      env: 'test',
      tallySafeMode: false,
      tallyRetryMaxAttempts: 3,
      tallyAutoReconnect: true,
      tallyCircuitBreakerEnabled: false,
    });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });

    await manager.start();
    await expect(
      manager.exchange(new TallyXmlRequestBuilder().buildConnectivityCheck(), {
        collectionId: 'License Info',
      }),
    ).rejects.toMatchObject({ statusCode: 503 });

    expect(calls).toBe(2);
    expect(manager.getDiagnostics().reconnectAttempts).toBe(1);

    await manager.stop();
  });
});
