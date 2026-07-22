"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const electron_1 = require("electron");
const desktopBridge = {
    getDashboardState: () => electron_1.ipcRenderer.invoke('desktop:get-dashboard'),
    getLogs: () => electron_1.ipcRenderer.invoke('desktop:get-logs'),
    getSettings: () => electron_1.ipcRenderer.invoke('desktop:get-settings'),
    getLifecycleStatus: () => electron_1.ipcRenderer.invoke('desktop:get-lifecycle-status'),
    startConnector: () => electron_1.ipcRenderer.invoke('desktop:start-connector'),
    stopConnector: () => electron_1.ipcRenderer.invoke('desktop:stop-connector'),
    restartConnector: () => electron_1.ipcRenderer.invoke('desktop:restart-connector'),
    getCompanies: () => electron_1.ipcRenderer.invoke('desktop:get-companies'),
    selectCompany: (companyId) => electron_1.ipcRenderer.invoke('desktop:select-company', companyId),
    clearCompany: () => electron_1.ipcRenderer.invoke('desktop:clear-company'),
    onStatusUpdated: (listener) => {
        const channel = 'desktop:status-updated';
        const wrapped = () => listener();
        electron_1.ipcRenderer.on(channel, wrapped);
        return () => {
            electron_1.ipcRenderer.removeListener(channel, wrapped);
        };
    },
};
electron_1.contextBridge.exposeInMainWorld('budcomDesktop', desktopBridge);
//# sourceMappingURL=preload.js.map