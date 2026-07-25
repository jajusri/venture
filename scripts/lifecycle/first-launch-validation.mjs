#!/usr/bin/env node
/**
 * Focused installed first-launch validation for runtime-closure gate.
 */
import { spawn, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { createSchemaV7Fixture } from './create-schema-v7-fixture.mjs';
import {
  buildStartupEvidence,
  classifyConnectorCommandLine,
  classifyHealthResponse,
  deriveFirstLaunchVerdict,
  getConnectorProcesses,
  getDesktopProcesses,
  isPortListening,
  readPrivacySafeStartupLogs,
  redactLine,
  resolveConnectorPort,
  sleep,
  waitForConnectorHealth,
  waitForLogFile,
} from './lifecycle-runtime-evidence.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const reportPath = path.join(repoRoot, 'release/controlled-pilot/0.4.3/reports/first-launch-validation-report.json');

const DB_MARKER_KEY = 'lifecycle_gate_marker';
const MARKER_ID = 'rc4-lifecycle-gate-20260725';

const paths = {
  installRoot: path.join(process.env.LOCALAPPDATA ?? '', 'Programs', 'Budcom Desktop'),
  appExe: path.join(process.env.LOCALAPPDATA ?? '', 'Programs', 'Budcom Desktop', 'Budcom Desktop.exe'),
  userDataRoot: path.join(process.env.APPDATA ?? '', '@budcom', 'desktop'),
  connectorDataDir: path.join(process.env.APPDATA ?? '', '@budcom', 'desktop', 'connector-data'),
  dbPath: path.join(process.env.APPDATA ?? '', '@budcom', 'desktop', 'connector-data', 'budcom-ledger.db'),
  logsDir: path.join(process.env.APPDATA ?? '', '@budcom', 'desktop', 'logs'),
  configPath: path.join(process.env.APPDATA ?? '', '@budcom', 'desktop', 'desktop-config.json'),
};

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function readSchemaVersion(dbPath) {
  if (!fs.existsSync(dbPath)) {
    return null;
  }
  const result = spawnSync('node', ['-e', `
    const { nodeSqlite } = require('./connector/budcom_connector/dist/storage/sqlite/node-sqlite.js');
    const db = new nodeSqlite.DatabaseSync(process.argv[1], { readOnly: true });
    const row = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get();
    db.close();
    console.log(row?.version ?? 0);
  `, dbPath], { cwd: repoRoot, encoding: 'utf8' });
  return Number.parseInt(String(result.stdout).trim(), 10) || null;
}

function readDatabaseMarker(dbPath) {
  if (!fs.existsSync(dbPath)) {
    return null;
  }
  const result = spawnSync('node', ['-e', `
    const { nodeSqlite } = require('./connector/budcom_connector/dist/storage/sqlite/node-sqlite.js');
    const db = new nodeSqlite.DatabaseSync(process.argv[1], { readOnly: true });
    const row = db.prepare('SELECT value FROM storage_meta WHERE key = ?').get(process.argv[2]);
    db.close();
    console.log(row?.value ?? '');
  `, dbPath, DB_MARKER_KEY], { cwd: repoRoot, encoding: 'utf8' });
  const marker = String(result.stdout).trim();
  return marker.length > 0 ? marker : null;
}

function writeDatabaseMarker(dbPath, markerId) {
  spawnSync('node', ['-e', `
    const { nodeSqlite } = require('./connector/budcom_connector/dist/storage/sqlite/node-sqlite.js');
    const db = new nodeSqlite.DatabaseSync(process.argv[1]);
    db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(process.argv[2], process.argv[3]);
    db.close();
  `, dbPath, DB_MARKER_KEY, markerId], { cwd: repoRoot, encoding: 'utf8' });
}

function killBudcomProcesses() {
  spawnSync('taskkill', ['/IM', 'Budcom Desktop.exe', '/F'], { stdio: 'ignore' });
  spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `Get-CimInstance Win32_Process |
      Where-Object {
        $_.Name -ne 'powershell.exe' -and $_.Name -ne 'pwsh.exe' -and
        ($_.CommandLine -like '*connector*dist*main.js*' -or $_.CommandLine -like '*resources*connector*main.js*')
      } |
      ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }`,
  ], { stdio: 'ignore' });
}

function runInstaller(installerPath) {
  return spawnSync(installerPath, ['/S'], { encoding: 'utf8' });
}

function launchDesktop() {
  if (process.platform === 'win32') {
    spawnSync('cmd.exe', ['/c', 'start', '""', paths.appExe], {
      detached: true,
      stdio: 'ignore',
      windowsHide: false,
    });
    return null;
  }
  const child = spawn(paths.appExe, [], { detached: true, stdio: 'ignore', windowsHide: false });
  child.unref();
  return child;
}

async function observeRunningDesktop(timeoutMs = 60000) {
  await sleep(5000);
  const desktopAlive = getDesktopProcesses().length > 0;
  const connectorProcesses = getConnectorProcesses();
  const connectorPort = resolveConnectorPort(paths.configPath);
  const healthUrl = `http://127.0.0.1:${connectorPort}`;
  const health = await waitForConnectorHealth(healthUrl, timeoutMs);
  const logFile = await waitForLogFile(paths.logsDir, 'budcom-desktop.log', timeoutMs);
  const logs = readPrivacySafeStartupLogs(logFile);
  const portListening = isPortListening(connectorPort);
  const connectorDiagnostics = connectorProcesses.map((row) => ({
    pid: row.ProcessId,
    classification: classifyConnectorCommandLine(String(row.CommandLine ?? '')),
    commandLineRedacted: redactLine(String(row.CommandLine ?? '')),
  }));
  const startupEvidence = buildStartupEvidence({
    desktopAlive,
    connectorProcesses,
    connectorPort,
    portListening,
    health,
    logs,
  });
  return {
    desktopAlive,
    connectorProcesses,
    connectorPort,
    healthUrl,
    health,
    healthClassification: classifyHealthResponse(health.body),
    logs,
    portListening,
    connectorDiagnostics,
    startupEvidence,
    connectorAlive: connectorProcesses.length === 1 && (health.ready || portListening),
  };
}

