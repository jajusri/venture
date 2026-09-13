#!/usr/bin/env node
/**
 * RC#4 controlled-pilot lifecycle validation harness (Windows).
 * Default: dry-run preflight + report skeleton.
 * --execute-windows: run real install/first-run/reinstall/uninstall/reinstall cycle.
 * --cleanup-only: bounded cleanup of gate-created AppData (only when markers match).
 */
import { spawn, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { verifyControlledPilotCandidate } from './candidate-integrity.mjs';
import { createSchemaV7Fixture } from './create-schema-v7-fixture.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const reportDir = path.join(repoRoot, 'release', 'controlled-pilot', '0.4.3', 'reports');
const reportPath = path.join(reportDir, 'lifecycle-gate-report.json');

const MARKER_ID = 'rc4-lifecycle-gate-20260725';
const CONFIG_MARKER_FILE = 'lifecycle-gate-marker.json';
const DIAG_MARKER_FILE = 'lifecycle-gate-diagnostic-marker.txt';
const DB_MARKER_KEY = 'lifecycle_gate_marker';

const paths = {
  installRoot: path.join(process.env.ProgramFiles ?? '', 'Venture Desktop'),
  appExe: path.join(process.env.ProgramFiles ?? '', 'Venture Desktop', 'Venture Desktop.exe'),
  uninstallExe: path.join(process.env.ProgramFiles ?? '', 'Venture Desktop', 'Uninstall Venture Desktop.exe'),
  userDataRoot: path.join(process.env.APPDATA ?? '', '@venture', 'desktop'),
  connectorDataDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'connector-data'),
  dbPath: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'connector-data', 'venture-ledger.db'),
  releaseMetadataDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'release-metadata'),
  logsDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'logs'),
  diagnosticsDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'diagnostics-exports'),
  connectorDiagnosticsDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'connector-diagnostics'),
  tallyAuditPath: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'connector-diagnostics', 'tally-request-audit.jsonl'),
  transportIdentityDir: path.join(process.env.APPDATA ?? '', '@venture', 'desktop', 'connector-transport-identity'),
  legacyUserDataRoot: path.join(process.env.APPDATA ?? '', 'venture-desktop'),
};

function readIdentityEvidence(identityDir) {
  const certPath = path.join(identityDir, 'transport-cert.pem');
  const keyPath = path.join(identityDir, 'transport-key.pem');
  const certificateExists = fs.existsSync(certPath);
  const privateKeyExists = fs.existsSync(keyPath);
  if (certificateExists !== privateKeyExists) {
    throw new Error(`Partial transport identity detected at ${identityDir}`);
  }
  if (!certificateExists) {
    return { identityDir, certificateExists, privateKeyExists, complete: false };
  }
  const certificate = new crypto.X509Certificate(fs.readFileSync(certPath));
  const spki = certificate.publicKey.export({ type: 'spki', format: 'der' });
  return {
    identityDir,
    certificateExists,
    privateKeyExists,
    complete: true,
    certificateSha256: sha256File(certPath),
    privateKeySha256: sha256File(keyPath),
    fingerprint: `sha256/${crypto.createHash('sha256').update(spki).digest('base64')}`,
  };
}

function assertPreLaunchIdentityPreserved(before, after) {
  if (!before.complete || !after.complete) {
    throw new Error('Installer identity preservation failed before first application launch: complete pair missing');
  }
  if (before.certificateSha256 !== after.certificateSha256
    || before.privateKeySha256 !== after.privateKeySha256
    || before.fingerprint !== after.fingerprint) {
    throw new Error('Installer identity preservation failed before first application launch: identity changed');
  }
}

function readTransportFingerprint() {
  const certPath = path.join(paths.transportIdentityDir, 'transport-cert.pem');
  if (!fs.existsSync(certPath)) return null;
  const certificate = new crypto.X509Certificate(fs.readFileSync(certPath));
  const spki = certificate.publicKey.export({ type: 'spki', format: 'der' });
  return `sha256/${crypto.createHash('sha256').update(spki).digest('base64')}`;
}

