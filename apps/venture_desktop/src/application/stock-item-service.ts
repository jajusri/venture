import { toUserMessage } from './connector-error.js';
import { ConnectorHttpClient } from './connector-http-client.js';
import type {
  StockItemListResult,
  StockItemPageState,
  StockItemStatisticsResult,
  StockItemSyncProgressResult,
  StockItemSyncResult,
} from './types.js';
import type { LogService } from './log-service.js';

export interface StockItemServiceOptions {
  readonly connectorBaseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly logService: LogService;
  readonly maxAttempts?: number;
  readonly retryBaseDelayMs?: number;
}

export class StockItemService {
  private readonly client: ConnectorHttpClient;
  private readonly logService: LogService;

  constructor(options: StockItemServiceOptions) {
    this.client = new ConnectorHttpClient({
      baseUrl: options.connectorBaseUrl,
      fetchImpl: options.fetchImpl,
      maxAttempts: options.maxAttempts,
      retryBaseDelayMs: options.retryBaseDelayMs,
    });
    this.logService = options.logService;
  }

  async getPageState(query = '', page = 1, pageSize = 25): Promise<StockItemPageState> {
    try {
      const [list, statistics, progress] = await Promise.all([
        this.client.getStockItems({ query, page, pageSize }),
        this.client.getStockItemStatistics(),
        this.client.getStockItemSyncStatus(),
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
      this.logService.append('error', `Stock item page load failed: ${message}`);
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

  async syncStockItems(incremental = false): Promise<StockItemSyncResult> {
    try {
      const result = await this.client.syncStockItems(incremental);
      this.logService.appendStructured({
        level: 'information',
        message: `Stock item sync ${result.status}`,
        event: 'stock_item_sync_completed',
        component: 'stock-item-service',
        metadata: {
          itemsAdded: result.progress.itemsAdded,
          itemsUpdated: result.progress.itemsUpdated,
          incompleteData: result.statistics.incompleteData,
          durationMs: result.progress.durationMs ?? 0,
        },
      });
      return result;
    } catch (error) {
      const message = toUserMessage(error);
      this.logService.append('error', `Stock item sync failed: ${message}`);
      throw error;
    }
  }

  async getStatistics(): Promise<StockItemStatisticsResult> {
    return this.client.getStockItemStatistics();
  }

  async getSyncProgress(): Promise<StockItemSyncProgressResult> {
    return this.client.getStockItemSyncStatus();
  }

  async getStockItems(params: {
    readonly query?: string;
    readonly page?: number;
    readonly pageSize?: number;
  }): Promise<StockItemListResult> {
    return this.client.getStockItems(params);
  }

  async cancelSync(): Promise<StockItemSyncProgressResult> {
    return this.client.cancelStockItemSync();
  }

  async clearCache(): Promise<{ ok: boolean; message: string }> {
    const result = await this.client.clearStockItemCache();
    this.logService.append('information', 'Stock item cache cleared');
    return result;
  }
}
