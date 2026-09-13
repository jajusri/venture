import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { resolveTallyRuntimeLimits } from '../../../src/tally/safety/tally-request-guard.js';

describe('resolveTallyRuntimeLimits', () => {
  it('enforces safe defaults when safe mode is enabled', () => {
    const limits = resolveTallyRuntimeLimits(
      loadConfig({
        env: 'test',
        tallySafeMode: true,
        tallyPoolMaxConnections: 8,
        tallyRetryMaxAttempts: 5,
        tallyAutoReconnect: true,
      }),
    );

    expect(limits.poolMaxConnections).toBe(1);
    expect(limits.retryMaxAttempts).toBe(1);
    expect(limits.autoReconnect).toBe(false);
    expect(limits.maxReconnectAttempts).toBe(0);
    expect(limits.circuitBreakerEnabled).toBe(true);
    expect(limits.minRequestIntervalMs).toBeGreaterThanOrEqual(2_000);
  });

  it('cannot weaken mandatory controls when safe mode is disabled', () => {
    // SAFE_MODE=false must NOT act as a master off-switch. Mandatory controls
    // (single-flight, no retry, circuit breaker, no reconnect storms) always hold.
    const limits = resolveTallyRuntimeLimits(
      loadConfig({
        env: 'test',
        tallySafeMode: false,
        tallyPoolMaxConnections: 4,
        tallyRetryMaxAttempts: 3,
        tallyAutoReconnect: true,
        tallyCircuitBreakerEnabled: false,
      }),
    );

    expect(limits.poolMaxConnections).toBe(1);
    expect(limits.retryMaxAttempts).toBe(1);
    expect(limits.autoReconnect).toBe(false);
    expect(limits.maxReconnectAttempts).toBe(0);
    expect(limits.circuitBreakerEnabled).toBe(true);
  });

  it('caps request size at the hard ceiling regardless of configuration', () => {
    const limits = resolveTallyRuntimeLimits(
      loadConfig({ env: 'test', tallyMaxRequestBytes: 99_999_999 }),
    );
    expect(limits.maxRequestBytes).toBeLessThanOrEqual(262_144);
  });
});
