import { toUserMessage } from './connector-error.js';
import { ConnectorHttpClient } from './connector-http-client.js';
import type {
  LedgerListResult,
  LedgerPageState,
  LedgerStatisticsResult,
  LedgerSyncProgressResult,
  LedgerSyncResult,
} from './types.js';
import type { LogService } from './log-service.js';

export interface LedgerServiceOptions {
  readonly connectorBaseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly logService: LogService;
  readonly maxAttempts?: number;
  readonly retryBaseDelayMs?: number;
}

export class LedgerService {
  private readonly client: ConnectorHttpClient;
  private readonly logService: LogService;

  constructor(options: LedgerServiceOptions) {
    this.client = new ConnectorHttpClient({
      baseUrl: options.connectorBaseUrl,
      fetchImpl: options.fetchImpl,
      maxAttempts: options.maxAttempts,
      retryBaseDelayMs: options.retryBaseDelayMs,
    });
    this.logService = options.logService;
  }

  async getPageState(query = '', page = 1, pageSize = 25): Promise<LedgerPageState> {
    try {
      const [list, statistics, progress] = await Promise.all([
        this.client.getLedgers({ query, page, pageSize }),
        this.client.getLedgerStatistics(),
        this.client.getLedgerSyncStatus(),
      ]);

      return {
        ok: true,
        list,
        statistics,
        progress,
        storage: progress.storage ?? null,
        userMessage: null,
      };
    } catch (error) {
      const message = toUserMessage(error);
      this.logService.append('error', `Ledger page load failed: ${message}`);
      return {
        ok: false,
        list: null,
        statistics: null,
        progress: null,
        storage: null,
        userMessage: message,
      };
    }
  }

  async syncLedgers(incremental = false): Promise<LedgerSyncResult> {
    try {
      const result = await this.client.syncLedgers(incremental);
      this.logService.appendStructured({
        level: 'information',
        message: `Ledger sync ${result.status}`,
        event: 'ledger_sync_completed',
        component: 'ledger-service',
        metadata: {
          itemsAdded: result.progress.itemsAdded,
          itemsUpdated: result.progress.itemsUpdated,
          durationMs: result.progress.durationMs ?? 0,
        },
      });
      return result;
    } catch (error) {
      const message = toUserMessage(error);
      this.logService.append('error', `Ledger sync failed: ${message}`);
      throw error;
    }
  }

  async getStatistics(): Promise<LedgerStatisticsResult> {
    return this.client.getLedgerStatistics();
  }

  async getSyncProgress(): Promise<LedgerSyncProgressResult> {
    return this.client.getLedgerSyncStatus();
  }

  async getLedgers(params: {
    readonly query?: string;
    readonly page?: number;
    readonly pageSize?: number;
  }): Promise<LedgerListResult> {
    return this.client.getLedgers(params);
  }

  async cancelSync(): Promise<LedgerSyncProgressResult> {
    return this.client.cancelLedgerSync();
  }

  async clearCache(): Promise<{ ok: boolean; message: string }> {
    const result = await this.client.clearLedgerCache();
    this.logService.append('information', 'Ledger cache cleared');
    return result;
  }
}
