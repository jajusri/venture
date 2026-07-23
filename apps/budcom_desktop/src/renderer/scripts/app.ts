import type {
  CompanyListItemDto,
  DashboardState,
  DiagnosticsSnapshot,
  LedgerPageState,
  StockItemPageState,
  LogEntry,
  SettingsSaveResult,
  SettingsState,
} from '../../application/types.js';
import type { ConnectorLifecycleStatus } from '../../application/connector-lifecycle-types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getSettings(): Promise<SettingsState>;
  validateSettings(input: Record<string, unknown>): Promise<{ ok: boolean; errors: readonly { field: string; message: string }[] }>;
  saveSettings(input: Record<string, unknown>): Promise<SettingsSaveResult>;
  restoreDefaultSettings(): Promise<SettingsSaveResult>;
  getLifecycleStatus(): Promise<ConnectorLifecycleStatus>;
  startConnector(): Promise<ConnectorLifecycleStatus>;
  stopConnector(): Promise<ConnectorLifecycleStatus>;
  restartConnector(): Promise<ConnectorLifecycleStatus>;
  getCompanies(): Promise<{ items: readonly CompanyListItemDto[]; status: string; reason?: string }>;
  selectCompany(companyId: string): Promise<{ ok: boolean; userMessage: string }>;
  clearCompany(): Promise<unknown>;
  getDiagnostics(): Promise<DiagnosticsSnapshot>;
  refreshDiagnostics(): Promise<DiagnosticsSnapshot>;
  copyDiagnosticsSummary(): Promise<string>;
  exportDiagnosticsBundle(): Promise<{ ok: boolean; message: string; bundlePath: string | null }>;
  openLogsFolder(): Promise<{ ok: boolean; message: string }>;
  clearNonessentialLogs(): Promise<{ ok: boolean; message: string }>;
  runHealthCheck(): Promise<{ ok: boolean; message: string }>;
  reloadRenderer(): Promise<{ ok: boolean }>;
  getLedgers(payload?: { query?: string; page?: number; pageSize?: number }): Promise<LedgerPageState>;
  syncLedgers(incremental?: boolean): Promise<unknown>;
  cancelLedgerSync(): Promise<unknown>;
  clearLedgerCache(): Promise<{ ok: boolean; message: string }>;
  getStockItems(payload?: { query?: string; page?: number; pageSize?: number }): Promise<StockItemPageState>;
  syncStockItems(incremental?: boolean): Promise<unknown>;
  cancelStockItemSync(): Promise<unknown>;
  clearStockItemCache(): Promise<{ ok: boolean; message: string }>;
  onStatusUpdated(listener: () => void): () => void;
}

declare global {
  interface Window {
    budcomDesktop: DesktopBridge;
  }
}

export type DesktopView = 'dashboard' | 'connection' | 'ledgers' | 'stock-items' | 'logs' | 'diagnostics' | 'settings' | 'about';

export interface UiLoadingState {
  readonly dashboard: boolean;
  readonly companies: boolean;
  readonly selecting: boolean;
  readonly settings: boolean;
  readonly diagnostics: boolean;
  readonly ledgers: boolean;
  readonly stockItems: boolean;
  readonly syncing: boolean;
  readonly syncingStockItems: boolean;
}

let refreshInFlight = false;
let currentSettings: SettingsState | null = null;
let settingsDirty = false;
let ledgerPage = 1;
let ledgerQuery = '';
const ledgerPageSize = 25;
let stockItemPage = 1;
let stockItemQuery = '';
const stockItemPageSize = 25;

export function setText(id: string, value: string): void {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
  }
}

export function setBanner(message: string | null, level: 'error' | 'warning' | 'information' = 'error'): void {
  const banner = document.getElementById('global-banner');
  if (!banner) {
    return;
  }
  if (!message) {
    banner.className = 'banner hidden';
    banner.textContent = '';
    return;
  }
  banner.className = `banner banner-${level}`;
  banner.textContent = message;
}