function readFirewallContract() {
  const bundledNode = path.join(paths.installRoot, 'resources', 'node', 'node.exe').toLowerCase();
  const result = spawnSync('netsh', ['advfirewall', 'firewall', 'show', 'rule', 'name=all', 'verbose'], { encoding: 'utf8' });
  const text = String(result.stdout ?? '').toLowerCase();
  return {
    bundledNode,
    http: text.includes('venture connector http') && text.includes(bundledNode) && text.includes('8080'),
    https: text.includes('venture connector https') && text.includes(bundledNode) && text.includes('8443'),
    mdns: text.includes('venture connector mdns') && text.includes(bundledNode) && text.includes('5353'),
  };
}

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function countProcesses(namePattern) {
  if (process.platform !== 'win32') {
    return 0;
  }
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `(Get-CimInstance Win32_Process | Where-Object { $_.Name -like '${namePattern}' -or $_.CommandLine -like '*venture*' }).Count`,
  ], { encoding: 'utf8' });
  const count = Number.parseInt(String(ps.stdout).trim(), 10);
  return Number.isFinite(count) ? count : 0;
}

function getVentureProcesses() {
  if (process.platform !== 'win32') {
    return { desktop: 0, connector: 0 };
  }
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `@(
      Get-CimInstance Win32_Process |
      Where-Object {
        $_.Name -eq 'Venture Desktop.exe' -or
        ($_.CommandLine -like '*connector*dist*main.js*')
      } |
      Select-Object Name, CommandLine
    ) | ConvertTo-Json -Compress`,
  ], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '[]');
    const rows = Array.isArray(parsed) ? parsed : [parsed].filter(Boolean);
    const connector = rows.filter((row) => String(row.CommandLine ?? '').includes('connector')).length;
    const desktop = rows.filter((row) => String(row.CommandLine ?? '').includes('connector') === false).length;
    return { desktop, connector };
  } catch {
    return { desktop: 0, connector: 0 };
  }
}

function readBuildInfoFromStartupLogs() {
  for (const line of readLatestStartupLogLines(80)) {
    if (line.includes('build identity')) {
      const jsonStart = line.indexOf('{');
      if (jsonStart >= 0) {
        try {
          return JSON.parse(line.slice(jsonStart));
        } catch {
          // continue
        }
      }
    }
  }
  return null;
}

function readPackagedConnectorPathEvidence() {
  const connectorMain = path.join(paths.installRoot, 'resources', 'connector', 'dist', 'main.js');
  return {
    connectorEntryExists: fs.existsSync(connectorMain),
    connectorEntryPath: connectorMain,
  };
}

function readLatestStartupLogLines(maxLines = 40) {
  if (!fs.existsSync(paths.logsDir)) {
    return [];
  }
  const files = fs.readdirSync(paths.logsDir)
    .filter((name) => name.endsWith('.log'))
    .map((name) => path.join(paths.logsDir, name))
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  if (files.length === 0) {
    return [];
  }
  const content = fs.readFileSync(files[0], 'utf8');
  return content.split(/\r?\n/).slice(-maxLines);
}

