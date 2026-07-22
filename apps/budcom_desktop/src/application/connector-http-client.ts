import type {
  ConnectorClientConfig,
  HealthResponse,
  SessionSnapshotResponse,
  SessionValidationResponse,
} from './types.js';

export class ConnectorHttpClient {
  private readonly fetchImpl: typeof fetch;
  private readonly baseUrl: string;
  private readonly timeoutMs: number;

  constructor(config: ConnectorClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.fetchImpl = config.fetchImpl ?? fetch;
    this.timeoutMs = config.timeoutMs ?? 10_000;
  }

  async getHealth(): Promise<HealthResponse> {
    return this.getJson<HealthResponse>('/health');
  }

  async getSession(): Promise<SessionSnapshotResponse> {
    return this.getJson<SessionSnapshotResponse>('/session');
  }

  async validateSession(): Promise<SessionValidationResponse> {
    return this.postJson<SessionValidationResponse>('/session/validate');
  }

  isReachable(): Promise<boolean> {
    return this.getHealth()
      .then(() => true)
      .catch(() => false);
  }

  private async getJson<T>(path: string): Promise<T> {
    const response = await this.request(path, { method: 'GET' });
    return this.parseJson<T>(response);
  }

  private async postJson<T>(path: string): Promise<T> {
    const response = await this.request(path, { method: 'POST', body: '{}' });
    return this.parseJson<T>(response);
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
      throw new Error(`Connector request failed: HTTP ${response.status}`);
    }
    return (await response.json()) as T;
  }
}
