"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DashboardService = exports.DESKTOP_WINDOW_TITLE = void 0;
const connector_http_client_js_1 = require("./connector-http-client.js");
const connection_status_mapper_js_1 = require("./connection-status-mapper.js");
const log_service_js_1 = require("./log-service.js");
const session_display_mapper_js_1 = require("./session-display-mapper.js");
const sync_status_mapper_js_1 = require("./sync-status-mapper.js");
exports.DESKTOP_WINDOW_TITLE = 'Business OS Tally Connector';
class DashboardService {
    client;
    logService;
    constructor(options) {
        this.client = new connector_http_client_js_1.ConnectorHttpClient({
            baseUrl: options.connectorBaseUrl,
            fetchImpl: options.fetchImpl,
        });
        this.logService = options.logService ?? new log_service_js_1.LogService();
    }
    getLogService() {
        return this.logService;
    }
    async getDashboardState() {
        let connectorReachable = false;
        let health = null;
        let session = null;
        try {
            health = await this.client.getHealth();
            connectorReachable = true;
            this.logService.append('information', `Health status: ${health.status}`);
        }
        catch (error) {
            const message = error instanceof Error ? error.message : String(error);
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
                const message = error instanceof Error ? error.message : String(error);
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
                const message = error instanceof Error ? error.message : String(error);
                this.logService.append('warning', `Session validation failed: ${message}`);
            }
        }
        const connectionIndicator = (0, connection_status_mapper_js_1.mapConnectionIndicator)(connectorReachable, health);
        const syncStatus = (0, sync_status_mapper_js_1.mapSyncDisplayStatus)(health?.services ?? []);
        const licensing = health?.services.find((service) => service.name === 'Licensing');
        return {
            windowTitle: exports.DESKTOP_WINDOW_TITLE,
            connectorVersion: health?.connectorVersion ?? session?.session.connectorVersion ?? '—',
            erpType: session?.session.erpType ?? 'tally',
            connectionIndicator,
            connectionLabel: (0, connection_status_mapper_js_1.getConnectionLabel)(connectionIndicator),
            companyName: (0, session_display_mapper_js_1.resolveCompanyName)(session),
            companyId: (0, session_display_mapper_js_1.resolveCompanyId)(session),
            selectionTime: (0, session_display_mapper_js_1.formatSelectionTime)(session?.session.selectedAt ?? null),
            sessionStatus: (0, session_display_mapper_js_1.resolveSessionStatus)(session, validation),
            syncStatus,
            syncLabel: (0, sync_status_mapper_js_1.getSyncLabel)(syncStatus),
            lastSync: (0, sync_status_mapper_js_1.formatLastSync)(session?.session.lastValidatedAt ?? null),
            licenseStatus: licensing?.message?.includes('Placeholder') ? 'Evaluation' : (licensing?.message ?? 'Unknown'),
            connectorReachable,
            healthStatus: health?.status ?? 'unknown',
        };
    }
}
exports.DashboardService = DashboardService;
//# sourceMappingURL=dashboard-service.js.map