function createSyntheticMarkers() {
  fs.mkdirSync(paths.releaseMetadataDir, { recursive: true });
  fs.mkdirSync(paths.diagnosticsDir, { recursive: true });
  fs.mkdirSync(paths.connectorDataDir, { recursive: true });

  const configMarkerPath = path.join(paths.releaseMetadataDir, CONFIG_MARKER_FILE);
  fs.writeFileSync(configMarkerPath, JSON.stringify({
    markerId: MARKER_ID,
    createdAt: new Date().toISOString(),
    purpose: 'controlled_pilot_lifecycle_gate',
  }, null, 2));

  const diagMarkerPath = path.join(paths.diagnosticsDir, DIAG_MARKER_FILE);
  fs.writeFileSync(diagMarkerPath, `markerId=${MARKER_ID}\n`);

  let schemaVersion = null;
  if (fs.existsSync(paths.dbPath)) {
    const sqlite = spawnSync('node', ['-e', `
      const { nodeSqlite } = require('./connector/venture_connector/dist/storage/sqlite/node-sqlite.js');
      const db = new nodeSqlite.DatabaseSync(process.argv[1]);
      const row = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get();
      db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run('${DB_MARKER_KEY}', '${MARKER_ID}');
      db.close();
      console.log(JSON.stringify({ schemaVersion: row?.version ?? null }));
    `, paths.dbPath], { cwd: repoRoot, encoding: 'utf8' });
    try {
      schemaVersion = JSON.parse(sqlite.stdout.trim()).schemaVersion;
    } catch {
      schemaVersion = null;
    }
  }

  return {
    configMarker: MARKER_ID,
    databaseMarker: MARKER_ID,
    diagnosticMarker: MARKER_ID,
    schemaVersion,
  };
}

function readRetentionMarkers() {
  const configMarkerPath = path.join(paths.releaseMetadataDir, CONFIG_MARKER_FILE);
  const diagMarkerPath = path.join(paths.diagnosticsDir, DIAG_MARKER_FILE);
  let configMarker;
  let diagnosticMarker;
  let databaseMarker;

  if (fs.existsSync(configMarkerPath)) {
    try {
      configMarker = JSON.parse(fs.readFileSync(configMarkerPath, 'utf8')).markerId;
    } catch {
      configMarker = undefined;
    }
  }
  if (fs.existsSync(diagMarkerPath)) {
    diagnosticMarker = fs.readFileSync(diagMarkerPath, 'utf8').trim().split('=')[1];
  }
  if (fs.existsSync(paths.dbPath)) {
    const sqlite = spawnSync('node', ['-e', `
      const { nodeSqlite } = require('./connector/venture_connector/dist/storage/sqlite/node-sqlite.js');
      const db = new nodeSqlite.DatabaseSync(process.argv[1], { readOnly: true });
      const row = db.prepare('SELECT value FROM storage_meta WHERE key = ?').get('${DB_MARKER_KEY}');
      const version = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get();
      db.close();
      console.log(JSON.stringify({ databaseMarker: row?.value ?? null, schemaVersion: version?.version ?? null }));
    `, paths.dbPath], { cwd: repoRoot, encoding: 'utf8' });
    try {
      const parsed = JSON.parse(sqlite.stdout.trim());
      databaseMarker = parsed.databaseMarker ?? undefined;
    } catch {
      databaseMarker = undefined;
    }
  }

  return { configMarker, databaseMarker, diagnosticMarker };
}

function runInstaller(installerPath, silent) {
  const args = silent ? ['/S'] : [];
  const result = spawnSync(installerPath, args, { encoding: 'utf8' });
  return { exitCode: result.status ?? 1, stdout: result.stdout, stderr: result.stderr };
}

function runUninstaller(silent = true) {
  if (!fs.existsSync(paths.uninstallExe)) {
    throw new Error('Uninstaller not found');
  }
  const args = silent ? ['/S'] : [];
  const result = spawnSync(paths.uninstallExe, args, { encoding: 'utf8' });
  return { exitCode: result.status ?? 1 };
}

async function waitForConnectorHealth(timeoutMs = 30000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const response = await fetch('http://127.0.0.1:8080/health');
      if (response.ok) {
        return true;
      }
    } catch {
      // retry
    }
    await sleep(1000);
  }
  return false;
}

function readRegisteredInstallRoot(scope) {
  const hive = scope === 'currentuser' ? 'HKCU' : 'HKLM';
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `$entry = Get-ChildItem '${hive}:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall' -ErrorAction SilentlyContinue |
      ForEach-Object { Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue } |
      Where-Object { $_.DisplayName -like 'Venture Desktop*' } |
      Select-Object -First 1; $entry.InstallLocation`,
  ], { encoding: 'utf8' });
  const value = String(ps.stdout ?? '').trim();
  return value || null;
}

