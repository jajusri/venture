"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEVELOPMENT_DEFAULTS = exports.PRODUCTION_DEFAULTS = void 0;
exports.getEnvironmentDefaults = getEnvironmentDefaults;
const desktop_config_schema_js_1 = require("./desktop-config-schema.js");
exports.PRODUCTION_DEFAULTS = {
    schemaVersion: desktop_config_schema_js_1.DESKTOP_CONFIG_SCHEMA_VERSION,
    connectorHost: 'localhost',
    connectorPort: 8080,
    autoStartConnector: true,
    healthPollIntervalMs: 5_000,
    startupTimeoutMs: 30_000,
    shutdownGraceMs: 5_000,
    maxRestartAttempts: 5,
    reconnectBaseDelayMs: 1_000,
    logLevel: 'info',
    diagnosticsRetentionDays: 14,
    tallyHost: 'localhost',
    tallyPort: 9000,
};
exports.DEVELOPMENT_DEFAULTS = {
    ...exports.PRODUCTION_DEFAULTS,
    logLevel: 'debug',
    diagnosticsRetentionDays: 7,
};
function getEnvironmentDefaults(isDevelopment) {
    return isDevelopment ? { ...exports.DEVELOPMENT_DEFAULTS } : { ...exports.PRODUCTION_DEFAULTS };
}
//# sourceMappingURL=desktop-config-defaults.js.map