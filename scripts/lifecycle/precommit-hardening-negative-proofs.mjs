#!/usr/bin/env node
/**
 * Pre-commit hardening negative proofs (tampered runtime, probe user-data, hostile network).
 * Uses isolated copies of the evidence win-unpacked tree — never mutates source or installer.
 */
import { createHash, randomUUID } from 'node:crypto';
import { createServer } from 'node:net';
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const reportPath = path.join(repoRoot, 'release/controlled-pilot/0.4.3/reports/precommit-hardening-negative-proofs-report.json');

const EVIDENCE_UNPACKED = path.join(
  repoRoot,
  'release/controlled-pilot/0.4.3/artifacts-precommit-hardening-20260725-235500/win-unpacked',
);

/** Hard cap — runner must never exceed this many desktop launches per invocation. */
const MAX_DESKTOP_LAUNCHES = 10;
let desktopLaunchCount = 0;
const trackedDesktopPids = new Set();
let interruptCleanupRegistered = false;

function registerInterruptCleanup() {
  if (interruptCleanupRegistered) {
    return;
  }
  interruptCleanupRegistered = true;
  const cleanup = () => {
    for (const pid of trackedDesktopPids) {
      killProcessTree(pid);
    }
    trackedDesktopPids.clear();
  };
  process.on('SIGINT', () => {
    cleanup();
    process.exit(130);
  });
  process.on('SIGTERM', () => {
    cleanup();
    process.exit(143);
  });
}

function assertLaunchBudget(testName) {
  if (desktopLaunchCount >= MAX_DESKTOP_LAUNCHES) {
    throw new Error(`Desktop launch budget exceeded (${MAX_DESKTOP_LAUNCHES}) before ${testName}. Aborting to prevent relaunch storm.`);
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sha256File(filePath) {
  const hash = createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

async function pickEphemeralPort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        server.close();
        reject(new Error('Unable to allocate ephemeral port.'));
        return;
      }
      const port = address.port;
      server.close((error) => (error ? reject(error) : resolve(port)));
    });
    server.on('error', reject);
  });
}

function copyDirRecursive(source, destination) {
  fs.mkdirSync(destination, { recursive: true });
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const from = path.join(source, entry.name);
    const to = path.join(destination, entry.name);
    if (entry.isDirectory()) {
      copyDirRecursive(from, to);
    } else {
      fs.copyFileSync(from, to);
    }
  }
}

function readStartupDiagnostics(logPath) {
  if (!fs.existsSync(logPath)) {
    return [];
  }
  return fs.readFileSync(logPath, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0)
    .map((line) => JSON.parse(line));
}

function queryProcesses(filterCommand) {
  const ps = spawnSync('powershell', ['-NoProfile', '-Command', filterCommand], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '[]');
    return Array.isArray(parsed) ? parsed : [parsed].filter(Boolean);
  } catch {
    return [];
  }
}

function listNodeProcessesUnder(installRootMarker) {
  const marker = installRootMarker.replace(/\\/g, '\\\\').replace(/'/g, "''");
  return queryProcesses(`@(
    Get-CimInstance Win32_Process |
    Where-Object { $_.Name -eq 'node.exe' -and $_.CommandLine -like '*${marker}*' } |
    Select-Object ProcessId, ParentProcessId, Name, CommandLine
  ) | ConvertTo-Json -Compress`);
}

function listBudcomDesktopForCopy(copyRootMarker) {
  const marker = copyRootMarker.replace(/\\/g, '\\\\').replace(/'/g, "''");
  return queryProcesses(`@(
    Get-CimInstance Win32_Process |
    Where-Object { $_.Name -eq 'Budcom Desktop.exe' -and $_.CommandLine -like '*${marker}*' } |
    Select-Object ProcessId, ParentProcessId, Name, CommandLine
  ) | ConvertTo-Json -Compress`);
}

function isProcessAlive(pid) {
  const check = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `(Get-Process -Id ${pid} -ErrorAction SilentlyContinue) -ne $null`,
  ], { encoding: 'utf8' });
  return String(check.stdout).trim().toLowerCase() === 'true';
}

