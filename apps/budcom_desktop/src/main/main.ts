import { app, BrowserWindow, ipcMain, shell } from 'electron';
import crypto from 'node:crypto';
import path from 'node:path';

import {
  applyUserDataDirOverride,
  sanitizeDesktopProcessEnvironment,
} from '../application/release/startup-environment.js';
import { CompanyService } from '../application/company-service.js';
import { LedgerService } from '../application/ledger-service.js';
import { StockItemService } from '../application/stock-item-service.js';
import { DiagnosticsService } from '../application/diagnostics-service.js';
import { DiagnosticExportRetentionService } from '../application/diagnostic-export-retention-service.js';
import { DesktopConfigTempReconciliationService } from '../application/desktop-config-temp-reconciliation-service.js';
import { getEnvironmentDefaults } from '../application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../application/desktop-config-paths.js';
import { DesktopConfigStore } from '../application/desktop-config-store.js';
import {
  ConnectorLifecycleService,
  HttpHealthChecker,
} from '../application/connector-lifecycle-service.js';
import { DashboardService, DESKTOP_WINDOW_TITLE } from '../application/dashboard-service.js';
import { ConnectorIdentityStore } from '../application/connector-identity-store.js';
import { PowerShellRouteQuerier } from '../application/network/route-querier.js';
import {
  evaluateTrustedLanEligibility,
  resolveActiveNetworkAdapter,
  type ActiveNetworkAdapter,
  type ActiveNetworkResolution,
} from '../application/network/active-network-resolver.js';
import { NetworkChangeWatcher } from '../application/network/network-change-watcher.js';
import { applyRouteBackedHost } from '../application/network/route-backed-lifecycle-override.js';
import {
  TrustedLanRebindCoordinator,
  type TrustedLanBindStatus,
} from '../application/network/trusted-lan-rebind-coordinator.js';
import { CrashRecoveryScheduler } from '../application/network/crash-recovery-scheduler.js';
import type { ConnectorLifecycleStatus } from '../application/connector-lifecycle-types.js';
import { MobileAccessStatusService, type RebindState } from '../application/mobile-access-status-service.js';
import { FileLogWriter } from '../application/file-log-writer.js';
import {
  assertAllowedIpcChannel,
  assertBoundedIpcPayload,
  validateCompanyId,
  validateExportDirectory,
  validateLedgerQuery,
  validateStockItemQuery,
  validateSettingsInput,
  validateSyncOptions,
} from '../application/ipc-allowlist.js';
import { LogService } from '../application/log-service.js';
import { NodeProcessSpawner } from '../application/node-process-spawner.js';
import { RecoveryService } from '../application/recovery-service.js';
import { SettingsService } from '../application/settings-service.js';
import { ensureAppDataDirectories, resolveAppDataLayout } from '../application/release/app-data-layout.js';
import { loadBuildInfo, formatBuildInfoForDiagnostics } from '../application/release/build-info.js';
import { resolveReleaseMode, ReleaseMode } from '../application/release/release-mode.js';
import { bindSecondInstanceFocus, requestDesktopSingleInstance } from '../application/release/single-instance.js';
import { StartupDiagnostics } from '../application/release/startup-diagnostics.js';

sanitizeDesktopProcessEnvironment();

if (!process.env.BUDCOM_STARTUP_CORRELATION_ID?.trim()) {
  process.env.BUDCOM_STARTUP_CORRELATION_ID = crypto.randomUUID();
}
const startupCorrelationId = process.env.BUDCOM_STARTUP_CORRELATION_ID.trim();

const skipSingleInstance = process.env.VITEST === 'true' || process.env.BUDCOM_SKIP_SINGLE_INSTANCE === 'true';

function startupLog(stage: string, detail?: string): void {
  const suffix = detail ? ` — ${detail}` : '';
  console.error(`[budcom-desktop:startup] ${stage}${suffix}`);
}

function createStartupDiagnostics(): StartupDiagnostics {
  const logsDir = path.join(app.getPath('userData'), 'logs');
  return new StartupDiagnostics({ logsDir });
}

const userDataOverride = applyUserDataDirOverride(process.env, app, process.argv, {
  installRoot: app.isPackaged ? path.dirname(process.execPath) : null,
  resourcesPath: process.resourcesPath ?? null,
});
const startupDiagnostics = createStartupDiagnostics();

