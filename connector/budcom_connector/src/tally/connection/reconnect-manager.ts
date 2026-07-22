import type { Logger } from '../../infrastructure/logging/logger.js';
import type { TallyConnectionState } from '../core/types.js';

export interface ReconnectManagerOptions {
  readonly autoReconnect: boolean;
  readonly reconnectDelayMs: number;
  readonly maxAttempts?: number;
}

export class ReconnectManager {
  private reconnectAttempts = 0;
  private lastReconnectAt?: string;

  constructor(
    private readonly options: ReconnectManagerOptions,
    private readonly logger: Logger,
  ) {}

  get attempts(): number {
    return this.reconnectAttempts;
  }

  get lastAttemptAt(): string | undefined {
    return this.lastReconnectAt;
  }

  shouldAttemptReconnect(state: TallyConnectionState): boolean {
    if (!this.options.autoReconnect || state === 'connected') {
      return false;
    }
    if (
      this.options.maxAttempts !== undefined &&
      this.reconnectAttempts >= this.options.maxAttempts
    ) {
      return false;
    }
    return true;
  }

  async backoffBeforeReconnect(): Promise<void> {
    this.reconnectAttempts += 1;
    this.lastReconnectAt = new Date().toISOString();
    this.logger.info('Tally reconnect backoff', {
      attempt: this.reconnectAttempts,
      delayMs: this.options.reconnectDelayMs,
    });
    await new Promise((resolve) => setTimeout(resolve, this.options.reconnectDelayMs));
  }

  reset(): void {
    this.reconnectAttempts = 0;
  }
}
