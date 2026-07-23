/**
 * Milestone 4D production readiness checker.
 * Run: node dist/scripts/production-readiness-check.js
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { getEnvironmentDefaults } from '../application/desktop-config-defaults.js';
import { validateDesktopConfig } from '../application/desktop-config-schema.js';
import { DesktopConfigStore } from '../application/desktop-config-store.js';
import { resolveDesktopConfigPaths } from '../application/desktop-config-paths.js';
import { resolveDesktopConfig } from '../application/desktop-config-resolver.js';
import { DiagnosticsService } from '../application/diagnostics-service.js';
import { FileLogWriter } from '../application/file-log-writer.js';
import { LogService } from '../application/log-service.js';
import { redactString } from '../application/log-redaction.js';

interface CheckResult {
  readonly id: string;
  readonly status: 'PASS' | 'FAIL' | 'BLOCKED';
  readonly detail: string;
}

const repoRoot = path.resolve(__dirname, '../../../../');
const desktopRoot = path.join(repoRoot, 'apps/budcom_desktop');
const connectorRoot = path.join(repoRoot, 'connector/budcom_connector');

function runCheck(id: string, fn: () => string): CheckResult {
  try {
    return { id, status: 'PASS', detail: fn() };
  } catch (error) {
    return {
      id,
      status: 'FAIL',
      detail: error instanceof Error ? error.message : String(error),
    };
  }
}

function runCommand(command: string, cwd: string): void {
  execSync(command, { cwd, stdio: 'pipe', encoding: 'utf8' });
}

async function runAsyncCheck(id: string, fn: () => Promise<string>): Promise<CheckResult> {
  try {
    return { id, status: 'PASS', detail: await fn() };
  } catch (error) {
    return {
      id,
      status: 'FAIL',
      detail: error instanceof Error ? error.message : String(error),
    };
  }
}

async function main(): Promise<void> {
  const checks: CheckResult[] = [];

  checks.push(runCheck('required-files', () => {
    const required = [
      path.join(desktopRoot, 'dist/main/main.js'),
      path.join(desktopRoot, 'dist/preload/preload.js'),
      path.join(connectorRoot, 'dist/main.js'),
    ];
    const missing = required.filter((file) => !fs.existsSync(file));
    if (missing.length > 0) {
      throw new Error(`Missing required files: ${missing.join(', ')}`);
    }
    return 'Required desktop and connector build artifacts exist.';
  }));

  checks.push(runCheck('config-schema', () => {
    const validated = validateDesktopConfig(getEnvironmentDefaults(true));
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
    const mainSource = fs.readFileSync(path.join(desktopRoot, 'src/main/main.ts'), 'utf8');
    const preloadSource = fs.readFileSync(path.join(desktopRoot, 'src/preload/preload.ts'), 'utf8');
    if (!mainSource.includes('contextIsolation: true')) throw new Error('contextIsolation not enabled');
    if (!mainSource.includes('nodeIntegration: false')) throw new Error('nodeIntegration not disabled');
    if (!mainSource.includes('sandbox: true')) throw new Error('sandbox not enabled');
    if (preloadSource.includes('require(')) {
      throw new Error('Preload exposes unsafe APIs');
    }
    return 'Core Electron security settings present.';
  }));

  checks.push(await runAsyncCheck('diagnostics-export', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-diag-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    const resolved = resolveDesktopConfig(store.getConfig(), getEnvironmentDefaults(true));
    const fileWriter = new FileLogWriter({ logsDir: paths.logsDir });
    const logService = new LogService({ fileWriter });
    const diagnostics = new DiagnosticsService({
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
      } as never,
      lifecycleService: {
        getStatus: () => ({
          stateLabel: 'Disconnected',
          managedByDesktop: false,
          externalProcessDetected: false,
          lastSuccessfulHealthCheck: null,
          managedProcessPid: null,
        }),
      } as never,
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
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-log-'));
    const writer = new FileLogWriter({ logsDir: tempDir });
    writer.appendLine(JSON.stringify({ message: redactString('token=secret-value') }));
    if (!writer.isWritable()) {
      throw new Error('Log writer not writable');
    }
    return 'Log writer works.';
  }));

  checks.push(runCheck('redaction', () => {
    const redacted = redactString('Authorization: Bearer abc.def.ghi password=hidden');
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

  const outDir = path.join(repoRoot, 'docs/diagnostics');
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'm4d-production-readiness.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
  if (output.summary.overall !== 'PASS') {
    process.exitCode = 1;
  }
}

void main();
