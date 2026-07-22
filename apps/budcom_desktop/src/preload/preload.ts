import { contextBridge, ipcRenderer } from 'electron';

import type { DashboardState, LogEntry } from '../application/types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getConnectorUrl(): Promise<string>;
  onStatusUpdated(listener: () => void): () => void;
}

const desktopBridge: DesktopBridge = {
  getDashboardState: () => ipcRenderer.invoke('desktop:get-dashboard'),
  getLogs: () => ipcRenderer.invoke('desktop:get-logs'),
  getConnectorUrl: () => ipcRenderer.invoke('desktop:get-connector-url'),
  onStatusUpdated: (listener) => {
    const channel = 'desktop:status-updated';
    const wrapped = (): void => listener();
    ipcRenderer.on(channel, wrapped);
    return () => {
      ipcRenderer.removeListener(channel, wrapped);
    };
  },
};

contextBridge.exposeInMainWorld('budcomDesktop', desktopBridge);
