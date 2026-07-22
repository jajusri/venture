"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.createMainWindow = createMainWindow;
exports.bootstrapApp = bootstrapApp;
const electron_1 = require("electron");
const node_path_1 = __importDefault(require("node:path"));
const dashboard_service_js_1 = require("../application/dashboard-service.js");
const CONNECTOR_BASE_URL = process.env.BUDCOM_CONNECTOR_URL ?? 'http://localhost:8080';
const POLL_INTERVAL_MS = 5_000;
let mainWindow = null;
let pollTimer = null;
function startupLog(stage, detail) {
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
const dashboardService = new dashboard_service_js_1.DashboardService({
    connectorBaseUrl: CONNECTOR_BASE_URL,
});
function createMainWindow() {
    startupLog('BrowserWindow creation started');
    const preloadPath = node_path_1.default.join(__dirname, '../preload/preload.js');
    const rendererPath = node_path_1.default.join(__dirname, '../renderer/index.html');
    startupLog('preload resolved', preloadPath);
    startupLog('renderer path resolved', rendererPath);
    const window = new electron_1.BrowserWindow({
        width: 1200,
        height: 800,
        minWidth: 960,
        minHeight: 640,
        title: dashboard_service_js_1.DESKTOP_WINDOW_TITLE,
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
    electron_1.app.on('child-process-gone', (_event, details) => {
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
        .catch((error) => {
        const message = error instanceof Error ? error.message : String(error);
        startupLog('load failure', message);
    });
    return window;
}
function registerIpcHandlers() {
    electron_1.ipcMain.handle('desktop:get-dashboard', async () => dashboardService.getDashboardState());
    electron_1.ipcMain.handle('desktop:get-logs', async () => dashboardService.getLogService().getEntries());
    electron_1.ipcMain.handle('desktop:get-connector-url', async () => CONNECTOR_BASE_URL);
}
function startPolling() {
    if (pollTimer) {
        clearInterval(pollTimer);
    }
    pollTimer = setInterval(() => {
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('desktop:status-updated');
        }
    }, POLL_INTERVAL_MS);
}
function bootstrapApp() {
    startupLog('bootstrapApp invoked');
    registerIpcHandlers();
    electron_1.app.whenReady().then(() => {
        startupLog('app ready');
        mainWindow = createMainWindow();
        startPolling();
        electron_1.app.on('activate', () => {
            if (electron_1.BrowserWindow.getAllWindows().length === 0) {
                mainWindow = createMainWindow();
            }
        });
    }).catch((error) => {
        const message = error instanceof Error ? error.message : String(error);
        startupLog('app.whenReady rejected', message);
    });
    electron_1.app.on('window-all-closed', () => {
        if (process.platform !== 'darwin') {
            electron_1.app.quit();
        }
    });
    electron_1.app.on('before-quit', () => {
        if (pollTimer) {
            clearInterval(pollTimer);
        }
    });
}
bootstrapApp();
//# sourceMappingURL=main.js.map