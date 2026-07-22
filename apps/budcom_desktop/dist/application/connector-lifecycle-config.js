"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveConnectorLifecycleConfig = resolveConnectorLifecycleConfig;
exports.validateConnectorExecutable = validateConnectorExecutable;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const DEFAULT_PORT = 8080;
function resolveDefaultConnectorScript() {
    const fromDistMain = node_path_1.default.resolve(__dirname, '../../../../connector/budcom_connector/dist/main.js');
    if (node_fs_1.default.existsSync(fromDistMain)) {
        return fromDistMain;
    }
    const fromRepoRoot = node_path_1.default.resolve(process.cwd(), '../../connector/budcom_connector/dist/main.js');
    return fromRepoRoot;
}
function parsePort(baseUrl, fallback) {
    try {
        const parsed = new URL(baseUrl);
        if (parsed.port) {
            return Number.parseInt(parsed.port, 10);
        }
        return parsed.protocol === 'https:' ? 443 : fallback;
    }
    catch {
        return fallback;
    }
}
function resolveConnectorLifecycleConfig(overrides = {}) {
    const connectorBaseUrl = overrides.connectorBaseUrl ?? process.env.BUDCOM_CONNECTOR_URL ?? 'http://localhost:8080';
    const connectorPort = overrides.connectorPort ?? parsePort(connectorBaseUrl, DEFAULT_PORT);
    const defaultScript = resolveDefaultConnectorScript();
    const connectorExecutable = overrides.connectorExecutable ?? process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? process.execPath;
    const connectorArgs = overrides.connectorArgs
        ?? (process.env.BUDCOM_CONNECTOR_ARGS
            ? process.env.BUDCOM_CONNECTOR_ARGS.split(' ')
            : [defaultScript]);
    const connectorCwd = overrides.connectorCwd
        ?? process.env.BUDCOM_CONNECTOR_CWD
        ?? node_path_1.default.dirname(defaultScript.includes('main.js') ? defaultScript : connectorExecutable);
    return {
        connectorBaseUrl,
        connectorPort,
        connectorExecutable,
        connectorArgs,
        connectorCwd,
        autoStart: overrides.autoStart ?? process.env.BUDCOM_CONNECTOR_AUTO_START !== 'false',
        healthPollIntervalMs: overrides.healthPollIntervalMs ?? 5_000,
        startupTimeoutMs: overrides.startupTimeoutMs ?? 30_000,
        shutdownGraceMs: overrides.shutdownGraceMs ?? 5_000,
        maxRestartAttempts: overrides.maxRestartAttempts ?? 5,
        reconnectBaseDelayMs: overrides.reconnectBaseDelayMs ?? 1_000,
        staleHealthThresholdMs: overrides.staleHealthThresholdMs ?? 20_000,
    };
}
function validateConnectorExecutable(config) {
    if (config.connectorExecutable.trim().length === 0) {
        return 'Connector executable path is not configured.';
    }
    const scriptPath = config.connectorArgs[0];
    if (scriptPath && scriptPath.endsWith('.js') && !node_fs_1.default.existsSync(scriptPath)) {
        return `Connector executable not found at ${scriptPath}. Build the connector first.`;
    }
    if (!scriptPath?.endsWith('.js') && !node_fs_1.default.existsSync(config.connectorExecutable)) {
        return `Connector executable not found at ${config.connectorExecutable}.`;
    }
    return null;
}
//# sourceMappingURL=connector-lifecycle-config.js.map