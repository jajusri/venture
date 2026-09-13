import { describe, expect, it } from 'vitest';

import { RetryPolicy } from '../../../src/tally/connection/retry-policy.js';
import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';

describe('RetryPolicy', () => {
  const policy = new RetryPolicy({
    maxAttempts: 3,
    baseDelayMs: 10,
    maxDelayMs: 100,
    jitterRatio: 0,
  });

  it('retries on transport AppError', () => {
    const error = new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'TALLY_HTTP_ERROR', 503);
    expect(policy.shouldRetry(1, error)).toBe(true);
  });

  it('retries on timeout AppError', () => {
    const error = new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'timeout', 504);
    expect(policy.shouldRetry(1, error)).toBe(true);
  });

  it('stops after max attempts', () => {
    const error = new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'timeout', 504);
    expect(policy.shouldRetry(3, error)).toBe(false);
  });

  it('computes exponential delay', () => {
    expect(policy.delayMsForAttempt(1)).toBe(10);
    expect(policy.delayMsForAttempt(2)).toBe(20);
    expect(policy.delayMsForAttempt(3)).toBe(40);
  });
});
