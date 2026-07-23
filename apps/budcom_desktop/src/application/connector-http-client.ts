import { ConnectorRequestError, mapConnectorErrorBody } from './connector-error.js';
import { retryWithBackoff } from './http-retry.js';
import type {
  CompanyListResult,
  CompanySelectionResult,
  ConnectorClientConfig,
  ConnectorErrorBody,
  HealthResponse,
  LedgerListResult,
  LedgerStatisticsResult,
  LedgerSyncProgressResult,
  LedgerSyncResult,
  SessionSnapshotResponse,
  SessionValidationResponse,
  StockItemListResult,
  StockItemStatisticsResult,
  StockItemSyncProgressResult,
  StockItemSyncResult,
} from './types.js';

export class ConnectorHttpClient {
  private readonly fetchImpl: typeof fetch;
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly maxAttempts: number;
  private readonly retryBaseDelayMs: number;

  constructor(config: ConnectorClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.fetchImpl = config.fetchImpl ?? fetch;
    this.timeoutMs = config.timeoutMs ?? 10_000;
    this.maxAttempts = config.maxAttempts ?? 3;
    this.retryBaseDelayMs = config.retryBaseDelayMs ?? 500;
  }

  async getHealth(): Promise<HealthResponse> {
    return this.getJsonWithRetry<HealthResponse>('/health');
  }

  async getSession(): Promise<SessionSnapshotResponse> {
    return this.getJsonWithRetry<SessionSnapshotResponse>('/session');
  }

  async validateSession(): Promise<SessionValidationResponse> {
    return this.postJsonWithRetry<SessionValidationResponse>('/session/validate');
  }

  async getCompanies(): Promise<CompanyListResult> {
    return this.getJsonWithRetry<CompanyListResult>('/companies');
  }

  async selectCompany(companyId: string): Promise<CompanySelectionResult> {
    const response = await this.requestWithRetry('/session/company', {
      method: 'POST',
      body: JSON.stringify({ companyId }),
    });
    const body = (await response.json()) as CompanySelectionResult;
    if (!response.ok && !body.status) {
      throw await this.buildRequestError(response);
    }
    return body;
  }

  async clearCompanySelection(): Promise<SessionSnapshotResponse> {
    const response = await this.requestWithRetry('/session/company', { method: 'DELETE' });
    const body = (await response.json()) as { session: SessionSnapshotResponse['session']; contractVersion?: string };
    if (!response.ok) {
      throw await this.buildRequestError(response);
    }
    return {
      session: body.session,
      contractVersion: body.contractVersion ?? '1',
    };
  }

  async getLedgers(params: {
    readonly query?: string;
    readonly page?: number;
    readonly pageSize?: number;
  } = {}): Promise<LedgerListResult> {
    const search = new URLSearchParams();
    if (params.query) search.set('query', params.query);
    if (params.page) search.set('page', String(params.page));
    if (params.pageSize) search.set('pageSize', String(params.pageSize));
    const suffix = search.toString() ? `?${search.toString()}` : '';
    return this.getJsonWithRetry<LedgerListResult>(`/ledgers${suffix}`);
  }

  async syncLedgers(incremental = false): Promise<LedgerSyncResult> {
    const response = await this.request('/sync/ledgers', {
      method: 'POST',
      body: JSON.stringify({ incremental }),
      timeoutMs: 600_000,
    });
    return this.parseJson<LedgerSyncResult>(response);
  }

  async getLedgerSyncStatus(): Promise<LedgerSyncProgressResult> {
    return this.getJsonWithRetry<LedgerSyncProgressResult>('/sync/ledgers/status');
  }

  async getLedgerStatistics(): Promise<LedgerStatisticsResult> {
    return this.getJsonWithRetry<LedgerStatisticsResult>('/sync/ledgers/statistics');
  }

  async cancelLedgerSync(): Promise<LedgerSyncProgressResult> {
    return this.postJsonWithRetry<LedgerSyncProgressResult>('/sync/ledgers/cancel');
  }

  async clearLedgerCache(): Promise<{ ok: boolean; message: string }> {
    return this.postJsonWithRetry<{ ok: boolean; message: string }>('/sync/ledgers/clear-cache');
  }

  async getStockItems(params: {
    readonly query?: string;
    readonly page?: number;
    readonly pageSize?: number;
  } = {}): Promise<StockItemListResult> {
    const search = new URLSearchParams();
    if (params.query) search.set('query', params.query);
    if (params.page) search.set('page', String(params.page));
    if (params.pageSize) search.set('pageSize', String(params.pageSize));
    const suffix = search.toString() ? `?${search.toString()}` : '';
    return this.getJsonWithRetry<StockItemListResult>(`/stock-items${suffix}`);
  }

  async syncStockItems(incremental = false): Promise<StockItemSyncResult> {
    const response = await this.request('/sync/stock-items', {
      method: 'POST',
      body: JSON.stringify({ incremental }),
      timeoutMs: 600_000,
    });
    return this.parseJson<StockItemSyncResult>(response);
  }

  async getStockItemSyncStatus(): Promise<StockItemSyncProgressResult> {
    return this.getJsonWithRetry<StockItemSyncProgressResult>('/sync/stock-items/status');
  }

  async getStockItemStatistics(): Promise<StockItemStatisticsResult> {
    return this.getJsonWithRetry<StockItemStatisticsResult>('/sync/stock-items/statistics');
  }

  async cancelStockItemSync(): Promise<StockItemSyncProgressResult> {
    return this.postJsonWithRetry<StockItemSyncProgressResult>('/sync/stock-items/cancel');
  }

  async clearStockItemCache(): Promise<{ ok: boolean; message: string }> {
    return this.postJsonWithRetry<{ ok: boolean; message: string }>('/sync/stock-items/clear-cache');
  }

  isReachable(): Promise<boolean> {
    return this.getHealth()
      .then(() => true)
      .catch(() => false);
  }

  private async getJsonWithRetry<T>(path: string): Promise<T> {
    return retryWithBackoff(
      async () => {
        const response = await this.request(path, { method: 'GET' });
        return this.parseJson<T>(response);
      },
      { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs },
    );
  }

  private async postJsonWithRetry<T>(path: string): Promise<T> {
    return retryWithBackoff(
      async () => {
        const response = await this.request(path, { method: 'POST', body: '{}' });
        return this.parseJson<T>(response);
      },
      { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs },
    );
  }

  private async requestWithRetry(path: string, init: RequestInit): Promise<Response> {
    return retryWithBackoff(
      async () => this.request(path, init),
      { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs },
    );
  }

  private async request(path: string, init: RequestInit & { timeoutMs?: number } = {}): Promise<Response> {
    const timeoutMs = init.timeoutMs ?? this.timeoutMs;
    const { timeoutMs: _ignored, ...requestInit } = init;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await this.fetchImpl(`${this.baseUrl}${path}`, {
        ...requestInit,
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          ...(requestInit.headers ?? {}),
        },
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timeout);
    }
  }

  private async parseJson<T>(response: Response): Promise<T> {
    if (!response.ok) {
      throw await this.buildRequestError(response);
    }
    return (await response.json()) as T;
  }

  private async buildRequestError(response: Response): Promise<ConnectorRequestError> {
    try {
      const body = (await response.json()) as ConnectorErrorBody;
      return mapConnectorErrorBody(response.status, body);
    } catch {
      return new ConnectorRequestError(
        response.status,
        'REQUEST_FAILED',
        `Connector request failed: HTTP ${response.status}`,
      );
    }
  }
}