process.on('uncaughtException', (error) => {
  startupDiagnostics.record('uncaught_exception', {
    message: error.message,
    name: error.name,
  });
  startupLog('uncaughtException', error.stack ?? error.message);
});

process.on('unhandledRejection', (reason) => {
  const detail = reason instanceof Error ? reason.stack ?? reason.message : String(reason);
  startupDiagnostics.record('unhandled_rejection', { detail });
  startupLog('unhandledRejection', detail);
});

startupDiagnostics.record('process_start', {
  pid: process.pid,
  execPath: process.execPath,
  packaged: app.isPackaged,
  platform: process.platform,
  arch: process.arch,
  electronVersion: process.versions.electron ?? null,
  nodeVersion: process.versions.node,
  resourcesPath: process.resourcesPath ?? null,
  userDataPath: app.getPath('userData'),
  userDataOverride: userDataOverride,
  electronRunAsNodeStripped: process.env.ELECTRON_RUN_AS_NODE === undefined,
  startupCorrelationId,
});

if (!skipSingleInstance) {
  const singleInstance = requestDesktopSingleInstance(app);
  startupDiagnostics.record('single_instance_lock', {
    acquired: singleInstance.acquired,
    shouldQuit: singleInstance.shouldQuit,
  });
  if (singleInstance.shouldQuit) {
    startupDiagnostics.record('single_instance_denied_exit', { exitCode: 0 });
    startupDiagnostics.flush();
    process.exit(0);
  }
}

startupDiagnostics.record('main_module_loaded', {
  requireMainIsModule: require.main === module,
});

const buildInfo = loadBuildInfo();
const releaseMode = resolveReleaseMode({
  buildInfoMode: buildInfo.releaseMode,
  isPackaged: app.isPackaged,
});
const isDevelopment = releaseMode === ReleaseMode.Development;

let mainWindow: BrowserWindow | null = null;

startupLog('main module loaded', `require.main === module: ${require.main === module}`);

const startedAt = Date.now();

startupDiagnostics.record('release_mode_resolved', {
  releaseMode,
  isDevelopment,
  appVersion: app.getVersion(),
});

const appDataLayout = resolveAppDataLayout({
  userDataDir: app.getPath('userData'),
  isPackaged: app.isPackaged,
  installRoot: app.isPackaged ? path.dirname(app.getPath('exe')) : null,
});
ensureAppDataDirectories(appDataLayout);
startupDiagnostics.record('app_data_ready', {
  userDataRoot: appDataLayout.userDataRoot,
  logsDir: appDataLayout.logsDir,
  connectorDataDir: appDataLayout.connectorDataDir,
});

const configPaths = resolveDesktopConfigPaths(appDataLayout.userDataRoot);
const fileLogWriter = new FileLogWriter({ logsDir: configPaths.logsDir });
const logService = new LogService({
  fileWriter: fileLogWriter,
  minimumLevel: isDevelopment ? 'debug' : 'info',
  consoleEnabled: isDevelopment,
});
const configTempReconciliationService = new DesktopConfigTempReconciliationService({
  log: (input) => logService.appendStructured(input),
});

function runDesktopConfigTempReconciliation(): void {
  try {
    configTempReconciliationService.reconcile({ paths: configPaths });
  } catch {
    // Reconciliation failure must not block startup.
  }
}

runDesktopConfigTempReconciliation();

const configStore = new DesktopConfigStore({
  paths: configPaths,
  defaults: getEnvironmentDefaults(isDevelopment),
});
const recoveryService = new RecoveryService(logService);
const configLoadResult = configStore.loadFromDisk();
recoveryService.handleConfigLoad(configLoadResult);

// Stable Connector identity — generated once, persisted in Desktop's private configuration,
// never derived from IP/MAC/username/machine name. Survives restarts, DHCP/Wi-Fi changes, and
// application upgrades because this file is never touched by any of those.
const connectorIdentityStore = new ConnectorIdentityStore({ userDataDir: appDataLayout.userDataRoot });
const connectorIdentity = connectorIdentityStore.getOrCreateIdentity();

const settingsService = new SettingsService({
  configStore,
  logService,
  isDevelopment,
  connectorExecutable: process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? process.execPath,
  lifecycleContext: {
    isPackaged: app.isPackaged,
    resourcesPath: process.resourcesPath,
    connectorDatabaseDir: appDataLayout.connectorDatabaseDir,
    connectorId: connectorIdentity.connectorId,
  },
});
settingsService.setConfigStatus(configLoadResult.status);

