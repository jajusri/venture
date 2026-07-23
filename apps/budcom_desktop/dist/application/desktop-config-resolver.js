"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.applyEnvironmentOverrides = applyEnvironmentOverrides;
exports.resolveDesktopConfig = resolveDesktopConfig;
exports.mergeSettingsPatch = mergeSettingsPatch;
const desktop_config_schema_js_1 = require("./desktop-config-schema.js");
const connector_lifecycle_config_js_1 = require("./connector-lifecycle-config.js");
function readEnvBoolean(name) {
    const value = process.env[name];
    if (value === undefined) {
        return undefined;
    }
    return value !== 'false' && value !== '0';
}
function readEnvNumber(name) {
    const value = process.env[name];
    if (!value) {
        return undefined;
    }
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) ? parsed : undefined;
}
function readEnvLogLevel(name) {
    const value = process.env[name]?.toLowerCase();
    if (value === 'debug' || value === 'info' || value === 'warn' || value === 'error') {
        return value;
    }
    return undefined;
}
function applyEnvironmentOverrides(base) {
    const sources = {};
    let config = { ...base };
    const envUrl = process.env.BUDCOM_CONNECTOR_URL;
    if (envUrl) {
        const parsed = (0, desktop_config_schema_js_1.parseConnectorHostPort)(envUrl);
        if (parsed) {
            config = { ...config, connectorHost: parsed.host, connectorPort: parsed.port };
            sources.connectorHost = 'environment';
            sources.connectorPort = 'environment';
        }
    }
    const envHost = process.env.BUDCOM_CONNECTOR_HOST;
    if (envHost) {
        config = { ...config, connectorHost: envHost };
        sources.connectorHost = 'environment';
    }
    const envPort = readEnvNumber('BUDCOM_CONNECTOR_PORT');
    if (envPort !== undefined) {
        config = { ...config, connectorPort: envPort };
        sources.connectorPort = 'environment';
    }
    const autoStart = readEnvBoolean('BUDCOM_CONNECTOR_AUTO_START');
    if (autoStart !== undefined) {
        config = { ...config, autoStartConnector: autoStart };
        sources.autoStartConnector = 'environment';
    }
    const pollMs = readEnvNumber('BUDCOM_HEALTH_POLL_MS');
    if (pollMs !== undefined) {
        config = { ...config, healthPollIntervalMs: pollMs };
        sources.healthPollIntervalMs = 'environment';
    }
    const startupTimeout = readEnvNumber('BUDCOM_STARTUP_TIMEOUT_MS');
    if (startupTimeout !== undefined) {
        config = { ...config, startupTimeoutMs: startupTimeout };
        sources.startupTimeoutMs = 'environment';
    }
    const shutdownGrace = readEnvNumber('BUDCOM_SHUTDOWN_GRACE_MS');
    if (shutdownGrace !== undefined) {
        config = { ...config, shutdownGraceMs: shutdownGrace };
        sources.shutdownGraceMs = 'environment';
    }
    const maxRestarts = readEnvNumber('BUDCOM_MAX_RESTART_ATTEMPTS');
    if (maxRestarts !== undefined) {
        config = { ...config, maxRestartAttempts: maxRestarts };
        sources.maxRestartAttempts = 'environment';
    }
    const reconnectDelay = readEnvNumber('BUDCOM_RECONNECT_BASE_DELAY_MS');
    if (reconnectDelay !== undefined) {
        config = { ...config, reconnectBaseDelayMs: reconnectDelay };
        sources.reconnectBaseDelayMs = 'environment';
    }
    const logLevel = readEnvLogLevel('BUDCOM_LOG_LEVEL');
    if (logLevel) {
        config = { ...config, logLevel };
        sources.logLevel = 'environment';
    }
    const tallyHost = process.env.BUDCOM_TALLY_HOST;
    if (tallyHost) {
        config = { ...config, tallyHost };
        sources.tallyHost = 'environment';
    }
    const tallyPort = readEnvNumber('BUDCOM_TALLY_PORT');
    if (tallyPort !== undefined) {
        config = { ...config, tallyPort };
        sources.tallyPort = 'environment';
    }
    return { config, sources };
}
function resolveDesktopConfig(persisted, defaults) {
    const validatedPersisted = (0, desktop_config_schema_js_1.validateDesktopConfig)(persisted);
    const safePersisted = validatedPersisted.ok && validatedPersisted.config
        ? validatedPersisted.config
        : defaults;
    const envApplied = applyEnvironmentOverrides(safePersisted);
    const effective = envApplied.config;
    const connectorBaseUrl = (0, desktop_config_schema_js_1.buildConnectorBaseUrl)(effective.connectorHost, effective.connectorPort);
    const lifecycleConfig = (0, connector_lifecycle_config_js_1.resolveConnectorLifecycleConfig)({
        connectorBaseUrl,
        connectorPort: effective.connectorPort,
        autoStart: effective.autoStartConnector,
        healthPollIntervalMs: effective.healthPollIntervalMs,
        startupTimeoutMs: effective.startupTimeoutMs,
        shutdownGraceMs: effective.shutdownGraceMs,
        maxRestartAttempts: effective.maxRestartAttempts,
        reconnectBaseDelayMs: effective.reconnectBaseDelayMs,
    });
    return {
        effective,
        persisted: safePersisted,
        sources: envApplied.sources,
        connectorBaseUrl,
        lifecycleConfig,
    };
}
function mergeSettingsPatch(current, patch) {
    return (0, desktop_config_schema_js_1.validateDesktopConfig)({
        ...current,
        ...patch,
        schemaVersion: current.schemaVersion,
    }).config ?? current;
}
//# sourceMappingURL=desktop-config-resolver.js.map