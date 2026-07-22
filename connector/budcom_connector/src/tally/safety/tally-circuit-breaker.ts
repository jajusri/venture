export interface CircuitBreakerOptions {
  readonly failureThreshold: number;
  readonly cooldownMs: number;
}

export type CircuitState = 'closed' | 'open' | 'half_open';

export class TallyCircuitBreaker {
  private state: CircuitState = 'closed';
  private consecutiveFailures = 0;
  private openedAt?: number;
  private lastFailureMessage?: string;

  constructor(private readonly options: CircuitBreakerOptions) {}

  getState(): CircuitState {
    if (this.state === 'open' && this.openedAt !== undefined) {
      if (Date.now() - this.openedAt >= this.options.cooldownMs) {
        this.state = 'half_open';
      }
    }
    return this.state;
  }

  getLastFailureMessage(): string | undefined {
    return this.lastFailureMessage;
  }

  assertRequestAllowed(): void {
    const state = this.getState();
    if (state === 'open') {
      throw new Error(
        `Tally circuit breaker is open; retry after ${this.options.cooldownMs}ms cooldown`,
      );
    }
  }

  recordSuccess(): void {
    this.consecutiveFailures = 0;
    this.state = 'closed';
    this.openedAt = undefined;
    this.lastFailureMessage = undefined;
  }

  recordFailure(message: string): void {
    this.consecutiveFailures += 1;
    this.lastFailureMessage = message;
    if (this.consecutiveFailures >= this.options.failureThreshold) {
      this.state = 'open';
      this.openedAt = Date.now();
    }
  }
}
