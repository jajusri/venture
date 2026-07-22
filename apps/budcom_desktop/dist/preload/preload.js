"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const electron_1 = require("electron");
const desktopBridge = {
    getDashboardState: () => electron_1.ipcRenderer.invoke('desktop:get-dashboard'),
    getLogs: () => electron_1.ipcRenderer.invoke('desktop:get-logs'),
    getConnectorUrl: () => electron_1.ipcRenderer.invoke('desktop:get-connector-url'),
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