export function setLoading(state: Partial<UiLoadingState>, message = 'Loading…'): void {
  const bar = document.getElementById('global-loading');
  const label = document.getElementById('loading-message');
  if (!bar || !label) {
    return;
  }
  const active = Boolean(
    state.dashboard ||
      state.companies ||
      state.selecting ||
      state.settings ||
      state.diagnostics ||
      state.ledgers ||
      state.stockItems ||
      state.syncing ||
      state.syncingStockItems,
  );
  bar.className = active ? 'loading-bar' : 'loading-bar hidden';
  if (state.selecting) {
    label.textContent = 'Selecting company…';
  } else if (state.companies) {
    label.textContent = 'Fetching companies…';
  } else if (state.settings) {
    label.textContent = 'Saving settings…';
  } else if (state.diagnostics) {
    label.textContent = 'Refreshing diagnostics…';
  } else if (state.syncingStockItems) {
    label.textContent = 'Syncing stock items…';
  } else if (state.syncing) {
    label.textContent = 'Syncing ledgers…';
  } else if (state.stockItems) {
    label.textContent = 'Loading stock items…';
  } else if (state.ledgers) {
    label.textContent = 'Loading ledgers…';
  } else {
    label.textContent = message;
  }
}

export function renderDashboard(state: DashboardState): void {
  setText('app-title', state.windowTitle);
  setText('header-version', state.connectorVersion);
  setText('header-connection-label', state.connectionLabel);
  setText('header-company', state.companyName);
  setText('header-sync', state.syncLabel);
  setText('header-last-sync', state.lastSync);

  const indicator = document.getElementById('connection-indicator');
  if (indicator) {
    indicator.className = `indicator indicator-${state.connectionIndicator}`;
  }

  setText('dashboard-connection', state.connectionLabel);
  setText('dashboard-health', `Health: ${state.healthStatus}`);
  setText('dashboard-company-name', state.companyName);
  setText('dashboard-company-id', state.companyId);
  setText('dashboard-selection-time', state.selectionTime);
  setText('dashboard-session-status', state.sessionStatus);
  setText('dashboard-erp-name', state.erpName);
  setText('dashboard-last-refresh', state.lastRefresh);
  setText('dashboard-sync', state.syncLabel);
  setText('dashboard-last-sync', state.lastSync);
  setText('dashboard-version', state.connectorVersion);
  setText('dashboard-desktop-version', state.desktopVersion);

  setText('connection-detail-indicator', state.connectionIndicator);
  setText('connection-detail-label', state.connectionLabel);
  setText('connection-detail-reachable', String(state.connectorReachable));
  setText('connection-detail-health', state.healthStatus);
  setText('connection-detail-session', state.sessionStatus);

  setText('footer-version', state.connectorVersion);
  setText('footer-erp', state.erpType);
  setText('footer-license', state.licenseStatus);

  if (state.userMessage && !state.connectorReachable) {
    setBanner(state.userMessage, 'warning');
  } else if (state.userMessage) {
    setBanner(state.userMessage, 'warning');
  } else {
    setBanner(null);
  }
}

function setInputValue(id: string, value: string | number | boolean): void {
  const element = document.getElementById(id) as HTMLInputElement | HTMLSelectElement | null;
  if (!element) {
    return;
  }
  if (element instanceof HTMLInputElement && element.type === 'checkbox') {
    element.checked = Boolean(value);
    return;
  }
  element.value = String(value);
}

export function renderSettingsForm(settings: SettingsState): void {
  currentSettings = settings;
  setInputValue('input-connector-host', settings.connectorHost);
  setInputValue('input-connector-port', settings.connectorPort);
  setInputValue('input-auto-start', settings.autoStartConnector);
  setInputValue('input-health-poll', settings.healthPollIntervalMs);
  setInputValue('input-startup-timeout', settings.startupTimeoutMs);
  setInputValue('input-log-level', settings.logLevel);
  setInputValue('input-tally-host', settings.tallyHost);
  setInputValue('input-tally-port', settings.tallyPort);
  setText('settings-desktop-version', settings.desktopVersion);
  setText('settings-config-source', settings.configSource);
  setText('settings-config-status', settings.configStatus);

  const restartNotice = document.getElementById('settings-restart-notice');
  if (restartNotice) {
    restartNotice.className = settings.restartRequired ? 'form-note' : 'form-note hidden';
  }
}

export function renderSettingsStatus(message: string, isError = false): void {
  const status = document.getElementById('settings-status-message');
  if (status) {
    status.textContent = message;
    status.className = isError ? 'panel-meta form-error' : 'panel-meta';
  }
}

