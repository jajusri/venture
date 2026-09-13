import { AppError } from '../../infrastructure/errors/app-error.js';

export interface RetryPolicyOptions {
  readonly maxAttempts: number;
  readonly baseDelayMs: number;
  readonly maxDelayMs: number;
  readonly jitterRatio: number;
}

export class RetryPolicy {
  constructor(private readonly options: RetryPolicyOptions) {}

  get maxAttempts(): number {
    return this.options.maxAttempts;
  }

  shouldRetry(attempt: number, error: unknown): boolean {
    if (attempt >= this.options.maxAttempts) {
      return false;
    }
    if (error instanceof AppError) {
      return error.statusCode === 503 || error.statusCode === 504;
    }
    if (error instanceof Error && error.name === 'AbortError') {
      return true;
    }
    if (error instanceof Error && /ECONNREFUSED|ETIMEDOUT|fetch failed/i.test(error.message)) {
      return true;
    }
    return error instanceof Error && error.message.includes('TALLY_HTTP_ERROR');
  }

  delayMsForAttempt(attempt: number): number {
    const exponential = this.options.baseDelayMs * 2 ** Math.max(0, attempt - 1);
    const capped = Math.min(exponential, this.options.maxDelayMs);
    const jitter = capped * this.options.jitterRatio * Math.random();
    return Math.round(capped + jitter);
  }

  async wait(attempt: number): Promise<void> {
    const delay = this.delayMsForAttempt(attempt);
    await new Promise((resolve) => setTimeout(resolve, delay));
  }
}
