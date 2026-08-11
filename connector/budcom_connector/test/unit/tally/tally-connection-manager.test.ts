import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { loadConfig } from '../../../src/config/index.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import {
  createMockFetch,
  createTallyMockFetch,
  SAMPLE_LICENSE_INFO_RESPONSE,
  SAMPLE_TALLY_COMPANY_LIST_RESPONSE,
} from '../../helpers/mock-fetch.js';

/**
 * A fetch stub whose behaviour for each collection is independently swappable mid-test, so a
 * single test can simulate "Tally goes down, then comes back" without a fixed call sequence.
 */
function createSwitchableTallyFetch() {
  const state: { licenseInfoFails: boolean; companyListFails: boolean } = {
    licenseInfoFails: false,
    companyListFails: false,
  };
  const licenseInfoCalls: number[] = [];
  const { fetchImpl, calls } = createMockFetch(({ init }, index) => {
    const body = typeof init?.body === 'string' ? init.body : '';
    if (body.includes('License Info')) {
      licenseInfoCalls.push(index);
      if (state.licenseInfoFails) return { status: 503, body: 'Service Unavailable' };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    }
    if (body.includes('List of Companies')) {
      if (state.companyListFails) return { status: 503, body: 'Service Unavailable' };
      return { body: SAMPLE_TALLY_COMPANY_LIST_RESPONSE };
    }
    return { status: 503, body: 'Service Unavailable' };
  });
  return { fetchImpl, calls, licenseInfoCalls, state };
}

function buildManager(fetchImpl: typeof fetch, overrides: Record<string, unknown> = {}) {
  return new TallyConnectionManager({
    config: loadConfig({
      env: 'test',
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerFailureThreshold: 2,
      tallyCircuitBreakerCooldownMs: 10,
      // Safe-mode's mandatory 2s minimum inter-request interval is real production behaviour,
      // but these tests deliberately chain several sequential Tally round-trips (failures,
      // cooldown, recovery probe, retry) in one case — left at the default it stacks up past
      // vitest's test timeout. Disabling it here only affects this suite's config instances.
      tallySafeMode: false,
      tallyMinRequestIntervalMs: 0,
      ...overrides,
    }),
    logger: createLogger({ service: 'test', level: 'error' }),
    transport: new TallyHttpTransport({
      config: loadConfig({ env: 'test' }),
      logger: createLogger({ service: 'test', level: 'error' }),
      fetchImpl,
    }),
  });
}

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