export function renderValidationErrors(errors: readonly { field: string; message: string }[]): void {
  const container = document.getElementById('settings-validation-errors');
  if (!container) {
    return;
  }
  if (errors.length === 0) {
    container.className = 'form-error hidden';
    container.textContent = '';
    return;
  }
  container.className = 'form-error';
  container.textContent = errors.map((error) => `${error.field}: ${error.message}`).join(' ');
}

function collectSettingsForm(): Record<string, unknown> {
  const host = (document.getElementById('input-connector-host') as HTMLInputElement | null)?.value ?? '';
  const port = Number.parseInt((document.getElementById('input-connector-port') as HTMLInputElement | null)?.value ?? '8080', 10);
  const autoStart = (document.getElementById('input-auto-start') as HTMLInputElement | null)?.checked ?? true;
  const healthPoll = Number.parseInt((document.getElementById('input-health-poll') as HTMLInputElement | null)?.value ?? '5000', 10);
  const startupTimeout = Number.parseInt((document.getElementById('input-startup-timeout') as HTMLInputElement | null)?.value ?? '30000', 10);
  const logLevel = (document.getElementById('input-log-level') as HTMLSelectElement | null)?.value ?? 'info';
  const tallyHost = (document.getElementById('input-tally-host') as HTMLInputElement | null)?.value ?? 'localhost';
  const tallyPort = Number.parseInt((document.getElementById('input-tally-port') as HTMLInputElement | null)?.value ?? '9000', 10);

  return {
    connectorHost: host,
    connectorPort: port,
    autoStartConnector: autoStart,
    healthPollIntervalMs: healthPoll,
    startupTimeoutMs: startupTimeout,
    shutdownGraceMs: currentSettings?.shutdownGraceMs ?? 5000,
    maxRestartAttempts: currentSettings?.maxRestartAttempts ?? 5,
    reconnectBaseDelayMs: currentSettings?.reconnectBaseDelayMs ?? 1000,
    logLevel,
    diagnosticsRetentionDays: currentSettings?.diagnosticsRetentionDays ?? 14,
    tallyHost,
    tallyPort,
  };
}

export function renderLifecycle(status: ConnectorLifecycleStatus): void {
  setText('lifecycle-state', status.stateLabel);
  setText('lifecycle-managed', status.managedByDesktop ? 'Yes' : 'No');
  setText('lifecycle-external', status.externalProcessDetected ? 'Yes' : 'No');
  setText('lifecycle-last-health', status.lastSuccessfulHealthCheck ?? '—');
  setText('lifecycle-restart-attempts', String(status.restartAttempts));
  setText('lifecycle-exit-code', status.processExitCode === null ? '—' : String(status.processExitCode));
  setText('lifecycle-port', String(status.connectorPort));

  if (status.userMessage && (status.state === 'failed' || status.state === 'reconnecting')) {
    setBanner(status.userMessage, status.state === 'failed' ? 'error' : 'warning');
  }
}

function renderLogList(containerId: string, entries: readonly LogEntry[]): void {
  const container = document.getElementById(containerId);
  if (!container) {
    return;
  }
  container.innerHTML = entries.length === 0
    ? '<p class="empty-state">No entries.</p>'
    : entries.map((entry) =>
      `<div class="log-entry log-${entry.level}"><span class="log-time">[${entry.timestamp}]</span> <span class="log-level">${entry.level.toUpperCase()}</span> ${entry.message}</div>`,
    ).join('');
}

export function renderLogs(entries: readonly LogEntry[]): void {
  renderLogList('log-list', entries);
}

export function renderDiagnostics(snapshot: DiagnosticsSnapshot, message?: string): void {
  setText('diag-desktop-version', snapshot.desktopVersion);
  setText('diag-connector-version', snapshot.connectorVersion ?? '—');
  setText('diag-runtime-versions', `${snapshot.electronVersion} / ${snapshot.nodeVersion}`);
  setText('diag-os', `${snapshot.platform} ${snapshot.osRelease} (${snapshot.architecture})`);
  setText('diag-uptime', `${snapshot.uptimeSeconds}s`);
  setText('diag-connector-url', snapshot.connectorBaseUrl);
  setText('diag-process-state', snapshot.connectorProcessState);
  setText('diag-ownership', snapshot.connectorOwnership);
  setText('diag-pid', snapshot.connectorPid === null ? '—' : String(snapshot.connectorPid));
  setText('diag-health', `${snapshot.healthStatus} (reachable=${snapshot.healthReachable})`);
  setText('diag-last-health', snapshot.lastSuccessfulHealthCheck ?? '—');
  setText('diag-session', snapshot.sessionSummary);
  setText('diag-config-status', `${snapshot.configStatus} · ${snapshot.configSource}`);
  setText('diag-log-file', snapshot.logFilePath ?? 'unavailable');
  renderLogList('diag-lifecycle-events', snapshot.recentLifecycleEvents);
  renderLogList('diag-recent-errors', snapshot.recentErrors);
  if (message) {
    setText('diagnostics-status-message', message);
  }
}

