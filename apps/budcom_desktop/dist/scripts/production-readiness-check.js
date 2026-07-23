"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Milestone 4D production readiness checker.
 * Run: node dist/scripts/production-readiness-check.js
 */
const node_child_process_1 = require("node:child_process");
const node_fs_1 = __importDefault(require("node:fs"));
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const desktop_config_defaults_js_1 = require("../application/desktop-config-defaults.js");
const desktop_config_schema_js_1 = require("../application/desktop-config-schema.js");
const desktop_config_store_js_1 = require("../application/desktop-config-store.js");
const desktop_config_paths_js_1 = require("../application/desktop-config-paths.js");
const desktop_config_resolver_js_1 = require("../application/desktop-config-resolver.js");
const diagnostics_service_js_1 = require("../application/diagnostics-service.js");
const file_log_writer_js_1 = require("../application/file-log-writer.js");
const log_service_js_1 = require("../application/log-service.js");
const log_redaction_js_1 = require("../application/log-redaction.js");
const repoRoot = node_path_1.default.resolve(__dirname, '../../../../');
const desktopRoot = node_path_1.default.join(repoRoot, 'apps/budcom_desktop');
const connectorRoot = node_path_1.default.join(repoRoot, 'connector/budcom_connector');
function runCheck(id, fn) {
    try {
        return { id, status: 'PASS', detail: fn() };
    }
    catch (error) {
        return {
            id,
            status: 'FAIL',
            detail: error instanceof Error ? error.message : String(error),
        };
    }
}
function runCommand(command, cwd) {
    (0, node_child_process_1.execSync)(command, { cwd, stdio: 'pipe', encoding: 'utf8' });
}
async function runAsyncCheck(id, fn) {
    try {
        return { id, status: 'PASS', detail: await fn() };
    }
    catch (error) {
        return {
            id,
            status: 'FAIL',
            detail: error instanceof Error ? error.message : String(error),
        };
    }
}
async function main() {
    const checks = [];
    checks.push(runCheck('required-files', () => {
        const required = [
            node_path_1.default.join(desktopRoot, 'dist/main/main.js'),
            node_path_1.default.join(desktopRoot, 'dist/preload/preload.js'),
            node_path_1.default.join(connectorRoot, 'dist/main.js'),
        ];
        const missing = required.filter((file) => !node_fs_1.default.existsSync(file));
        if (missing.length > 0) {
            throw new Error(`Missing required files: ${missing.join(', ')}`);
        }
        return 'Required desktop and connector build artifacts exist.';
    }));
    checks.push(runCheck('config-schema', () => {
        const validated = (0, desktop_config_schema_js_1.validateDesktopConfig)((0, desktop_config_defaults_js_1.getEnvironmentDefaults)(true));
        if (!validated.ok) {
            throw new Error('Default configuration failed validation.');
        }
        return 'Configuration schema validates defaults.';
    }));
    checks.push(runCheck('desktop-build', () => {
        runCommand('npm run build', desktopRoot);
        return 'Desktop build passed.';
    }));
    checks.push(runCheck('connector-build', () => {
        runCommand('npm run build', connectorRoot);
        return 'Connector build passed.';
    }));
    checks.push(runCheck('desktop-tests', () => {
        runCommand('npm test', desktopRoot);
        return 'Desktop tests passed.';
    }));
    checks.push(runCheck('connector-tests', () => {
        runCommand('npm test', connectorRoot);
        return 'Connector tests passed.';
    }));
    checks.push(runCheck('security-settings', () => {
        const mainSource = node_fs_1.default.readFileSync(node_path_1.default.join(desktopRoot, 'src/main/main.ts'), 'utf8');
        const preloadSource = node_fs_1.default.readFileSync(node_path_1.default.join(desktopRoot, 'src/preload/preload.ts'), 'utf8');
        if (!mainSource.includes('contextIsolation: true'))
            throw new Error('contextIsolation not enabled');
        if (!mainSource.includes('nodeIntegration: false'))
            throw new Error('nodeIntegration not disabled');
        if (!mainSource.includes('sandbox: true'))
            throw new Error('sandbox not enabled');
        if (preloadSource.includes('require(')) {
            throw new Error('Preload exposes unsafe APIs');
        }
        return 'Core Electron security settings present.';
    }));
    checks.push(await runAsyncCheck('diagnostics-export', async () => {
        const tempDir = node_fs_1.default.mkdtempSync(node_path_1.default.join(node_os_1.default.tmpdir(), 'budcom-diag-'));
        const paths = (0, desktop_config_paths_js_1.resolveDesktopConfigPaths)(tempDir);
        const store = new desktop_config_store_js_1.DesktopConfigStore({ paths, defaults: (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(true) });
        const resolved = (0, desktop_config_resolver_js_1.resolveDesktopConfig)(store.getConfig(), (0, desktop_config_defaults_js_1.getEnvironmentDefaults)(true));
        const fileWriter = new file_log_writer_js_1.FileLogWriter({ logsDir: paths.logsDir });
        const logService = new log_service_js_1.LogService({ fileWriter });
        const diagnostics = new diagnostics_service_js_1.DiagnosticsService({
            desktopVersion: '0.4.3',
            electronVersion: process.versions.electron ?? 'test',
            configStore: store,
            resolvedConfig: resolved,
            configStatus: 'test',
            dashboardService: {
                getDashboardState: async () => ({
                    connectorVersion: '0.3.1',
                    connectorReachable: false,
                    healthStatus: 'unknown',
                    companyName: '—',
                    sessionStatus: 'NO_COMPANY_SELECTED',
                }),
            },
            lifecycleService: {
                getStatus: () => ({
                    stateLabel: 'Disconnected',
                    managedByDesktop: false,
                    externalProcessDetected: false,
                    lastSuccessfulHealthCheck: null,
                    managedProcessPid: null,
                }),
            },
            logService,
            exportDir: paths.diagnosticsExportDir,
            startedAt: Date.now(),
        });
        const result = await diagnostics.exportBundle();
        if (!result.ok || !result.bundlePath) {
            throw new Error(result.message);
        }
        return 'Diagnostics export works and bundle created.';
    }));
    checks.push(runCheck('log-writer', () => {
        const tempDir = node_fs_1.default.mkdtempSync(node_path_1.default.join(node_os_1.default.tmpdir(), 'budcom-log-'));
        const writer = new file_log_writer_js_1.FileLogWriter({ logsDir: tempDir });
        writer.appendLine(JSON.stringify({ message: (0, log_redaction_js_1.redactString)('token=secret-value') }));
        if (!writer.isWritable()) {
            throw new Error('Log writer not writable');
        }
        return 'Log writer works.';
    }));
    checks.push(runCheck('redaction', () => {
        const redacted = (0, log_redaction_js_1.redactString)('Authorization: Bearer abc.def.ghi password=hidden');
        if (redacted.includes('abc.def.ghi') || redacted.includes('hidden')) {
            throw new Error('Redaction failed');
        }
        return 'Redaction utility masks sensitive values.';
    }));
    const passCount = checks.filter((check) => check.status === 'PASS').length;
    const failCount = checks.filter((check) => check.status === 'FAIL').length;
    const score = Math.round((passCount / checks.length) * 100);
    const output = {
        checkedAt: new Date().toISOString(),
        desktopVersion: '0.4.3',
        checks,
        summary: {
            pass: passCount,
            fail: failCount,
            blocked: 0,
            total: checks.length,
            readinessScore: score,
            overall: failCount === 0 ? 'PASS' : 'FAIL',
        },
    };
    const outDir = node_path_1.default.join(repoRoot, 'docs/diagnostics');
    node_fs_1.default.mkdirSync(outDir, { recursive: true });
    node_fs_1.default.writeFileSync(node_path_1.default.join(outDir, 'm4d-production-readiness.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output, null, 2));
    if (output.summary.overall !== 'PASS') {
        process.exitCode = 1;
    }
}
void main();
//# sourceMappingURL=production-readiness-check.js.map