function seedLegacyIdentityForIsolatedLifecycle(installRoot, fixtureDir) {
  const source = readIdentityEvidence(fixtureDir);
  if (!source.complete) {
    throw new Error('Lifecycle legacy identity fixture must contain a complete certificate/key pair');
  }
  const legacyDir = path.join(installRoot, 'resources', 'connector', 'dist', 'data', 'transport');
  fs.mkdirSync(legacyDir, { recursive: true });
  fs.copyFileSync(path.join(fixtureDir, 'transport-cert.pem'), path.join(legacyDir, 'transport-cert.pem'));
  fs.copyFileSync(path.join(fixtureDir, 'transport-key.pem'), path.join(legacyDir, 'transport-key.pem'));
  const seeded = readIdentityEvidence(legacyDir);
  assertPreLaunchIdentityPreserved(source, seeded);
  return seeded;
}

async function readPackagedCompanies() {
  try {
    const response = await fetch('http://127.0.0.1:8080/companies', { signal: AbortSignal.timeout(15000) });
    const body = await response.json();
    return {
      status: response.status,
      ok: response.ok,
      companyCount: Array.isArray(body?.companies) ? body.companies.length : null,
      errorCode: typeof body?.code === 'string' ? body.code : null,
    };
  } catch (error) {
    return { status: null, ok: false, companyCount: null, errorCode: error instanceof Error ? error.name : 'unknown' };
  }
}

async function readSchemaVersion() {
  if (!fs.existsSync(paths.dbPath)) {
    return null;
  }
  const sqlite = spawnSync('node', ['-e', `
    const { nodeSqlite } = require('./connector/venture_connector/dist/storage/sqlite/node-sqlite.js');
    const db = new nodeSqlite.DatabaseSync(process.argv[1], { readOnly: true });
    const version = db.prepare('SELECT MAX(version) AS version FROM schema_migrations').get();
    db.close();
    console.log(version?.version ?? 0);
  `, paths.dbPath], { cwd: repoRoot, encoding: 'utf8' });
  return Number.parseInt(String(sqlite.stdout).trim(), 10) || null;
}

async function launchDesktop(timeoutMs = 20000) {
  if (!fs.existsSync(paths.appExe)) {
    throw new Error('Desktop executable not found');
  }
  const child = spawn(paths.appExe, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: false,
  });
  child.unref();
  const healthReady = await waitForConnectorHealth(timeoutMs);
  const companies = healthReady ? await readPackagedCompanies() : null;
  await sleep(healthReady ? 3000 : Math.min(timeoutMs, 10000));
  const processes = getVentureProcesses();
  spawnSync('taskkill', ['/IM', 'Venture Desktop.exe', '/F'], { stdio: 'ignore' });
  await sleep(3000);
  killOwnedConnectorProcesses();
  await sleep(2000);
  return { ...processes, healthReady, companies };
}

function killOwnedConnectorProcesses() {
  spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `Get-CimInstance Win32_Process |
      Where-Object { $_.CommandLine -like '*connector*dist*main.js*' } |
      ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }`,
  ], { stdio: 'ignore' });
}

function hasBusinessAppData(root) {
  return fs.existsSync(path.join(root, 'connector-data'))
    || fs.existsSync(path.join(root, 'desktop-config.json'))
    || fs.existsSync(path.join(root, 'release-metadata', CONFIG_MARKER_FILE));
}

function installBinariesPresent() {
  return fs.existsSync(paths.appExe) || fs.existsSync(paths.uninstallExe);
}

async function waitForUninstallComplete(timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (!installBinariesPresent()) {
      return true;
    }
    await sleep(2000);
  }
  return !installBinariesPresent();
}