export function renderCompanyList(
  companies: readonly CompanyListItemDto[],
  selectedCompanyId: string,
  statusText: string,
): void {
  const container = document.getElementById('company-list');
  const status = document.getElementById('company-list-status');
  if (status) {
    status.textContent = statusText;
  }
  if (!container) {
    return;
  }

  if (companies.length === 0) {
    container.innerHTML = '<p class="empty-state">No companies available.</p>';
    return;
  }

  container.innerHTML = companies
    .map((company) => {
      const selected = company.id === selectedCompanyId ? ' selected' : '';
      return `<button type="button" class="company-item${selected}" data-company-id="${company.id}" role="option" aria-selected="${company.id === selectedCompanyId}"><span class="company-name">${company.name}</span><span class="company-id">${company.id}</span></button>`;
    })
    .join('');
}

export function activateView(view: DesktopView): void {
  document.querySelectorAll('.view').forEach((element) => element.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach((button) => button.classList.remove('active'));

  document.getElementById(`view-${view}`)?.classList.add('active');
  document.querySelector(`.nav-btn[data-view="${view}"]`)?.classList.add('active');

  if (view === 'diagnostics') {
    void refreshDiagnostics();
  }
  if (view === 'ledgers') {
    void loadLedgers();
  }
  if (view === 'stock-items') {
    void loadStockItems();
  }
}

export async function refreshUi(): Promise<void> {
  if (refreshInFlight) {
    return;
  }
  refreshInFlight = true;
  setLoading({ dashboard: true }, 'Refreshing dashboard…');
  try {
    const bridge = window.budcomDesktop;
    const [state, logs, settings, lifecycle] = await Promise.all([
      bridge.getDashboardState(),
      bridge.getLogs(),
      bridge.getSettings(),
      bridge.getLifecycleStatus(),
    ]);
    renderDashboard(state);
    renderLogs(logs);
    if (!settingsDirty) {
      renderSettingsForm(settings);
    }
    renderLifecycle(lifecycle);
  } finally {
    setLoading({ dashboard: false });
    refreshInFlight = false;
  }
}

export async function refreshDiagnostics(): Promise<void> {
  setLoading({ diagnostics: true });
  try {
    const snapshot = await window.budcomDesktop.refreshDiagnostics();
    renderDiagnostics(snapshot, `Diagnostics refreshed at ${snapshot.generatedAt}`);
  } catch {
    setText('diagnostics-status-message', 'Unable to refresh diagnostics.');
  } finally {
    setLoading({ diagnostics: false });
  }
}

export async function loadCompanies(): Promise<void> {
  setLoading({ companies: true });
  try {
    const bridge = window.budcomDesktop;
    const [companies, dashboard] = await Promise.all([
      bridge.getCompanies(),
      bridge.getDashboardState(),
    ]);
    renderCompanyList(
      companies.items,
      dashboard.companyId === '—' ? '' : dashboard.companyId,
      companies.items.length > 0
        ? `${companies.items.length} companies available`
        : `Discovery status: ${companies.status}`,
    );
  } catch {
    setBanner('Unable to load companies from the connector.', 'error');
    renderCompanyList([], '', 'Company discovery failed.');
  } finally {
    setLoading({ companies: false });
  }
}

export async function handleCompanySelection(companyId: string): Promise<void> {
  setLoading({ selecting: true });
  try {
    const outcome = await window.budcomDesktop.selectCompany(companyId);
    await refreshUi();
    await loadCompanies();
    if (!outcome.ok) {
      setBanner(outcome.userMessage, 'warning');
    } else {
      setBanner(outcome.userMessage, 'information');
    }
  } catch {
    setBanner('Company selection failed. Please try again.', 'error');
  } finally {
    setLoading({ selecting: false });
  }
}

export async function handleClearCompany(): Promise<void> {
  setLoading({ selecting: true });
  try {
    await window.budcomDesktop.clearCompany();
    setBanner('Company selection cleared.', 'information');
    await refreshUi();
    await loadCompanies();
  } catch {
    setBanner('Unable to clear company selection.', 'error');
  } finally {
    setLoading({ selecting: false });
  }
}

export function bindLifecycleActions(): void {
  document.getElementById('btn-start-connector')?.addEventListener('click', () => {
    void runLifecycleAction('Starting connector…', () => window.budcomDesktop.startConnector());
  });
  document.getElementById('btn-stop-connector')?.addEventListener('click', () => {
    void runLifecycleAction('Stopping connector…', () => window.budcomDesktop.stopConnector());
  });
  document.getElementById('btn-restart-connector')?.addEventListener('click', () => {
    void runLifecycleAction('Restarting connector…', () => window.budcomDesktop.restartConnector());
  });
}

async function runLifecycleAction(
  message: string,
  action: () => Promise<ConnectorLifecycleStatus>,
): Promise<void> {
  setLoading({ dashboard: true }, message);
  try {
    const status = await action();
    renderLifecycle(status);
    await refreshUi();
  } catch {
    setBanner('Connector lifecycle action failed.', 'error');
  } finally {
    setLoading({ dashboard: false });
  }
}

export function bindSettingsActions(): void {
  const form = document.getElementById('settings-form');
  form?.addEventListener('input', () => {
    settingsDirty = true;
  });
  form?.addEventListener('submit', (event) => {
    event.preventDefault();
    void saveSettings();
  });
  document.getElementById('btn-restore-settings')?.addEventListener('click', () => {
    void restoreSettings();
  });
}

async function saveSettings(): Promise<void> {
  setLoading({ settings: true });
  renderValidationErrors([]);
  try {
    const payload = collectSettingsForm();
    const validation = await window.budcomDesktop.validateSettings(payload);
    if (!validation.ok) {
      renderValidationErrors(validation.errors);
      renderSettingsStatus('Fix validation errors before saving.', true);
      return;
    }
    const result = await window.budcomDesktop.saveSettings(payload);
    if (!result.ok || !result.settings) {
      renderSettingsStatus(result.message, true);
      return;
    }
    settingsDirty = false;
    renderSettingsForm(result.settings);
    renderSettingsStatus(result.message, false);
    setBanner(result.message, result.restartRequired ? 'warning' : 'information');
    await refreshUi();
  } catch {
    renderSettingsStatus('Settings save failed.', true);
  } finally {
    setLoading({ settings: false });
  }
}

async function restoreSettings(): Promise<void> {
  setLoading({ settings: true });
  try {
    const result = await window.budcomDesktop.restoreDefaultSettings();
    if (result.settings) {
      settingsDirty = false;
      renderSettingsForm(result.settings);
    }
    renderSettingsStatus(result.message, !result.ok);
    setBanner(result.message, result.restartRequired ? 'warning' : 'information');
    await refreshUi();
  } catch {
    renderSettingsStatus('Unable to restore defaults.', true);
  } finally {
    setLoading({ settings: false });
  }
}

export function bindDiagnosticsActions(): void {
  document.getElementById('btn-refresh-diagnostics')?.addEventListener('click', () => {
    void refreshDiagnostics();
  });
  document.getElementById('btn-copy-diagnostics')?.addEventListener('click', () => {
    void copyDiagnostics();
  });
  document.getElementById('btn-export-diagnostics')?.addEventListener('click', () => {
    void exportDiagnostics();
  });
  document.getElementById('btn-run-health-check')?.addEventListener('click', () => {
    void runHealthCheckAction();
  });
  document.getElementById('btn-open-logs-folder')?.addEventListener('click', () => {
    void window.budcomDesktop.openLogsFolder();
  });
  document.getElementById('btn-clear-logs')?.addEventListener('click', () => {
    if (window.confirm('Clear nonessential logs? Error logs will be kept in memory.')) {
      void clearLogsAction();
    }
  });
  document.getElementById('btn-reload-renderer')?.addEventListener('click', () => {
    void window.budcomDesktop.reloadRenderer();
  });
}

async function copyDiagnostics(): Promise<void> {
  try {
    const summary = await window.budcomDesktop.copyDiagnosticsSummary();
    await navigator.clipboard.writeText(summary);
    setText('diagnostics-status-message', 'Diagnostics summary copied to clipboard.');
  } catch {
    setText('diagnostics-status-message', 'Unable to copy diagnostics summary.');
  }
}

async function exportDiagnostics(): Promise<void> {
  setLoading({ diagnostics: true });
  try {
    const result = await window.budcomDesktop.exportDiagnosticsBundle();
    setText('diagnostics-status-message', result.ok ? `${result.message} ${result.bundlePath ?? ''}` : result.message);
  } catch {
    setText('diagnostics-status-message', 'Diagnostics export failed.');
  } finally {
    setLoading({ diagnostics: false });
  }
}

async function runHealthCheckAction(): Promise<void> {
  setLoading({ diagnostics: true });
  try {
    const result = await window.budcomDesktop.runHealthCheck();
    setText('diagnostics-status-message', result.message);
    await refreshDiagnostics();
  } finally {
    setLoading({ diagnostics: false });
  }
}

async function clearLogsAction(): Promise<void> {
  const result = await window.budcomDesktop.clearNonessentialLogs();
  setText('diagnostics-status-message', result.message);
  await refreshUi();
}

const SYNC_BUSY_STATUSES = new Set(['running', 'cancelling', 'recovering']);

function formatSyncStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'running':
      return 'Running';
    case 'cancelling':
      return 'Cancelling…';
    case 'cancelled':
      return 'Cancelled';
    case 'recovering':
      return 'Recovering';
    case 'completed':
      return 'Completed';
    case 'failed':
      return 'Failed';
    case 'interrupted':
      return 'Interrupted';
    default:
      return status ?? '—';
  }
}

