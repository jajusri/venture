import { ConnectorRequestError, mapConnectorErrorBody } from './connector-error.js';
import { retryWithBackoff } from './http-retry.js';
import type {
  CompanyListResult,
  CompanySelectionResult,
  ConnectorClientConfig,
  ConnectorErrorBody,
  HealthResponse,
  SessionSnapshotResponse,
  SessionValidationResponse,
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

  private async request(path: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      return await this.fetchImpl(`${this.baseUrl}${path}`, {
        ...init,
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          ...(init.headers ?? {}),
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