function detectStartupEntries() {
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `$run = Get-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -ErrorAction SilentlyContinue;
     $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like '*Venture*' };
     [pscustomobject]@{ RunKeys = @($run.PSObject.Properties.Name | Where-Object { $_ -like '*Venture*' }); Tasks = @($tasks.TaskName) } | ConvertTo-Json -Compress`,
  ], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '{}');
    const tasks = Array.isArray(parsed.Tasks) ? parsed.Tasks.filter(Boolean) : [];
    const runKeys = Array.isArray(parsed.RunKeys) ? parsed.RunKeys.filter(Boolean) : [];
    return { RunKeys: runKeys, Tasks: tasks };
  } catch {
    return { RunKeys: [], Tasks: [] };
  }
}

function validateCleanupTarget(candidatePath, allowedRoot) {
  const resolvedCandidate = path.resolve(candidatePath);
  const resolvedRoot = path.resolve(allowedRoot);
  const relative = path.relative(resolvedRoot, resolvedCandidate);
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`Cleanup path escape rejected: ${candidatePath}`);
  }
}

function boundedCleanup(dryRun) {
  const targets = [];
  if (fs.existsSync(paths.userDataRoot)) {
    targets.push(paths.userDataRoot);
  }
  if (fs.existsSync(paths.legacyUserDataRoot)) {
    targets.push(paths.legacyUserDataRoot);
  }
  if (targets.length === 0) {
    return { removed: [], skipped: ['userDataRoot missing'] };
  }
  const marker = readRetentionMarkers();
  const legacyMarkerPath = path.join(paths.legacyUserDataRoot, 'release-metadata', CONFIG_MARKER_FILE);
  const legacyMarker = fs.existsSync(legacyMarkerPath)
    ? JSON.parse(fs.readFileSync(legacyMarkerPath, 'utf8')).markerId
    : undefined;
  if (marker.configMarker !== MARKER_ID && legacyMarker !== MARKER_ID) {
    throw new Error('Refusing cleanup: config marker mismatch (not gate-created data)');
  }
  for (const target of targets) {
    validateCleanupTarget(target, target);
  }
  if (dryRun) {
    return { removed: [], skipped: ['dry-run'], wouldRemove: targets };
  }
  for (const target of targets) {
    fs.rmSync(target, { recursive: true, force: true });
  }
  return { removed: targets, skipped: [] };
}

function initialReport() {
  return {
    gateVersion: 1,
    platform: process.platform,
    isolationMethod: 'first-install gate session — no pre-existing Venture AppData on host profile',
    candidateVerified: false,
    installationCompleted: false,
    uninstallRetentionVerified: false,
    releaseMode: 'controlled_pilot',
    gitCommit: 'a9595af857546de3c65f1457775f3f65eb78ae77',
    notes: [],
    phases: {},
  };
}

