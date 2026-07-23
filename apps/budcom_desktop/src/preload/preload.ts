import { contextBridge, ipcRenderer } from 'electron';

import type { ConnectorLifecycleStatus } from '../application/connector-lifecycle-types.js';
import type {
  CompanyListResult,
  CompanySelectionOutcome,
  ConnectorSessionDto,
  DashboardState,
  DiagnosticsExportResult,
  DiagnosticsSnapshot,
  HealthCheckResult,
  LedgerPageState,
  LedgerSyncProgressResult,
  LedgerSyncResult,
  LogEntry,
  SettingsSaveResult,
  SettingsState,
  SettingsValidationResult,
} from '../application/types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getSettings(): Promise<SettingsState>;
  validateSettings(input: Record<string, unknown>): Promise<SettingsValidationResult>;
  saveSettings(input: Record<string, unknown>): Promise<SettingsSaveResult>;
  restoreDefaultSettings(): Promise<SettingsSaveResult>;
  getLifecycleStatus(): Promise<ConnectorLifecycleStatus>;
  startConnector(): Promise<ConnectorLifecycleStatus>;
  stopConnector(): Promise<ConnectorLifecycleStatus>;
  restartConnector(): Promise<ConnectorLifecycleStatus>;
  getCompanies(): Promise<CompanyListResult>;
  selectCompany(companyId: string): Promise<CompanySelectionOutcome>;
  clearCompany(): Promise<ConnectorSessionDto | null>;
  getDiagnostics(): Promise<DiagnosticsSnapshot>;
  refreshDiagnostics(): Promise<DiagnosticsSnapshot>;
  copyDiagnosticsSummary(): Promise<string>;
  exportDiagnosticsBundle(): Promise<DiagnosticsExportResult>;
  openLogsFolder(): Promise<{ ok: boolean; message: string }>;
  clearNonessentialLogs(): Promise<{ ok: boolean; message: string }>;
  runHealthCheck(): Promise<HealthCheckResult>;
  reloadRenderer(): Promise<{ ok: boolean }>;
  getLedgers(payload?: { query?: string; page?: number; pageSize?: number }): Promise<LedgerPageState>;
  syncLedgers(incremental?: boolean): Promise<LedgerSyncResult>;
  cancelLedgerSync(): Promise<LedgerSyncProgressResult>;
  clearLedgerCache(): Promise<{ ok: boolean; message: string }>;
  onStatusUpdated(listener: () => void): () => void;
}

const desktopBridge: DesktopBridge = {
  getDashboardState: () => ipcRenderer.invoke('desktop:get-dashboard'),
  getLogs: () => ipcRenderer.invoke('desktop:get-logs'),
  getSettings: () => ipcRenderer.invoke('desktop:get-settings'),
  validateSettings: (input) => ipcRenderer.invoke('desktop:validate-settings', input),
  saveSettings: (input) => ipcRenderer.invoke('desktop:save-settings', input),
  restoreDefaultSettings: () => ipcRenderer.invoke('desktop:restore-default-settings'),
  getLifecycleStatus: () => ipcRenderer.invoke('desktop:get-lifecycle-status'),
  startConnector: () => ipcRenderer.invoke('desktop:start-connector'),
  stopConnector: () => ipcRenderer.invoke('desktop:stop-connector'),
  restartConnector: () => ipcRenderer.invoke('desktop:restart-connector'),
  getCompanies: () => ipcRenderer.invoke('desktop:get-companies'),
  selectCompany: (companyId) => ipcRenderer.invoke('desktop:select-company', companyId),
  clearCompany: () => ipcRenderer.invoke('desktop:clear-company'),
  getDiagnostics: () => ipcRenderer.invoke('desktop:get-diagnostics'),
  refreshDiagnostics: () => ipcRenderer.invoke('desktop:refresh-diagnostics'),
  copyDiagnosticsSummary: () => ipcRenderer.invoke('desktop:copy-diagnostics-summary'),
  exportDiagnosticsBundle: () => ipcRenderer.invoke('desktop:export-diagnostics-bundle'),
  openLogsFolder: () => ipcRenderer.invoke('desktop:open-logs-folder'),
  clearNonessentialLogs: () => ipcRenderer.invoke('desktop:clear-nonessential-logs'),
  runHealthCheck: () => ipcRenderer.invoke('desktop:run-health-check'),
  reloadRenderer: () => ipcRenderer.invoke('desktop:reload-renderer'),
  getLedgers: (payload) => ipcRenderer.invoke('desktop:get-ledgers', payload),
  syncLedgers: (incremental = false) => ipcRenderer.invoke('desktop:sync-ledgers', { incremental }),
  cancelLedgerSync: () => ipcRenderer.invoke('desktop:cancel-ledger-sync'),
  clearLedgerCache: () => ipcRenderer.invoke('desktop:clear-ledger-cache'),
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