let resolved = settingsService.getResolvedConfig();
logService.appendStructured({
  level: 'information',
  message: 'Connector lifecycle configuration resolved.',
  event: 'connector_lifecycle_config_resolved',
  component: 'startup',
  metadata: {
    configSource: Object.keys(resolved.sources).length > 0 ? 'mixed (env + persisted)' : 'persisted/default',
    bindMode: resolved.effective.connectorBindMode,
    bindHost: resolved.lifecycleConfig.connectorHost,
    bindPort: resolved.lifecycleConfig.connectorPort,
    reachableLanUrl: resolved.effective.connectorBindMode === 'trusted-lan'
      ? `http://${resolved.lifecycleConfig.connectorHost}:${resolved.lifecycleConfig.connectorPort}`
      : null,
    connectorExecutable: resolved.lifecycleConfig.connectorExecutable,
    connectorScript: resolved.lifecycleConfig.connectorArgs[0] ?? null,
  },
});
let dashboardService = createDashboardService(resolved.connectorBaseUrl);
let companyService = createCompanyService(resolved.connectorBaseUrl);
let ledgerService = createLedgerService(resolved.connectorBaseUrl);
let stockItemService = createStockItemService(resolved.connectorBaseUrl);

// Route-backed network state. activeNetworkAdapter feeds computeEffectiveLifecycleConfig() so a
// trusted-LAN Connector is always spawned bound to the current default-route adapter, never a
// stale hand-typed IP. desktopStartupComplete guards the very first network resolution (which
// naturally differs from "no prior state") from triggering a redundant rebind before the initial
// lifecycleService.initialize() has even run once.
let activeNetworkAdapter: ActiveNetworkAdapter | null = null;
let lastNetworkResolution: ActiveNetworkResolution = { adapter: null, rejectedReason: null };
let rebindState: RebindState = { status: 'idle', at: null, message: null };
let desktopStartupComplete = false;

function computeEffectiveLifecycleConfig() {
  return applyRouteBackedHost(resolved.lifecycleConfig, activeNetworkAdapter);
}

let lifecycleService = createLifecycleService();
const diagnosticExportRetentionService = new DiagnosticExportRetentionService({
  log: (input) => logService.appendStructured(input),
});
let diagnosticsService = createDiagnosticsService();

function runDiagnosticExportRetentionCleanup(): void {
  try {
    diagnosticExportRetentionService.cleanup({
      exportDir: configPaths.diagnosticsExportDir,
      retentionDays: settingsService.getResolvedConfig().effective.diagnosticsRetentionDays,
    });
  } catch {
    // Cleanup failure must not block startup.
  }
}

function createDashboardService(baseUrl: string): DashboardService {
  return new DashboardService({
    connectorBaseUrl: baseUrl,
    logService,
  });
}

function createCompanyService(baseUrl: string): CompanyService {
  return new CompanyService({
    connectorBaseUrl: baseUrl,
    logService,
  });
}

function createLedgerService(baseUrl: string): LedgerService {
  return new LedgerService({
    connectorBaseUrl: baseUrl,
    logService,
  });
}

function createStockItemService(baseUrl: string): StockItemService {
  return new StockItemService({
    connectorBaseUrl: baseUrl,
    logService,
  });
}

function createLifecycleService(): ConnectorLifecycleService {
  const config = computeEffectiveLifecycleConfig();
  const isTrustedLan = resolved.effective.connectorBindMode === 'trusted-lan';
  const service = new ConnectorLifecycleService({
    config,
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(config.connectorBaseUrl, undefined, {
      expectedPort: config.connectorPort,
      expectedCorrelationId: config.startupCorrelationId,
    }),
    logService,
    // Trusted-LAN children have no self-restart authority — an unexpected exit or failed health
    // check only notifies handleUnexpectedConnectorExit, which asks trustedLanCoordinator to
    // recover (fresh network resolve + eligibility check) rather than restarting directly.
    // Local-only keeps its original bounded internal reconnect loop (loopback-only, no policy).
    restartOwnership: isTrustedLan ? 'external' : 'internal',
    onUnexpectedExit: isTrustedLan ? handleUnexpectedConnectorExit : undefined,
    onDiagnostic: (stage, detail) => {
      startupDiagnostics.record(stage, detail);
    },
  });
  service.setStatusListener(() => {
    notifyRenderer();
  });
  return service;
}

