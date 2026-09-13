import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

export const DEFAULT_CONNECTOR_PORT = 8080;

export function redactLine(line) {
  return line
    .replace(/[A-Z]:\\Users\\[^\\]+/gi, '%USERPROFILE%')
    .replace(/\/Users\/[^\s/]+/g, '/Users/%USER%')
    .replace(/[A-Z]:\\[^\s"]+/gi, (match) => `%PATH:${path.basename(match)}%`);
}

export function resolveConnectorPort(configPath, fallbackPort = DEFAULT_CONNECTOR_PORT) {
  if (configPath && fs.existsSync(configPath)) {
    try {
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      if (config.connectorPort) {
        return config.connectorPort;
      }
      if (config.effective?.connectorPort) {
        return config.effective.connectorPort;
      }
    } catch {
      // fall through
    }
  }
  return fallbackPort;
}

export async function waitForLogFile(logsDir, fileName = 'venture-desktop.log', timeoutMs = 45000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const logPath = path.join(logsDir, fileName);
    if (fs.existsSync(logPath) && fs.statSync(logPath).size > 0) {
      return logPath;
    }
    await sleep(1000);
  }
  return null;
}

export function readPrivacySafeStartupLogs(logPath, maxLines = 80) {
  if (!logPath || !fs.existsSync(logPath)) {
    return {
      logPath: null,
      lines: [],
      mainProcessException: null,
      connectorStartupFailure: null,
      connectorExitEvidence: null,
      empty: true,
    };
  }
  const lines = fs.readFileSync(logPath, 'utf8').split(/\r?\n/).slice(-maxLines).map(redactLine);
  const mainProcessException = lines.find((line) =>
    /SyntaxError|Cannot use import statement outside a module|uncaughtException|\[venture-desktop:startup\] uncaughtException/i.test(line),
  ) ?? null;
  const connectorStartupFailure = lines.find((line) =>
    /\[lifecycle:startup_failure\]|Fatal bootstrap error|connector process exited/i.test(line),
  ) ?? null;
  const connectorExitEvidence = lines.find((line) =>
    /connector.*exit|exit code|signal SIG/i.test(line),
  ) ?? null;
  return {
    logPath,
    lines: lines.filter((line) => !/password|gstin|voucher|<\?xml/i.test(line)),
    mainProcessException,
    connectorStartupFailure,
    connectorExitEvidence,
    empty: lines.length === 0,
  };
}

export async function waitForConnectorHealth(baseUrl, timeoutMs = 45000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(`${baseUrl}/health`);
      if (response.ok) {
        const body = await response.json();
        return { ready: true, status: response.status, body };
      }
    } catch {
      // retry
    }
    await sleep(1000);
  }
  return { ready: false, status: null, body: null };
}

export function isPortListening(port) {
  const result = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `(Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).LocalPort`,
  ], { encoding: 'utf8' });
  return String(result.stdout).trim() === String(port);
}

export function getConnectorProcesses() {
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `@(
      Get-CimInstance Win32_Process |
      Where-Object {
        $_.Name -ne 'powershell.exe' -and
        $_.Name -ne 'pwsh.exe' -and
        ($_.CommandLine -like '*connector*dist*main.js*' -or $_.CommandLine -like '*resources*connector*main.js*')
      } |
      Select-Object ProcessId, CommandLine
    ) | ConvertTo-Json -Compress`,
  ], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '[]');
    return Array.isArray(parsed) ? parsed : [parsed].filter(Boolean);
  } catch {
    return [];
  }
}

export function getDesktopProcesses() {
  const ps = spawnSync('powershell', [
    '-NoProfile',
    '-Command',
    `@(
      Get-CimInstance Win32_Process |
      Where-Object { $_.Name -eq 'Venture Desktop.exe' } |
      Select-Object ProcessId, Name, CommandLine
    ) | ConvertTo-Json -Compress`,
  ], { encoding: 'utf8' });
  try {
    const parsed = JSON.parse(ps.stdout.trim() || '[]');
    return Array.isArray(parsed) ? parsed : [parsed].filter(Boolean);
  } catch {
    return [];
  }
}