export function renderLedgers(state: LedgerPageState): void {
  const stats = state.statistics?.statistics;
  const syncStatus = state.progress?.progress.status;
  setText('ledger-stat-total', stats ? String(stats.totalLedgers) : '—');
  setText('ledger-stat-active', stats ? String(stats.activeLedgers) : '—');
  setText('ledger-stat-gst', stats ? String(stats.withGst) : '—');
  setText('ledger-stat-last-sync', stats?.lastSyncedAt ?? 'Never');
  setText('ledger-sync-status', formatSyncStatusLabel(syncStatus));
  setText(
    'ledger-sync-duration',
    state.progress?.progress.durationMs != null ? `${state.progress.progress.durationMs} ms` : '—',
  );
  setText(
    'ledger-storage-status',
    state.storage
      ? `${state.storage.backend} · ${state.storage.databaseHealthy ? 'healthy' : 'unhealthy'}`
      : '—',
  );
  setText('ledger-migration-status', state.storage?.migrationStatus ?? state.progress?.progress.migrationStatus ?? '—');

  const syncButton = document.getElementById('btn-sync-ledgers') as HTMLButtonElement | null;
  const cancelButton = document.getElementById('btn-cancel-ledger-sync');
  const busy = SYNC_BUSY_STATUSES.has(syncStatus ?? '');
  if (syncButton) {
    syncButton.disabled = busy;
  }
  cancelButton?.classList.toggle('hidden', !busy);

  const list = document.getElementById('ledger-list');
  const meta = document.getElementById('ledger-list-meta');
  if (!list || !meta) {
    return;
  }

  if (!state.ok || !state.list) {
    meta.textContent = state.userMessage ?? 'Unable to load ledgers.';
    list.innerHTML = '';
    return;
  }

  meta.textContent = `${state.list.pagination.totalItems} ledgers · page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`;
  list.innerHTML = state.list.items
    .map(
      (ledger) => `
        <div class="ledger-row" role="row">
          <div class="ledger-name" role="cell">${ledger.name}</div>
          <div class="ledger-meta" role="cell">${ledger.parentGroup ?? '—'}</div>
          <div class="ledger-meta" role="cell">${ledger.status}</div>
        </div>`,
    )
    .join('');

  setText('ledger-page-label', `Page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`);
}

