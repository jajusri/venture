"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConnectorHttpClient = void 0;
class ConnectorHttpClient {
    fetchImpl;
    baseUrl;
    timeoutMs;
    constructor(config) {
        this.baseUrl = config.baseUrl.replace(/\/$/, '');
        this.fetchImpl = config.fetchImpl ?? fetch;
        this.timeoutMs = config.timeoutMs ?? 10_000;
    }
    async getHealth() {
        return this.getJson('/health');
    }
    async getSession() {
        return this.getJson('/session');
    }
    async validateSession() {
        return this.postJson('/session/validate');
    }
    isReachable() {
        return this.getHealth()
            .then(() => true)
            .catch(() => false);
    }
    async getJson(path) {
        const response = await this.request(path, { method: 'GET' });
        return this.parseJson(response);
    }
    async postJson(path) {
        const response = await this.request(path, { method: 'POST', body: '{}' });
        return this.parseJson(response);
    }
    async request(path, init) {
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
        }
        finally {
            clearTimeout(timeout);
        }
    }
    async parseJson(response) {
        if (!response.ok) {
            throw new Error(`Connector request failed: HTTP ${response.status}`);
        }
        return (await response.json());
    }
}
exports.ConnectorHttpClient = ConnectorHttpClient;
//# sourceMappingURL=connector-http-client.js.map