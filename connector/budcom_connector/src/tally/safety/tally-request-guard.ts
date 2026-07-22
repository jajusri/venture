import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { TallyCircuitBreaker } from './tally-circuit-breaker.js';
import { TallyRequestAuditor } from './tally-request-auditor.js';
import { validateTallyRequestXml } from './xml-request-validator.js';

export interface TallyRequestGuardOptions {
  readonly config: ConnectorConfig;
  readonly logger: Logger;
  readonly auditor?: TallyRequestAuditor;
}

export interface GuardedRequestContext {
  readonly correlationId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly xml: string;
}

export interface TallyRuntimeLimits {
  readonly poolMaxConnections: number;
  readonly retryMaxAttempts: number;
  readonly minRequestIntervalMs: number;
  readonly maxRequestBytes: number;
  readonly maxResponseBytes: number;
  readonly circuitBreakerEnabled: boolean;
  readonly autoReconnect: boolean;
  readonly maxReconnectAttempts: number;
}

export function resolveTallyRuntimeLimits(config: ConnectorConfig): TallyRuntimeLimits {
  if (!config.tallySafeMode) {
    return {
      poolMaxConnections: Math.max(1, config.tallyPoolMaxConnections),
      retryMaxAttempts: Math.max(1, config.tallyRetryMaxAttempts),
      minRequestIntervalMs: config.tallyMinRequestIntervalMs,
      maxRequestBytes: config.tallyMaxRequestBytes,
      maxResponseBytes: config.tallyMaxResponseBytes,
      circuitBreakerEnabled: config.tallyCircuitBreakerEnabled,
      autoReconnect: config.tallyAutoReconnect,
      maxReconnectAttempts: Number.MAX_SAFE_INTEGER,
    };
  }

  return {
    poolMaxConnections: 1,
    retryMaxAttempts: 1,
    minRequestIntervalMs: Math.max(config.tallyMinRequestIntervalMs, 2_000),
    maxRequestBytes: config.tallyMaxRequestBytes,
    maxResponseBytes: config.tallyMaxResponseBytes,
    circuitBreakerEnabled: true,
    autoReconnect: false,
    maxReconnectAttempts: 0,
  };
}

export class TallyRequestGuard {
  private readonly limits: TallyRuntimeLimits;
  private readonly circuitBreaker: TallyCircuitBreaker;
  private lastRequestFinishedAt = 0;
  private inFlight = false;
  private queue: Array<() => void> = [];
  lastRequest?: {
    correlationId: string;
    collectionId?: string;
    reportId?: string;
    sentAt: string;
    outcome?: string;
  };

  constructor(private readonly options: TallyRequestGuardOptions) {
    this.limits = resolveTallyRuntimeLimits(options.config);
    this.circuitBreaker = new TallyCircuitBreaker({
      failureThreshold: options.config.tallyCircuitBreakerFailureThreshold,
      cooldownMs: options.config.tallyCircuitBreakerCooldownMs,
    });
  }

  get circuitState() {
    return this.circuitBreaker.getState();
  }

  get runtimeLimits(): TallyRuntimeLimits {
    return this.limits;
  }

  async prepare(context: GuardedRequestContext): Promise<void> {
    if (this.limits.circuitBreakerEnabled) {
      try {
        this.circuitBreaker.assertRequestAllowed();
      } catch (error) {
        await this.audit(context, 'blocked', error);
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          error instanceof Error ? error.message : 'Tally circuit breaker open',
          503,
        );
      }
    }

    validateTallyRequestXml(context.xml, this.limits.maxRequestBytes);

    await this.acquireSingleFlight();
    await this.enforceRateLimit();

    this.lastRequest = {
      correlationId: context.correlationId,
      collectionId: context.collectionId,
      reportId: context.reportId,
      sentAt: new Date().toISOString(),
    };

    this.options.logger.info('Tally request prepared', {
      correlationId: context.correlationId,
      collectionId: context.collectionId ?? context.reportId,
      requestBytes: Buffer.byteLength(context.xml, 'utf8'),
      safeMode: this.options.config.tallySafeMode,
      circuitState: this.circuitBreaker.getState(),
    });
  }

  async recordSuccess(context: GuardedRequestContext, responseBytes: number): Promise<void> {
    if (responseBytes > this.limits.maxResponseBytes) {
      const error = new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        `Tally response exceeds maximum size (${this.limits.maxResponseBytes} bytes)`,
        503,
        { responseBytes, maxResponseBytes: this.limits.maxResponseBytes },
      );
      await this.recordFailure(context, error);
      throw error;
    }

    this.circuitBreaker.recordSuccess();
    this.lastRequest = { ...this.lastRequest!, outcome: 'success' };
    await this.audit(context, 'sent');
    if (this.inFlight) {
      this.releaseSingleFlight();
    }
  }

  async recordFailure(context: GuardedRequestContext, error: unknown): Promise<void> {
    const message = error instanceof Error ? error.message : String(error);
    if (this.limits.circuitBreakerEnabled) {
      this.circuitBreaker.recordFailure(message);
    }
    this.lastRequest = { ...this.lastRequest!, outcome: 'failed' };
    await this.audit(context, 'failed', error);
    if (this.inFlight) {
      this.releaseSingleFlight();
    }
  }

  private async audit(
    context: GuardedRequestContext,
    outcome: 'sent' | 'failed' | 'blocked',
    error?: unknown,
  ): Promise<void> {
    if (!this.options.auditor) return;
    await this.options.auditor.record({
      timestamp: new Date().toISOString(),
      correlationId: context.correlationId,
      collectionId: context.collectionId,
      reportId: context.reportId,
      requestByteLength: Buffer.byteLength(context.xml, 'utf8'),
      outcome,
      errorMessage: error instanceof Error ? error.message : undefined,
      xml: context.xml,
    });
  }

  private async enforceRateLimit(): Promise<void> {
    const elapsed = Date.now() - this.lastRequestFinishedAt;
    const waitMs = this.limits.minRequestIntervalMs - elapsed;
    if (waitMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, waitMs));
    }
  }

  private async acquireSingleFlight(): Promise<void> {
    if (!this.options.config.tallySafeMode && this.limits.poolMaxConnections > 1) {
      return;
    }
    if (!this.inFlight) {
      this.inFlight = true;
      return;
    }
    await new Promise<void>((resolve) => {
      this.queue.push(resolve);
    });
    this.inFlight = true;
  }

  private releaseSingleFlight(): void {
    this.lastRequestFinishedAt = Date.now();
    const next = this.queue.shift();
    if (next) {
      next();
      return;
    }
    this.inFlight = false;
  }
}