export async function loadLedgers(): Promise<void> {
  setLoading({ ledgers: true });
  try {
    const state = await window.budcomDesktop.getLedgers({
      query: ledgerQuery,
      page: ledgerPage,
      pageSize: ledgerPageSize,
    });
    renderLedgers(state);
    if (state.userMessage) {
      setBanner(state.userMessage, 'warning');
    }
  } catch {
    setBanner('Unable to load ledgers.', 'error');
  } finally {
    setLoading({ ledgers: false });
  }
}

async function handleLedgerSync(): Promise<void> {
  setLoading({ syncing: true });
  const progress = document.getElementById('ledger-progress');
  progress?.classList.remove('hidden');
  try {
    await window.budcomDesktop.syncLedgers(false);
    await loadLedgers();
    await refreshUi();
  } catch {
    setBanner('Ledger sync failed.', 'error');
  } finally {
    progress?.classList.add('hidden');
    setLoading({ syncing: false });
  }
}

async function handleCancelLedgerSync(): Promise<void> {
  setLoading({ syncing: true });
  try {
    await window.budcomDesktop.cancelLedgerSync();
    await loadLedgers();
    await refreshUi();
    setBanner('Ledger sync cancellation requested.', 'information');
  } catch {
    setBanner('Unable to cancel ledger sync.', 'error');
  } finally {
    setLoading({ syncing: false });
  }
}