function killProcessTree(rootPid) {
  if (rootPid) {
    spawnSync('taskkill', ['/PID', String(rootPid), '/T', '/F'], { stdio: 'ignore' });
  }
}

function countStage(entries, stage) {
  return entries.filter((entry) => entry.stage === stage).length;
}

function buildBaseEnv(userDataDir, connectorPort, correlationId, extra = {}) {
  const env = { ...process.env, ...extra };
  delete env.ELECTRON_RUN_AS_NODE;
  env.BUDCOM_SKIP_SINGLE_INSTANCE = '1';
  env.BUDCOM_CONNECTOR_URL = `http://127.0.0.1:${connectorPort}`;
  env.BUDCOM_CONNECTOR_PORT = String(connectorPort);
  env.BUDCOM_CONNECTOR_HOST = '127.0.0.1';
  env.BUDCOM_STARTUP_CORRELATION_ID = correlationId;
  if (userDataDir) {
    env.BUDCOM_USER_DATA_DIR = userDataDir;
  } else {
    delete env.BUDCOM_USER_DATA_DIR;
  }
  return env;
}

function findRecentStartupLog(sinceMs) {
  const roots = [
    os.tmpdir(),
    path.join(process.env.APPDATA ?? '', 'budcom-desktop'),
    path.join(process.env.APPDATA ?? '', 'Budcom Desktop'),
    path.join(process.env.APPDATA ?? '', '@budcom', 'desktop'),
    path.join(process.env.LOCALAPPDATA ?? '', 'budcom-desktop'),
  ].filter(Boolean);
  let newest = null;
  for (const root of roots) {
    const candidate = path.join(root, 'logs', 'startup-diagnostics.jsonl');
    if (!fs.existsSync(candidate)) {
      continue;
    }
    const stat = fs.statSync(candidate);
    if (stat.mtimeMs >= sinceMs && (!newest || stat.mtimeMs > newest.mtimeMs)) {
      newest = { path: candidate, mtimeMs: stat.mtimeMs };
    }
  }
  if (newest) {
    return newest.path;
  }
  const search = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `@(
      Get-ChildItem -Path $env:APPDATA,$env:TEMP -Recurse -Filter startup-diagnostics.jsonl -ErrorAction SilentlyContinue |
      Where-Object { $_.LastWriteTimeUtc -ge (Get-Date).AddMinutes(-5) } |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1 -ExpandProperty FullName
    )`,
  ], { encoding: 'utf8' });
  const found = search.stdout.trim();
  return found.length > 0 ? found : null;
}

