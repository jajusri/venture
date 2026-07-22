"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConnectorHttpClient = void 0;
const connector_error_js_1 = require("./connector-error.js");
const http_retry_js_1 = require("./http-retry.js");
class ConnectorHttpClient {
    fetchImpl;
    baseUrl;
    timeoutMs;
    maxAttempts;
    retryBaseDelayMs;
    constructor(config) {
        this.baseUrl = config.baseUrl.replace(/\/$/, '');
        this.fetchImpl = config.fetchImpl ?? fetch;
        this.timeoutMs = config.timeoutMs ?? 10_000;
        this.maxAttempts = config.maxAttempts ?? 3;
        this.retryBaseDelayMs = config.retryBaseDelayMs ?? 500;
    }
    async getHealth() {
        return this.getJsonWithRetry('/health');
    }
    async getSession() {
        return this.getJsonWithRetry('/session');
    }
    async validateSession() {
        return this.postJsonWithRetry('/session/validate');
    }
    async getCompanies() {
        return this.getJsonWithRetry('/companies');
    }
    async selectCompany(companyId) {
        const response = await this.requestWithRetry('/session/company', {
            method: 'POST',
            body: JSON.stringify({ companyId }),
        });
        const body = (await response.json());
        if (!response.ok && !body.status) {
            throw await this.buildRequestError(response);
        }
        return body;
    }
    async clearCompanySelection() {
        const response = await this.requestWithRetry('/session/company', { method: 'DELETE' });
        const body = (await response.json());
        if (!response.ok) {
            throw await this.buildRequestError(response);
        }
        return {
            session: body.session,
            contractVersion: body.contractVersion ?? '1',
        };
    }
    isReachable() {
        return this.getHealth()
            .then(() => true)
            .catch(() => false);
    }
    async getJsonWithRetry(path) {
        return (0, http_retry_js_1.retryWithBackoff)(async () => {
            const response = await this.request(path, { method: 'GET' });
            return this.parseJson(response);
        }, { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs });
    }
    async postJsonWithRetry(path) {
        return (0, http_retry_js_1.retryWithBackoff)(async () => {
            const response = await this.request(path, { method: 'POST', body: '{}' });
            return this.parseJson(response);
        }, { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs });
    }
    async requestWithRetry(path, init) {
        return (0, http_retry_js_1.retryWithBackoff)(async () => this.request(path, init), { maxAttempts: this.maxAttempts, baseDelayMs: this.retryBaseDelayMs });
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
            throw await this.buildRequestError(response);
        }
        return (await response.json());
    }
    async buildRequestError(response) {
        try {
            const body = (await response.json());
            return (0, connector_error_js_1.mapConnectorErrorBody)(response.status, body);
        }
        catch {
            return new connector_error_js_1.ConnectorRequestError(response.status, 'REQUEST_FAILED', `Connector request failed: HTTP ${response.status}`);
        }
    }
}
exports.ConnectorHttpClient = ConnectorHttpClient;
//# sourceMappingURL=connector-http-client.js.map