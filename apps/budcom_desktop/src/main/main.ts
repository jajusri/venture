import { app, BrowserWindow, ipcMain } from 'electron';
import path from 'node:path';

import { DashboardService, DESKTOP_WINDOW_TITLE } from '../application/dashboard-service.js';

const CONNECTOR_BASE_URL = process.env.BUDCOM_CONNECTOR_URL ?? 'http://localhost:8080';
const POLL_INTERVAL_MS = 5_000;

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

const dashboardService = new DashboardService({
  connectorBaseUrl: CONNECTOR_BASE_URL,
});

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
    },
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

function registerIpcHandlers(): void {
  ipcMain.handle('desktop:get-dashboard', async () => dashboardService.getDashboardState());
  ipcMain.handle('desktop:get-logs', async () => dashboardService.getLogService().getEntries());
  ipcMain.handle('desktop:get-connector-url', async () => CONNECTOR_BASE_URL);
}

function startPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('desktop:status-updated');
    }
  }, POLL_INTERVAL_MS);
}

export function bootstrapApp(): void {
  startupLog('bootstrapApp invoked');
  registerIpcHandlers();

  app.whenReady().then(() => {
    startupLog('app ready');
    mainWindow = createMainWindow();
    startPolling();

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
  });
}

bootstrapApp();
