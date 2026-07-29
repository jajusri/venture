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
    if (request.signal) {
      if (request.signal.aborted) {
        clearTimeout(timer);
        throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Tally request was cancelled.', 499);
      }
      request.signal.addEventListener('abort', () => controller.abort(), { once: true });
    }

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

      const maxResponseBytes =
        request.maxResponseBytes ?? resolveTallyRuntimeLimits(this.options.config).maxResponseBytes;
      const body = await readBoundedUtf8Body(
        response,
        maxResponseBytes,
        controller,
        request.responseLimitLabel,
      );
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
        byteLength: Buffer.byteLength(body, 'utf8'),
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
        if (request.signal?.aborted) {
          throw new AppError(ErrorCodes.SYNC_CANCELLED, 'Tally request was cancelled.', 499);
        }
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

async function readBoundedUtf8Body(
  response: Response,
  maxResponseBytes: number,
  controller: AbortController,
  responseLimitLabel?: string,
): Promise<string> {
  if (!response.body) return '';
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  const parts: string[] = [];
  let byteLength = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      byteLength += value.byteLength;
      if (byteLength > maxResponseBytes) {
        await reader.cancel('response-size-limit-exceeded').catch(() => undefined);
        controller.abort();
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          responseLimitLabel
            ? `Response for ${responseLimitLabel} exceeds contract maximum (${maxResponseBytes} bytes)`
            : `Tally response exceeds maximum size (${maxResponseBytes} bytes)`,
          503,
          { responseBytes: byteLength, maxResponseBytes },
        );
      }
      parts.push(decoder.decode(value, { stream: true }));
    }
    parts.push(decoder.decode());
    return parts.join('');
  } catch (error) {
    if (error instanceof AppError) throw error;
    throw new AppError(
      ErrorCodes.SERVICE_UNAVAILABLE,
      'TALLY_HTTP_ERROR: Tally response body was truncated or invalid UTF-8',
      503,
    );
  } finally {
    reader.releaseLock();
  }
}
