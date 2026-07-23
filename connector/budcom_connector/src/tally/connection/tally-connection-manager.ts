import { randomUUID } from 'node:crypto';

import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ErpTransport } from '../core/erp-transport.interface.js';
import type {
  TallyConnectionState,
  TallyDiagnosticsSnapshot,
  TallyExchangeResult,
} from '../core/types.js';
import { ReconnectManager } from './reconnect-manager.js';
import { RetryPolicy } from './retry-policy.js';
import { TallyRequestGuard, resolveTallyRuntimeLimits } from '../safety/tally-request-guard.js';
import type { TallyRequestAuditor } from '../safety/tally-request-auditor.js';
import { TallyXmlRequestBuilder } from '../xml/request-builder.js';

export interface TallyConnectionManagerOptions {
  readonly config: ConnectorConfig;
  readonly logger: Logger;
  readonly transport: ErpTransport;
  readonly requestBuilder?: TallyXmlRequestBuilder;
  readonly retryPolicy?: RetryPolicy;
  readonly reconnectManager?: ReconnectManager;
  readonly requestGuard?: TallyRequestGuard;
  readonly requestAuditor?: TallyRequestAuditor;
}

export class TallyConnectionManager {
  private running = false;
  private state: TallyConnectionState = 'disconnected';
  private readonly requestBuilder: TallyXmlRequestBuilder;
  private readonly retryPolicy: RetryPolicy;
  private readonly reconnectManager: ReconnectManager;
  private readonly requestGuard: TallyRequestGuard;
  private readonly runtimeLimits;
  private totalRequests = 0;
  private failedRequests = 0;
  private totalLatencyMs = 0;
  private lastSuccessfulPingAt?: string;
  private lastErrorAt?: string;
  private lastErrorCode?: string;
  private lastErrorMessage?: string;

  constructor(private readonly options: TallyConnectionManagerOptions) {
    this.runtimeLimits = resolveTallyRuntimeLimits(options.config);
    this.requestBuilder = options.requestBuilder ?? new TallyXmlRequestBuilder();
    this.retryPolicy =
      options.retryPolicy ??
      new RetryPolicy({
        maxAttempts: this.runtimeLimits.retryMaxAttempts,
        baseDelayMs: options.config.tallyRetryBaseDelayMs,
        maxDelayMs: options.config.tallyRetryMaxDelayMs,
        jitterRatio: options.config.tallyRetryJitterRatio,
      });
    this.reconnectManager =
      options.reconnectManager ??
      new ReconnectManager(
        {
          autoReconnect: this.runtimeLimits.autoReconnect,
          reconnectDelayMs: options.config.tallyReconnectDelayMs,
          maxAttempts: this.runtimeLimits.maxReconnectAttempts,
        },
        options.logger,
      );
    this.requestGuard =
      options.requestGuard ??
      new TallyRequestGuard({
        config: options.config,
        logger: options.logger,
        auditor: options.requestAuditor,
      });
  }

  async start(): Promise<void> {
    this.running = true;
    this.state = 'connecting';
    this.options.logger.info('Tally connection manager starting', {
      host: this.options.config.tallyHost,
      port: this.options.config.tallyPort,
      safeMode: this.options.config.tallySafeMode,
      poolMaxConnections: this.runtimeLimits.poolMaxConnections,
      retryMaxAttempts: this.runtimeLimits.retryMaxAttempts,
    });
  }

  async stop(): Promise<void> {
    this.running = false;
    this.state = 'disconnected';
    await this.options.transport.close();
    this.options.logger.info('Tally connection manager stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getState(): TallyConnectionState {
    return this.state;
  }

  async ping(): Promise<boolean> {
    try {
      await this.exchange(this.requestBuilder.buildConnectivityCheck(), {
        collectionId: 'License Info',
      });
      this.markConnected();
      this.lastSuccessfulPingAt = new Date().toISOString();
      this.reconnectManager.reset();
      return true;
    } catch (error) {
      this.markFailure(error);
      return false;
    }
  }

  async exchange(
    xml: string,
    metadata: { collectionId?: string; reportId?: string; timeoutMs?: number; signal?: AbortSignal } = {},
  ): Promise<TallyExchangeResult> {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Tally connection manager is not running',
        503,
      );
    }

    let attempt = 0;
    let lastError: unknown;