/**
 * Fired when a trusted-LAN Connector child exits unexpectedly or a health check fails in a way
 * that would previously have triggered a direct self-restart. Delegates bounded backoff/attempt
 * limiting to crashRecoveryScheduler, which asks trustedLanCoordinator to recover — re-resolving
 * the network and re-evaluating Public/unknown-profile eligibility fresh before ever starting a
 * replacement child, exactly like a manual Start.
 */
function handleUnexpectedConnectorExit(): void {
  crashRecoveryScheduler.scheduleRecovery();
}

function createDiagnosticsService(): DiagnosticsService {
  return new DiagnosticsService({
    desktopVersion: settingsService.getSettingsState().desktopVersion,
    electronVersion: process.versions.electron,
    configStore,
    resolvedConfig: resolved,
    configStatus: settingsService.getConfigStatus(),
    dashboardService,
    lifecycleService,
    logService,
    exportDir: configPaths.diagnosticsExportDir,
    startedAt,
    retentionService: diagnosticExportRetentionService,
  });
}

/** Stops whatever trusted-LAN child is currently running. Safe to call when nothing is running. */
async function stopManagedLanChild(): Promise<void> {
  await lifecycleService.shutdown();
  diagnosticsService = createDiagnosticsService();
}

/**
 * Starts a trusted-LAN child for an already-eligibility-checked resolution. Only ever invoked by
 * trustedLanCoordinator, which is the sole place that decision is made — see its class doc.
 */
async function startManagedLanChildFor(resolution: ActiveNetworkResolution): Promise<void> {
  activeNetworkAdapter = resolution.adapter;
  dashboardService = createDashboardService(resolved.connectorBaseUrl);
  companyService = createCompanyService(resolved.connectorBaseUrl);
  ledgerService = createLedgerService(resolved.connectorBaseUrl);
  stockItemService = createStockItemService(resolved.connectorBaseUrl);
  lifecycleService = createLifecycleService();
  diagnosticsService = createDiagnosticsService();
  await lifecycleService.initialize();
}

/**
 * Re-resolves the active-route adapter fresh from Windows, bypassing whatever the periodic
 * NetworkChangeWatcher last cached. Used only by manual Start/Restart so a button click is judged
 * against the network as it is right now — not a value up to one poll interval (5s) stale — and
 * updates the same module-level bookkeeping handleNetworkChange keeps, so status displays stay
 * consistent regardless of which trigger last ran.
 */
async function resolveCurrentNetworkFresh(): Promise<ActiveNetworkResolution> {
  const adapters = await routeQuerier.queryAdapters();
  const resolution = resolveActiveNetworkAdapter(adapters);
  activeNetworkAdapter = resolution.adapter;
  lastNetworkResolution = resolution;
  return resolution;
}

/**
 * The single authoritative owner of trusted-LAN bind/rebind decisions: evaluates Public/unknown
 * profile refusal (evaluateTrustedLanEligibility) and guarantees at most one stop/start
 * transition runs at a time, with a newer network resolution always winning over a slower older
 * one. See TrustedLanRebindCoordinator's class doc for the concurrency guarantees. This is the
 * only object permitted to mutate the trusted-LAN child; the manual Start/Stop/Restart IPC
 * handlers route through it instead of touching lifecycleService directly.
 */
const trustedLanCoordinator = new TrustedLanRebindCoordinator({
  stopCurrentChild: stopManagedLanChild,
  startChildFor: startManagedLanChildFor,
  resolveCurrentNetwork: resolveCurrentNetworkFresh,
  onStatusChanged: (status) => {
    if (status.kind === 'connected') {
      rebindState = { status: 'succeeded', at: new Date().toISOString(), message: null };
      // A genuinely healthy connection — via any trigger, not just crash recovery — proves the
      // crash-loop (if any) is over. Give a fresh bound of retries to any future, unrelated crash.
      crashRecoveryScheduler.notifyRecovered();
    } else if (status.kind === 'blocked') {
      rebindState = { status: 'blocked', at: new Date().toISOString(), message: status.reason };
    } else if (status.kind === 'stopped') {
      rebindState = { status: 'idle', at: new Date().toISOString(), message: 'Stopped by user.' };
      // A deliberate stop ends any in-progress crash-recovery context.
      crashRecoveryScheduler.notifyStoppedIntentionally();
    } else {
      rebindState = { status: 'failed', at: new Date().toISOString(), message: status.message };
    }
    notifyRenderer();
  },
});