async function gracefulShutdown(timeoutMs = 15000) {
  spawnSync('taskkill', ['/IM', 'Budcom Desktop.exe', '/F'], { stdio: 'ignore' });
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const desktop = getDesktopProcesses().length;
    const connector = getConnectorProcesses().length;
    if (desktop === 0 && connector === 0) {
      return { desktop: 0, connector: 0 };
    }
    await sleep(1000);
  }
  killBudcomProcesses();
  await sleep(2000);
  return {
    desktop: getDesktopProcesses().length,
    connector: getConnectorProcesses().length,
  };
}

function findRuntimeClosureInstaller() {
  const candidates = [
    path.join(repoRoot, 'release/controlled-pilot/0.4.3/artifacts-runtime-closure/BudcomDesktop-0.4.3-x64-setup.exe'),
    path.join(repoRoot, 'release/controlled-pilot/0.4.3/artifacts/BudcomDesktop-0.4.3-x64-setup.exe'),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

export async function runFirstLaunchValidation(options = {}) {
  const installerPath = options.installerPath
    ?? process.env.BUDCOM_LIFECYCLE_INSTALLER
    ?? findRuntimeClosureInstaller();
  if (!installerPath || !fs.existsSync(installerPath)) {
    throw new Error('Installer path not found. Build runtime-closure candidate first.');
  }

  killBudcomProcesses();
  if (fs.existsSync(paths.installRoot)) {
    throw new Error('Existing installation detected. Remove manually before first-launch validation.');
  }
  if (fs.existsSync(paths.userDataRoot)) {
    throw new Error('Existing AppData detected. Use cleanup-only before first-launch validation.');
  }

  const connectorPort = resolveConnectorPort(paths.configPath);
  if (isPortListening(connectorPort)) {
    throw new Error(`Configured connector port ${connectorPort} is already in use`);
  }

  const install = runInstaller(installerPath);
  if ((install.status ?? 1) !== 0 || !fs.existsSync(paths.appExe)) {
    throw new Error(`Installation failed with exit code ${install.status ?? 1}`);
  }

  fs.mkdirSync(paths.connectorDataDir, { recursive: true });
  const v7Fixture = path.join(os.tmpdir(), 'budcom-first-launch-v7.db');
  createSchemaV7Fixture(v7Fixture);
  fs.copyFileSync(v7Fixture, paths.dbPath);
  writeDatabaseMarker(paths.dbPath, MARKER_ID);
  const schemaBefore = readSchemaVersion(paths.dbPath);

  launchDesktop();
  const firstRun = await observeRunningDesktop(60000);
  const schemaAfterFirstRun = readSchemaVersion(paths.dbPath);
  const markerAfterFirstRun = readDatabaseMarker(paths.dbPath);

  const shutdownAfterFirst = await gracefulShutdown(15000);

  launchDesktop();
  const reopenRun = await observeRunningDesktop(45000);
  const schemaAfterReopen = readSchemaVersion(paths.dbPath);
  const markerAfterReopen = readDatabaseMarker(paths.dbPath);

  const shutdownFinal = await gracefulShutdown(15000);

  const verdict = deriveFirstLaunchVerdict({
    desktopAlive: firstRun.desktopAlive,
    logs: firstRun.logs,
    connectorProcesses: firstRun.connectorProcesses,
    connectorAlive: firstRun.connectorAlive,
    health: firstRun.health,
    healthClassification: firstRun.healthClassification,
    schemaBefore,
    schemaAfter: schemaAfterFirstRun,
    schemaAfterReopen,
    orphanDesktop: shutdownFinal.desktop,
    orphanConnector: shutdownFinal.connector,
    startupEvidence: firstRun.startupEvidence,
    connectorDiagnostics: firstRun.connectorDiagnostics,
    reopenHealth: reopenRun.health,
    reopenConnectorCount: reopenRun.connectorProcesses.length,
  });

  const report = {
    gateVersion: 3,
    classification: 'pre_commit_runtime_proof_only',
    installerPath,
    installerSha256: sha256File(installerPath),
    installationExitCode: install.status ?? 0,
    schemaBefore,
    schemaAfterFirstRun,
    schemaAfterReopen,
    markerAfterFirstRun,
    markerAfterReopen,
    reopenIdempotent: schemaAfterReopen === 8 && markerAfterReopen === MARKER_ID && reopenRun.health.ready,
    firstRun: { ...firstRun, schemaAfterFirstRun },
    reopenRun,
    shutdownAfterFirst,
    shutdownFinal,
    gracefulShutdownVerdict: shutdownFinal.desktop === 0 && shutdownFinal.connector === 0 ? 'PASS' : 'FAIL',
    firstLaunchVerdict: verdict.verdict,
    failures: verdict.failures,
  };

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  return report;
}

const invoked = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (invoked) {
  runFirstLaunchValidation({ installerPath: process.argv[2] })
    .then((report) => {
      console.log(JSON.stringify(report, null, 2));
      if (report.firstLaunchVerdict !== 'PASS') {
        process.exit(1);
      }
    })
    .catch((error) => {
      console.error('First launch validation FAIL:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    });
}

export { paths, classifyHealthResponse, deriveFirstLaunchVerdict as deriveVerdict };
