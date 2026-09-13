import type { Logger } from '../../infrastructure/logging/logger.js';
import type { TallyDiagnosticsSnapshot } from '../../tally/core/types.js';
import type { TallyConnectionService } from '../interfaces/tally-connection.js';
import { PlaceholderService } from './base-placeholder.js';

export class TallyConnectionStub extends PlaceholderService implements TallyConnectionService {
  constructor(logger: Logger) {
    super('TallyConnection', logger);
  }

  async ping(): Promise<boolean> {
    return false;
  }

  getDiagnostics(): TallyDiagnosticsSnapshot {
    return {
      state: 'disconnected',
      host: 'localhost',
      port: 9000,
      totalRequests: 0,
      failedRequests: 0,
      reconnectAttempts: 0,
      averageLatencyMs: 0,
      poolActiveConnections: 0,
      poolWaitingRequests: 0,
      safeMode: true,
      circuitState: 'closed',
      runtimeLimits: {
        poolMaxConnections: 1,
        retryMaxAttempts: 1,
        minRequestIntervalMs: 2_000,
        maxRequestBytes: 65_536,
        maxResponseBytes: 10_485_760,
        circuitBreakerEnabled: true,
        timeoutMs: 120_000,
      },
    };
  }
}
