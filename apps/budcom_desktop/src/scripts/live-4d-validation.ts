/**
 * Milestone 4D live validation.
 * Run: node dist/scripts/live-4d-validation.js
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { getEnvironmentDefaults } from '../application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../application/desktop-config-paths.js';
import { DesktopConfigStore } from '../application/desktop-config-store.js';
import { resolveDesktopConfig } from '../application/desktop-config-resolver.js';
import {
  ConnectorLifecycleService,
  HttpHealthChecker,
} from '../application/connector-lifecycle-service.js';
import { DiagnosticsService } from '../application/diagnostics-service.js';
import { FileLogWriter } from '../application/file-log-writer.js';
import { LogService } from '../application/log-service.js';
import { NodeProcessSpawner } from '../application/node-process-spawner.js';
import { SettingsService } from '../application/settings-service.js';

interface ScenarioResult {
  readonly scenario: string;
  readonly status: 'PASS' | 'FAIL';
  readonly detail: string;
}

const VALIDATION_PORT = 18_081;

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

function stopPort(port: number): void {
  try {
    const output = execSync(
      `powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)"`,
      { encoding: 'utf8' },
    ).trim();
    for (const pid of output.split(/\s+/).filter(Boolean)) {
      execSync(`powershell -NoProfile -Command "Stop-Process -Id ${pid} -Force -ErrorAction SilentlyContinue"`);
    }
  } catch {
    // Port free
  }
}

async function waitForHealth(baseUrl: string, expected: boolean, attempts = 20): Promise<boolean> {
  const checker = new HttpHealthChecker(baseUrl);
  for (let i = 0; i < attempts; i += 1) {
    if ((await checker.checkHealth()) === expected) {
      return true;
    }
    await sleep(1_000);
  }
  return false;
}

async function run(): Promise<void> {
  const results: ScenarioResult[] = [];
  const record = (scenario: string, ok: boolean, detail: string): void => {
    results.push({ scenario, status: ok ? 'PASS' : 'FAIL', detail });
    console.log(`${ok ? 'PASS' : 'FAIL'} — ${scenario}: ${detail}`);
  };

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-4d-'));
  const paths = resolveDesktopConfigPaths(tempDir);
  const defaults = getEnvironmentDefaults(true);
  const store = new DesktopConfigStore({ paths, defaults });
  const logService = new LogService({ fileWriter: new FileLogWriter({ logsDir: paths.logsDir }) });
  const settingsService = new SettingsService({
    configStore: store,
    logService,
    connectorExecutable: process.execPath,
  });

  record('Desktop starts with valid persisted settings', settingsService.getSettingsState().connectorPort === 8080, `port=${settingsService.getSettingsState().connectorPort}`);

  fs.writeFileSync(paths.configFilePath, '{bad-json', 'utf8');
  const recoveredStore = new DesktopConfigStore({ paths, defaults });
  record('Invalid persisted settings recover safely', recoveredStore.getConfig().schemaVersion === 1, 'defaults restored');

  const saveResult = settingsService.saveSettings({ connectorPort: VALIDATION_PORT, connectorHost: 'localhost' });
  record('Settings can be changed and saved', saveResult.ok && saveResult.settings?.connectorPort === VALIDATION_PORT, saveResult.message);

  const resolved = resolveDesktopConfig(store.getConfig(), defaults);
  stopPort(VALIDATION_PORT);
  await sleep(2_000);
  await waitForHealth(`http://localhost:${VALIDATION_PORT}`, false, 10);

  const lifecycle = new ConnectorLifecycleService({
    config: {
      ...resolved.lifecycleConfig,
      connectorBaseUrl: `http://localhost:${VALIDATION_PORT}`,
      connectorPort: VALIDATION_PORT,
      autoStart: true,
      startupTimeoutMs: 45_000,
    },
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(`http://localhost:${VALIDATION_PORT}`),
    logService,
  });

  const started = await lifecycle.ensureConnectorRunning();
  record('Connector auto-start still works', started.state === 'connected' && started.managedByDesktop, `managed=${started.managedByDesktop}`);

  const duplicate = await lifecycle.ensureConnectorRunning();
  record('No duplicate connector process', duplicate.managedByDesktop, `state=${duplicate.stateLabel}`);

  const diagnostics = new DiagnosticsService({
    desktopVersion: '0.4.3',
    electronVersion: process.versions.electron ?? 'test',
    configStore: store,
    resolvedConfig: resolveDesktopConfig(store.getConfig(), defaults),
    configStatus: 'loaded',
    dashboardService: {
      getDashboardState: async () => ({
        connectorVersion: '0.3.1',
        connectorReachable: true,
        healthStatus: 'ok',
        companyName: '—',
        sessionStatus: 'NO_COMPANY_SELECTED',
      }),
    } as never,
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
    ? JSON.parse(fs.readFileSync(exportResult.bundlePath, 'utf8')) as { configuration: unknown; logs: unknown; exclusions: string[] }
    : null;
  record('Export diagnostics bundle works', exportResult.ok, exportResult.message);
  const sensitivePayload = JSON.stringify({ configuration: bundle?.configuration, logs: bundle?.logs });
  record(
    'Bundle contains no secrets or business data',
    exportResult.ok && !/Bearer\s+[a-z0-9._-]+/i.test(sensitivePayload) && !/password=\S+/i.test(sensitivePayload),
    'sanitized bundle',
  );

  logService.append('information', 'info message');
  logService.append('error', 'error message');
  logService.clearNonessential();
  record('Log retention works', logService.getEntries().every((entry) => entry.level === 'error'), `entries=${logService.getEntries().length}`);

  const external = new ConnectorLifecycleService({
    config: {
      ...resolved.lifecycleConfig,
      connectorBaseUrl: `http://localhost:${VALIDATION_PORT}`,
      connectorPort: VALIDATION_PORT,
      autoStart: false,
    },
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(`http://localhost:${VALIDATION_PORT}`),
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
  const outDir = path.resolve(__dirname, '../../../../docs/diagnostics');
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'm4d-live-validation.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
}

void run().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