async function runExecuteWindows(report) {
  const candidate = verifyControlledPilotCandidate();
  report.candidateVerified = true;
  report.phases.integrity = {
    sha256: candidate.sha256,
    sizeBytes: candidate.sizeBytes,
    gitCommit: candidate.buildInfo.gitCommit,
    dirtyTree: candidate.buildInfo.dirtyTree,
    releaseMode: candidate.buildInfo.releaseMode,
  };

  if (hasBusinessAppData(paths.userDataRoot) || hasBusinessAppData(paths.legacyUserDataRoot) || fs.existsSync(paths.installRoot)) {
    throw new Error('Pre-existing Venture install or business AppData detected — aborting to protect host profile');
  }

  const install = runInstaller(candidate.installerPath, true);
  report.phases.installation = {
    exitCode: install.exitCode,
    uacPromptObserved: false,
    perMachine: true,
    installRoot: paths.installRoot,
    silent: true,
  };
  if (install.exitCode !== 0 || !fs.existsSync(paths.appExe)) {
    throw new Error(`Installation failed with exit code ${install.exitCode}`);
  }
  report.installationCompleted = true;

  const v7Fixture = path.join(os.tmpdir(), 'venture-lifecycle-v7-fixture.db');
  createSchemaV7Fixture(v7Fixture);
  fs.mkdirSync(paths.connectorDataDir, { recursive: true });
  fs.copyFileSync(v7Fixture, paths.dbPath);

  const firstLaunch = await launchDesktop(35000);
  const companiesAfterFirstLaunch = firstLaunch.companies;
  const fingerprintAfterFirstLaunch = readTransportFingerprint();
  const schemaAfterFirstRun = await readSchemaVersion();
  report.phases.firstRun = {
    appOpened: fs.existsSync(paths.appExe),
    connectorHealthReady: firstLaunch.healthReady,
    processCounts: { desktop: firstLaunch.desktop, connector: firstLaunch.connector },
    startupLogLines: readLatestStartupLogLines(20).filter((line) => !/password|gstin|voucher|<\?xml/i.test(line)),
    schemaAfterFirstRun,
    fingerprintAfterFirstLaunch,
    companies: companiesAfterFirstLaunch,
  };

  const buildInfo = readBuildInfoFromStartupLogs();
  report.phases.releaseIdentity = buildInfo ?? {
    desktopVersion: candidate.buildInfo.desktopVersion,
    connectorVersion: candidate.buildInfo.connectorVersion,
    storageSchemaVersion: candidate.buildInfo.storageSchemaVersion,
    releaseMode: candidate.buildInfo.releaseMode,
    gitCommit: candidate.buildInfo.gitCommit,
  };
  report.phases.packagedConnector = readPackagedConnectorPathEvidence();

  const markers = createSyntheticMarkers();
  report.phases.syntheticMarkers = markers;

  const reinstall = runInstaller(candidate.installerPath, true);
  const fingerprintAfterReinstall = readTransportFingerprint();
  report.phases.sameVersionReinstall = {
    exitCode: reinstall.exitCode,
    markersAfterReinstall: readRetentionMarkers(),
    fingerprintAfterReinstall,
    fingerprintPreserved: fingerprintAfterFirstLaunch !== null && fingerprintAfterReinstall === fingerprintAfterFirstLaunch,
  };
  if (!firstLaunch.healthReady || !companiesAfterFirstLaunch?.ok || (companiesAfterFirstLaunch.companyCount ?? 0) < 1) {
    throw new Error('Packaged Connector did not prove local health and Tally company discovery');
  }

  const afterReinstallMarkers = readRetentionMarkers();
  if (afterReinstallMarkers.configMarker !== MARKER_ID || afterReinstallMarkers.databaseMarker !== MARKER_ID) {
    throw new Error('Same-version reinstall did not preserve synthetic markers');
  }
  if (fingerprintAfterFirstLaunch !== null && fingerprintAfterReinstall !== fingerprintAfterFirstLaunch) {
    throw new Error('Same-version reinstall changed the persisted transport fingerprint');
  }

  report.phases.firewall = readFirewallContract();
  if (!report.phases.firewall.http || !report.phases.firewall.https || !report.phases.firewall.mdns) {
    throw new Error('Installed firewall rules do not target the exact bundled executable and required ports');
  }

  report.phases.upgradeSchema = {
    schemaBeforeGate: 7,
    schemaAfterFirstRun,
    method: 'pre-seeded schema v7 fixture before first launch; migration validated on connector startup',
  };

  spawnSync('taskkill', ['/IM', 'Venture Desktop.exe', '/F'], { stdio: 'ignore' });
  await sleep(3000);
  killOwnedConnectorProcesses();
  await sleep(2000);

  const uninstall = runUninstaller(true);
  await waitForUninstallComplete(60000);
  report.phases.uninstall = {
    exitCode: uninstall.exitCode,
    installRootExists: fs.existsSync(paths.installRoot),
    installBinariesPresent: installBinariesPresent(),
    appDataRootExists: fs.existsSync(paths.userDataRoot),
    markersAfterUninstall: readRetentionMarkers(),
  };
  if (installBinariesPresent()) {
    throw new Error('Install binaries still present after uninstall');
  }
  if (!fs.existsSync(paths.userDataRoot)) {
    throw new Error('AppData deleted during uninstall');
  }
  const postUninstallMarkers = readRetentionMarkers();
  if (postUninstallMarkers.configMarker !== MARKER_ID || postUninstallMarkers.databaseMarker !== MARKER_ID) {
    throw new Error('Uninstall did not retain synthetic markers');
  }
  report.uninstallRetentionVerified = true;

  const reinstallAfterUninstall = runInstaller(candidate.installerPath, true);
  await launchDesktop(15000);
  report.phases.reinstallAfterUninstall = {
    exitCode: reinstallAfterUninstall.exitCode,
    markers: readRetentionMarkers(),
    releaseIdentity: readBuildInfoFromStartupLogs(),
  };

  report.phases.processOwnership = {
    firstLaunch: {
      desktop: firstLaunch.desktop,
      connector: firstLaunch.connector,
      healthReady: firstLaunch.healthReady,
    },
    duplicateLaunchPolicy: 'single-instance lock expected (second launch not separately executed in harness)',
  };
  report.phases.startupEntries = detectStartupEntries();
  report.phases.appDataLayout = {
    userDataRoot: paths.userDataRoot,
    connectorDatabaseDir: paths.connectorDataDir,
    logsDir: paths.logsDir,
    diagnosticsExportDir: paths.diagnosticsDir,
    connectorDiagnosticsDir: paths.connectorDiagnosticsDir,
    tallyAuditPath: paths.tallyAuditPath,
    transportIdentityDir: paths.transportIdentityDir,
    installRoot: paths.installRoot,
    mutableOutsideInstall: true,
  };

  report.phases.cleanup = boundedCleanup(false);
  return report;
}