export function classifyConnectorCommandLine(commandLine) {
  const line = String(commandLine ?? '');
  return {
    usesPackagedConnectorScript: /connector[\\/]+dist[\\/]+main\.js/i.test(line) || /resources[\\/]+connector/i.test(line),
    usesElectronRunAsNode: /ELECTRON_RUN_AS_NODE=1/i.test(line) || (!/node\.exe/i.test(line) && /Venture Desktop\.exe/i.test(line)),
    usesRepositoryPath: /Projects[\\/]+Venture/i.test(line),
    spawnViaElectronExe: /Venture Desktop\.exe/i.test(line),
  };
}

export function buildStartupEvidence(input) {
  const {
    desktopAlive,
    connectorProcesses,
    connectorPort,
    portListening,
    health,
    logs,
  } = input;
  const connectorSpawned = connectorProcesses.length > 0;
  const connectorListening = portListening || health.ready;
  const connectorCrashed = connectorSpawned && !connectorListening && !health.ready
    && (logs.connectorStartupFailure !== null || logs.connectorExitEvidence !== null);
  return {
    desktopProcessAlive: desktopAlive,
    connectorSpawned,
    connectorListening,
    healthEndpointReady: health.ready,
    connectorCrashed,
    startupLogPresent: logs.logPath !== null && !logs.empty,
    mainProcessException: logs.mainProcessException,
    connectorStartupFailure: logs.connectorStartupFailure,
    connectorExitEvidence: logs.connectorExitEvidence,
  };
}

export function classifyHealthResponse(body) {
  if (!body || typeof body !== 'object') {
    return { privacySafe: false, loopbackOnly: false, readOnly: null, tallyRequired: null };
  }
  const serialized = JSON.stringify(body);
  const privacySafe = !/gstin|voucher|<\?xml|password|ledger name|company name/i.test(serialized);
  const loopbackOnly = body.bindHost === '127.0.0.1' || body.bindHost === 'localhost';
  return {
    privacySafe,
    loopbackOnly,
    readOnly: body.readOnly === true,
    tallyReachable: body.tallyReachable === false || body.tallyReachable === true ? body.tallyReachable : null,
    status: body.status ?? null,
    bindHost: body.bindHost ?? null,
    bindPort: body.bindPort ?? null,
  };
}

export function deriveFirstLaunchVerdict(input) {
  const failures = [];
  if (!input.desktopAlive) failures.push('desktop-not-alive');
  if (input.logs?.mainProcessException) failures.push('main-process-exception');
  if (input.connectorProcesses?.length !== 1) failures.push('connector-process-count');
  if (!input.connectorAlive) failures.push('connector-not-alive');
  if (!input.health?.ready) failures.push('health-not-ready');
  if (input.health?.ready && input.health.status !== 200) failures.push('health-non-200');
  if (input.healthClassification && !input.healthClassification.privacySafe) failures.push('health-not-privacy-safe');
  if (input.healthClassification && !input.healthClassification.loopbackOnly) failures.push('health-not-loopback');
  if (input.schemaBefore !== 7) failures.push('schema-before-not-7');
  if (input.schemaAfter !== 8) failures.push('schema-not-migrated-to-8');
  if (input.orphanDesktop > 0 || input.orphanConnector > 0) failures.push('orphan-process-after-shutdown');
  if (!input.startupEvidence?.startupLogPresent) failures.push('startup-log-missing');
  if (input.connectorDiagnostics?.some((row) => row.classification.usesRepositoryPath)) failures.push('repository-path-spawn');
  if (input.connectorDiagnostics?.some((row) => !row.classification.usesPackagedConnectorScript)) failures.push('non-packaged-connector-script');
  if (input.reopenHealth && !input.reopenHealth.ready) failures.push('reopen-health-not-ready');
  if (input.schemaAfterReopen !== 8) failures.push('reopen-schema-not-8');
  if (input.reopenConnectorCount > 1) failures.push('reopen-duplicate-connector');
  return { verdict: failures.length === 0 ? 'PASS' : 'FAIL', failures };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export { sleep };
