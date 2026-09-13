import { describe, expect, it } from 'vitest';

import { TallyCircuitBreaker } from '../../../src/tally/safety/tally-circuit-breaker.js';

describe('TallyCircuitBreaker', () => {
  it('opens after consecutive failures', () => {
    const breaker = new TallyCircuitBreaker({ failureThreshold: 2, cooldownMs: 60_000 });
    breaker.recordFailure('fetch failed');
    expect(breaker.getState()).toBe('closed');
    breaker.recordFailure('fetch failed');
    expect(breaker.getState()).toBe('open');
    expect(() => breaker.assertRequestAllowed()).toThrow(/circuit breaker is open/i);
  });

  it('resets on success', () => {
    const breaker = new TallyCircuitBreaker({ failureThreshold: 2, cooldownMs: 60_000 });
    breaker.recordFailure('fetch failed');
    breaker.recordSuccess();
    breaker.recordFailure('fetch failed');
    expect(breaker.getState()).toBe('closed');
  });
});