    while (attempt < this.retryPolicy.maxAttempts) {
      attempt += 1;
      const correlationId = randomUUID();
      const context = {
        correlationId,
        collectionId: metadata.collectionId,
        reportId: metadata.reportId,
        xml,
      };

      let prepared = false;
      try {
        if (this.state === 'disconnected' || this.state === 'reconnecting') {
          this.state = 'connecting';
        }

        await this.requestGuard.prepare(context);
        prepared = true;

        const sentAt = new Date().toISOString();
        const response = await this.options.transport.send({
          body: xml,
          contentType: 'text/xml',
          correlationId,
          timeoutMs: metadata.timeoutMs,
          signal: metadata.signal,
        });

        await this.requestGuard.recordSuccess(context, response.body.length);

        this.totalRequests += 1;
        this.totalLatencyMs += response.durationMs;
        this.markConnected();

        return {
          request: {
            requestId: correlationId,
            collectionId: metadata.collectionId,
            reportId: metadata.reportId,
            sentAt,
          },
          response: {
            requestId: correlationId,
            receivedAt: new Date().toISOString(),
            durationMs: response.durationMs,
            statusCode: response.statusCode,
            byteLength: response.body.length,
          },
          rawXml: response.body,
        };
      } catch (error) {
        lastError = error;
        if (error instanceof AppError && error.code === ErrorCodes.SYNC_CANCELLED) {
          break;
        }
        if (prepared) {
          this.failedRequests += 1;
          this.markFailure(error);
          await this.requestGuard.recordFailure(context, error);
        } else {
          this.markFailure(error);
        }

        if (this.requestGuard.circuitState === 'open') {
          break;
        }

        if (!this.retryPolicy.shouldRetry(attempt, error)) {
          break;
        }

        this.state = 'reconnecting';
        await this.retryPolicy.wait(attempt);
      }
    }

    throw lastError instanceof Error
      ? lastError
      : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Tally exchange failed', 503);
  }

  getDiagnostics(): TallyDiagnosticsSnapshot {
    const poolStats =
      'poolStats' in this.options.transport
        ? (this.options.transport as { poolStats: { activeConnections: number; waitingRequests: number } })
            .poolStats
        : { activeConnections: 0, waitingRequests: 0 };

    return {
      state: this.state,
      host: this.options.config.tallyHost,
      port: this.options.config.tallyPort,
      lastSuccessfulPingAt: this.lastSuccessfulPingAt,
      lastErrorAt: this.lastErrorAt,
      lastErrorCode: this.lastErrorCode,
      lastErrorMessage: this.lastErrorMessage,
      totalRequests: this.totalRequests,
      failedRequests: this.failedRequests,
      reconnectAttempts: this.reconnectManager.attempts,
      averageLatencyMs:
        this.totalRequests === 0 ? 0 : Math.round(this.totalLatencyMs / this.totalRequests),
      poolActiveConnections: poolStats.activeConnections,
      poolWaitingRequests: poolStats.waitingRequests,
      safeMode: this.options.config.tallySafeMode,
      circuitState: this.requestGuard.circuitState,
      lastRequest: this.requestGuard.lastRequest,
      runtimeLimits: {
        poolMaxConnections: this.runtimeLimits.poolMaxConnections,
        retryMaxAttempts: this.runtimeLimits.retryMaxAttempts,
        minRequestIntervalMs: this.runtimeLimits.minRequestIntervalMs,
        maxRequestBytes: this.runtimeLimits.maxRequestBytes,
        maxResponseBytes: this.runtimeLimits.maxResponseBytes,
        circuitBreakerEnabled: this.runtimeLimits.circuitBreakerEnabled,
        timeoutMs: this.options.config.tallyTimeoutMs,
      },
    };
  }

  private markConnected(): void {
    this.state = 'connected';
  }

  private markFailure(error: unknown): void {
    this.lastErrorAt = new Date().toISOString();
    if (error instanceof AppError) {
      this.lastErrorCode = error.code;
      this.lastErrorMessage = error.message;
      this.state = error.statusCode >= 500 ? 'degraded' : 'disconnected';
      return;
    }
    this.lastErrorCode = ErrorCodes.SERVICE_UNAVAILABLE;
    this.lastErrorMessage = error instanceof Error ? error.message : String(error);
    this.state = 'disconnected';
  }
}
