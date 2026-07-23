"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Milestone 4D live validation.
 * Run: node dist/scripts/live-4d-validation.js
 */
const node_child_process_1 = require("node:child_process");
const node_fs_1 = __importDefault(require("node:fs"));
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const desktop_config_defaults_js_1 = require("../application/desktop-config-defaults.js");
const desktop_config_paths_js_1 = require("../application/desktop-config-paths.js");
const desktop_config_store_js_1 = require("../application/desktop-config-store.js");
const desktop_config_resolver_js_1 = require("../application/desktop-config-resolver.js");
const connector_lifecycle_service_js_1 = require("../application/connector-lifecycle-service.js");
const diagnostics_service_js_1 = require("../application/diagnostics-service.js");
const file_log_writer_js_1 = require("../application/file-log-writer.js");
const log_service_js_1 = require("../application/log-service.js");
const node_process_spawner_js_1 = require("../application/node-process-spawner.js");
const settings_service_js_1 = require("../application/settings-service.js");
const VALIDATION_PORT = 18_081;
async function sleep(ms) {
    await new Promise((resolve) => setTimeout(resolve, ms));
}
function stopPort(port) {
    try {
        const output = (0, node_child_process_1.execSync)(`powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)"`, { encoding: 'utf8' }).trim();
        for (const pid of output.split(/\s+/).filter(Boolean)) {
            (0, node_child_process_1.execSync)(`powershell -NoProfile -Command "Stop-Process -Id ${pid} -Force -ErrorAction SilentlyContinue"`);
        }
    }
    catch {
        // Port free
    }
}
async function waitForHealth(baseUrl, expected, attempts = 20) {
    const checker = new connector_lifecycle_service_js_1.HttpHealthChecker(baseUrl);
    for (let i = 0; i < attempts; i += 1) {
        if ((await checker.checkHealth()) === expected) {
            return true;
        }
        await sleep(1_000);
    }
    return false;
}
async function run() {
    const results = [];
    const record = (scenario, ok, detail) => {
        results.push({ scenario, status: ok ? 'PASS' : 'FAIL', detail });
        console.log(`${ok ? 'PASS' : 'FAIL'} — ${scenario}: ${detail}`);
    };
    const tempDir = node_fs_1.default.mkdtempSync(node_path_1.default.join(node_os_1.default.tmpdir(), 'budcom-4d-'));
    const paths = (0, desktop_config_paths_js_1.resolveDesktopConfigPaths)(tempDir);
    const defaults = (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(true);
    const store = new desktop_config_store_js_1.DesktopConfigStore({ paths, defaults });
    const logService = new log_service_js_1.LogService({ fileWriter: new file_log_writer_js_1.FileLogWriter({ logsDir: paths.logsDir }) });
    const settingsService = new settings_service_js_1.SettingsService({
        configStore: store,
        logService,
        connectorExecutable: process.execPath,
    });
    record('Desktop starts with valid persisted settings', settingsService.getSettingsState().connectorPort === 8080, `port=${settingsService.getSettingsState().connectorPort}`);
    node_fs_1.default.writeFileSync(paths.configFilePath, '{bad-json', 'utf8');
    const recoveredStore = new desktop_config_store_js_1.DesktopConfigStore({ paths, defaults });
    record('Invalid persisted settings recover safely', recoveredStore.getConfig().schemaVersion === 1, 'defaults restored');
    const saveResult = settingsService.saveSettings({ connectorPort: VALIDATION_PORT, connectorHost: 'localhost' });
    record('Settings can be changed and saved', saveResult.ok && saveResult.settings?.connectorPort === VALIDATION_PORT, saveResult.message);
    const resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(store.getConfig(), defaults);
    stopPort(VALIDATION_PORT);
    await sleep(2_000);
    await waitForHealth(`http://localhost:${VALIDATION_PORT}`, false, 10);
    const lifecycle = new connector_lifecycle_service_js_1.ConnectorLifecycleService({
        config: {
            ...resolved.lifecycleConfig,
            connectorBaseUrl: `http://localhost:${VALIDATION_PORT}`,
            connectorPort: VALIDATION_PORT,
            autoStart: true,
            startupTimeoutMs: 45_000,
        },
        processSpawner: new node_process_spawner_js_1.NodeProcessSpawner(),
        healthChecker: new connector_lifecycle_service_js_1.HttpHealthChecker(`http://localhost:${VALIDATION_PORT}`),
        logService,
    });
    const started = await lifecycle.ensureConnectorRunning();
    record('Connector auto-start still works', started.state === 'connected' && started.managedByDesktop, `managed=${started.managedByDesktop}`);
    const duplicate = await lifecycle.ensureConnectorRunning();
    record('No duplicate connector process', duplicate.managedByDesktop, `state=${duplicate.stateLabel}`);
    const diagnostics = new diagnostics_service_js_1.DiagnosticsService({
        desktopVersion: '0.4.3',
        electronVersion: process.versions.electron ?? 'test',
        configStore: store,
        resolvedConfig: (0, desktop_config_resolver_js_1.resolveDesktopConfig)(store.getConfig(), defaults),
        configStatus: 'loaded',
        dashboardService: {
            getDashboardState: async () => ({
                connectorVersion: '0.3.1',
                connectorReachable: true,
                healthStatus: 'ok',
                companyName: '—',
                sessionStatus: 'NO_COMPANY_SELECTED',
            }),
        },
        lifecycleService: lifecycle,
        logService,
        exportDir: paths.diagnosticsExportDir,
        startedAt: Date.now(),
    });
    const snapshot = await diagnostics.getSnapshot();
    record('Diagnostics refresh works', snapshot.desktopVersion === '0.4.3', `health=${snapshot.healthStatus}`);
    const summary = diagnostics.formatSummary(snapshot);
    record('Copy diagnostics works', summary.includes('Budcom Desktop Diagnostics Summary'), 'summary generated');
    const exportResult = await diagnostics.exportBundle();
    const bundle = exportResult.bundlePath
        ? JSON.parse(node_fs_1.default.readFileSync(exportResult.bundlePath, 'utf8'))
        : null;
    record('Export diagnostics bundle works', exportResult.ok, exportResult.message);
    const sensitivePayload = JSON.stringify({ configuration: bundle?.configuration, logs: bundle?.logs });
    record('Bundle contains no secrets or business data', exportResult.ok && !/Bearer\s+[a-z0-9._-]+/i.test(sensitivePayload) && !/password=\S+/i.test(sensitivePayload), 'sanitized bundle');
    logService.append('information', 'info message');
    logService.append('error', 'error message');
    logService.clearNonessential();
    record('Log retention works', logService.getEntries().every((entry) => entry.level === 'error'), `entries=${logService.getEntries().length}`);
    const external = new connector_lifecycle_service_js_1.ConnectorLifecycleService({
        config: {
            ...resolved.lifecycleConfig,
            connectorBaseUrl: `http://localhost:${VALIDATION_PORT}`,
            connectorPort: VALIDATION_PORT,
            autoStart: false,
        },
        processSpawner: new node_process_spawner_js_1.NodeProcessSpawner(),
        healthChecker: new connector_lifecycle_service_js_1.HttpHealthChecker(`http://localhost:${VALIDATION_PORT}`),
        logService,
    });
    const externalStatus = await external.ensureConnectorRunning();
    record('External connector detection still works', externalStatus.externalProcessDetected, `external=${externalStatus.externalProcessDetected}`);
    await lifecycle.shutdown();
    stopPort(VALIDATION_PORT);
    const output = {
        validatedAt: new Date().toISOString(),
        desktopVersion: '0.4.3',
        validationPort: VALIDATION_PORT,
        results,
    };
    const outDir = node_path_1.default.resolve(__dirname, '../../../../docs/diagnostics');
    node_fs_1.default.mkdirSync(outDir, { recursive: true });
    node_fs_1.default.writeFileSync(node_path_1.default.join(outDir, 'm4d-live-validation.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output, null, 2));
}
void run().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
});
//# sourceMappingURL=live-4d-validation.js.map