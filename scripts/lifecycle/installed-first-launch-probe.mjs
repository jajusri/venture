#!/usr/bin/env node
/**
 * Installed first-launch probe with ownership verification and single-instance checks.
 */
import { createServer } from 'node:net';
import { spawn, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const reportPath = path.join(repoRoot, 'release/controlled-pilot/0.4.3/reports/installed-first-launch-probe-report.json');

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizePath(value) {
  return path.resolve(value).replace(/\\/g, '/').toLowerCase();
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
      server.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        resolve(port);
      });
    });
    server.on('error', reject);
  });
}

function buildProbeChildEnvironment(userDataDir, connectorPort, correlationId, options = {}) {
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  if (options.skipSingleInstance) {
    env.VENTURE_SKIP_SINGLE_INSTANCE = '1';
  } else {
    delete env.VENTURE_SKIP_SINGLE_INSTANCE;
  }
  env.VENTURE_INSTALLED_PROBE_MODE = '1';
  env.VENTURE_USER_DATA_DIR = userDataDir;
  env.VENTURE_CONNECTOR_URL = `http://127.0.0.1:${connectorPort}`;
  env.VENTURE_CONNECTOR_PORT = String(connectorPort);
  env.VENTURE_CONNECTOR_HOST = '127.0.0.1';
  env.VENTURE_STARTUP_CORRELATION_ID = correlationId;
  return env;
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

function hasOwnedConnectorHealth(startupEntries) {
  return startupEntries.some(
    (entry) => entry.stage === 'connector_health_check' && entry.detail?.ready === true,
  );
}

function hasStartupReadySignal(entries) {
  return entries.some((entry) => entry.stage === 'renderer_loaded' || entry.stage === 'ready_to_show');
}

function queryProcesses(filterCommand) {
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    filterCommand,
  ], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '[]');
    return Array.isArray(parsed) ? parsed : [parsed].filter(Boolean);
  } catch {
    return [];
  }
}

function listVentureDesktopProcesses() {
  return queryProcesses(`@(
    Get-CimInstance Win32_Process |
    Where-Object { $_.Name -eq 'Venture Desktop.exe' } |
    Select-Object ProcessId, ParentProcessId, Name, CommandLine
  ) | ConvertTo-Json -Compress`);
}

