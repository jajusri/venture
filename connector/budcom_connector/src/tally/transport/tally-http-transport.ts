import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ErpTransport } from '../core/erp-transport.interface.js';
import type { ErpTransportRequest, ErpTransportResponse } from '../core/types.js';
import { resolveTallyRuntimeLimits } from '../safety/tally-request-guard.js';
import { ConnectionPool } from './connection-pool.js';

export interface TallyHttpTransportOptions {
  readonly config: ConnectorConfig;
  readonly logger: Logger;
  readonly fetchImpl?: typeof fetch;
  readonly pool?: ConnectionPool;
}

export class TallyHttpTransport implements ErpTransport {
  private readonly pool: ConnectionPool;
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly options: TallyHttpTransportOptions) {
    const poolMax = resolveTallyRuntimeLimits(options.config).poolMaxConnections;
    this.pool = options.pool ?? new ConnectionPool(poolMax);
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  get poolStats() {
    return {
      activeConnections: this.pool.activeConnections,
      waitingRequests: this.pool.waitingRequests,
    };
  }

  async send(request: ErpTransportRequest): Promise<ErpTransportResponse> {
    return this.pool.run(async () => this.sendOnce(request));
  }

  async close(): Promise<void> {
    // Pool uses in-process semaphores; nothing to close for HTTP.
  }

  private async sendOnce(request: ErpTransportRequest): Promise<ErpTransportResponse> {
    const timeoutMs = request.timeoutMs ?? this.options.config.tallyTimeoutMs;
    const url = `http://${this.options.config.tallyHost}:${this.options.config.tallyPort}`;
    const started = Date.now();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await this.fetchImpl(url, {
        method: 'POST',
        headers: {
          'Content-Type': request.contentType,
          Accept: 'text/xml',
        },
        body: request.body,
        signal: controller.signal,
      });

      const body = await response.text();
      const durationMs = Date.now() - started;

      if (!response.ok) {
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          `TALLY_HTTP_ERROR: Tally returned HTTP ${response.status}`,
          503,
          { statusCode: response.status, durationMs },
        );
      }

      if (!body.trim()) {
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          'TALLY_HTTP_ERROR: Tally returned an empty response',
          503,
        );
      }

      const headers: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        headers[key] = value;
      });

      this.options.logger.debug('Tally HTTP exchange complete', {
        correlationId: request.correlationId,
        durationMs,
        byteLength: body.length,
        statusCode: response.status,
      });

      return {
        body,
        statusCode: response.status,
        durationMs,
        headers,
      };
    } catch (error) {
      if (error instanceof AppError) {
        throw error;
      }
      if (error instanceof Error && error.name === 'AbortError') {
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          `Tally request timed out after ${timeoutMs}ms`,
          504,
          { timeoutMs },
        );
      }
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        `Tally connection failed: ${error instanceof Error ? error.message : String(error)}. Tally may be unavailable or restarting.`,
        503,
      );
    } finally {
      clearTimeout(timer);
    }
  }
}
