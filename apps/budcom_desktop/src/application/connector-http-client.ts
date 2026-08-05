import { ConnectorRequestError, mapConnectorErrorBody } from './connector-error.js';
import { retryWithBackoff } from './http-retry.js';
import type {
  CompanyListResult,
  CompanySelectionResult,
  ConnectorClientConfig,
  ConnectorErrorBody,
  DeviceListResult,
  HealthResponse,
  LedgerListResult,
  LedgerStatisticsResult,
  LedgerSyncProgressResult,
  LedgerSyncResult,
  PairingCredentialListResult,
  PairingSessionCreateResult,
  PairingSessionStatusResult,
  SessionSnapshotResponse,
  SessionValidationResponse,
  StockItemListResult,
  StockItemStatisticsResult,
  StockItemSyncProgressResult,
  StockItemSyncResult,
} from './types.js';

/** Must match require-desktop-control-token.ts's CONTROL_TOKEN_HEADER on the Connector side. */
export const DESKTOP_CONTROL_TOKEN_HEADER = 'x-budcom-desktop-control-token';

export class ConnectorHttpClient {
  private readonly fetchImpl: typeof fetch;
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly maxAttempts: number;
  private readonly retryBaseDelayMs: number;
  private readonly defaultHeaders: Readonly<Record<string, string>>;

  constructor(config: ConnectorClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.fetchImpl = config.fetchImpl ?? fetch;
    this.timeoutMs = config.timeoutMs ?? 10_000;
    this.maxAttempts = config.maxAttempts ?? 3;
    this.retryBaseDelayMs = config.retryBaseDelayMs ?? 500;
    this.defaultHeaders = config.defaultHeaders ?? {};
  }

  async getHealth(): Promise<HealthResponse> {
    return this.getJsonWithRetry<HealthResponse>('/health');
  }

  async getSession(): Promise<SessionSnapshotResponse> {
    return this.getJsonWithRetry<SessionSnapshotResponse>('/session');
  }

  /** Paired-device count for the Mobile Access status model. Never includes tokens. */
  async getDeviceList(): Promise<DeviceListResult> {
    return this.getJsonWithRetry<DeviceListResult>('/device/list');
  }

  /**
   * Creates a pairing session. Requires the Desktop control-token header (via `defaultHeaders`
   * on this client instance) whenever the Connector is bound off-loopback — see
   * require-desktop-control-token.ts on the Connector side. No retry: a pairing session must not
   * be silently created twice by a transient-failure retry.
   */
  async createPairingSession(): Promise<PairingSessionCreateResult> {
    const response = await this.request('/device/pairing-session', { method: 'POST', body: '{}' });
    return this.parseJson<PairingSessionCreateResult>(response);
  }

  async getPairingSessionStatus(pairingSessionId: string): Promise<PairingSessionStatusResult> {
    return this.getJsonWithRetry<PairingSessionStatusResult>(
      `/device/pairing-session/status?pairingSessionId=${encodeURIComponent(pairingSessionId)}`,
    );
  }

  /** No retry — cancellation must not be attempted twice on a transient network blip. */
  async cancelPairingSession(pairingSessionId: string): Promise<{ ok: boolean }> {
    const response = await this.request('/device/pairing-session/cancel', {
      method: 'POST',
      body: JSON.stringify({ pairingSessionId }),
    });
    return this.parseJson<{ ok: boolean }>(response);
  }

  async listPairingCredentials(): Promise<PairingCredentialListResult> {
    return this.getJsonWithRetry<PairingCredentialListResult>('/device/pairing-credentials');
  }

  /** No retry — revocation must not be attempted twice on a transient network blip. */
  async revokePairingCredential(credentialId: string): Promise<{ ok: boolean; message: string }> {
    const response = await this.request('/device/pairing-credential/revoke', {
      method: 'POST',
      body: JSON.stringify({ credentialId }),
    });
    return this.parseJson<{ ok: boolean; message: string }>(response);
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
          ...this.defaultHeaders,
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
