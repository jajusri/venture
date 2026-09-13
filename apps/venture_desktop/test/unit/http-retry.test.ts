import { describe, expect, it, vi } from 'vitest';

import { retryWithBackoff } from '../../src/application/http-retry.js';

describe('http-retry', () => {
  it('retries transient failures and eventually succeeds', async () => {
    let attempts = 0;
    const result = await retryWithBackoff(
      async () => {
        attempts += 1;
        if (attempts < 3) {
          throw new TypeError('fetch failed');
        }
        return 'ok';
      },
      { maxAttempts: 3, baseDelayMs: 1 },
    );

    expect(result).toBe('ok');
    expect(attempts).toBe(3);
  });

  it('stops retrying when error is not retryable', async () => {
    const operation = vi.fn(async () => {
      throw new Error('validation failed');
    });

    await expect(
      retryWithBackoff(operation, { maxAttempts: 3, baseDelayMs: 1 }),
    ).rejects.toThrow('validation failed');
    expect(operation).toHaveBeenCalledTimes(1);
  });
});