async function handleClearLedgerCache(): Promise<void> {
  if (!window.confirm('Clear the local ledger cache for the selected company?')) {
    return;
  }
  try {
    const result = await window.budcomDesktop.clearLedgerCache();
    setBanner(result.message, result.ok ? 'information' : 'warning');
    await loadLedgers();
  } catch {
    setBanner('Unable to clear ledger cache.', 'error');
  }
}

export function bindLedgerActions(): void {
  document.getElementById('btn-sync-ledgers')?.addEventListener('click', () => {
    void handleLedgerSync();
  });
  document.getElementById('btn-cancel-ledger-sync')?.addEventListener('click', () => {
    void handleCancelLedgerSync();
  });
  document.getElementById('btn-refresh-ledgers')?.addEventListener('click', () => {
    void loadLedgers();
  });
  document.getElementById('btn-clear-ledger-cache')?.addEventListener('click', () => {
    void handleClearLedgerCache();
  });
  document.getElementById('btn-ledger-prev')?.addEventListener('click', () => {
    if (ledgerPage > 1) {
      ledgerPage -= 1;
      void loadLedgers();
    }
  });
  document.getElementById('btn-ledger-next')?.addEventListener('click', () => {
    ledgerPage += 1;
    void loadLedgers();
  });
  document.getElementById('ledger-search-input')?.addEventListener('change', (event) => {
    ledgerQuery = (event.target as HTMLInputElement).value.trim();
    ledgerPage = 1;
    void loadLedgers();
  });
}

export function renderStockItems(state: StockItemPageState): void {
  const stats = state.statistics?.statistics;
  const syncStatus = state.progress?.progress.status;
  setText('stock-item-stat-total', stats ? String(stats.totalStockItems) : '—');
  setText('stock-item-stat-unit', stats ? String(stats.withBaseUnit) : '—');
  setText('stock-item-stat-incomplete', stats ? String(stats.incompleteData) : '—');
  setText('stock-item-stat-last-sync', stats?.lastSyncedAt ?? 'Never');
  setText('stock-item-sync-status', formatSyncStatusLabel(syncStatus));
  setText(
    'stock-item-sync-duration',
    state.progress?.progress.durationMs != null ? `${state.progress.progress.durationMs} ms` : '—',
  );
  setText(
    'stock-item-storage-status',
    state.storage
      ? `${state.storage.backend} · ${state.storage.databaseHealthy ? 'healthy' : 'unhealthy'}`
      : '—',
  );
  setText(
    'stock-item-migration-status',
    state.storage?.migrationStatus ?? state.progress?.progress.migrationStatus ?? '—',
  );

  const syncButton = document.getElementById('btn-sync-stock-items') as HTMLButtonElement | null;
  const cancelButton = document.getElementById('btn-cancel-stock-item-sync');
  const busy = SYNC_BUSY_STATUSES.has(syncStatus ?? '');
  if (syncButton) {
    syncButton.disabled = busy;
  }
  cancelButton?.classList.toggle('hidden', !busy);

  const list = document.getElementById('stock-item-list');
  const meta = document.getElementById('stock-item-list-meta');
  if (!list || !meta) {
    return;
  }

  if (!state.ok || !state.list) {
    meta.textContent = state.userMessage ?? 'Unable to load stock items.';
    list.innerHTML = '';
    return;
  }

  meta.textContent = `${state.list.pagination.totalItems} stock items · page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`;
  list.innerHTML = state.list.items
    .map(
      (item) => `
        <div class="ledger-row" role="row">
          <div class="ledger-name" role="cell">${item.name}</div>
          <div class="ledger-meta" role="cell">${item.parentGroup ?? '—'}</div>
          <div class="ledger-meta" role="cell">${item.baseUnit ?? item.dataQuality}</div>
        </div>`,
    )
    .join('');

  setText('stock-item-page-label', `Page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`);
}

