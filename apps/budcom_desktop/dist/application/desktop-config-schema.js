"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RESTART_REQUIRED_FIELDS = exports.DESKTOP_CONFIG_SCHEMA_VERSION = void 0;
exports.buildConnectorBaseUrl = buildConnectorBaseUrl;
exports.parseConnectorHostPort = parseConnectorHostPort;
exports.validateDesktopConfig = validateDesktopConfig;
exports.validateSettingsPatch = validateSettingsPatch;
exports.requiresRestart = requiresRestart;
exports.DESKTOP_CONFIG_SCHEMA_VERSION = 1;
const LOG_LEVELS = ['debug', 'info', 'warn', 'error'];
function isRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function readString(value, field, errors) {
    if (typeof value !== 'string' || value.trim().length === 0) {
        errors.push({ field, message: `${field} must be a non-empty string.` });
        return null;
    }
    return value.trim();
}
function readNumber(value, field, errors, min, max) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
        errors.push({ field, message: `${field} must be a number between ${min} and ${max}.` });
        return null;
    }
    return value;
}
function readBoolean(value, field, errors) {
    if (typeof value !== 'boolean') {
        errors.push({ field, message: `${field} must be a boolean.` });
        return null;
    }
    return value;
}
function buildConnectorBaseUrl(host, port) {
    return `http://${host}:${port}`;
}
function parseConnectorHostPort(baseUrl) {
    try {
        const parsed = new URL(baseUrl);
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
            return null;
        }
        const host = parsed.hostname;
        const port = parsed.port
            ? Number.parseInt(parsed.port, 10)
            : parsed.protocol === 'https:' ? 443 : 80;
        if (!host || !Number.isFinite(port)) {
            return null;
        }
        return { host, port };
    }
    catch {
        return null;
    }
}
function validateDesktopConfig(input) {
    const errors = [];
    if (!isRecord(input)) {
        return {
            ok: false,
            config: null,
            errors: [{ field: 'root', message: 'Configuration must be a JSON object.' }],
        };
    }
    if (input.schemaVersion !== exports.DESKTOP_CONFIG_SCHEMA_VERSION) {
        errors.push({
            field: 'schemaVersion',
            message: `Unsupported schema version. Expected ${exports.DESKTOP_CONFIG_SCHEMA_VERSION}.`,
        });
    }
    const connectorHost = readString(input.connectorHost, 'connectorHost', errors);
    const connectorPort = readNumber(input.connectorPort, 'connectorPort', errors, 1, 65535);
    const autoStartConnector = readBoolean(input.autoStartConnector, 'autoStartConnector', errors);
    const healthPollIntervalMs = readNumber(input.healthPollIntervalMs, 'healthPollIntervalMs', errors, 1_000, 300_000);
    const startupTimeoutMs = readNumber(input.startupTimeoutMs, 'startupTimeoutMs', errors, 5_000, 300_000);
    const shutdownGraceMs = readNumber(input.shutdownGraceMs, 'shutdownGraceMs', errors, 1_000, 60_000);
    const maxRestartAttempts = readNumber(input.maxRestartAttempts, 'maxRestartAttempts', errors, 0, 20);
    const reconnectBaseDelayMs = readNumber(input.reconnectBaseDelayMs, 'reconnectBaseDelayMs', errors, 100, 60_000);
    const diagnosticsRetentionDays = readNumber(input.diagnosticsRetentionDays, 'diagnosticsRetentionDays', errors, 1, 90);
    let logLevel = null;
    if (typeof input.logLevel !== 'string' || !LOG_LEVELS.includes(input.logLevel)) {
        errors.push({ field: 'logLevel', message: `logLevel must be one of: ${LOG_LEVELS.join(', ')}.` });
    }
    else {
        logLevel = input.logLevel;
    }
    const tallyHost = readString(input.tallyHost, 'tallyHost', errors);
    const tallyPort = readNumber(input.tallyPort, 'tallyPort', errors, 1, 65535);
    if (connectorHost && !/^[a-zA-Z0-9.-]+$/.test(connectorHost)) {
        errors.push({ field: 'connectorHost', message: 'connectorHost contains invalid characters.' });
    }
    if (errors.length > 0) {
        return { ok: false, config: null, errors };
    }
    return {
        ok: true,
        config: {
            schemaVersion: exports.DESKTOP_CONFIG_SCHEMA_VERSION,
            connectorHost: connectorHost,
            connectorPort: connectorPort,
            autoStartConnector: autoStartConnector,
            healthPollIntervalMs: healthPollIntervalMs,
            startupTimeoutMs: startupTimeoutMs,
            shutdownGraceMs: shutdownGraceMs,
            maxRestartAttempts: maxRestartAttempts,
            reconnectBaseDelayMs: reconnectBaseDelayMs,
            logLevel: logLevel,
            diagnosticsRetentionDays: diagnosticsRetentionDays,
            tallyHost: tallyHost,
            tallyPort: tallyPort,
        },
        errors: [],
    };
}
function validateSettingsPatch(input, base) {
    if (!isRecord(input)) {
        return {
            ok: false,
            config: null,
            errors: [{ field: 'root', message: 'Settings payload must be an object.' }],
        };
    }
    return validateDesktopConfig({
        ...base,
        ...input,
        schemaVersion: exports.DESKTOP_CONFIG_SCHEMA_VERSION,
    });
}
/** Fields that require desktop restart or lifecycle re-init to take full effect. */
exports.RESTART_REQUIRED_FIELDS = [
    'connectorHost',
    'connectorPort',
    'healthPollIntervalMs',
    'startupTimeoutMs',
    'shutdownGraceMs',
    'maxRestartAttempts',
    'reconnectBaseDelayMs',
    'tallyHost',
    'tallyPort',
];
function requiresRestart(previous, next) {
    return exports.RESTART_REQUIRED_FIELDS.some((field) => previous[field] !== next[field]);
}
//# sourceMappingURL=desktop-config-schema.js.map