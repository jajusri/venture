import { contextBridge, ipcRenderer } from 'electron';

import type { ConnectorLifecycleStatus } from '../application/connector-lifecycle-types.js';
import type {
  CompanyListResult,
  CompanySelectionOutcome,
  ConnectorSessionDto,
  DashboardState,
  LogEntry,
  SettingsState,
} from '../application/types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getSettings(): Promise<SettingsState>;
  getLifecycleStatus(): Promise<ConnectorLifecycleStatus>;
  startConnector(): Promise<ConnectorLifecycleStatus>;
  stopConnector(): Promise<ConnectorLifecycleStatus>;
  restartConnector(): Promise<ConnectorLifecycleStatus>;
  getCompanies(): Promise<CompanyListResult>;
  selectCompany(companyId: string): Promise<CompanySelectionOutcome>;
  clearCompany(): Promise<ConnectorSessionDto | null>;
  onStatusUpdated(listener: () => void): () => void;
}

const desktopBridge: DesktopBridge = {
  getDashboardState: () => ipcRenderer.invoke('desktop:get-dashboard'),
  getLogs: () => ipcRenderer.invoke('desktop:get-logs'),
  getSettings: () => ipcRenderer.invoke('desktop:get-settings'),
  getLifecycleStatus: () => ipcRenderer.invoke('desktop:get-lifecycle-status'),
  startConnector: () => ipcRenderer.invoke('desktop:start-connector'),
  stopConnector: () => ipcRenderer.invoke('desktop:stop-connector'),
  restartConnector: () => ipcRenderer.invoke('desktop:restart-connector'),
  getCompanies: () => ipcRenderer.invoke('desktop:get-companies'),
  selectCompany: (companyId) => ipcRenderer.invoke('desktop:select-company', companyId),
  clearCompany: () => ipcRenderer.invoke('desktop:clear-company'),
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