async function launchPackagedDesktop(appExe, env, options = {}) {
  registerInterruptCleanup();
  assertLaunchBudget(options.testName ?? 'unknown');

  const observationMs = options.observationMs ?? 35000;
  const preferredLogDir = options.preferredLogDir ?? null;
  const earlyExit = options.earlyExit ?? null;

  const launchStarted = Date.now();
  if (preferredLogDir) {
    fs.mkdirSync(path.join(preferredLogDir, 'logs'), { recursive: true });
  }

  desktopLaunchCount += 1;
  const child = spawn(appExe, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: options.showWindow === true ? false : true,
    env,
  });
  child.unref();
  const desktopPid = child.pid ?? null;
  if (desktopPid) {
    trackedDesktopPids.add(desktopPid);
  }

  let startupLogPath = preferredLogDir
    ? path.join(preferredLogDir, 'logs', 'startup-diagnostics.jsonl')
    : null;
  let entries = [];

  while (Date.now() - launchStarted < observationMs) {
    if (desktopPid && !isProcessAlive(desktopPid)) {
      break;
    }
    if (startupLogPath && fs.existsSync(startupLogPath)) {
      entries = readStartupDiagnostics(startupLogPath);
    } else if (!startupLogPath) {
      startupLogPath = findRecentStartupLog(launchStarted - 5000);
      if (startupLogPath) {
        entries = readStartupDiagnostics(startupLogPath);
      }
    } else {
      entries = readStartupDiagnostics(startupLogPath);
    }
    if (earlyExit && entries.length > 0 && earlyExit(entries)) {
      break;
    }
    await sleep(2000);
  }

  if (!startupLogPath || entries.length === 0) {
    if (!startupLogPath) {
      startupLogPath = preferredLogDir
        ? path.join(preferredLogDir, 'logs', 'startup-diagnostics.jsonl')
        : findRecentStartupLog(launchStarted - 5000);
    }
    entries = startupLogPath ? readStartupDiagnostics(startupLogPath) : [];
  }
  const processStart = entries.find((entry) => entry.stage === 'process_start')?.detail ?? {};
  const integrityFailure = entries.find((entry) => entry.stage === 'packaged_runtime_integrity_failure');
  const startupFailure = entries.filter((entry) => entry.stage === 'connector_startup_failure');
  const spawnAttempts = countStage(entries, 'connector_spawn_attempt');
  const spawnEvents = countStage(entries, 'connector_spawned');

  let healthBody = null;
  const connectorPort = Number(env.BUDCOM_CONNECTOR_PORT ?? 0);
  if (spawnEvents > 0 && connectorPort > 0) {
    try {
      const response = await fetch(`http://127.0.0.1:${connectorPort}/health`, { signal: AbortSignal.timeout(5000) });
      if (response.ok) {
        healthBody = await response.json();
      }
    } catch {
      healthBody = null;
    }
  }

  const desktopAlive = desktopPid ? isProcessAlive(desktopPid) : false;
  const rendererAvailable = entries.some((entry) => entry.stage === 'renderer_loaded' || entry.stage === 'ready_to_show');

  killProcessTree(desktopPid);
  if (desktopPid) {
    trackedDesktopPids.delete(desktopPid);
  }
  await sleep(1500);

  return {
    desktopPid,
    desktopLaunchIndex: desktopLaunchCount,
    desktopAliveAtEnd: desktopAlive,
    rendererAvailable,
    startupLogPath,
    startupEntries: entries,
    processStart,
    integrityFailure: integrityFailure?.detail ?? null,
    integrityFailureCategory: integrityFailure?.detail?.category ?? null,
    startupFailureCount: startupFailure.length,
    connectorSpawnAttemptCount: spawnAttempts,
    connectorSpawnedCount: spawnEvents,
    userDataOverrideApplied: processStart.userDataOverride ?? null,
    userDataPath: processStart.userDataPath ?? null,
    healthBody,
  };
}

