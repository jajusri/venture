"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.createMainWindow = createMainWindow;
exports.bootstrapApp = bootstrapApp;
const electron_1 = require("electron");
const node_path_1 = __importDefault(require("node:path"));
const company_service_js_1 = require("../application/company-service.js");
const connector_lifecycle_config_js_1 = require("../application/connector-lifecycle-config.js");
const connector_lifecycle_service_js_1 = require("../application/connector-lifecycle-service.js");
const dashboard_service_js_1 = require("../application/dashboard-service.js");
const log_service_js_1 = require("../application/log-service.js");
const node_process_spawner_js_1 = require("../application/node-process-spawner.js");
const lifecycleConfig = (0, connector_lifecycle_config_js_1.resolveConnectorLifecycleConfig)();
const CONNECTOR_BASE_URL = lifecycleConfig.connectorBaseUrl;
const POLL_INTERVAL_MS = lifecycleConfig.healthPollIntervalMs;
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
const logService = new log_service_js_1.LogService();
const dashboardService = new dashboard_service_js_1.DashboardService({
    connectorBaseUrl: CONNECTOR_BASE_URL,
    logService,
});
const companyService = new company_service_js_1.CompanyService({
    connectorBaseUrl: CONNECTOR_BASE_URL,
    logService,
});
const lifecycleService = new connector_lifecycle_service_js_1.ConnectorLifecycleService({
    config: lifecycleConfig,
    processSpawner: new node_process_spawner_js_1.NodeProcessSpawner(),
    healthChecker: new connector_lifecycle_service_js_1.HttpHealthChecker(CONNECTOR_BASE_URL),
    logService,
});
lifecycleService.setStatusListener(() => {
    notifyRenderer();
});
function notifyRenderer() {
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('desktop:status-updated');
    }
}
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
    electron_1.ipcMain.handle('desktop:get-settings', async () => ({
        ...dashboardService.getSettingsState(),
        connectorExecutable: lifecycleConfig.connectorExecutable,
        connectorPort: lifecycleConfig.connectorPort,
        autoStartConnector: lifecycleConfig.autoStart,
    }));
    electron_1.ipcMain.handle('desktop:get-lifecycle-status', async () => lifecycleService.getStatus());
    electron_1.ipcMain.handle('desktop:start-connector', async () => lifecycleService.ensureConnectorRunning());
    electron_1.ipcMain.handle('desktop:stop-connector', async () => lifecycleService.stopConnector());
    electron_1.ipcMain.handle('desktop:restart-connector', async () => lifecycleService.restartConnector());
    electron_1.ipcMain.handle('desktop:get-companies', async () => companyService.discoverCompanies());
    electron_1.ipcMain.handle('desktop:select-company', async (_event, companyId) => {
        const outcome = await companyService.selectCompany(companyId);
        notifyRenderer();
        return outcome;
    });
    electron_1.ipcMain.handle('desktop:clear-company', async () => {
        const session = await companyService.clearSelection();
        notifyRenderer();
        return session;
    });
}
function startPolling() {
    if (pollTimer) {
        clearInterval(pollTimer);
    }
    pollTimer = setInterval(() => {
        notifyRenderer();
    }, POLL_INTERVAL_MS);
}
function bootstrapApp() {
    startupLog('bootstrapApp invoked');
    registerIpcHandlers();
    electron_1.app.whenReady().then(() => {
        startupLog('app ready');
        mainWindow = createMainWindow();
        startPolling();
        void lifecycleService.initialize();
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
        void lifecycleService.shutdown();
    });
}
bootstrapApp();
//# sourceMappingURL=main.js.map