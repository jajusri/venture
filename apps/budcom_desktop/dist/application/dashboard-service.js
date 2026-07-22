"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DashboardService = exports.POLL_INTERVAL_SECONDS = exports.DESKTOP_VERSION = exports.DESKTOP_WINDOW_TITLE = void 0;
const connector_error_js_1 = require("./connector-error.js");
const connector_http_client_js_1 = require("./connector-http-client.js");
const connection_status_mapper_js_1 = require("./connection-status-mapper.js");
const log_service_js_1 = require("./log-service.js");
const session_display_mapper_js_1 = require("./session-display-mapper.js");
const sync_status_mapper_js_1 = require("./sync-status-mapper.js");
exports.DESKTOP_WINDOW_TITLE = 'Business OS Tally Connector';
exports.DESKTOP_VERSION = '0.4.2';
exports.POLL_INTERVAL_SECONDS = 5;
class DashboardService {
    client;
    logService;
    connectorBaseUrl;
    lastRefreshAt = null;
    constructor(options) {
        this.connectorBaseUrl = options.connectorBaseUrl;
        this.client = new connector_http_client_js_1.ConnectorHttpClient({
            baseUrl: options.connectorBaseUrl,
            fetchImpl: options.fetchImpl,
            maxAttempts: options.maxAttempts,
            retryBaseDelayMs: options.retryBaseDelayMs,
        });
        this.logService = options.logService ?? new log_service_js_1.LogService();
    }
    getLogService() {
        return this.logService;
    }
    getSettingsState(overrides = {}) {
        return {
            connectorUrl: this.connectorBaseUrl,
            apiVersion: '1.0.0',
            desktopVersion: exports.DESKTOP_VERSION,
            erpType: 'tally',
            pollIntervalSeconds: exports.POLL_INTERVAL_SECONDS,
            connectorExecutable: '—',
            connectorPort: 8080,
            autoStartConnector: true,
            ...overrides,
        };
    }
    async getDashboardState() {
        let connectorReachable = false;
        let health = null;
        let session = null;
        let userMessage = null;
        try {
            health = await this.client.getHealth();
            connectorReachable = true;
            this.logService.append('information', `Health status: ${health.status}`);
        }
        catch (error) {
            const message = (0, connector_error_js_1.toUserMessage)(error);
            userMessage = message;
            this.logService.append('error', `Connector health request failed: ${message}`);
        }
        if (connectorReachable) {
            try {
                session = await this.client.getSession();
                this.logService.append('information', session.session.selectedCompany
                    ? `Session company: ${session.session.selectedCompany.name}`
                    : 'No company selected in session');
            }
            catch (error) {
                const message = (0, connector_error_js_1.toUserMessage)(error);
                userMessage = message;
                this.logService.append('warning', `Session request failed: ${message}`);
            }
        }
        let validation = null;
        if (connectorReachable && session?.session.selectedCompany) {
            try {
                validation = await this.client.validateSession();
                if (validation.status !== 'SUCCESS') {
                    this.logService.append('warning', `Session validation: ${validation.status}`);
                }
            }
            catch (error) {
                const message = (0, connector_error_js_1.toUserMessage)(error);
                this.logService.append('warning', `Session validation failed: ${message}`);
            }
        }
        this.lastRefreshAt = new Date().toISOString();
        const connectionIndicator = (0, connection_status_mapper_js_1.mapConnectionIndicator)(connectorReachable, health);
        const syncStatus = (0, sync_status_mapper_js_1.mapSyncDisplayStatus)(health?.services ?? []);
        const licensing = health?.services.find((service) => service.name === 'Licensing');
        return {
            windowTitle: exports.DESKTOP_WINDOW_TITLE,
            connectorVersion: health?.connectorVersion ?? session?.session.connectorVersion ?? '—',
            apiVersion: health?.schemaVersion ?? '—',
            desktopVersion: exports.DESKTOP_VERSION,
            erpType: session?.session.erpType ?? 'tally',
            erpName: (0, session_display_mapper_js_1.resolveErpName)(session),
            connectionIndicator,
            connectionLabel: (0, connection_status_mapper_js_1.getConnectionLabel)(connectionIndicator),
            companyName: (0, session_display_mapper_js_1.resolveCompanyName)(session),
            companyId: (0, session_display_mapper_js_1.resolveCompanyId)(session),
            selectionTime: (0, session_display_mapper_js_1.formatSelectionTime)(session?.session.selectedAt ?? null),
            sessionStatus: (0, session_display_mapper_js_1.mapSessionDisplayStatus)(connectorReachable, session, validation),
            syncStatus,
            syncLabel: (0, sync_status_mapper_js_1.getSyncLabel)(syncStatus),
            lastSync: (0, sync_status_mapper_js_1.formatLastSync)(session?.session.lastValidatedAt ?? null),
            lastRefresh: (0, session_display_mapper_js_1.formatTimestamp)(this.lastRefreshAt),
            licenseStatus: licensing?.message?.includes('Placeholder') ? 'Evaluation' : (licensing?.message ?? 'Unknown'),
            connectorReachable,
            healthStatus: health?.status ?? 'unknown',
            userMessage,
        };
    }
}
exports.DashboardService = DashboardService;
//# sourceMappingURL=dashboard-service.js.map