/**
 * Bounded backoff/attempt-limit policy for trusted-LAN crash recovery — see the class doc for why
 * this must live here rather than inside ConnectorLifecycleService. Reused for every unexpected
 * exit regardless of which ConnectorLifecycleService instance reports it.
 */
const crashRecoveryScheduler = new CrashRecoveryScheduler({
  getMaxAttempts: () => computeEffectiveLifecycleConfig().maxRestartAttempts,
  getBaseDelayMs: () => computeEffectiveLifecycleConfig().reconnectBaseDelayMs,
  getGeneration: () => trustedLanCoordinator.getGeneration(),
  recover: () => {
    void trustedLanCoordinator.recoverFromUnexpectedExit();
  },
  onExhausted: () => {
    rebindState = {
      status: 'failed',
      at: new Date().toISOString(),
      message: 'The Connector crashed repeatedly and reached the maximum number of restart attempts.',
    };
    notifyRenderer();
  },
});

/**
 * Maps a coordinator result to the pre-existing ConnectorLifecycleStatus shape the renderer
 * already understands (no IPC protocol/type change): base fields come from the real
 * lifecycleService.getStatus() read (a non-mutating call), with state/userMessage overridden to
 * reflect the coordinator's authoritative outcome for blocked/failed/stopped results.
 */
function toLifecycleStatusForManualCommand(status: TrustedLanBindStatus): ConnectorLifecycleStatus {
  const base = lifecycleService.getStatus();
  if (status.kind === 'blocked') {
    return { ...base, state: 'disconnected', stateLabel: 'Disconnected', userMessage: status.reason };
  }
  if (status.kind === 'stopped') {
    return { ...base, state: 'disconnected', stateLabel: 'Disconnected', userMessage: null };
  }
  if (status.kind === 'failed') {
    return { ...base, userMessage: status.message };
  }
  return base;
}

/**
 * Re-derives runtime services from current settings (user-initiated: save-settings, restore-
 * defaults). For trusted-LAN mode this routes through trustedLanCoordinator exactly like a
 * network-change rebind, so a user flipping bind mode to trusted-LAN while on a Public network
 * is refused the same way an automatic rebind would be — one authoritative policy, every trigger.
 */
async function reinitializeRuntimeServices(): Promise<void> {
  resolved = settingsService.getResolvedConfig();
  logService.setMinimumLevel(resolved.effective.logLevel);

  if (resolved.effective.connectorBindMode === 'trusted-lan') {
    trustedLanCoordinator.requestEvaluation(lastNetworkResolution);
    await trustedLanCoordinator.settle();
    return;
  }

  await lifecycleService.shutdown();
  dashboardService = createDashboardService(resolved.connectorBaseUrl);
  companyService = createCompanyService(resolved.connectorBaseUrl);
  ledgerService = createLedgerService(resolved.connectorBaseUrl);
  stockItemService = createStockItemService(resolved.connectorBaseUrl);
  lifecycleService = createLifecycleService();
  diagnosticsService = createDiagnosticsService();
  await lifecycleService.initialize();
}

/**
 * Fires on every detected Windows network change (DHCP renewal, Wi-Fi switch, cable
 * unplug/replug). Always records the new resolution for diagnostics/status. Only schedules a
 * trusted-LAN rebind evaluation when: startup has already completed once (avoids racing the very
 * first initialize()), and the Connector is configured for trusted-LAN mode (local-only installs
 * always bind 127.0.0.1 regardless of the active adapter, so there is nothing to rebind).
 * Scheduling is fire-and-forget-safe — trustedLanCoordinator serializes and de-duplicates.
 */