describe('TallyConnectionManager — HALF_OPEN circuit recovery (BUDCOM MVP-1 V7-D)', () => {
  const builder = new TallyXmlRequestBuilder();
  const companyListRequest = () =>
    builder.build({ tallyRequest: 'Export', type: 'Collection', id: 'List of Companies' });

  async function exchangeCompanyList(manager: TallyConnectionManager) {
    return manager.exchange(companyListRequest(), { collectionId: 'List of Companies' });
  }

  it('1. allows a normal (non-probe) operation while the circuit is closed and Tally is healthy', async () => {
    const { fetchImpl } = createSwitchableTallyFetch();
    const manager = buildManager(fetchImpl);
    await manager.start();

    await expect(exchangeCompanyList(manager)).resolves.toBeDefined();
    expect(manager.getDiagnostics().circuitState).toBe('closed');

    await manager.stop();
  });

  it('2. opens the circuit after consecutive failures from real business operations, not just ping()', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl);
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getDiagnostics().circuitState).toBe('open');

    await manager.stop();
  });

  it('3. denies normal operations while OPEN, before the cooldown elapses', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 60_000 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getDiagnostics().circuitState).toBe('open');

    // Tally recovers, but the cooldown hasn't elapsed yet — still denied, no Tally contact.
    state.companyListFails = false;
    await expect(exchangeCompanyList(manager)).rejects.toThrow(/circuit breaker is open/i);

    await manager.stop();
  });

  it('4. transitions OPEN -> HALF_OPEN once the cooldown elapses', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getDiagnostics().circuitState).toBe('open');

    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(manager.getDiagnostics().circuitState).toBe('half_open');

    await manager.stop();
  });

  it('5 & 6. HealthCheck (ping) is admitted in HALF_OPEN and a successful probe closes the circuit', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(manager.getDiagnostics().circuitState).toBe('half_open');

    // Tally is actually back now — the health probe itself must be admitted through HALF_OPEN.
    await expect(manager.ping()).resolves.toBe(true);
    expect(manager.getDiagnostics().circuitState).toBe('closed');

    await manager.stop();
  });

  it('7 & 8. a normal operation that lands on a HALF_OPEN circuit triggers recovery and succeeds in the same call — no restart, no separate manual step', async () => {
    const { fetchImpl, state, licenseInfoCalls } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(manager.getDiagnostics().circuitState).toBe('half_open');

    // Tally has recovered by the time the next ordinary call (e.g. "Refresh Companies") arrives.
    state.companyListFails = false;
    const result = await exchangeCompanyList(manager);

    expect(result).toBeDefined();
    expect(manager.getDiagnostics().circuitState).toBe('closed');
    expect(licenseInfoCalls.length).toBe(1);

    await manager.stop();
  });

  it('9. a failed HALF_OPEN HealthCheck reopens the circuit with a fresh cooldown', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    state.companyListFails = true;
    state.licenseInfoFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(manager.getDiagnostics().circuitState).toBe('half_open');

    // Tally is still down — the probe itself fails too, so the triggering call must fail closed.
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getDiagnostics().circuitState).toBe('open');

    await manager.stop();
  });

  it('10. recovers from a second, later outage — not just a one-shot recovery', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    // First outage/recovery cycle.
    state.companyListFails = true;
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await new Promise((resolve) => setTimeout(resolve, 20));
    state.companyListFails = false;
    await expect(exchangeCompanyList(manager)).resolves.toBeDefined();
    expect(manager.getDiagnostics().circuitState).toBe('closed');

    // Second, independent outage/recovery cycle.
    state.companyListFails = true;
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getDiagnostics().circuitState).toBe('open');
    await new Promise((resolve) => setTimeout(resolve, 20));
    state.companyListFails = false;
    await expect(exchangeCompanyList(manager)).resolves.toBeDefined();
    expect(manager.getDiagnostics().circuitState).toBe('closed');

    await manager.stop();
  });

  it('11. concurrent requests during HALF_OPEN share a single recovery probe (no thundering herd)', async () => {
    const { fetchImpl, state, licenseInfoCalls, calls } = createSwitchableTallyFetch();
    state.companyListFails = true;
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(manager.getDiagnostics().circuitState).toBe('half_open');

    state.companyListFails = false;
    const callsBefore = calls.length;
    const results = await Promise.allSettled([
      exchangeCompanyList(manager),
      exchangeCompanyList(manager),
      exchangeCompanyList(manager),
    ]);

    // Single-flight (mandatory, pre-existing) serializes these, so all three should eventually
    // succeed once the shared probe closes the circuit — but only one probe request is ever sent.
    expect(results.every((r) => r.status === 'fulfilled')).toBe(true);
    expect(licenseInfoCalls.length).toBe(1);
    expect(calls.length).toBe(callsBefore + 1 /* probe */ + 3 /* the three company-list calls */);

    await manager.stop();
  });

  it('12. getState()/circuitState correctly reflect connected vs degraded/disconnected across an outage-recovery cycle', async () => {
    const { fetchImpl, state } = createSwitchableTallyFetch();
    const manager = buildManager(fetchImpl, { tallyCircuitBreakerCooldownMs: 10 });
    await manager.start();

    await expect(exchangeCompanyList(manager)).resolves.toBeDefined();
    expect(manager.getState()).toBe('connected');

    state.companyListFails = true;
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    await expect(exchangeCompanyList(manager)).rejects.toThrow();
    expect(manager.getState()).not.toBe('connected');

    await new Promise((resolve) => setTimeout(resolve, 20));
    state.companyListFails = false;
    await expect(exchangeCompanyList(manager)).resolves.toBeDefined();
    expect(manager.getState()).toBe('connected');

    await manager.stop();
  });
});
