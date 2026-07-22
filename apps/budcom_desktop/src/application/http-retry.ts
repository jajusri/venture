export interface RetryOptions {
  readonly maxAttempts: number;
  readonly baseDelayMs: number;
  readonly shouldRetry?: (error: unknown, attempt: number) => boolean;
}

const DEFAULT_SHOULD_RETRY = (error: unknown): boolean => {
  if (error instanceof Error) {
    if (error.name === 'AbortError') {
      return true;
    }
    if (error.message.includes('fetch failed') || error.message.includes('ECONNREFUSED')) {
      return true;
    }
    if (error.message.includes('Connector request failed: HTTP 503')) {
      return true;
    }
  }
  return false;
};

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

export async function retryWithBackoff<T>(
  operation: () => Promise<T>,
  options: RetryOptions,
): Promise<T> {
  const shouldRetry = options.shouldRetry ?? DEFAULT_SHOULD_RETRY;
  let lastError: unknown;

  for (let attempt = 1; attempt <= options.maxAttempts; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (attempt >= options.maxAttempts || !shouldRetry(error, attempt)) {
        throw error;
      }
      await delay(options.baseDelayMs * attempt);
    }
  }

  throw lastError;
}