async function handleNetworkChange(resolution: ActiveNetworkResolution): Promise<void> {
  activeNetworkAdapter = resolution.adapter;
  lastNetworkResolution = resolution;
  logService.appendStructured({
    level: 'information',
    message: 'Active network resolution changed.',
    event: 'network_change_detected',
    component: 'network',
    metadata: {
      adapterName: resolution.adapter?.adapterName ?? null,
      ipv4: resolution.adapter?.ipv4 ?? null,
      profileCategory: resolution.adapter?.profileCategory ?? null,
      rejectedReason: resolution.rejectedReason,
    },
  });

  if (!desktopStartupComplete || resolved.effective.connectorBindMode !== 'trusted-lan') {
    notifyRenderer();
    return;
  }

  rebindState = {
    status: 'rebinding',
    at: new Date().toISOString(),
    message: 'Network changed — evaluating trusted-LAN rebind.',
  };
  notifyRenderer();
  trustedLanCoordinator.requestEvaluation(resolution);
}

const routeQuerier = new PowerShellRouteQuerier();
const networkWatcher = new NetworkChangeWatcher({
  routeQuerier,
  pollIntervalMs: 5_000,
  onChange: (resolution) => {
    void handleNetworkChange(resolution);
  },
  onError: (error) => {
    logService.appendStructured({
      level: 'warning',
      message: 'Active network resolution failed.',
      event: 'network_resolution_failed',
      component: 'network',
      metadata: { error: error instanceof Error ? error.message : String(error) },
    });
  },
});

const mobileAccessStatusService = new MobileAccessStatusService({
  getConnectorBaseUrl: () => resolved.connectorBaseUrl,
  getConnectorBindMode: () => resolved.effective.connectorBindMode,
  getActiveNetwork: () => activeNetworkAdapter,
  getTrustedLanEligibility: () => evaluateTrustedLanEligibility(lastNetworkResolution),
  getRebindState: () => rebindState,
});

function notifyRenderer(): void {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('desktop:status-updated');
  }
}

export function createMainWindow(): BrowserWindow {
  startupDiagnostics.record('window_creation_start');
  startupLog('BrowserWindow creation started');

  const preloadPath = path.join(__dirname, '../preload/preload.js');
  const rendererPath = path.join(__dirname, '../renderer/index.html');
  startupDiagnostics.record('renderer_path_selected', {
    preloadPath,
    rendererPath,
  });

  const window = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    title: DESKTOP_WINDOW_TITLE,
    show: true,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      devTools: isDevelopment,
    },
  });

  if (!isDevelopment) {
    window.webContents.on('before-input-event', (event, input) => {
      if (input.key === 'F12' || (input.control && input.shift && input.key.toLowerCase() === 'i')) {
        event.preventDefault();
      }
    });
  }

  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event) => {
    event.preventDefault();
  });

  startupDiagnostics.record('window_created', { windowId: window.id });
  startupLog('BrowserWindow created', `id=${window.id}`);

  window.on('closed', () => {
    startupDiagnostics.record('window_closed', { windowId: window.id });
    if (mainWindow === window) {
      mainWindow = null;
    }
  });

  window.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
    startupDiagnostics.record('did_fail_load', {
      errorCode,
      errorDescription,
      validatedURL,
    });
    startupLog('did-fail-load', `${errorCode} ${errorDescription} url=${validatedURL}`);
  });

  window.webContents.on('render-process-gone', (_event, details) => {
    startupDiagnostics.record('render_process_gone', {
      reason: details.reason,
      exitCode: details.exitCode,
    });
    startupLog('render-process-gone', `${details.reason} exitCode=${details.exitCode}`);
  });

  window.webContents.on('did-finish-load', () => {
    startupDiagnostics.record('renderer_loaded', { rendererPath });
  });

  app.on('child-process-gone', (_event, details) => {
    startupLog('child-process-gone', `${details.type} ${details.reason} exitCode=${details.exitCode}`);
  });

  window.once('ready-to-show', () => {
    startupDiagnostics.record('ready_to_show', { windowId: window.id });
    startupLog('ready-to-show');
    window.show();
    startupLog('window shown');
  });

  void window
    .loadFile(rendererPath)
    .then(() => {
      startupLog('renderer loaded', rendererPath);
    })
    .catch((error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      startupLog('load failure', message);
    });

  return window;
}

function registerIpcHandler<T extends unknown[], R>(
  channel: string,
  handler: (...args: T) => Promise<R> | R,
): void {
  assertAllowedIpcChannel(channel);
  ipcMain.handle(channel, async (_event, ...args: T) => {
    assertBoundedIpcPayload(args);
    return handler(...args);
  });
}

