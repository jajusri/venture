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

  it('respects configured limits when safe mode is disabled', () => {
    const limits = resolveTallyRuntimeLimits(
      loadConfig({
        env: 'test',
        tallySafeMode: false,
        tallyPoolMaxConnections: 4,
        tallyRetryMaxAttempts: 3,
        tallyAutoReconnect: true,
      }),
    );

    expect(limits.poolMaxConnections).toBe(4);
    expect(limits.retryMaxAttempts).toBe(3);
    expect(limits.autoReconnect).toBe(true);
  });
});
