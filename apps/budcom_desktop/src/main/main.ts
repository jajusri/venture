import { app, BrowserWindow, ipcMain, shell } from 'electron';
import path from 'node:path';

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
import { FileLogWriter } from '../application/file-log-writer.js';
import {
  assertAllowedIpcChannel,
  validateCompanyId,
  validateExportDirectory,
  validateLedgerQuery,
  validateStockItemQuery,
  validateSettingsInput,
} from '../application/ipc-allowlist.js';
import { LogService } from '../application/log-service.js';
import { NodeProcessSpawner } from '../application/node-process-spawner.js';
import { RecoveryService } from '../application/recovery-service.js';
import { SettingsService } from '../application/settings-service.js';

const startedAt = Date.now();
const isDevelopment = process.env.NODE_ENV !== 'production';

let mainWindow: BrowserWindow | null = null;
let pollTimer: NodeJS.Timeout | null = null;

function startupLog(stage: string, detail?: string): void {
  const suffix = detail ? ` — ${detail}` : '';
  console.error(`[budcom-desktop:startup] ${stage}${suffix}`);
}

startupLog('main module loaded', `require.main === module: ${require.main === module}`);

process.on('uncaughtException', (error) => {
  startupLog('uncaughtException', error.stack ?? error.message);
});

process.on('unhandledRejection', (reason) => {
  const detail = reason instanceof Error ? reason.stack ?? reason.message : String(reason);
  startupLog('unhandledRejection', detail);
});

const configPaths = resolveDesktopConfigPaths(app.getPath('userData'));
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

const settingsService = new SettingsService({
  configStore,
  logService,
  isDevelopment,
  connectorExecutable: process.env.BUDCOM_CONNECTOR_EXECUTABLE ?? process.execPath,
});
settingsService.setConfigStatus(configLoadResult.status);

let resolved = settingsService.getResolvedConfig();
let dashboardService = createDashboardService(resolved.connectorBaseUrl);
let companyService = createCompanyService(resolved.connectorBaseUrl);
let ledgerService = createLedgerService(resolved.connectorBaseUrl);
let stockItemService = createStockItemService(resolved.connectorBaseUrl);
let lifecycleService = createLifecycleService(resolved.lifecycleConfig);
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

function createLifecycleService(config: ReturnType<typeof settingsService.getResolvedConfig>['lifecycleConfig']): ConnectorLifecycleService {
  const service = new ConnectorLifecycleService({
    config,
    processSpawner: new NodeProcessSpawner(),
    healthChecker: new HttpHealthChecker(config.connectorBaseUrl),
    logService,
  });
  service.setStatusListener(() => {
    notifyRenderer();
  });
  return service;
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

async function reinitializeRuntimeServices(): Promise<void> {
  await lifecycleService.shutdown();
  resolved = settingsService.getResolvedConfig();
  logService.setMinimumLevel(resolved.effective.logLevel);
  dashboardService = createDashboardService(resolved.connectorBaseUrl);
  companyService = createCompanyService(resolved.connectorBaseUrl);
  ledgerService = createLedgerService(resolved.connectorBaseUrl);
  stockItemService = createStockItemService(resolved.connectorBaseUrl);
  lifecycleService = createLifecycleService(resolved.lifecycleConfig);
  diagnosticsService = createDiagnosticsService();
  startPolling(resolved.effective.healthPollIntervalMs);
  await lifecycleService.initialize();
}

function notifyRenderer(): void {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('desktop:status-updated');
  }
}

export function createMainWindow(): BrowserWindow {
  startupLog('BrowserWindow creation started');

  const preloadPath = path.join(__dirname, '../preload/preload.js');
  const rendererPath = path.join(__dirname, '../renderer/index.html');
  startupLog('preload resolved', preloadPath);
  startupLog('renderer path resolved', rendererPath);

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

  startupLog('BrowserWindow created', `id=${window.id}`);

  window.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
    startupLog('did-fail-load', `${errorCode} ${errorDescription} url=${validatedURL}`);
  });

  window.webContents.on('render-process-gone', (_event, details) => {
    startupLog('render-process-gone', `${details.reason} exitCode=${details.exitCode}`);
  });

  app.on('child-process-gone', (_event, details) => {
    startupLog('child-process-gone', `${details.type} ${details.reason} exitCode=${details.exitCode}`);
  });

  window.once('ready-to-show', () => {
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
  ipcMain.handle(channel, async (_event, ...args: T) => handler(...args));
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
  registerIpcHandler('desktop:start-connector', async () => lifecycleService.ensureConnectorRunning());
  registerIpcHandler('desktop:stop-connector', async () => lifecycleService.stopConnector());
  registerIpcHandler('desktop:restart-connector', async () => {
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
    const incremental = Boolean(
      payload && typeof payload === 'object' && !Array.isArray(payload)
        ? (payload as { incremental?: boolean }).incremental
        : false,
    );
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
    const incremental = Boolean(
      payload && typeof payload === 'object' && !Array.isArray(payload)
        ? (payload as { incremental?: boolean }).incremental
        : false,
    );
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
    return diagnosticsService.exportBundle(validateExportDirectory(targetDir));
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

function startPolling(intervalMs: number): void {
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    notifyRenderer();
  }, intervalMs);
}

export function bootstrapApp(): void {
  startupLog('bootstrapApp invoked');
  registerIpcHandlers();

  app.whenReady().then(() => {
    startupLog('app ready');
    mainWindow = createMainWindow();
    startPolling(resolved.effective.healthPollIntervalMs);
    void lifecycleService.initialize();
    runDiagnosticExportRetentionCleanup();

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        mainWindow = createMainWindow();
      }
    });
  }).catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    startupLog('app.whenReady rejected', message);
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit();
    }
  });

  app.on('before-quit', () => {
    if (pollTimer) {
      clearInterval(pollTimer);
    }
    void lifecycleService.shutdown();
  });
}

bootstrapApp();