async function proveTamperedRuntime() {
  const proofRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-neg-tamper-'));
  const copyRoot = path.join(proofRoot, 'app-copy');
  copyDirRecursive(EVIDENCE_UNPACKED, copyRoot);

  const appExe = path.join(copyRoot, 'Budcom Desktop.exe');
  const nodeExe = path.join(copyRoot, 'resources', 'node', 'node.exe');
  const manifestPath = path.join(copyRoot, 'resources', 'node', 'node-runtime.manifest.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const expectedHash = manifest.nodeExecutableSha256;

  const buffer = fs.readFileSync(nodeExe);
  buffer[buffer.length - 1] ^= 0xff;
  fs.writeFileSync(nodeExe, buffer);
  const computedHash = sha256File(nodeExe);
  const hashMismatch = computedHash.toLowerCase() !== expectedHash.toLowerCase();

  const userDataDir = path.join(proofRoot, 'user-data');
  fs.mkdirSync(userDataDir, { recursive: true });
  const connectorPort = await pickEphemeralPort();
  const correlationId = randomUUID();
  const env = buildBaseEnv(userDataDir, connectorPort, correlationId, {
    BUDCOM_INSTALLED_PROBE_MODE: '1',
  });

  const observation = await launchPackagedDesktop(appExe, env, {
    testName: 'tampered_runtime',
    observationMs: 30000,
    preferredLogDir: userDataDir,
    earlyExit: (entries) => entries.some((entry) => entry.stage === 'packaged_runtime_integrity_failure')
      && entries.some((entry) => entry.stage === 'renderer_loaded' || entry.stage === 'ready_to_show'),
  });
  const copyMarker = copyRoot.slice(-40);
  const nodeProcs = listNodeProcessesUnder(copyMarker);
  const nodeUsesSystemPath = nodeProcs.some((proc) => {
    const cmd = String(proc.CommandLine ?? '').toLowerCase();
    return !cmd.includes('resources\\node\\node.exe') && !cmd.includes('resources/node/node.exe');
  });

  try {
    fs.rmSync(proofRoot, { recursive: true, force: true });
  } catch {
    // best effort
  }

  const pass = hashMismatch
    && observation.integrityFailureCategory === 'hash_mismatch'
    && observation.connectorSpawnAttemptCount === 0
    && observation.connectorSpawnedCount === 0
    && observation.desktopAliveAtEnd
    && observation.rendererAvailable
    && observation.connectorSpawnAttemptCount === observation.connectorSpawnedCount;

  return {
    pass,
    tamperType: 'node_exe_byte_flip',
    expectedHash,
    computedHash,
    hashMismatch,
    integrityFailureCategory: observation.integrityFailureCategory,
    connectorSpawnAttempted: observation.connectorSpawnAttemptCount > 0,
    connectorProcessCreated: observation.connectorSpawnedCount > 0,
    packagedNodeExecuted: observation.connectorSpawnedCount > 0,
    systemNodeFallback: nodeUsesSystemPath,
    desktopAlive: observation.desktopAliveAtEnd,
    rendererAvailable: observation.rendererAvailable,
    startupFailureCount: observation.startupFailureCount,
    connectorSpawnAttemptCount: observation.connectorSpawnAttemptCount,
    connectorSpawnedCount: observation.connectorSpawnedCount,
    retryStorm: observation.connectorSpawnAttemptCount > 1,
  };
}

async function proveUserDataScenario(label, envOverrides, rejectedPath, expectedCategory = null) {
  const proofRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-neg-userdata-'));
  const copyRoot = path.join(proofRoot, 'app-copy');
  copyDirRecursive(EVIDENCE_UNPACKED, copyRoot);
  const appExe = path.join(copyRoot, 'Budcom Desktop.exe');

  const dedicatedUserData = path.join(proofRoot, 'probe-user-data');
  fs.mkdirSync(dedicatedUserData, { recursive: true });
  const connectorPort = await pickEphemeralPort();
  const correlationId = randomUUID();

  const env = buildBaseEnv(dedicatedUserData, connectorPort, correlationId, envOverrides);
  if (rejectedPath) {
    env.BUDCOM_USER_DATA_DIR = rejectedPath;
  }

  const observation = await launchPackagedDesktop(appExe, env, {
    testName: `user_data_${label}`,
    observationMs: 25000,
    earlyExit: (entries) => entries.some((entry) => entry.stage === 'process_start')
      && entries.some((entry) => entry.stage === 'renderer_loaded' || entry.stage === 'ready_to_show'),
  });
  const overrideApplied = observation.userDataOverrideApplied;
  const actualUserData = observation.userDataPath;
  const rejectedPathNormalized = rejectedPath ? path.resolve(rejectedPath) : null;
  const overrideIgnored = rejectedPath
    ? (overrideApplied === null && actualUserData !== rejectedPathNormalized)
    : overrideApplied === null;

  const budcomDiagnosticsInRejectedPath = rejectedPath
    ? fs.existsSync(path.join(path.resolve(rejectedPath), 'logs', 'startup-diagnostics.jsonl'))
    : false;

  try {
    fs.rmSync(proofRoot, { recursive: true, force: true });
  } catch {
    // best effort
  }

  return {
    label,
    pass: observation.desktopAliveAtEnd
      && observation.rendererAvailable
      && overrideIgnored
      && !budcomDiagnosticsInRejectedPath,
    expectedRejectionCategory: expectedCategory,
    overrideApplied,
    userDataPathSelected: actualUserData,
    overrideIgnored,
    budcomDiagnosticsInRejectedPath,
    desktopAlive: observation.desktopAliveAtEnd,
    rendererAvailable: observation.rendererAvailable,
  };
}

async function classifyRejectedPath(candidatePath, installRoot, resourcesPath) {
  const { validateInstalledProbeUserDataDir, UserDataOverrideRejectedError } = await import(
    '../../apps/budcom_desktop/dist/application/release/startup-environment.js'
  );
  try {
    validateInstalledProbeUserDataDir(candidatePath, { installRoot, resourcesPath });
    return { accepted: true, category: null };
  } catch (error) {
    if (error instanceof UserDataOverrideRejectedError) {
      return { accepted: false, category: error.category };
    }
    return { accepted: false, category: 'unknown' };
  }
}

async function proveUserDataNegativeProofs() {
  const installRoot = EVIDENCE_UNPACKED;
  const resourcesPath = path.join(EVIDENCE_UNPACKED, 'resources');
  const tempDedicated = path.join(os.tmpdir(), `budcom-probe-valid-${Date.now()}`);
  const tempRoot = path.resolve(os.tmpdir());
  const fsRoot = path.parse(tempRoot).root;
  const outsideTemp = path.join(fsRoot, 'budcom-outside-temp-negative-proof');

  const scenarioA = await proveUserDataScenario(
    'marker_absent',
    { BUDCOM_INSTALLED_PROBE_MODE: undefined },
    tempDedicated,
    'probe_mode_required',
  );

  const invalidCases = [
    { label: 'filesystem_root', path: fsRoot, expected: 'outside_probe_temp_root' },
    { label: 'install_directory', path: installRoot, expected: 'install_path_rejected' },
    { label: 'resources_directory', path: resourcesPath, expected: 'resources_path_rejected' },
    { label: 'os_temp_root', path: tempRoot, expected: null },
    { label: 'outside_os_temp', path: outsideTemp, expected: 'outside_probe_temp_root' },
    { label: 'relative_path', path: 'probe-relative-subdir', expected: 'relative_path_rejected' },
  ];

  const scenarioB = [];
  for (const testCase of invalidCases) {
    const classification = await classifyRejectedPath(testCase.path, installRoot, resourcesPath);
    const observed = await proveUserDataScenario(
      `invalid_probe_path_${testCase.label}`,
      { BUDCOM_INSTALLED_PROBE_MODE: '1' },
      testCase.path,
      testCase.expected ?? classification.category,
    );
    scenarioB.push({
      ...observed,
      validationCategory: classification.category,
      validationAccepted: classification.accepted,
    });
  }

  return {
    scenarioA,
    scenarioB,
    pass: scenarioA.pass && scenarioB.every((entry) => {
      if (entry.label === 'invalid_probe_path_os_temp_root' && entry.validationAccepted) {
        return entry.desktopAlive && entry.rendererAvailable;
      }
      return entry.pass;
    }),
  };
}

async function proveHostileNetwork() {
  const proofRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-neg-network-'));
  const copyRoot = path.join(proofRoot, 'app-copy');
  copyDirRecursive(EVIDENCE_UNPACKED, copyRoot);
  const appExe = path.join(copyRoot, 'Budcom Desktop.exe');
  const userDataDir = path.join(proofRoot, 'user-data');
  fs.mkdirSync(userDataDir, { recursive: true });
  const connectorPort = await pickEphemeralPort();
  const correlationId = randomUUID();

  const env = buildBaseEnv(userDataDir, connectorPort, correlationId, {
    BUDCOM_INSTALLED_PROBE_MODE: '1',
    BUDCOM_CONNECTOR_HOST: '0.0.0.0',
    BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED: '1',
    HOST: '192.168.1.20',
    BIND_HOST: 'example.com',
    LISTEN_HOST: '::',
    BUDCOM_CONNECTOR_URL: `http://192.168.1.20:${connectorPort}`,
  });

  const observation = await launchPackagedDesktop(appExe, env, {
    testName: 'hostile_network',
    observationMs: 35000,
    preferredLogDir: userDataDir,
    earlyExit: (entries) => entries.some((entry) => entry.stage === 'connector_spawned')
      && entries.some((entry) => entry.stage === 'connector_health_check' && entry.detail?.ready === true),
  });
  const spawnAttempt = observation.startupEntries.find((entry) => entry.stage === 'connector_spawn_attempt');
  const healthBody = observation.healthBody;

  try {
    fs.rmSync(proofRoot, { recursive: true, force: true });
  } catch {
    // best effort
  }

  const bindHost = healthBody?.bindHost ?? null;
  const pass = observation.connectorSpawnedCount > 0
    && bindHost === '127.0.0.1'
    && healthBody?.networkExposure === 'loopback'
    && healthBody?.networkPolicySatisfied === true
    && spawnAttempt?.detail?.port === connectorPort;

  return {
    pass,
    hostileEnv: {
      BUDCOM_CONNECTOR_HOST: '0.0.0.0',
      BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED: '1',
      HOST: '192.168.1.20',
      BIND_HOST: 'example.com',
      LISTEN_HOST: '::',
      BUDCOM_CONNECTOR_URL: 'http://192.168.1.20:9099',
    },
    connectorSpawned: observation.connectorSpawnedCount > 0,
    healthBindHost: bindHost,
    networkExposure: healthBody?.networkExposure ?? null,
    networkPolicySatisfied: healthBody?.networkPolicySatisfied ?? null,
    healthOnLoopbackOnly: bindHost === '127.0.0.1',
    spawnPort: spawnAttempt?.detail?.port ?? null,
    expectedPort: connectorPort,
  };
}

export async function runNegativeProofs(options = {}) {
  registerInterruptCleanup();
  desktopLaunchCount = 0;
  trackedDesktopPids.clear();

  if (!fs.existsSync(path.join(EVIDENCE_UNPACKED, 'Budcom Desktop.exe'))) {
    throw new Error(`Evidence unpacked app missing: ${EVIDENCE_UNPACKED}`);
  }

  const only = options.only ?? 'all';
  const tamperedRuntime = only === 'all' || only === 'tampered'
    ? await proveTamperedRuntime()
    : null;
  const userData = only === 'all' || only === 'userdata'
    ? await proveUserDataNegativeProofs()
    : null;
  const hostileNetwork = only === 'all' || only === 'hostile'
    ? await proveHostileNetwork()
    : null;

  const report = {
    classification: 'precommit_hardening_negative_proofs',
    recordedAt: new Date().toISOString(),
    evidenceSource: 'artifacts-precommit-hardening-20260725-235500/win-unpacked (isolated copies only)',
    harness: {
      maxDesktopLaunches: MAX_DESKTOP_LAUNCHES,
      desktopLaunchesPerformed: desktopLaunchCount,
      boundedSequentialLaunches: true,
      windowsHiddenByDefault: true,
    },
    tamperedRuntime,
    userData,
    hostileNetwork,
    verdict: 'NOT_RUN',
  };

  const results = [tamperedRuntime?.pass, userData?.pass, hostileNetwork?.pass].filter((value) => value !== undefined && value !== null);
  report.verdict = results.every(Boolean) ? 'PASS' : results.some((value) => value === false) ? 'FAIL' : 'PARTIAL';

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  return report;
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (invokedDirectly) {
  const onlyArg = process.argv.find((arg) => arg.startsWith('--only='))?.slice('--only='.length) ?? 'all';
  runNegativeProofs({ only: onlyArg })
    .then((report) => {
      console.log(JSON.stringify(report, null, 2));
      process.exit(report.verdict === 'PASS' ? 0 : 1);
    })
    .catch((error) => {
      console.error('Negative proofs FAIL:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    });
}