export async function loadStockItems(): Promise<void> {
  setLoading({ stockItems: true });
  try {
    const state = await window.budcomDesktop.getStockItems({
      query: stockItemQuery,
      page: stockItemPage,
      pageSize: stockItemPageSize,
    });
    renderStockItems(state);
    if (state.userMessage) {
      setBanner(state.userMessage, 'warning');
    }
  } catch {
    setBanner('Unable to load stock items.', 'error');
  } finally {
    setLoading({ stockItems: false });
  }
}

async function handleStockItemSync(): Promise<void> {
  setLoading({ syncingStockItems: true });
  const progress = document.getElementById('stock-item-progress');
  progress?.classList.remove('hidden');
  try {
    await window.budcomDesktop.syncStockItems(false);
    await loadStockItems();
    await refreshUi();
  } catch {
    setBanner('Stock item sync failed.', 'error');
  } finally {
    progress?.classList.add('hidden');
    setLoading({ syncingStockItems: false });
  }
}

async function handleCancelStockItemSync(): Promise<void> {
  setLoading({ syncingStockItems: true });
  try {
    await window.budcomDesktop.cancelStockItemSync();
    await loadStockItems();
    await refreshUi();
    setBanner('Stock item sync cancellation requested.', 'information');
  } catch {
    setBanner('Unable to cancel stock item sync.', 'error');
  } finally {
    setLoading({ syncingStockItems: false });
  }
}

async function handleClearStockItemCache(): Promise<void> {
  if (!window.confirm('Clear the local stock item cache for the selected company?')) {
    return;
  }
  try {
    const result = await window.budcomDesktop.clearStockItemCache();
    setBanner(result.message, result.ok ? 'information' : 'warning');
    await loadStockItems();
  } catch {
    setBanner('Unable to clear stock item cache.', 'error');
  }
}

export function bindStockItemActions(): void {
  document.getElementById('btn-sync-stock-items')?.addEventListener('click', () => {
    void handleStockItemSync();
  });
  document.getElementById('btn-cancel-stock-item-sync')?.addEventListener('click', () => {
    void handleCancelStockItemSync();
  });
  document.getElementById('btn-refresh-stock-items')?.addEventListener('click', () => {
    void loadStockItems();
  });
  document.getElementById('btn-clear-stock-item-cache')?.addEventListener('click', () => {
    void handleClearStockItemCache();
  });
  document.getElementById('btn-stock-item-prev')?.addEventListener('click', () => {
    if (stockItemPage > 1) {
      stockItemPage -= 1;
      void loadStockItems();
    }
  });
  document.getElementById('btn-stock-item-next')?.addEventListener('click', () => {
    stockItemPage += 1;
    void loadStockItems();
  });
  document.getElementById('stock-item-search-input')?.addEventListener('change', (event) => {
    stockItemQuery = (event.target as HTMLInputElement).value.trim();
    stockItemPage = 1;
    void loadStockItems();
  });
}

export function bindCompanyActions(): void {
  document.getElementById('btn-refresh-companies')?.addEventListener('click', () => {
    void loadCompanies();
  });
  document.getElementById('btn-clear-company')?.addEventListener('click', () => {
    void handleClearCompany();
  });
  document.getElementById('company-list')?.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const button = target.closest('[data-company-id]') as HTMLElement | null;
    const companyId = button?.getAttribute('data-company-id');
    if (companyId) {
      void handleCompanySelection(companyId);
    }
  });
}

export function bindNavigation(): void {
  document.querySelectorAll('.nav-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const view = button.getAttribute('data-view') as DesktopView | null;
      if (view) {
        activateView(view);
      }
    });
  });
}

export async function startDesktopShell(): Promise<void> {
  bindNavigation();
  bindCompanyActions();
  bindLedgerActions();
  bindStockItemActions();
  bindLifecycleActions();
  bindSettingsActions();
  bindDiagnosticsActions();
  await refreshUi();
  await loadCompanies();
  window.budcomDesktop.onStatusUpdated(() => {
    void refreshUi();
  });
}

if (typeof window !== 'undefined' && window.budcomDesktop) {
  void startDesktopShell();
}
