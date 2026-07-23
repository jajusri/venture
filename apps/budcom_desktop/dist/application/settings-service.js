"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SettingsService = void 0;
const desktop_config_schema_js_1 = require("./desktop-config-schema.js");
const desktop_config_resolver_js_1 = require("./desktop-config-resolver.js");
const desktop_config_defaults_js_1 = require("./desktop-config-defaults.js");
const dashboard_service_js_1 = require("./dashboard-service.js");
class SettingsService {
    configStore;
    logService;
    isDevelopment;
    connectorExecutable;
    resolved;
    configStatus;
    pendingDraft = null;
    constructor(options) {
        this.configStore = options.configStore;
        this.logService = options.logService;
        this.isDevelopment = options.isDevelopment ?? process.env.NODE_ENV !== 'production';
        this.connectorExecutable = options.connectorExecutable;
        const loadResult = this.configStore.loadFromDisk();
        this.configStatus = loadResult.status;
        this.resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(loadResult.config, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
    }
    getResolvedConfig() {
        return this.resolved;
    }
    getConfigStatus() {
        return this.configStatus;
    }
    setConfigStatus(status) {
        this.configStatus = status;
    }
    reload() {
        const loadResult = this.configStore.loadFromDisk();
        this.configStatus = loadResult.status;
        this.resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(loadResult.config, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
        this.pendingDraft = null;
        return this.resolved;
    }
    applyPersistedConfig(config) {
        this.resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(config, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
        return this.resolved;
    }
    validateInput(input) {
        const result = (0, desktop_config_schema_js_1.validateSettingsPatch)(input, this.resolved.persisted);
        return {
            ok: result.ok,
            errors: result.errors,
        };
    }
    getSettingsState() {
        const effective = this.resolved.effective;
        return {
            connectorUrl: this.resolved.connectorBaseUrl,
            connectorHost: effective.connectorHost,
            apiVersion: '1.0.0',
            desktopVersion: dashboard_service_js_1.DESKTOP_VERSION,
            erpType: 'tally',
            pollIntervalSeconds: Math.round(effective.healthPollIntervalMs / 1000),
            connectorExecutable: this.connectorExecutable,
            connectorPort: effective.connectorPort,
            autoStartConnector: effective.autoStartConnector,
            healthPollIntervalMs: effective.healthPollIntervalMs,
            startupTimeoutMs: effective.startupTimeoutMs,
            shutdownGraceMs: effective.shutdownGraceMs,
            maxRestartAttempts: effective.maxRestartAttempts,
            reconnectBaseDelayMs: effective.reconnectBaseDelayMs,
            logLevel: effective.logLevel,
            diagnosticsRetentionDays: effective.diagnosticsRetentionDays,
            tallyHost: effective.tallyHost,
            tallyPort: effective.tallyPort,
            configSource: Object.keys(this.resolved.sources).length > 0 ? 'mixed (env + persisted)' : 'persisted/default',
            configStatus: this.configStatus,
            restartRequired: false,
            hasUnsavedChanges: this.pendingDraft !== null,
        };
    }
    previewPatch(input) {
        const validation = (0, desktop_config_schema_js_1.validateSettingsPatch)(input, this.resolved.persisted);
        if (!validation.ok || !validation.config) {
            return this.getSettingsState();
        }
        this.pendingDraft = validation.config;
        const preview = validation.config;
        const previewResolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(preview, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
        return {
            ...this.getSettingsState(),
            connectorUrl: previewResolved.connectorBaseUrl,
            connectorHost: preview.connectorHost,
            connectorPort: preview.connectorPort,
            autoStartConnector: preview.autoStartConnector,
            healthPollIntervalMs: preview.healthPollIntervalMs,
            startupTimeoutMs: preview.startupTimeoutMs,
            shutdownGraceMs: preview.shutdownGraceMs,
            maxRestartAttempts: preview.maxRestartAttempts,
            reconnectBaseDelayMs: preview.reconnectBaseDelayMs,
            logLevel: preview.logLevel,
            diagnosticsRetentionDays: preview.diagnosticsRetentionDays,
            tallyHost: preview.tallyHost,
            tallyPort: preview.tallyPort,
            restartRequired: (0, desktop_config_schema_js_1.requiresRestart)(this.resolved.effective, preview),
            hasUnsavedChanges: true,
        };
    }
    saveSettings(input) {
        const validation = (0, desktop_config_schema_js_1.validateSettingsPatch)(input, this.resolved.persisted);
        if (!validation.ok || !validation.config) {
            return {
                ok: false,
                message: validation.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
                restartRequired: false,
                settings: null,
            };
        }
        const merged = validation.config;
        const validated = (0, desktop_config_schema_js_1.validateDesktopConfig)(merged);
        if (!validated.ok || !validated.config) {
            return {
                ok: false,
                message: validated.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
                restartRequired: false,
                settings: null,
            };
        }
        const restartRequired = (0, desktop_config_schema_js_1.requiresRestart)(this.resolved.effective, validated.config);
        const saveResult = this.configStore.save(validated.config);
        if (!saveResult.ok) {
            return {
                ok: false,
                message: saveResult.message,
                restartRequired: false,
                settings: null,
            };
        }
        this.configStatus = 'loaded';
        this.resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(validated.config, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
        this.pendingDraft = null;
        this.logService.appendStructured({
            level: 'information',
            message: 'Desktop settings saved.',
            event: 'settings_saved',
            component: 'settings',
            metadata: { restartRequired },
        });
        return {
            ok: true,
            message: restartRequired
                ? 'Settings saved. Restart the desktop app or reconnect the connector for all changes to take effect.'
                : 'Settings saved successfully.',
            restartRequired,
            settings: this.getSettingsState(),
        };
    }
    restoreDefaults() {
        const defaults = this.configStore.restoreDefaults();
        this.configStatus = 'defaults';
        this.resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(defaults, (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(this.isDevelopment));
        this.pendingDraft = null;
        this.logService.appendStructured({
            level: 'information',
            message: 'Desktop settings restored to defaults.',
            event: 'settings_restored',
            component: 'settings',
        });
        return {
            ok: true,
            message: 'Default settings restored.',
            restartRequired: true,
            settings: this.getSettingsState(),
        };
    }
}
exports.SettingsService = SettingsService;
//# sourceMappingURL=settings-service.js.map