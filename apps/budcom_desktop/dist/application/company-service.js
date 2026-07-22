"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CompanyService = void 0;
const connector_error_js_1 = require("./connector-error.js");
const connector_http_client_js_1 = require("./connector-http-client.js");
class CompanyService {
    client;
    logService;
    constructor(options) {
        this.client = new connector_http_client_js_1.ConnectorHttpClient({
            baseUrl: options.connectorBaseUrl,
            fetchImpl: options.fetchImpl,
            maxAttempts: options.maxAttempts,
            retryBaseDelayMs: options.retryBaseDelayMs,
        });
        this.logService = options.logService;
    }
    async discoverCompanies() {
        try {
            const result = await this.client.getCompanies();
            this.logService.append('information', result.items.length > 0
                ? `Discovered ${result.items.length} companies`
                : `Company discovery returned ${result.status}`);
            if (result.status !== 'SUCCESS' && result.status !== 'EMPTY' && result.status !== 'INCOMPLETE') {
                this.logService.append('warning', (0, connector_error_js_1.mapDiscoveryUserMessage)(result.status, result.reason));
            }
            return result;
        }
        catch (error) {
            const message = (0, connector_error_js_1.toUserMessage)(error);
            this.logService.append('error', `Company discovery failed: ${message}`);
            throw error;
        }
    }
    async selectCompany(companyId) {
        try {
            const result = await this.client.selectCompany(companyId);
            const userMessage = (0, connector_error_js_1.mapSelectionUserMessage)(result.status, result.reason);
            const ok = result.status === 'SUCCESS' || result.status === 'DUPLICATE_SELECTION';
            this.logService.append(ok ? 'information' : 'warning', ok
                ? `Selected company: ${result.session.selectedCompany?.name ?? companyId}`
                : `Company selection failed: ${userMessage}`);
            return {
                ok,
                status: result.status,
                userMessage,
                session: result.session,
            };
        }
        catch (error) {
            const userMessage = (0, connector_error_js_1.toUserMessage)(error);
            this.logService.append('error', `Company selection failed: ${userMessage}`);
            return {
                ok: false,
                status: 'CONNECTOR_UNAVAILABLE',
                userMessage,
                session: null,
            };
        }
    }
    async clearSelection() {
        try {
            const result = await this.client.clearCompanySelection();
            this.logService.append('information', 'Cleared company selection');
            return result.session;
        }
        catch (error) {
            const message = (0, connector_error_js_1.toUserMessage)(error);
            this.logService.append('error', `Clear company selection failed: ${message}`);
            throw error;
        }
    }
    static isSuccessfulSelection(status) {
        return status === 'SUCCESS' || status === 'DUPLICATE_SELECTION';
    }
}
exports.CompanyService = CompanyService;
//# sourceMappingURL=company-service.js.map