import { contextBridge, ipcRenderer } from 'electron';

import type { ConnectorLifecycleStatus } from '../application/connector-lifecycle-types.js';
import type { MobileAccessStatus } from '../application/mobile-access-status-service.js';
import type { RemovableVolumeInfo } from '../application/private-storage/removable-volume-enumerator.js';
import type { ChooseStorageModeResult, StorageGateState } from '../application/private-storage/private-storage-types.js';
import type {
  ActivePairingSessionView,
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
  SecurePairingCapability,
  SettingsMutationResult,
  StockItemPageState,
  StockItemSyncProgressResult,
  StockItemSyncResult,
  LogEntry,
  SettingsSaveResult,
  SettingsState,
  SettingsValidationResult,
  TrustedPairingDeviceSummary,
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
  getStockItems(payload?: { query?: string; page?: number; pageSize?: number }): Promise<StockItemPageState>;
  syncStockItems(incremental?: boolean): Promise<StockItemSyncResult>;
  cancelStockItemSync(): Promise<StockItemSyncProgressResult>;
  clearStockItemCache(): Promise<{ ok: boolean; message: string }>;
  getMobileAccessStatus(): Promise<MobileAccessStatus>;
  getSecurePairingCapability(): Promise<SecurePairingCapability>;
  enableSecurePairing(): Promise<SettingsMutationResult>;
  disableSecurePairing(): Promise<SettingsMutationResult>;
  startPairing(): Promise<ActivePairingSessionView>;
  getPairingStatus(): Promise<ActivePairingSessionView>;
  cancelPairing(): Promise<ActivePairingSessionView>;
  listTrustedPairingDevices(): Promise<readonly TrustedPairingDeviceSummary[]>;
  revokeTrustedPairingDevice(credentialId: string): Promise<{ ok: boolean; message: string }>;
  getStorageStatus(): Promise<StorageGateState>;
  listRemovableVolumes(): Promise<readonly RemovableVolumeInfo[]>;
  chooseStorageMode(input: { mode: 'standard' } | { mode: 'private-removable'; driveLetter: string }): Promise<ChooseStorageModeResult>;
  retryStorageConnection(): Promise<StorageGateState>;
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
  getStockItems: (payload) => ipcRenderer.invoke('desktop:get-stock-items', payload),
  syncStockItems: (incremental = false) => ipcRenderer.invoke('desktop:sync-stock-items', { incremental }),
  cancelStockItemSync: () => ipcRenderer.invoke('desktop:cancel-stock-item-sync'),
  clearStockItemCache: () => ipcRenderer.invoke('desktop:clear-stock-item-cache'),
  getMobileAccessStatus: () => ipcRenderer.invoke('desktop:get-mobile-access-status'),
  getSecurePairingCapability: () => ipcRenderer.invoke('desktop:get-secure-pairing-capability'),
  enableSecurePairing: () => ipcRenderer.invoke('desktop:enable-secure-pairing'),
  disableSecurePairing: () => ipcRenderer.invoke('desktop:disable-secure-pairing'),
  startPairing: () => ipcRenderer.invoke('desktop:start-pairing'),
  getPairingStatus: () => ipcRenderer.invoke('desktop:get-pairing-status'),
  cancelPairing: () => ipcRenderer.invoke('desktop:cancel-pairing'),
  listTrustedPairingDevices: () => ipcRenderer.invoke('desktop:list-trusted-pairing-devices'),
  revokeTrustedPairingDevice: (credentialId) => ipcRenderer.invoke('desktop:revoke-trusted-pairing-device', credentialId),
  getStorageStatus: () => ipcRenderer.invoke('desktop:get-storage-status'),
  listRemovableVolumes: () => ipcRenderer.invoke('desktop:list-removable-volumes'),
  chooseStorageMode: (input) => ipcRenderer.invoke('desktop:choose-storage-mode', input),
  retryStorageConnection: () => ipcRenderer.invoke('desktop:retry-storage-connection'),
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