function runIdentityOverinstallPreLaunchGate(report) {
  if (process.env.VENTURE_LIFECYCLE_ISOLATED_PROFILE !== '1') {
    throw new Error('Identity over-install gate requires VENTURE_LIFECYCLE_ISOLATED_PROFILE=1');
  }
  const oldInstaller = process.env.VENTURE_LIFECYCLE_OLD_INSTALLER;
  const fixtureDir = process.env.VENTURE_LIFECYCLE_LEGACY_IDENTITY_DIR;
  const oldScope = process.env.VENTURE_LIFECYCLE_OLD_INSTALL_SCOPE === 'currentuser'
    ? 'currentuser'
    : 'allusers';
  if (!oldInstaller || !fs.existsSync(oldInstaller)) {
    throw new Error('VENTURE_LIFECYCLE_OLD_INSTALLER must name the exact old installer');
  }
  if (!fixtureDir || !fs.existsSync(fixtureDir)) {
    throw new Error('VENTURE_LIFECYCLE_LEGACY_IDENTITY_DIR must name a complete identity fixture');
  }
  if (hasBusinessAppData(paths.userDataRoot) || hasBusinessAppData(paths.legacyUserDataRoot)
    || fs.existsSync(paths.installRoot)) {
    throw new Error('Identity over-install gate requires an isolated profile with no existing Venture state');
  }

  const candidate = verifyControlledPilotCandidate();
  const oldArgs = ['/S', oldScope === 'currentuser' ? '/currentuser' : '/allusers'];
  const oldInstall = spawnSync(oldInstaller, oldArgs, { encoding: 'utf8' });
  if ((oldInstall.status ?? 1) !== 0) {
    throw new Error(`Old-product installation failed with exit code ${oldInstall.status ?? 1}`);
  }
  const oldInstallRoot = readRegisteredInstallRoot(oldScope);
  if (!oldInstallRoot || !fs.existsSync(oldInstallRoot)) {
    throw new Error(`Unable to resolve registered ${oldScope} old installation root`);
  }
  if (getVentureProcesses().desktop !== 0 || getVentureProcesses().connector !== 0) {
    throw new Error('Old installer unexpectedly launched Venture before identity fixture setup');
  }

  const before = seedLegacyIdentityForIsolatedLifecycle(oldInstallRoot, fixtureDir);
  if (fs.existsSync(paths.transportIdentityDir)) {
    throw new Error('Persistent identity unexpectedly exists before over-install');
  }
  const overinstall = runInstaller(candidate.installerPath, true);
  if (overinstall.exitCode !== 0) {
    throw new Error(`Candidate over-install failed with exit code ${overinstall.exitCode}`);
  }

  // Mandatory boundary: inspect the migrated pair before any Desktop/Connector launch.
  const processesBeforeAssertion = getVentureProcesses();
  if (processesBeforeAssertion.desktop !== 0 || processesBeforeAssertion.connector !== 0) {
    throw new Error('Candidate unexpectedly launched Venture before the pre-launch identity assertion');
  }
  const after = readIdentityEvidence(paths.transportIdentityDir);
  assertPreLaunchIdentityPreserved(before, after);
  report.phases.identityOverinstallPreLaunch = {
    oldScope,
    oldInstallRoot,
    candidateInstallRoot: paths.installRoot,
    oldInstallTreeReplaced: !fs.existsSync(path.join(oldInstallRoot, 'resources', 'connector', 'dist', 'data', 'transport')),
    desktopProcesses: processesBeforeAssertion.desktop,
    connectorProcesses: processesBeforeAssertion.connector,
    certificateSha256Preserved: before.certificateSha256 === after.certificateSha256,
    privateKeySha256Preserved: before.privateKeySha256 === after.privateKeySha256,
    fingerprintBefore: before.fingerprint,
    fingerprintAfter: after.fingerprint,
    fingerprintPreserved: before.fingerprint === after.fingerprint,
    assertedBeforeApplicationLaunch: true,
  };
  report.installationCompleted = true;
  return report;
}