function getDesktopProcessTree(rootPid) {
  return queryProcesses(`@(
    Get-CimInstance Win32_Process |
    Where-Object { $_.ProcessId -eq ${rootPid} -or $_.ParentProcessId -eq ${rootPid} } |
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

async function waitForHealth(url, correlationId, timeoutMs = 45000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (response.ok) {
        const body = await response.json();
        if (body.bindPort === undefined || body.startupCorrelationId !== correlationId) {
          await sleep(1000);
          continue;
        }
        return { ready: true, status: response.status, body };
      }
    } catch {
      // retry
    }
    await sleep(1000);
  }
  return { ready: false, status: null, body: null };
}

function killProcessTree(rootPid, trackedPids = []) {
  if (rootPid) {
    spawnSync('taskkill', ['/PID', String(rootPid), '/T', '/F'], { stdio: 'ignore' });
  }
  for (const pid of trackedPids) {
    if (pid && pid !== rootPid) {
      spawnSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
    }
  }
}

function verifyConnectorOwnership(startupEntries, installRoot, connectorPort, correlationId, processTree) {
  const spawnAttempt = startupEntries.find((entry) => entry.stage === 'connector_spawn_attempt');
  const spawned = startupEntries.find((entry) => entry.stage === 'connector_spawned');
  const healthCheck = startupEntries.find((entry) => entry.stage === 'connector_health_check' && entry.detail?.ready === true);

  const expectedNode = normalizePath(path.join(installRoot, 'resources', 'node', 'node.exe'));
  const expectedScript = normalizePath(path.join(installRoot, 'resources', 'connector', 'dist', 'main.js'));

  const spawnCommand = spawnAttempt?.detail?.command ? normalizePath(String(spawnAttempt.detail.command)) : null;
  const spawnScript = spawnAttempt?.detail?.script ? normalizePath(String(spawnAttempt.detail.script)) : null;
  const spawnPort = spawnAttempt?.detail?.port ?? null;
  const spawnCorrelation = spawnAttempt?.detail?.startupCorrelationId ?? null;

  const connectorPid = spawned?.detail?.pid ?? null;
  const connectorInTree = connectorPid
    ? processTree.some((proc) => proc.ProcessId === connectorPid)
    : false;

  const connectorProc = connectorPid
    ? processTree.find((proc) => proc.ProcessId === connectorPid)
    : null;
  const connectorCommand = connectorProc?.CommandLine ?? '';
  const commandUsesPackagedNode = normalizePath(connectorCommand).includes(expectedNode);
  const commandUsesPackagedScript = normalizePath(connectorCommand).includes(expectedScript);

  return {
    hasSpawnAttempt: Boolean(spawnAttempt),
    hasSpawned: Boolean(spawned),
    hasOwnedHealthCheck: Boolean(healthCheck),
    spawnCommandMatchesNode: spawnCommand === expectedNode,
    spawnScriptMatches: spawnScript === expectedScript,
    spawnPortMatches: spawnPort === connectorPort,
    spawnCorrelationMatches: spawnCorrelation === correlationId,
    connectorPid,
    connectorInTree,
    commandUsesPackagedNode,
    commandUsesPackagedScript,
    expectedNode,
    expectedScript,
  };
}

async function runSecondInstanceCheck(appExe, userDataDir, connectorPort, correlationId, firstDesktopPid) {
  const childEnv = buildProbeChildEnvironment(userDataDir, connectorPort, correlationId, { skipSingleInstance: false });
  const second = spawn(appExe, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: false,
    env: childEnv,
  });
  second.unref();
  await sleep(5000);

  const startupLogPath = path.join(userDataDir, 'logs', 'startup-diagnostics.jsonl');
  const entries = readStartupDiagnostics(startupLogPath);
  const denied = entries.some((entry) => entry.stage === 'single_instance_denied_exit');
  const secondAlive = second.pid ? isProcessAlive(second.pid) : false;
  const firstAlive = isProcessAlive(firstDesktopPid);

  if (second.pid && secondAlive) {
    killProcessTree(second.pid);
  }

  return {
    secondDesktopPid: second.pid ?? null,
    secondDesktopAlive: secondAlive,
    firstDesktopAlive: firstAlive,
    singleInstanceDenied: denied,
    pass: firstAlive && (!secondAlive || denied),
  };
}

export async function runInstalledFirstLaunchProbe(options = {}) {
  const installerPath = options.installerPath
    ?? path.join(repoRoot, 'release/controlled-pilot/0.4.3/artifacts-runtime-closure-v2/VentureDesktop-0.4.3-x64-setup.exe');
  if (!fs.existsSync(installerPath)) {
    throw new Error(`Installer not found: ${installerPath}`);
  }

  const preExistingDesktop = listVentureDesktopProcesses();
  const profileRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-probe-profile-'));
  const userDataDir = path.join(profileRoot, 'user-data');
  const installRoot = path.join(profileRoot, 'install');
  fs.mkdirSync(installRoot, { recursive: true });

  const connectorPort = options.connectorPort ?? await pickEphemeralPort();
  const correlationId = options.correlationId ?? crypto.randomUUID();
  const trackedPids = [];

  const install = spawnSync(installerPath, ['/S', `/D=${installRoot}`], { encoding: 'utf8' });
  const appExe = path.join(installRoot, 'Venture Desktop.exe');
  if ((install.status ?? 1) !== 0 || !fs.existsSync(appExe)) {
    throw new Error(`Installation failed with exit code ${install.status ?? 1}`);
  }

  const startupLogPath = path.join(userDataDir, 'logs', 'startup-diagnostics.jsonl');
  const childEnv = buildProbeChildEnvironment(userDataDir, connectorPort, correlationId, { skipSingleInstance: false });

  const child = spawn(appExe, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: false,
    env: childEnv,
  });
  child.unref();
  const desktopPid = child.pid ?? null;
  if (desktopPid) {
    trackedPids.push(desktopPid);
  }

  const observationMs = options.observationMs ?? 60000;
  const started = Date.now();
  let desktopAlive = false;

  while (Date.now() - started < observationMs) {
    if (desktopPid && !isProcessAlive(desktopPid)) {
      desktopAlive = false;
      break;
    }
    desktopAlive = desktopPid ? isProcessAlive(desktopPid) : false;
    const startupEntries = readStartupDiagnostics(startupLogPath);
    if (hasStartupReadySignal(startupEntries) && hasOwnedConnectorHealth(startupEntries)) {
      await sleep(2000);
      break;
    }
    await sleep(2000);
  }

  desktopAlive = desktopPid ? isProcessAlive(desktopPid) : false;
  const startupEntries = readStartupDiagnostics(startupLogPath);
  const processTree = desktopPid ? getDesktopProcessTree(desktopPid) : [];
  for (const proc of processTree) {
    if (proc.ProcessId) {
      trackedPids.push(proc.ProcessId);
    }
  }

  const health = await waitForHealth(`http://127.0.0.1:${connectorPort}/health`, correlationId, 15000);
  const ownership = verifyConnectorOwnership(startupEntries, installRoot, connectorPort, correlationId, processTree);

  let secondInstance = null;
  if (desktopAlive && desktopPid) {
    secondInstance = await runSecondInstanceCheck(appExe, userDataDir, connectorPort, correlationId, desktopPid);
  }

  killProcessTree(desktopPid, trackedPids);
  await sleep(3000);

  const postDesktop = listVentureDesktopProcesses().filter((proc) => !preExistingDesktop.some((existing) => existing.ProcessId === proc.ProcessId));
  const orphanDesktopCount = postDesktop.length;

  const verdict = (
    !preExistingDesktop.length
    && desktopAlive
    && hasStartupReadySignal(startupEntries)
    && ownership.hasSpawnAttempt
    && ownership.hasSpawned
    && ownership.hasOwnedHealthCheck
    && ownership.spawnCommandMatchesNode
    && ownership.spawnScriptMatches
    && ownership.spawnPortMatches
    && ownership.spawnCorrelationMatches
    && ownership.connectorInTree
    && ownership.commandUsesPackagedNode
    && ownership.commandUsesPackagedScript
    && health.ready
    && health.body?.bindPort === connectorPort
    && health.body?.startupCorrelationId === correlationId
    && orphanDesktopCount === 0
    && (secondInstance?.pass ?? false)
  ) ? 'PASS' : 'FAIL';

  const report = {
    classification: 'focused_installed_first_launch_probe',
    installerPath,
    installerSha256: sha256File(installerPath),
    profileRoot,
    userDataDir,
    installRoot,
    connectorPort,
    correlationId,
    preExistingDesktop,
    preExistingBlocked: preExistingDesktop.length > 0,
    desktopPid,
    desktopAlive,
    startupLogPath,
    startupEntries,
    startupReady: hasStartupReadySignal(startupEntries),
    ownership,
    health,
    processTree,
    secondInstance,
    orphanDesktop: orphanDesktopCount,
    verdict,
  };

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

  try {
    fs.rmSync(profileRoot, { recursive: true, force: true });
  } catch {
    // best effort cleanup
  }

  return report;
}

if (import.meta.url === pathToFileURL(path.resolve(process.argv[1] ?? '')).href) {
  runInstalledFirstLaunchProbe({ installerPath: process.argv[2] })
    .then((report) => {
      console.log(JSON.stringify(report, null, 2));
      if (report.verdict !== 'PASS') {
        process.exit(1);
      }
    })
    .catch((error) => {
      console.error('Installed first-launch probe FAIL:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    });
}