function registerIpcHandlers(): void {
  registerIpcHandler('desktop:get-dashboard', async () => dashboardService.getDashboardState());
  registerIpcHandler('desktop:get-logs', async () => dashboardService.getLogService().getEntries());
  registerIpcHandler('desktop:get-settings', async () => settingsService.getSettingsState());
  registerIpcHandler('desktop:validate-settings', async (input: unknown) => settingsService.validateInput(validateSettingsInput(input)));
  registerIpcHandler('desktop:save-settings', async (input: unknown) => {
    const result = settingsService.saveSettings(validateSettingsInput(input));
    if (result.ok && result.restartRequired) {
      await reinitializeRuntimeServices();
    } else if (result.ok) {
      diagnosticsService.updateResolvedConfig(settingsService.getResolvedConfig());
      logService.setMinimumLevel(settingsService.getResolvedConfig().effective.logLevel);
    }
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:restore-default-settings', async () => {
    const result = settingsService.restoreDefaults();
    await reinitializeRuntimeServices();
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:get-lifecycle-status', async () => lifecycleService.getStatus());
  registerIpcHandler('desktop:get-mobile-access-status', async () => mobileAccessStatusService.getStatus());
  registerIpcHandler('desktop:start-connector', async () => {
    if (resolved.effective.connectorBindMode === 'trusted-lan') {
      const status = await trustedLanCoordinator.manualStart();
      notifyRenderer();
      return toLifecycleStatusForManualCommand(status);
    }
    return lifecycleService.ensureConnectorRunning();
  });
  registerIpcHandler('desktop:stop-connector', async () => {
    if (resolved.effective.connectorBindMode === 'trusted-lan') {
      const status = await trustedLanCoordinator.manualStop();
      notifyRenderer();
      return toLifecycleStatusForManualCommand(status);
    }
    return lifecycleService.stopConnector();
  });
  registerIpcHandler('desktop:restart-connector', async () => {
    if (resolved.effective.connectorBindMode === 'trusted-lan') {
      const status = await trustedLanCoordinator.manualRestart();
      notifyRenderer();
      return toLifecycleStatusForManualCommand(status);
    }
    const status = lifecycleService.getStatus();
    if (status.externalProcessDetected && !status.managedByDesktop) {
      return status;
    }
    return lifecycleService.restartConnector();
  });
  registerIpcHandler('desktop:get-companies', async () => companyService.discoverCompanies());
  registerIpcHandler('desktop:select-company', async (companyId: unknown) => {
    const outcome = await companyService.selectCompany(validateCompanyId(companyId));
    notifyRenderer();
    return outcome;
  });
  registerIpcHandler('desktop:clear-company', async () => {
    const session = await companyService.clearSelection();
    notifyRenderer();
    return session;
  });
  registerIpcHandler('desktop:get-ledgers', async (payload: unknown) => {
    const input = validateLedgerQuery(payload);
    return ledgerService.getPageState(input.query, input.page, input.pageSize);
  });
  registerIpcHandler('desktop:sync-ledgers', async (payload: unknown) => {
    const { incremental } = validateSyncOptions(payload);
    const result = await ledgerService.syncLedgers(incremental);
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:cancel-ledger-sync', async () => {
    const result = await ledgerService.cancelSync();
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:get-ledger-statistics', async () => ledgerService.getStatistics());
  registerIpcHandler('desktop:clear-ledger-cache', async () => {
    const result = await ledgerService.clearCache();
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:get-stock-items', async (payload: unknown) => {
    const input = validateStockItemQuery(payload);
    return stockItemService.getPageState(input.query, input.page, input.pageSize);
  });
  registerIpcHandler('desktop:sync-stock-items', async (payload: unknown) => {
    const { incremental } = validateSyncOptions(payload);
    const result = await stockItemService.syncStockItems(incremental);
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:cancel-stock-item-sync', async () => {
    const result = await stockItemService.cancelSync();
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:get-stock-item-statistics', async () => stockItemService.getStatistics());
  registerIpcHandler('desktop:clear-stock-item-cache', async () => {
    const result = await stockItemService.clearCache();
    notifyRenderer();
    return result;
  });
  registerIpcHandler('desktop:get-diagnostics', async () => diagnosticsService.getSnapshot());
  registerIpcHandler('desktop:refresh-diagnostics', async () => diagnosticsService.getSnapshot());
  registerIpcHandler('desktop:copy-diagnostics-summary', async () => {
    const snapshot = await diagnosticsService.getSnapshot();
    return diagnosticsService.formatSummary(snapshot);
  });
  registerIpcHandler('desktop:export-diagnostics-bundle', async (targetDir?: unknown) => {
    return diagnosticsService.exportBundle(validateExportDirectory(targetDir, configPaths.diagnosticsExportDir));
  });
  registerIpcHandler('desktop:open-logs-folder', async () => {
    const result = await shell.openPath(configPaths.logsDir);
    return { ok: result === '', message: result || 'Logs folder opened.' };
  });
  registerIpcHandler('desktop:clear-nonessential-logs', async () => {
    logService.clearNonessential();
    fileLogWriter.clearCurrentLog();
    return { ok: true, message: 'Nonessential logs cleared.' };
  });
  registerIpcHandler('desktop:run-health-check', async () => {
    const healthy = await new HttpHealthChecker(resolved.connectorBaseUrl).checkHealth();
    let status = 'unknown';
    if (healthy) {
      try {
        const health = await dashboardService.getDashboardState();
        status = health.healthStatus;
      } catch {
        status = 'reachable';
      }
    }
    return {
      ok: healthy,
      reachable: healthy,
      status,
      message: healthy ? 'Connector health check passed.' : 'Connector health check failed.',
      checkedAt: new Date().toISOString(),
    };
  });
  registerIpcHandler('desktop:reload-renderer', async () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      await mainWindow.webContents.reload();
    }
    return { ok: true };
  });
}

/**
 * Resolves the current default-route adapter once before the first Connector spawn (so a
 * trusted-LAN install's very first launch already binds to the route-backed address rather than
 * a possibly-stale hand-typed one), then starts periodic network-change polling. Fire-and-forget
 * from bootstrapApp() — matches the existing non-blocking connector-startup behavior; the
 * Electron window shows immediately regardless of how long adapter resolution takes.
 */
async function startConnectorWithRouteResolution(): Promise<void> {
  // start() resolves the current default-route adapter once (populating activeNetworkAdapter /
  // lastNetworkResolution synchronously via handleNetworkChange, which no-ops its rebind trigger
  // while desktopStartupComplete is still false) before arming its periodic poll — so the very
  // first Connector spawn below already reflects the live network, not a stale hand-typed value.
  await networkWatcher.start();

  if (resolved.effective.connectorBindMode === 'trusted-lan') {
    // Route the very first bind through the same authoritative eligibility check every later
    // rebind uses — a Public/unknown-profile network must never get an initial LAN bind either.
    trustedLanCoordinator.requestEvaluation(lastNetworkResolution);
    await trustedLanCoordinator.settle();
  } else {
    lifecycleService = createLifecycleService();
    diagnosticsService = createDiagnosticsService();
    await lifecycleService.initialize();
  }

  desktopStartupComplete = true;
}

export function bootstrapApp(): void {
  startupLog('bootstrapApp invoked');
  registerIpcHandlers();
  bindSecondInstanceFocus(app, () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    startupDiagnostics.record('app_ready');
    startupLog('app ready');
    mainWindow = createMainWindow();
    void startConnectorWithRouteResolution();
    runDiagnosticExportRetentionCleanup();

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        mainWindow = createMainWindow();
      }
    });
  }).catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    startupDiagnostics.record('bootstrap_error', { message });
    startupLog('app.whenReady rejected', message);
  });

  app.on('window-all-closed', () => {
    startupDiagnostics.record('window_all_closed', { platform: process.platform });
    if (process.platform !== 'darwin') {
      startupDiagnostics.record('explicit_app_quit', { reason: 'window-all-closed' });
      app.quit();
    }
  });

  app.on('before-quit', () => {
    startupDiagnostics.record('before_quit');
    networkWatcher.stop();
    crashRecoveryScheduler.notifyStoppedIntentionally();
    // trustedLanCoordinator.shutdown() cancels any in-flight/queued rebind before stopping the
    // child; calling lifecycleService.shutdown() directly here too would race it and could
    // resurrect a child the coordinator just tore down.
    if (resolved.effective.connectorBindMode === 'trusted-lan') {
      void trustedLanCoordinator.shutdown();
    } else {
      void lifecycleService.shutdown();
    }
  });

  app.on('will-quit', () => {
    startupDiagnostics.record('will_quit');
    startupDiagnostics.flush();
  });
}

bootstrapApp();
