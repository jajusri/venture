"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Milestone 4C live lifecycle validation.
 * Run: node dist/scripts/live-lifecycle-validation.js
 */
const node_child_process_1 = require("node:child_process");
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const connector_lifecycle_config_js_1 = require("../application/connector-lifecycle-config.js");
const connector_lifecycle_service_js_1 = require("../application/connector-lifecycle-service.js");
const log_service_js_1 = require("../application/log-service.js");
const node_process_spawner_js_1 = require("../application/node-process-spawner.js");
async function sleep(ms) {
    await new Promise((resolve) => setTimeout(resolve, ms));
}
async function waitForHealth(baseUrl, expected, attempts = 15) {
    const checker = new connector_lifecycle_service_js_1.HttpHealthChecker(baseUrl);
    for (let i = 0; i < attempts; i += 1) {
        const healthy = await checker.checkHealth();
        if (healthy === expected) {
            return true;
        }
        await sleep(1_000);
    }
    return false;
}
function stopPort(port) {
    try {
        const output = (0, node_child_process_1.execSync)(`powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)"`, { encoding: 'utf8' }).trim();
        for (const pid of output.split(/\s+/).filter(Boolean)) {
            (0, node_child_process_1.execSync)(`powershell -NoProfile -Command "Stop-Process -Id ${pid} -Force -ErrorAction SilentlyContinue"`);
        }
    }
    catch {
        // Port already free
    }
}
async function run() {
    const results = [];
    const record = (scenario, ok, detail) => {
        results.push({ scenario, status: ok ? 'PASS' : 'FAIL', detail });
        console.log(`${ok ? 'PASS' : 'FAIL'} — ${scenario}: ${detail}`);
    };
    const validationPort = 18_080;
    const config = (0, connector_lifecycle_config_js_1.resolveConnectorLifecycleConfig)({
        autoStart: false,
        startupTimeoutMs: 45_000,
        connectorBaseUrl: `http://localhost:${validationPort}`,
        connectorPort: validationPort,
    });
    const scriptPath = config.connectorArgs[0] ?? '';
    if (!scriptPath || !node_fs_1.default.existsSync(scriptPath)) {
        throw new Error(`Connector script missing at ${scriptPath}. Run npm run build in connector package.`);
    }
    stopPort(validationPort);
    await sleep(3_000);
    const portFree = await waitForHealth(config.connectorBaseUrl, false, 30);
    if (!portFree) {
        throw new Error(`Port ${validationPort} still occupied after cleanup. Stop external connector processes and retry.`);
    }
    const logService = new log_service_js_1.LogService();
    const lifecycle = new connector_lifecycle_service_js_1.ConnectorLifecycleService({
        config,
        processSpawner: new node_process_spawner_js_1.NodeProcessSpawner(),
        healthChecker: new connector_lifecycle_service_js_1.HttpHealthChecker(config.connectorBaseUrl),
        logService,
    });
    const started = await lifecycle.ensureConnectorRunning();
    record('Connector auto-start', started.state === 'connected' && started.managedByDesktop, `state=${started.stateLabel} managed=${started.managedByDesktop}`);
    const duplicate = await lifecycle.ensureConnectorRunning();
    record('Duplicate prevention', duplicate.managedByDesktop && duplicate.state === 'connected', `managed=${duplicate.managedByDesktop} state=${duplicate.stateLabel}`);
    record('Health monitoring', duplicate.lastSuccessfulHealthCheck !== null, `lastHealth=${duplicate.lastSuccessfulHealthCheck}`);
    const stopped = await lifecycle.stopConnector();
    record('Graceful stop', stopped.state === 'disconnected', `state=${stopped.stateLabel}`);
    await waitForHealth(config.connectorBaseUrl, false);
    const restarted = await lifecycle.restartConnector();
    record('Manual restart', restarted.state === 'connected' && restarted.managedByDesktop, `state=${restarted.stateLabel}`);
    await lifecycle.stopConnector();
    await waitForHealth(config.connectorBaseUrl, false);
    await lifecycle.ensureConnectorRunning();
    await lifecycle.stopConnector();
    const recovered = await lifecycle.ensureConnectorRunning();
    record('Crash recovery / re-start cycle', recovered.state === 'connected', `state=${recovered.stateLabel}`);
    const externalCheck = new connector_lifecycle_service_js_1.ConnectorLifecycleService({
        config,
        processSpawner: new node_process_spawner_js_1.NodeProcessSpawner(),
        healthChecker: new connector_lifecycle_service_js_1.HttpHealthChecker(config.connectorBaseUrl),
        logService,
    });
    const external = await externalCheck.ensureConnectorRunning();
    record('Connector already running', external.state === 'connected' && external.externalProcessDetected, `external=${external.externalProcessDetected} managed=${external.managedByDesktop}`);
    const output = {
        validatedAt: new Date().toISOString(),
        connectorVersion: '0.3.1',
        desktopVersion: '0.4.2',
        validationPort,
        connectorScript: scriptPath,
        results,
    };
    const outDir = node_path_1.default.resolve(__dirname, '../../../../docs/diagnostics');
    node_fs_1.default.mkdirSync(outDir, { recursive: true });
    node_fs_1.default.writeFileSync(node_path_1.default.join(outDir, 'm4c-live-lifecycle-validation.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output, null, 2));
    await externalCheck.shutdown();
    await lifecycle.shutdown();
    stopPort(validationPort);
}
void run().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
});
//# sourceMappingURL=live-lifecycle-validation.js.map