/**
 * Milestone 4C live lifecycle validation.
 * Run: node dist/scripts/live-lifecycle-validation.js
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

import { resolveConnectorLifecycleConfig } from '../application/connector-lifecycle-config.js';
import {
  ConnectorLifecycleService,
  HttpHealthChecker,
} from '../application/connector-lifecycle-service.js';
import { LogService } from '../application/log-service.js';
import { NodeProcessSpawner } from '../application/node-process-spawner.js';

interface ScenarioResult {
  readonly scenario: string;
  readonly status: 'PASS' | 'FAIL';
  readonly detail: string;
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForHealth(baseUrl: string, expected: boolean, attempts = 15): Promise<boolean> {
  const checker = new HttpHealthChecker(baseUrl);
  for (let i = 0; i < attempts; i += 1) {
    const healthy = await checker.checkHealth();
    if (healthy === expected) {
      return true;
    }
    await sleep(1_000);
  }
  return false;
}

function stopPort(port: number): void {
  try {
    const output = execSync(
      `powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)"`,
      { encoding: 'utf8' },
    ).trim();
    for (const pid of output.split(/\s+/).filter(Boolean)) {
      execSync(
        `powershell -NoProfile -Command "Stop-Process -Id ${pid} -Force -ErrorAction SilentlyContinue"`,
      );
    }
  } catch {
    // Port already free
  }
}

async function run(): Promise<void> {
  const results: ScenarioResult[] = [];
  const record = (scenario: string, ok: boolean, detail: string): void => {
    results.push({ scenario, status: ok ? 'PASS' : 'FAIL', detail });
    console.log(`${ok ? 'PASS' : 'FAIL'} — ${scenario}: ${detail}`);
  };

  const validationPort = 18_080;
  const config = resolveConnectorLifecycleConfig({
    autoStart: false,
    startupTimeoutMs: 45_000,
    connectorBaseUrl: `http://localhost:${validationPort}`,
    connectorPort: validationPort,
  });
  const scriptPath = config.connectorArgs[0] ?? '';
  if (!scriptPath || !fs.existsSync(scriptPath)) {
    throw new Error(`Connector script missing at ${scriptPath}. Run npm run build in connector package.`);
  }

  stopPort(validationPort);
  await sleep(3_000);
  const portFree = await waitForHealth(config.connectorBaseUrl, false, 30);
  if (!portFree) {
    throw new Error(`Port ${validationPort} still occupied after cleanup. Stop external connector processes and retry.`);
  }

  const logService = new LogService();
  const lifecycle = new ConnectorLifecycleService({
    config,
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(config.connectorBaseUrl),
    logService,
  });

  const started = await lifecycle.ensureConnectorRunning();
  record(
    'Connector auto-start',
    started.state === 'connected' && started.managedByDesktop,
    `state=${started.stateLabel} managed=${started.managedByDesktop}`,
  );

  const duplicate = await lifecycle.ensureConnectorRunning();
  record(
    'Duplicate prevention',
    duplicate.managedByDesktop && duplicate.state === 'connected',
    `managed=${duplicate.managedByDesktop} state=${duplicate.stateLabel}`,
  );

  record(
    'Health monitoring',
    duplicate.lastSuccessfulHealthCheck !== null,
    `lastHealth=${duplicate.lastSuccessfulHealthCheck}`,
  );

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

  const externalCheck = new ConnectorLifecycleService({
    config,
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(config.connectorBaseUrl),
    logService,
  });
  const external = await externalCheck.ensureConnectorRunning();
  record(
    'Connector already running',
    external.state === 'connected' && external.externalProcessDetected,
    `external=${external.externalProcessDetected} managed=${external.managedByDesktop}`,
  );

  const output = {
    validatedAt: new Date().toISOString(),
    connectorVersion: '0.3.1',
    desktopVersion: '0.4.2',
    validationPort,
    connectorScript: scriptPath,
    results,
  };

  const outDir = path.resolve(__dirname, '../../../../docs/diagnostics');
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'm4c-live-lifecycle-validation.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));

  await externalCheck.shutdown();
  await lifecycle.shutdown();
  stopPort(validationPort);
}

void run().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