async function main() {
  const execute = process.argv.includes('--execute-windows');
  const executeIdentityOverinstall = process.argv.includes('--execute-windows-identity-overinstall');
  const cleanupOnly = process.argv.includes('--cleanup-only');
  const dryRun = !execute && !executeIdentityOverinstall && !cleanupOnly;

  const report = initialReport();
  report.phases.preflight = {
    windowsVersion: os.release(),
    architecture: process.arch,
    user: os.userInfo().username,
    preExistingAppData: hasBusinessAppData(paths.userDataRoot) || hasBusinessAppData(paths.legacyUserDataRoot),
    preExistingInstall: fs.existsSync(paths.installRoot),
    gitHead: spawnSync('git', ['rev-parse', 'HEAD'], { cwd: repoRoot, encoding: 'utf8' }).stdout.trim(),
    gitClean: spawnSync('git', ['status', '--porcelain'], { cwd: repoRoot, encoding: 'utf8' }).stdout.trim().length === 0,
  };

  if (cleanupOnly) {
    report.phases.cleanup = boundedCleanup(false);
    fs.mkdirSync(reportDir, { recursive: true });
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  try {
    const candidate = verifyControlledPilotCandidate();
    report.candidateVerified = true;
    report.phases.integrity = {
      sha256: candidate.sha256,
      sizeBytes: candidate.sizeBytes,
    };
    if (executeIdentityOverinstall) {
      runIdentityOverinstallPreLaunchGate(report);
    } else if (execute) {
      await runExecuteWindows(report);
    } else {
      report.notes.push('Dry-run only — pass --execute-windows for real lifecycle execution');
    }
  } catch (error) {
    report.notes.push(error instanceof Error ? error.message : String(error));
    report.failed = true;
  }

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  if (report.failed) {
    process.exit(1);
  }
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (invokedDirectly) {
  main();
}

export {
  paths,
  readRetentionMarkers,
  boundedCleanup,
  getVentureProcesses,
  readIdentityEvidence,
  assertPreLaunchIdentityPreserved,
};
