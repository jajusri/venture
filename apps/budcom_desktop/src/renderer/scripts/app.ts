import type {
  ActivePairingSessionView,
  CompanyListItemDto,
  DashboardState,
  DiagnosticsSnapshot,
  LedgerPageState,
  LedgerSyncProgressDto,
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
  TrustedPairingDeviceSummary,
} from '../../application/types.js';
import type { ConnectorLifecycleStatus } from '../../application/connector-lifecycle-types.js';
import type { MobileAccessStatus } from '../../application/mobile-access-status-service.js';
import type { RemovableVolumeInfo } from '../../application/private-storage/removable-volume-enumerator.js';
import type { ChooseStorageModeResult, StorageGateState } from '../../application/private-storage/private-storage-types.js';
import { renderStorageGate } from './storage-gate.js';

// Renderer code is emitted as browser ESM, while application services are emitted as CommonJS.
// Importing a runtime value from application/connector-error.js therefore passes TypeScript but
// fails when Chromium links the packaged module. Keep this presentation-only mapping inside the
// renderer boundary. Type-only application imports remain safe because they are erased.
function mapDiscoveryUserMessage(status: string, reason?: string): string {
  if (reason) return reason;
  switch (status) {
    case 'EMPTY':
      return 'No companies were found in Tally.';
    case 'UNAVAILABLE':
    case 'TIMEOUT':
      return 'Tally is unavailable for company discovery.';
    case 'DENIED':
      return 'Company discovery is not permitted by connector policy.';
    case 'MALFORMED':
      return 'Company discovery returned unexpected data.';
    default:
      return 'Unable to load companies from the connector.';
  }
}

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getMobileAccessStatus(): Promise<MobileAccessStatus>;
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
  syncLedgers(incremental?: boolean): Promise<LedgerSyncResult>;
  cancelLedgerSync(): Promise<LedgerSyncProgressResult>;
  clearLedgerCache(): Promise<{ ok: boolean; message: string }>;
  getStockItems(payload?: { query?: string; page?: number; pageSize?: number }): Promise<StockItemPageState>;
  syncStockItems(incremental?: boolean): Promise<StockItemSyncResult>;
  cancelStockItemSync(): Promise<StockItemSyncProgressResult>;
  clearStockItemCache(): Promise<{ ok: boolean; message: string }>;
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
  chooseStorageMode(
    input:
      | { mode: 'standard'; confirmSwitch?: boolean }
      | { mode: 'private-removable'; driveLetter: string; confirmSwitch?: boolean },
  ): Promise<ChooseStorageModeResult>;
  retryStorageConnection(): Promise<StorageGateState>;
  onStatusUpdated(listener: () => void): () => void;
}

declare global {
  interface Window {
    budcomDesktop: DesktopBridge;
  }
}

export type DesktopView = 'dashboard' | 'connection' | 'ledgers' | 'stock-items' | 'pairing' | 'logs' | 'diagnostics' | 'settings' | 'about';

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
let trailingRefreshQueued = false;
let activeView: DesktopView = 'dashboard';
let companySelectionInFlight = false;
let latestDashboardState: DashboardState | null = null;
let latestDiagnosticsSnapshot: DiagnosticsSnapshot | null = null;
let transientStatus: { readonly label: string; readonly tone: string } | null = null;
let lifecycleStatus: ConnectorLifecycleStatus['state'] | null = null;
let confirmedCompanyName: string | null = null;
let currentSettings: SettingsState | null = null;
let settingsDirty = false;
let ledgerPage = 1;
let ledgerQuery = '';
const ledgerPageSize = 25;
let stockItemPage = 1;
let stockItemQuery = '';
const stockItemPageSize = 25;
const SYNC_PROGRESS_POLL_INTERVAL_MS = 1_000;
const TERMINAL_SYNC_STATUSES = new Set(['completed', 'cancelled', 'failed', 'interrupted']);
const PAIRING_STATUS_POLL_INTERVAL_MS = 2_000;
const PAIRING_SESSION_TERMINAL_STATES = new Set(['redeemed', 'expired', 'cancelled', 'failed']);
let pairingCapability: SecurePairingCapability | null = null;
let pairingSession: ActivePairingSessionView | null = null;
let trustedPairingDevices: readonly TrustedPairingDeviceSummary[] = [];
let pairingStatusPollActive = false;
let pairingStatusPollTimer: number | null = null;
let pairingStatusRequestInFlight = false;
let pairingCountdownTimer: number | null = null;
let pairingActionInFlight = false;
let ledgerProgressPollActive = false;
let ledgerProgressPollTimer: number | null = null;
let ledgerProgressRequestInFlight = false;
let ledgerSyncActionInFlight = false;
let stockItemProgressPollActive = false;
let stockItemProgressPollTimer: number | null = null;
let stockItemProgressRequestInFlight = false;
let stockItemSyncActionInFlight = false;

// TD-014: bounded automatic recovery for a transient dashboard/company failure that leaves no
// further ConnectorLifecycleService state transition to hang a re-check off of. Never a
// permanent poll — see reconcileBoundedRecovery()/runDashboardRecoveryCycle() below.
const DASHBOARD_RECOVERY_DELAYS_MS = [2_000, 5_000, 10_000, 20_000, 30_000];
let recoveryGeneration = 0;
let recoveryTimer: number | null = null;
let companyLoadGeneration = 0;
let lastCompanyLoadFailed = false;

export function setText(id: string, value: string): void {
  const element = document.getElementById(id);
  if (element && element.textContent !== value) {
    element.textContent = value;
  }
}

export function setBanner(message: string | null, level: 'error' | 'warning' | 'information' = 'error'): void {
  const notification = document.getElementById('app-notification');
  if (!notification) {
    return;
  }
  if (!message) {
    notification.className = 'app-notification hidden';
    notification.textContent = '';
    return;
  }
  const kindLabel = level === 'error' ? 'Error' : level === 'warning' ? 'Notice' : 'Success';
  notification.className = `app-notification app-notification-${level}`;
  notification.textContent = `${kindLabel}: ${message}`;
}

export function setLoading(state: Partial<UiLoadingState>, message = 'Loading…'): void {
  if (state.dashboard === undefined && state.selecting === undefined) {
    return;
  }
  const active = Boolean(state.dashboard || state.selecting);
  let label = message;
  let tone = 'refreshing';
  if (state.selecting) {
    label = 'Selecting company…';
    tone = 'selecting';
  }
  transientStatus = active ? { label, tone } : null;
  renderConnectionDisplay();
}

function formatLicenceStatus(status: string | undefined): string {
  const normalized = status?.trim().toLowerCase() ?? '';
  if (normalized.includes('evaluat')) return 'Licence: Evaluating…';
  if (normalized === 'active' || normalized.includes('licensed')) return 'Licence: Active';
  if (normalized.includes('expired')) return 'Licence: Expired';
  if (normalized.includes('error') || normalized.includes('invalid')) return 'Licence: Needs attention';
  return 'Licence: Checking…';
}

export interface DisplayConnectionState {
  readonly label: 'Connected' | 'Starting connector…' | 'Reconnecting…' | 'Connecting…' | 'Not connected';
  readonly tone: 'connected' | 'connecting' | 'disconnected';
  readonly overall: 'Working normally' | 'Ready — select a company' | 'Connecting…' | 'Needs attention' | 'Not connected';
}

export function getDisplayConnectionState(): DisplayConnectionState {
  const state = latestDashboardState;
  const diagnostics = latestDiagnosticsSnapshot;
  const health = (state?.healthStatus ?? diagnostics?.healthStatus ?? '').trim().toLowerCase();
  const healthy = health === 'ok' || health === 'healthy' || health.startsWith('ok ');
  const degraded = ['degraded', 'unhealthy', 'warning', 'error'].some((value) =>
    health.includes(value));
  const definitivelyDisconnected =
    lifecycleStatus === 'disconnected' ||
    lifecycleStatus === 'failed' ||
    state?.connectorReachable === false ||
    diagnostics?.healthReachable === false;
  let display: DisplayConnectionState = {
    label: 'Connecting…',
    tone: 'connecting',
    overall: 'Connecting…',
  };
  if (definitivelyDisconnected) {
    display = { label: 'Not connected', tone: 'disconnected', overall: 'Not connected' };
  } else if (
    (lifecycleStatus === 'connected' ||
      (lifecycleStatus === null && state?.connectionIndicator === 'connected')) &&
    state?.connectorReachable === true &&
    healthy
  ) {
    const activeCompany = state.sessionStatus === 'ACTIVE' && state.companyName !== '—';
    display = {
      label: 'Connected',
      tone: 'connected',
      overall: activeCompany ? 'Working normally' : 'Ready — select a company',
    };
  } else if (
    lifecycleStatus === 'connected' &&
    state?.connectorReachable === true &&
    degraded
  ) {
    display = { label: 'Connected', tone: 'connecting', overall: 'Needs attention' };
  }
  if (lifecycleStatus === 'starting') {
    display = { label: 'Starting connector…', tone: 'connecting', overall: 'Connecting…' };
  }
  if (lifecycleStatus === 'reconnecting') {
    display = { label: 'Reconnecting…', tone: 'connecting', overall: 'Connecting…' };
  }
  if (
    confirmedCompanyName &&
    !definitivelyDisconnected &&
    lifecycleStatus !== 'starting' &&
    lifecycleStatus !== 'reconnecting'
  ) {
    display = { label: 'Connected', tone: 'connected', overall: 'Working normally' };
  }
  return display;
}

function formatConnectionTone(tone: DisplayConnectionState['tone']): string {
  switch (tone) {
    case 'connected':
      return 'Connected';
    case 'connecting':
      return 'Connecting';
    case 'disconnected':
      return 'Disconnected';
    default:
      return tone;
  }
}

function formatSessionStatus(status: string): string {
  switch (status) {
    case 'NO_COMPANY_SELECTED':
      return 'No company selected';
    case 'ACTIVE':
      return 'Active';
    case 'INVALID':
      return 'Invalid';
    case 'DISCONNECTED':
      return 'Disconnected';
    case 'ERROR':
      return 'Error';
    default:
      return status;
  }
}

function renderConnectionDisplay(): void {
  const state = latestDashboardState;
  const display = getDisplayConnectionState();
  const footerLabel = transientStatus?.label ?? display.label;
  const footerTone = transientStatus?.tone ?? display.tone;
  setText('header-connection-label', display.label);
  setText('dashboard-connection', display.label);
  setText('connection-detail-label', display.label);
  setText('connection-detail-indicator', formatConnectionTone(display.tone));
  setText('diag-connector-status', display.label);
  setText('diag-overall', display.overall);
  const headerIndicator = document.getElementById('connection-indicator');
  const headerIndicatorClass = `indicator indicator-${display.tone}`;
  if (headerIndicator && headerIndicator.className !== headerIndicatorClass) {
    headerIndicator.className = headerIndicatorClass;
  }
  setText('footer-connection-status', footerLabel);
  const indicator = document.getElementById('footer-connection-indicator');
  const indicatorClass = `status-dot status-${footerTone}`;
  if (indicator && indicator.className !== indicatorClass) {
    indicator.className = indicatorClass;
  }
  const company = confirmedCompanyName ?? state?.companyName;
  setText('footer-company', company && company !== '—' ? `· ${company}` : '');
  setText('footer-license', formatLicenceStatus(state?.licenseStatus));
}

function hasActiveCompany(): boolean {
  return Boolean(
    latestDashboardState?.sessionStatus === 'ACTIVE' &&
    latestDashboardState.companyName !== '—',
  );
}

function updateCompanyRequiredButton(buttonId: string, operationInFlight = false): void {
  const button = document.getElementById(buttonId) as HTMLButtonElement | null;
  if (!button) {
    return;
  }
  const companyRequired = !hasActiveCompany();
  button.disabled = operationInFlight || companyRequired;
  button.title = companyRequired ? 'Select a company before synchronizing.' : '';
}

function updateCompanyRequiredActions(): void {
  updateCompanyRequiredButton('btn-sync-ledgers', ledgerSyncActionInFlight);
  updateCompanyRequiredButton('btn-sync-stock-items', stockItemSyncActionInFlight);
}

export function renderDashboard(
  state: DashboardState,
  options: {
    readonly includeRefreshTimestamp?: boolean;
    readonly showStatusFeedback?: boolean;
  } = {},
): void {
  latestDashboardState = state;
  confirmedCompanyName = null;
  setText('app-title', state.windowTitle);
  setText('header-version', state.connectorVersion);
  const companyName = state.companyName !== '—' ? state.companyName : 'No company selected';
  setText('header-company', companyName);
  setText('header-sync', state.syncLabel);
  setText('header-last-sync', state.lastSync);

  setText('dashboard-health', `Health: ${state.healthStatus}`);
  setText('dashboard-company-name', companyName);
  setText('dashboard-erp-name', state.erpName);
  if (options.includeRefreshTimestamp ?? true) {
    setText('dashboard-last-refresh', state.lastRefresh);
  }
  setText('dashboard-sync', state.syncLabel);
  setText('dashboard-last-sync', state.lastSync);
  setText('dashboard-version', state.connectorVersion);
  setText('dashboard-desktop-version', state.desktopVersion);

  setText('connection-detail-reachable', state.connectorReachable ? 'Yes' : 'No');
  setText('connection-detail-health', state.healthStatus);
  setText('connection-detail-session', formatSessionStatus(state.sessionStatus));

  renderConnectionDisplay();
  updateCompanyRequiredActions();

  if (options.showStatusFeedback ?? true) {
    if (state.userMessage) {
      setBanner(state.userMessage, 'warning');
    } else {
      setBanner(null);
    }
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
  setInputValue('input-connector-bind-mode', settings.connectorBindMode);
  setInputValue('input-connector-host', settings.connectorHost);
  setInputValue('input-connector-port', settings.connectorPort);
  setInputValue('input-reachable-lan-url', settings.reachableLanUrl ?? 'Not available in local-only mode');
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

  const hostInput = document.getElementById('input-connector-host') as HTMLInputElement | null;
  if (hostInput) {
    const isLocalOnly = settings.connectorBindMode === 'local-only';
    hostInput.readOnly = isLocalOnly;
    if (isLocalOnly) {
      hostInput.value = '127.0.0.1';
    }
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
  const bindMode =
    ((document.getElementById('input-connector-bind-mode') as HTMLSelectElement | null)?.value
      ?? 'local-only') as 'local-only' | 'trusted-lan';
  const host = (document.getElementById('input-connector-host') as HTMLInputElement | null)?.value ?? '';
  const port = Number.parseInt((document.getElementById('input-connector-port') as HTMLInputElement | null)?.value ?? '8080', 10);
  const autoStart = (document.getElementById('input-auto-start') as HTMLInputElement | null)?.checked ?? true;
  const healthPoll = Number.parseInt((document.getElementById('input-health-poll') as HTMLInputElement | null)?.value ?? '5000', 10);
  const startupTimeout = Number.parseInt((document.getElementById('input-startup-timeout') as HTMLInputElement | null)?.value ?? '30000', 10);
  const logLevel = (document.getElementById('input-log-level') as HTMLSelectElement | null)?.value ?? 'info';
  const tallyHost = (document.getElementById('input-tally-host') as HTMLInputElement | null)?.value ?? 'localhost';
  const tallyPort = Number.parseInt((document.getElementById('input-tally-port') as HTMLInputElement | null)?.value ?? '9000', 10);

  return {
    connectorBindMode: bindMode,
    connectorHost: bindMode === 'local-only' ? '127.0.0.1' : host,
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
  lifecycleStatus = status.state;
  renderConnectionDisplay();
  setText('lifecycle-state', status.stateLabel);
  setText('lifecycle-managed', status.managedByDesktop ? 'Yes' : 'No');
  setText('lifecycle-external', status.externalProcessDetected ? 'Yes' : 'No');
  setText('lifecycle-last-health', status.lastSuccessfulHealthCheck ?? '—');
  setText('lifecycle-restart-attempts', String(status.restartAttempts));
  setText('lifecycle-exit-code', status.processExitCode === null ? '—' : String(status.processExitCode));
  setText('lifecycle-port', String(status.connectorPort));

}

export const HOSTILE_HTML_SAMPLES = [
  '<img src=x onerror=alert(1)>',
  '<script>window.__xss = true</script>',
  '"><svg onload=alert(1)>',
  '& < > " \'',
];

function clearElement(element: HTMLElement): void {
  element.replaceChildren();
}

function appendTextElement(parent: HTMLElement, tagName: string, className: string, text: string): HTMLElement {
  const element = document.createElement(tagName);
  element.className = className;
  element.textContent = text;
  parent.appendChild(element);
  return element;
}

function renderLogList(containerId: string, entries: readonly LogEntry[]): void {
  const container = document.getElementById(containerId);
  if (!container) {
    return;
  }
  const signature = entries.map((entry) => [
    entry.id,
    entry.timestamp,
    entry.level,
    entry.message,
  ].join('\u0000')).join('\u0001');
  if (container.dataset.logSignature === signature) {
    return;
  }

  const previousScrollTop = container.scrollTop;
  const previousScrollHeight = container.scrollHeight;
  const wasAtTop = previousScrollTop <= 1;
  const existing = new Map<string, HTMLElement>();
  container.querySelectorAll<HTMLElement>('[data-log-id]').forEach((row) => {
    const id = row.dataset.logId;
    if (id) {
      existing.set(id, row);
    }
  });

  if (entries.length === 0) {
    clearElement(container);
    const empty = document.createElement('p');
    empty.className = 'empty-state';
    empty.textContent = 'No entries.';
    container.appendChild(empty);
    container.dataset.logSignature = signature;
    return;
  }

  container.querySelector('.empty-state')?.remove();
  entries.forEach((entry, index) => {
    const entrySignature = [entry.timestamp, entry.level, entry.message].join('\u0000');
    let row = existing.get(entry.id);
    if (!row) {
      row = document.createElement('div');
      row.dataset.logId = entry.id;
    }
    if (row.dataset.logEntrySignature !== entrySignature) {
      clearElement(row);
      row.dataset.logEntrySignature = entrySignature;
      row.className = `log-entry log-${entry.level}`;
      appendTextElement(row, 'span', 'log-time', `[${entry.timestamp}]`);
      row.append(document.createTextNode(' '));
      appendTextElement(row, 'span', 'log-level', entry.level.toUpperCase());
      row.append(document.createTextNode(` ${entry.message}`));
    }
    const currentAtIndex = container.children.item(index);
    if (currentAtIndex !== row) {
      container.insertBefore(row, currentAtIndex);
    }
    existing.delete(entry.id);
  });
  for (const stale of existing.values()) {
    stale.remove();
  }
  container.dataset.logSignature = signature;
  if (!wasAtTop) {
    container.scrollTop = previousScrollTop + (container.scrollHeight - previousScrollHeight);
  }
}

export function renderLogs(entries: readonly LogEntry[]): void {
  renderLogList('log-list', entries);
}

export function renderDiagnostics(snapshot: DiagnosticsSnapshot, message?: string): void {
  latestDiagnosticsSnapshot = snapshot;
  setText('diag-desktop-version', snapshot.desktopVersion);
  setText('diag-connector-version', snapshot.connectorVersion ?? '—');
  setText('diag-bundled-connector-version', snapshot.bundledConnectorVersion ?? '—');
  setText('diag-runtime-versions', `${snapshot.electronVersion} / ${snapshot.nodeVersion}`);
  setText('diag-os', `${snapshot.platform} ${snapshot.osRelease} (${snapshot.architecture})`);
  setText('diag-uptime', `${snapshot.uptimeSeconds}s`);
  setText('diag-connector-url', snapshot.connectorBaseUrl);
  setText('diag-connector-host', snapshot.connectorBindHost);
  try {
    setText('diag-connector-port', new URL(snapshot.connectorBaseUrl).port || '—');
  } catch {
    setText('diag-connector-port', '—');
  }
  setText('diag-process-state', snapshot.connectorProcessState);
  setText('diag-ownership', snapshot.connectorOwnership);
  setText('diag-pid', snapshot.connectorPid === null ? '—' : String(snapshot.connectorPid));
  setText('diag-health', snapshot.healthReachable ? 'Healthy' : 'Needs attention');
  const lastHealth = snapshot.lastSuccessfulHealthCheck
    ? new Date(snapshot.lastSuccessfulHealthCheck).toLocaleString()
    : 'Not available';
  setText('diag-last-health', lastHealth);
  const company = latestDashboardState?.companyName;
  setText(
    'diag-selected-company',
    snapshot.selectedCompanyPresent && company && company !== '—' ? company : 'None selected',
  );
  setText('diag-session', snapshot.sessionDisplayLabel);
  setText('diag-config-status', `${snapshot.configStatus} · ${snapshot.configSource}`);
  setText('diag-log-file', snapshot.logFile.available ? (snapshot.logFile.basename ?? 'available') : 'unavailable');
  setText('diag-generated-at', snapshot.generatedAt);
  renderConnectionDisplay();
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
    clearElement(container);
    const empty = document.createElement('p');
    empty.className = 'empty-state';
    empty.textContent = 'No companies available.';
    container.appendChild(empty);
    return;
  }

  clearElement(container);
  for (const company of companies) {
    const selected = company.id === selectedCompanyId;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `company-item${selected ? ' selected' : ''}`;
    button.dataset.companyId = company.id;
    button.setAttribute('role', 'radio');
    button.setAttribute('aria-checked', String(selected));
    button.tabIndex = selected || (!selectedCompanyId && container.children.length === 0) ? 0 : -1;
    const radio = appendTextElement(button, 'span', 'company-radio', '');
    radio.setAttribute('aria-hidden', 'true');
    appendTextElement(button, 'span', 'company-name', company.name);
    appendTextElement(button, 'span', 'company-id', company.id);
    container.appendChild(button);
  }
}

export function activateView(view: DesktopView): void {
  activeView = view;
  if (view !== 'ledgers') {
    stopLedgerProgressPolling();
  }
  if (view !== 'stock-items') {
    stopStockItemProgressPolling();
  }
  if (view !== 'pairing') {
    stopPairingStatusPolling();
  }
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
  if (view === 'pairing') {
    void loadPairingPanel();
  }
}

export async function refreshUi(options: { readonly showLoading?: boolean } = {}): Promise<void> {
  if (refreshInFlight) {
    trailingRefreshQueued = true;
    return;
  }
  refreshInFlight = true;
  const showLoading = options.showLoading ?? false;
  if (showLoading) {
    setLoading({ dashboard: true }, 'Refreshing…');
  }
  try {
    do {
      trailingRefreshQueued = false;
      const bridge = window.budcomDesktop;
      const [state, logs, settings, lifecycle] = await Promise.all([
        bridge.getDashboardState(),
        bridge.getLogs(),
        bridge.getSettings(),
        bridge.getLifecycleStatus(),
      ]);
      renderDashboard(state, {
        includeRefreshTimestamp: showLoading,
        showStatusFeedback: showLoading,
      });
      renderLogs(logs);
      if (!settingsDirty) {
        renderSettingsForm(settings);
      }
      renderLifecycle(lifecycle);
    } while (trailingRefreshQueued);
  } finally {
    if (showLoading) {
      setLoading({ dashboard: false });
    }
    refreshInFlight = false;
  }
}

export async function refreshDiagnostics(): Promise<void> {
  setLoading({ diagnostics: true });
  try {
    const snapshot = await window.budcomDesktop.refreshDiagnostics();
    renderDiagnostics(snapshot, 'Diagnostics refreshed.');
  } catch {
    setText('diagnostics-status-message', 'Unable to refresh diagnostics.');
  } finally {
    setLoading({ diagnostics: false });
  }
}

/**
 * TD-014: guarded by [companyLoadGeneration] so an older, slower call can never overwrite a
 * newer one's result — e.g. a bounded-recovery retry started before a manual Refresh click, but
 * the manual click's own request completes first. The older call's eventual resolution/rejection
 * is a no-op once a newer call has started. [lastCompanyLoadFailed] is the signal
 * reconcileBoundedRecovery() reads to decide whether company state still needs recovery.
 */
export async function loadCompanies(): Promise<void> {
  const generation = ++companyLoadGeneration;
  const refreshBtn = document.getElementById('btn-refresh-companies') as HTMLButtonElement | null;
  if (refreshBtn) {
    refreshBtn.disabled = true;
    refreshBtn.classList.add('busy');
  }
  setLoading({ companies: true });
  try {
    const bridge = window.budcomDesktop;
    const [companies, dashboard] = await Promise.all([
      bridge.getCompanies(),
      bridge.getDashboardState(),
    ]);
    if (generation !== companyLoadGeneration) {
      return;
    }
    // TD-014 continuation: this call already fetches a fresh DashboardState to resolve the
    // selected company id below — render it too, so the Connection/Sync/Version summary the
    // user is looking at actually updates on a manual Refresh click instead of only the company
    // list. Quiet (no refresh-timestamp bump, no status banner) since this isn't the user-facing
    // "Refreshing…" flow that refreshUi({ showLoading: true }) drives.
    renderDashboard(dashboard, { includeRefreshTimestamp: false, showStatusFeedback: false });
    renderCompanyList(
      companies.items,
      dashboard.companyId === '—' ? '' : dashboard.companyId,
      companies.items.length > 0
        ? `${companies.items.length} companies available`
        : mapDiscoveryUserMessage(companies.status, companies.reason),
    );
    lastCompanyLoadFailed = false;
  } catch {
    if (generation !== companyLoadGeneration) {
      return;
    }
    setBanner('Unable to load companies from the connector.', 'error');
    renderCompanyList([], '', 'Company discovery failed.');
    lastCompanyLoadFailed = true;
  } finally {
    if (generation === companyLoadGeneration) {
      if (refreshBtn) {
        refreshBtn.disabled = false;
        refreshBtn.classList.remove('busy');
      }
      setLoading({ companies: false });
    }
  }
}

/**
 * TD-014 continuation: previously observed after a Desktop restart — the backend/Connector
 * session can auto-recover a previously-selected company (e.g. ESTIMATION) slightly after it
 * first becomes reachable, so the very first post-restart snapshot can still read
 * NO_COMPANY_SELECTED even though the backend is about to settle on an active company on its
 * own. isDashboardHealthy() treating "reachable" alone as done let the bounded recovery loop
 * stop immediately on that first snapshot, leaving the renderer showing "no company" forever
 * until the user manually reselected it — even though the backend already had it. Requiring the
 * session to have actually settled (not just be reachable) before calling recovery "done" closes
 * that race: the loop now keeps polling, on the same existing bounded schedule, until either a
 * company becomes active or the schedule is exhausted — unchanged behavior for a genuinely
 * company-less installation, which still ends the same way it always did once the bounded
 * attempts run out.
 */
function isDashboardHealthy(): boolean {
  return (
    latestDashboardState?.connectorReachable === true
    && latestDashboardState.sessionStatus !== 'NO_COMPANY_SELECTED'
  );
}

/** Cancels any pending bounded-recovery retry without scheduling a new one — used on teardown. */
export function stopBoundedRecovery(): void {
  recoveryGeneration += 1;
  if (recoveryTimer !== null) {
    window.clearTimeout(recoveryTimer);
    recoveryTimer = null;
  }
}

function scheduleDashboardRecoveryAttempt(generation: number, attemptIndex: number): void {
  recoveryTimer = window.setTimeout(
    () => void runDashboardRecoveryCycle(generation, attemptIndex),
    DASHBOARD_RECOVERY_DELAYS_MS[attemptIndex],
  );
}

/**
 * TD-014: the actual bounded retry — re-fetches both dashboard and company state together (never
 * just one), so a recovery cycle can't leave Company stale while Connection recovers or vice
 * versa. [generation] must still match [recoveryGeneration] at each checkpoint or this run has
 * been superseded (a newer reconcileBoundedRecovery() call, e.g. from a real lifecycle
 * transition or a manual refresh, takes priority) and quietly stops rather than fighting it.
 */
async function runDashboardRecoveryCycle(generation: number, attemptIndex: number): Promise<void> {
  if (generation !== recoveryGeneration) {
    return;
  }
  await refreshUi({ showLoading: false });
  if (generation !== recoveryGeneration) {
    return;
  }
  await loadCompanies();
  if (generation !== recoveryGeneration) {
    return;
  }
  if (isDashboardHealthy() && !lastCompanyLoadFailed) {
    recoveryTimer = null;
    return;
  }
  const nextAttemptIndex = attemptIndex + 1;
  if (nextAttemptIndex >= DASHBOARD_RECOVERY_DELAYS_MS.length) {
    recoveryTimer = null;
    return;
  }
  scheduleDashboardRecoveryAttempt(generation, nextAttemptIndex);
}

/**
 * TD-014: call after any point-in-time refresh (startup, a lifecycle status push, an explicit
 * user action) to decide whether bounded automatic recovery is still needed. Healthy now (both
 * dashboard reachable and the last company load succeeded) cancels any pending retry — including
 * one scheduled by an now-superseded, since-resolved failure. Still unhealthy starts a fresh
 * bounded cycle from attempt 0, discarding whatever cycle (if any) was already in flight, so
 * overlapping triggers never stack concurrent retry chains.
 */
export function reconcileBoundedRecovery(): void {
  if (isDashboardHealthy() && !lastCompanyLoadFailed) {
    if (recoveryTimer !== null) {
      window.clearTimeout(recoveryTimer);
      recoveryTimer = null;
    }
    return;
  }
  recoveryGeneration += 1;
  const generation = recoveryGeneration;
  if (recoveryTimer !== null) {
    window.clearTimeout(recoveryTimer);
  }
  scheduleDashboardRecoveryAttempt(generation, 0);
}

export async function handleCompanySelection(companyId: string): Promise<void> {
  if (companySelectionInFlight) {
    return;
  }
  companySelectionInFlight = true;
  const companyButtons = Array.from(
    document.querySelectorAll<HTMLButtonElement>('[data-company-id]'),
  );
  companyButtons.forEach((button) => {
    button.disabled = true;
  });
  setText('company-list-status', 'Selecting company…');
  transientStatus = { label: 'Selecting company…', tone: 'selecting' };
  renderConnectionDisplay();
  try {
    const outcome = await window.budcomDesktop.selectCompany(companyId);
    if (!outcome.ok) {
      setText('company-list-status', outcome.userMessage);
      setBanner(outcome.userMessage, 'warning');
    } else {
      companyButtons.forEach((button) => {
        const selected = button.dataset.companyId === companyId;
        button.classList.toggle('selected', selected);
        button.setAttribute('aria-checked', String(selected));
        button.tabIndex = selected ? 0 : -1;
      });
      setText('company-list-status', outcome.userMessage);
      const selectedCompany = companyButtons.find(
        (button) => button.dataset.companyId === companyId,
      )?.textContent?.trim();
      confirmedCompanyName = selectedCompany || null;
      if (latestDashboardState) {
        latestDashboardState = {
          ...latestDashboardState,
          companyId,
          companyName: selectedCompany || latestDashboardState.companyName,
          connectionIndicator: 'connected',
          connectionLabel: 'Connected',
          connectorReachable: true,
        };
      }
    }
  } catch {
    setText('company-list-status', 'Company selection failed. Please try again.');
    setBanner('Company selection failed. Please try again.', 'error');
  } finally {
    transientStatus = null;
    renderConnectionDisplay();
    companyButtons.forEach((button) => {
      button.disabled = false;
    });
    companySelectionInFlight = false;
  }
}

export async function handleClearCompany(): Promise<void> {
  const clearBtn = document.getElementById('btn-clear-company') as HTMLButtonElement | null;
  if (clearBtn) {
    clearBtn.disabled = true;
    clearBtn.classList.add('busy');
  }
  setLoading({ selecting: true });
  try {
    await window.budcomDesktop.clearCompany();
    await refreshUi({ showLoading: false });
    await loadCompanies();
    setText('company-list-status', 'Company selection cleared.');
    setBanner('Company selection cleared.', 'information');
  } catch {
    setText('company-list-status', 'Unable to clear company selection. Please try again.');
    setBanner('Unable to clear company selection.', 'error');
  } finally {
    if (clearBtn) {
      clearBtn.disabled = false;
      clearBtn.classList.remove('busy');
    }
    setLoading({ selecting: false });
  }
}

const LIFECYCLE_BUTTON_IDS = ['btn-start-connector', 'btn-stop-connector', 'btn-restart-connector'] as const;

function setLifecycleBusy(busy: boolean): void {
  for (const id of LIFECYCLE_BUTTON_IDS) {
    const btn = document.getElementById(id) as HTMLButtonElement | null;
    if (!btn) continue;
    btn.disabled = busy;
    btn.classList.toggle('busy', busy);
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

function setLifecycleStatusMessage(message: string | null, isError: boolean): void {
  const status = document.getElementById('lifecycle-status-message');
  if (status) {
    status.textContent = message ?? '';
    status.className = isError ? 'panel-meta form-error' : 'panel-meta';
  }
}

async function runLifecycleAction(
  message: string,
  action: () => Promise<ConnectorLifecycleStatus>,
): Promise<void> {
  setLifecycleBusy(true);
  setLoading({ dashboard: true }, message);
  try {
    const status = await action();
    renderLifecycle(status);
    setLifecycleStatusMessage(status.userMessage, status.state === 'failed');
    setBanner(status.userMessage, status.state === 'failed' ? 'error' : 'information');
    await refreshUi({ showLoading: false });
    // TD-014: Start/Restart is exactly the post-startup timing window the defect was found in —
    // reconcile in case this refresh landed during the same transient unhealthy window.
    reconcileBoundedRecovery();
  } catch {
    const failureMessage = 'Connector action failed. Check the Logs view for details.';
    setLifecycleStatusMessage(failureMessage, true);
    setBanner(failureMessage, 'error');
  } finally {
    setLifecycleBusy(false);
    setLoading({ dashboard: false });
  }
}

/**
 * Fills the (read-only, Advanced-Connector-Settings-only) host field from the same live
 * network detection the Mobile Access / pairing panel uses — a normal user switching to
 * Trusted-LAN mode never has to know or type a private IPv4 address themselves (TD-012).
 * Leaves the field blank with a plain-language reason when no eligible network is detected yet,
 * rather than a raw IPv4-validation error — validation still runs at save time as a backstop.
 */
async function applyDetectedTrustedLanHost(hostInput: HTMLInputElement): Promise<void> {
  try {
    const status = await window.budcomDesktop.getMobileAccessStatus();
    if (status.trustedLanEligible && status.activeNetwork) {
      hostInput.value = status.activeNetwork.ipv4;
      renderSettingsStatus('Detected your network address automatically.', false);
    } else {
      hostInput.value = '';
      renderSettingsStatus(
        status.trustedLanBlockedReason
          ?? 'Could not detect a private network yet. Connect this computer to Wi-Fi or Ethernet, then reselect Trusted LAN.',
        true,
      );
    }
  } catch {
    hostInput.value = '';
    renderSettingsStatus('Could not detect your network address. Try again in a moment.', true);
  }
}

export function bindSettingsActions(): void {
  const form = document.getElementById('settings-form');
  form?.addEventListener('input', () => {
    settingsDirty = true;
  });
  document.getElementById('input-connector-bind-mode')?.addEventListener('change', (event) => {
    const target = event.target as HTMLSelectElement;
    const hostInput = document.getElementById('input-connector-host') as HTMLInputElement | null;
    if (!hostInput) {
      return;
    }
    const isLocalOnly = target.value === 'local-only';
    hostInput.readOnly = true;
    if (isLocalOnly) {
      hostInput.value = '127.0.0.1';
    } else {
      // Trusted-LAN: the operator must never have to look up or type their own machine's LAN
      // IPv4 address (see TD-012, docs/technical-debt/registry.md) — auto-detect it the same
      // way the Mobile Access panel does, via the one shared network-readiness source of truth.
      void applyDetectedTrustedLanHost(hostInput);
    }
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
    await refreshUi({ showLoading: false });
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
    await refreshUi({ showLoading: false });
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
    setText('diagnostics-status-message', result.message);
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
  await refreshUi({ showLoading: false });
}

const SYNC_BUSY_STATUSES = new Set(['running', 'cancelling', 'recovering']);

function calculateSyncPercentage(progress: LedgerSyncProgressDto): number | null {
  const total = progress.totalExpected;
  if (total === undefined || total === null || total < 0) {
    return null;
  }
  if (total === 0) {
    return progress.status === 'completed' ? 100 : 0;
  }
  return Math.min(100, Math.max(0, Math.floor((progress.itemsProcessed / total) * 100)));
}

function formatSyncDuration(durationMs: number | null): string {
  if (durationMs === null) {
    return '—';
  }
  if (durationMs < 1_000) {
    return `${durationMs} ms`;
  }
  return `${(durationMs / 1_000).toFixed(1)} s`;
}

function setModuleProgressError(module: 'ledger' | 'stock-item', message: string | null): void {
  const error = document.getElementById(`${module}-progress-error`);
  if (!error) {
    return;
  }
  setText(`${module}-progress-error`, message ?? '');
  error.classList.toggle('hidden', !message);
}

export function renderModuleSyncProgress(
  module: 'ledger' | 'stock-item',
  progress: LedgerSyncProgressDto,
  lastSyncedAt: string | null,
): void {
  const label = module === 'ledger' ? 'Ledger' : 'Stock Item';
  const percentage = calculateSyncPercentage(progress);
  const total = progress.totalExpected;
  const totalKnown = total !== undefined && total !== null && total >= 0;
  let message = formatSyncStatusLabel(progress.status);
  if (SYNC_BUSY_STATUSES.has(progress.status) && !totalKnown) {
    message = 'Preparing synchronization…';
  } else if (progress.status === 'completed') {
    message = `✓ ${label} Synchronization Completed`;
  }

  setText(`${module}-progress-message`, message);
  setText(
    `${module}-progress-count`,
    totalKnown ? `${progress.itemsProcessed} / ${total}` : `Processed: ${progress.itemsProcessed}`,
  );
  setText(`${module}-progress-percentage`, percentage === null ? '—' : `${percentage}%`);
  setText(`${module}-progress-added`, String(progress.itemsAdded));
  setText(`${module}-progress-updated`, String(progress.itemsUpdated));
  setText(`${module}-progress-skipped`, String(progress.itemsSkipped));
  setText(`${module}-progress-failed`, String(progress.itemsFailed));
  setText(`${module}-progress-duration`, formatSyncDuration(progress.durationMs));
  setText(`${module}-progress-last-sync`, lastSyncedAt ?? 'Never');

  const bar = document.getElementById(`${module}-progress-bar`);
  const width = `${percentage ?? 0}%`;
  if (bar && bar.style.width !== width) {
    bar.style.width = width;
  }
  const track = bar?.parentElement;
  if (track && track.getAttribute('aria-valuenow') !== String(percentage ?? 0)) {
    track.setAttribute('aria-valuenow', String(percentage ?? 0));
  }
  const errorMessage = progress.status === 'failed' ? progress.lastError : null;
  setModuleProgressError(module, errorMessage);
}

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
  if (state.progress) {
    renderModuleSyncProgress('ledger', state.progress.progress, stats?.lastSyncedAt ?? null);
  }

  const cancelButton = document.getElementById('btn-cancel-ledger-sync');
  const busy = SYNC_BUSY_STATUSES.has(syncStatus ?? '');
  updateCompanyRequiredButton('btn-sync-ledgers', busy || ledgerSyncActionInFlight);
  cancelButton?.classList.toggle('hidden', !busy);
  if (busy && activeView === 'ledgers') {
    startLedgerProgressPolling();
  }

  const list = document.getElementById('ledger-list');
  const meta = document.getElementById('ledger-list-meta');
  if (!list || !meta) {
    return;
  }

  if (!state.ok || !state.list) {
    meta.textContent = state.userMessage ?? 'Unable to load ledgers.';
    clearElement(list);
    return;
  }

  meta.textContent = `${state.list.pagination.totalItems} ledgers · page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`;
  clearElement(list);
  for (const ledger of state.list.items) {
    const row = document.createElement('div');
    row.className = 'ledger-row';
    row.setAttribute('role', 'row');
    appendTextElement(row, 'div', 'ledger-name', ledger.name).setAttribute('role', 'cell');
    appendTextElement(row, 'div', 'ledger-meta', ledger.parentGroup ?? '—').setAttribute('role', 'cell');
    appendTextElement(row, 'div', 'ledger-meta', ledger.status).setAttribute('role', 'cell');
    list.appendChild(row);
  }

  setText('ledger-page-label', `Page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`);
}

export async function loadLedgers(options: { readonly showLoading?: boolean } = {}): Promise<void> {
  const showLoading = options.showLoading ?? true;
  if (showLoading) {
    setLoading({ ledgers: true });
  }
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
    if (showLoading) {
      setLoading({ ledgers: false });
    }
  }
}

function stopLedgerProgressPolling(): void {
  ledgerProgressPollActive = false;
  if (ledgerProgressPollTimer !== null) {
    window.clearTimeout(ledgerProgressPollTimer);
    ledgerProgressPollTimer = null;
  }
}

async function pollLedgerProgress(): Promise<void> {
  if (!ledgerProgressPollActive || activeView !== 'ledgers' || ledgerProgressRequestInFlight) {
    return;
  }
  ledgerProgressRequestInFlight = true;
  try {
    const state = await window.budcomDesktop.getLedgers({
      query: ledgerQuery,
      page: ledgerPage,
      pageSize: ledgerPageSize,
    });
    if (state.progress) {
      renderModuleSyncProgress(
        'ledger',
        state.progress.progress,
        state.statistics?.statistics.lastSyncedAt ?? null,
      );
      if (TERMINAL_SYNC_STATUSES.has(state.progress.progress.status)) {
        stopLedgerProgressPolling();
        return;
      }
    }
  } catch {
    setText('ledger-progress-message', 'Progress temporarily unavailable.');
  } finally {
    ledgerProgressRequestInFlight = false;
  }
  if (ledgerProgressPollActive) {
    ledgerProgressPollTimer = window.setTimeout(() => void pollLedgerProgress(), SYNC_PROGRESS_POLL_INTERVAL_MS);
  }
}

export function startLedgerProgressPolling(): void {
  if (ledgerProgressPollActive || activeView !== 'ledgers') {
    return;
  }
  ledgerProgressPollActive = true;
  ledgerProgressPollTimer = window.setTimeout(
    () => void pollLedgerProgress(),
    SYNC_PROGRESS_POLL_INTERVAL_MS,
  );
}

async function handleLedgerSync(): Promise<void> {
  if (ledgerSyncActionInFlight) {
    return;
  }
  ledgerSyncActionInFlight = true;
  const syncButton = document.getElementById('btn-sync-ledgers') as HTMLButtonElement | null;
  if (syncButton) {
    syncButton.disabled = true;
  }
  setText('ledger-progress-message', 'Preparing synchronization…');
  setModuleProgressError('ledger', null);
  startLedgerProgressPolling();
  try {
    const result = await window.budcomDesktop.syncLedgers(false);
    renderModuleSyncProgress('ledger', result.progress, result.statistics.lastSyncedAt);
    await loadLedgers({ showLoading: false });
  } catch {
    const message = 'Ledger sync failed.';
    setModuleProgressError('ledger', message);
    setBanner(message, 'error');
  } finally {
    ledgerSyncActionInFlight = false;
    stopLedgerProgressPolling();
    updateCompanyRequiredButton('btn-sync-ledgers');
  }
}

async function handleCancelLedgerSync(): Promise<void> {
  try {
    const result = await window.budcomDesktop.cancelLedgerSync();
    renderModuleSyncProgress('ledger', result.progress, null);
    await loadLedgers({ showLoading: false });
    setBanner('Ledger sync cancellation requested.', 'information');
  } catch {
    const message = 'Unable to cancel ledger sync.';
    setModuleProgressError('ledger', message);
    setBanner(message, 'error');
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
  if (state.progress) {
    renderModuleSyncProgress('stock-item', state.progress.progress, stats?.lastSyncedAt ?? null);
  }

  const cancelButton = document.getElementById('btn-cancel-stock-item-sync');
  const busy = SYNC_BUSY_STATUSES.has(syncStatus ?? '');
  updateCompanyRequiredButton('btn-sync-stock-items', busy || stockItemSyncActionInFlight);
  cancelButton?.classList.toggle('hidden', !busy);
  if (busy && activeView === 'stock-items') {
    startStockItemProgressPolling();
  }

  const list = document.getElementById('stock-item-list');
  const meta = document.getElementById('stock-item-list-meta');
  if (!list || !meta) {
    return;
  }

  if (!state.ok || !state.list) {
    meta.textContent = state.userMessage ?? 'Unable to load stock items.';
    clearElement(list);
    return;
  }

  meta.textContent = `${state.list.pagination.totalItems} stock items · page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`;
  clearElement(list);
  for (const item of state.list.items) {
    const row = document.createElement('div');
    row.className = 'ledger-row';
    row.setAttribute('role', 'row');
    appendTextElement(row, 'div', 'ledger-name', item.name).setAttribute('role', 'cell');
    appendTextElement(row, 'div', 'ledger-meta', item.parentGroup ?? '—').setAttribute('role', 'cell');
    appendTextElement(row, 'div', 'ledger-meta', item.baseUnit ?? item.dataQuality).setAttribute('role', 'cell');
    list.appendChild(row);
  }

  setText('stock-item-page-label', `Page ${state.list.pagination.page} of ${state.list.pagination.totalPages}`);
}

export async function loadStockItems(options: { readonly showLoading?: boolean } = {}): Promise<void> {
  const showLoading = options.showLoading ?? true;
  if (showLoading) {
    setLoading({ stockItems: true });
  }
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
    if (showLoading) {
      setLoading({ stockItems: false });
    }
  }
}

function stopStockItemProgressPolling(): void {
  stockItemProgressPollActive = false;
  if (stockItemProgressPollTimer !== null) {
    window.clearTimeout(stockItemProgressPollTimer);
    stockItemProgressPollTimer = null;
  }
}

export function disposeSyncProgressPolling(): void {
  stopLedgerProgressPolling();
  stopStockItemProgressPolling();
  stopPairingStatusPolling();
}

// ---------------------------------------------------------------------------
// Secure Mobile Pairing panel
// ---------------------------------------------------------------------------

function stopPairingCountdown(): void {
  if (pairingCountdownTimer !== null) {
    window.clearInterval(pairingCountdownTimer);
    pairingCountdownTimer = null;
  }
}

function startPairingCountdown(expiresAt: string): void {
  stopPairingCountdown();
  const tick = (): void => {
    const remainingMs = new Date(expiresAt).getTime() - Date.now();
    const el = document.getElementById('pairing-countdown');
    if (!el) {
      return;
    }
    if (remainingMs <= 0) {
      el.textContent = 'Expired';
      stopPairingCountdown();
      return;
    }
    const totalSeconds = Math.ceil(remainingMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    el.textContent = `${minutes}:${String(seconds).padStart(2, '0')}`;
  };
  tick();
  pairingCountdownTimer = window.setInterval(tick, 1_000);
}

function stopPairingStatusPolling(): void {
  pairingStatusPollActive = false;
  stopPairingCountdown();
  if (pairingStatusPollTimer !== null) {
    window.clearTimeout(pairingStatusPollTimer);
    pairingStatusPollTimer = null;
  }
}

async function pollPairingStatus(): Promise<void> {
  if (!pairingStatusPollActive || activeView !== 'pairing' || pairingStatusRequestInFlight) {
    return;
  }
  if (!pairingSession || PAIRING_SESSION_TERMINAL_STATES.has(pairingSession.state)) {
    stopPairingStatusPolling();
    return;
  }
  pairingStatusRequestInFlight = true;
  try {
    pairingSession = await window.budcomDesktop.getPairingStatus();
    renderPairingSession();
    if (PAIRING_SESSION_TERMINAL_STATES.has(pairingSession.state)) {
      stopPairingStatusPolling();
      if (pairingSession.state === 'redeemed') {
        await loadTrustedPairingDevices();
      }
      return;
    }
  } catch {
    // A single missed poll must not interrupt an otherwise-active session — the countdown timer
    // and the next tick continue independently.
  } finally {
    pairingStatusRequestInFlight = false;
  }
  if (pairingStatusPollActive) {
    pairingStatusPollTimer = window.setTimeout(() => void pollPairingStatus(), PAIRING_STATUS_POLL_INTERVAL_MS);
  }
}

export function startPairingStatusPolling(): void {
  if (pairingStatusPollActive || activeView !== 'pairing') {
    return;
  }
  pairingStatusPollActive = true;
  pairingStatusPollTimer = window.setTimeout(() => void pollPairingStatus(), PAIRING_STATUS_POLL_INTERVAL_MS);
}

function renderPairingCapability(): void {
  const disabledEl = document.getElementById('pairing-state-disabled');
  const readyEl = document.getElementById('pairing-state-ready');
  const unavailableEl = document.getElementById('pairing-state-unavailable');
  if (!pairingCapability) {
    return;
  }
  const state = pairingCapability.state;
  disabledEl?.classList.toggle('hidden', state !== 'disabled');
  readyEl?.classList.toggle('hidden', state !== 'ready');
  unavailableEl?.classList.toggle(
    'hidden',
    state !== 'unavailable' && state !== 'restart_required',
  );

  if (state === 'ready') {
    setText('pairing-connector-name', pairingCapability.connectorName ?? 'This Connector');
    setText(
      'pairing-trusted-count',
      pairingCapability.trustedDeviceCount !== null ? String(pairingCapability.trustedDeviceCount) : '—',
    );
  }
  if (state === 'unavailable' || state === 'restart_required') {
    setText(
      'pairing-unavailable-message',
      pairingCapability.userMessage
        ?? (state === 'restart_required'
          ? 'Restart the Connector for secure mobile pairing to take effect.'
          : 'The Connector is not currently available.'),
    );
  }

  const startButton = document.getElementById('btn-start-pairing') as HTMLButtonElement | null;
  if (startButton) {
    startButton.disabled = state !== 'ready' || pairingActionInFlight;
  }
}

function renderPairingSession(): void {
  const activeEl = document.getElementById('pairing-session-active');
  const redeemedEl = document.getElementById('pairing-session-redeemed');
  const endedEl = document.getElementById('pairing-session-ended');
  if (!pairingSession) {
    activeEl?.classList.add('hidden');
    redeemedEl?.classList.add('hidden');
    endedEl?.classList.add('hidden');
    return;
  }

  const state = pairingSession.state;
  activeEl?.classList.toggle('hidden', state !== 'active');
  redeemedEl?.classList.toggle('hidden', state !== 'redeemed');
  endedEl?.classList.toggle('hidden', state !== 'expired' && state !== 'cancelled' && state !== 'failed');

  if (state === 'active') {
    const qrImage = document.getElementById('pairing-qr-image') as HTMLImageElement | null;
    if (qrImage && pairingSession.qrDataUrl) {
      qrImage.src = pairingSession.qrDataUrl;
    }
    setText('pairing-short-code', pairingSession.shortCode ?? '—');
    if (pairingSession.expiresAt) {
      startPairingCountdown(pairingSession.expiresAt);
    }
  } else {
    stopPairingCountdown();
  }

  if (state === 'redeemed') {
    setText(
      'pairing-redeemed-message',
      pairingSession.redeemedDeviceLabel
        ? `Paired successfully: ${pairingSession.redeemedDeviceLabel}`
        : 'Device paired successfully.',
    );
  }

  if (state === 'expired' || state === 'cancelled' || state === 'failed') {
    const label = state === 'expired'
      ? 'This pairing session expired.'
      : state === 'cancelled'
        ? 'Pairing was cancelled.'
        : (pairingSession.userMessage ?? 'Pairing failed.');
    setText('pairing-ended-message', label);
  }
}

function renderTrustedPairingDevices(): void {
  const list = document.getElementById('pairing-device-list');
  if (!list) {
    return;
  }
  clearElement(list);
  if (trustedPairingDevices.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty-state';
    empty.textContent = 'No devices paired yet.';
    list.appendChild(empty);
    return;
  }
  for (const device of trustedPairingDevices) {
    const row = document.createElement('div');
    row.className = 'pairing-device-row';

    const label = document.createElement('span');
    label.className = 'pairing-device-label';
    label.textContent = device.deviceLabel ?? 'Unnamed device';
    row.appendChild(label);

    const meta = document.createElement('span');
    meta.className = 'pairing-device-meta';
    meta.textContent = `Paired ${device.firstPairedAt} · Last used ${device.lastUsedAt ?? 'never'}`;
    row.appendChild(meta);

    const status = document.createElement('span');
    status.className = `pairing-device-status pairing-device-status-${device.status}`;
    status.textContent = device.status === 'active' ? 'Active' : 'Revoked';
    row.appendChild(status);

    if (device.status === 'active') {
      const revokeButton = document.createElement('button');
      revokeButton.type = 'button';
      revokeButton.className = 'action-btn secondary';
      revokeButton.textContent = 'Revoke';
      revokeButton.setAttribute('data-credential-id', device.credentialId);
      row.appendChild(revokeButton);
    }

    list.appendChild(row);
  }
}

async function loadTrustedPairingDevices(): Promise<void> {
  try {
    trustedPairingDevices = await window.budcomDesktop.listTrustedPairingDevices();
  } catch {
    trustedPairingDevices = [];
  }
  renderTrustedPairingDevices();
}

export async function loadPairingPanel(): Promise<void> {
  try {
    pairingCapability = await window.budcomDesktop.getSecurePairingCapability();
  } catch {
    pairingCapability = {
      state: 'unavailable',
      connectorName: null,
      transportFingerprint: null,
      trustedDeviceCount: null,
      userMessage: 'Unable to reach the Connector.',
    };
  }
  renderPairingCapability();
  if (pairingCapability.state === 'ready') {
    await loadTrustedPairingDevices();
  }
}

async function handleEnableSecurePairing(): Promise<void> {
  if (pairingActionInFlight) {
    return;
  }
  pairingActionInFlight = true;
  try {
    const result = await window.budcomDesktop.enableSecurePairing();
    setBanner(result.message, result.ok ? 'information' : 'error');
    await loadPairingPanel();
  } catch {
    setBanner('Unable to enable secure mobile pairing.', 'error');
  } finally {
    pairingActionInFlight = false;
  }
}

async function handleStartPairing(): Promise<void> {
  if (pairingActionInFlight) {
    return;
  }
  pairingActionInFlight = true;
  try {
    pairingSession = await window.budcomDesktop.startPairing();
    renderPairingSession();
    if (pairingSession.state === 'active') {
      startPairingStatusPolling();
    } else if (pairingSession.userMessage) {
      setBanner(pairingSession.userMessage, 'error');
    }
  } catch {
    setBanner('Unable to start pairing.', 'error');
  } finally {
    pairingActionInFlight = false;
    renderPairingCapability();
  }
}

async function handleCancelPairing(): Promise<void> {
  try {
    pairingSession = await window.budcomDesktop.cancelPairing();
    renderPairingSession();
  } catch {
    setBanner('Unable to cancel pairing.', 'error');
  } finally {
    stopPairingStatusPolling();
  }
}

async function handleRevokeTrustedDevice(credentialId: string): Promise<void> {
  if (!window.confirm('Revoke this device? It will immediately lose access to this Connector.')) {
    return;
  }
  try {
    const result = await window.budcomDesktop.revokeTrustedPairingDevice(credentialId);
    setBanner(result.message, result.ok ? 'information' : 'warning');
    await loadTrustedPairingDevices();
    await loadPairingPanel();
  } catch {
    setBanner('Unable to revoke device.', 'error');
  }
}

export function bindPairingActions(): void {
  document.getElementById('btn-enable-secure-pairing')?.addEventListener('click', () => {
    void handleEnableSecurePairing();
  });
  document.getElementById('btn-start-pairing')?.addEventListener('click', () => {
    void handleStartPairing();
  });
  document.getElementById('btn-cancel-pairing')?.addEventListener('click', () => {
    void handleCancelPairing();
  });
  document.getElementById('btn-retry-pairing-availability')?.addEventListener('click', () => {
    void loadPairingPanel();
  });
  document.querySelectorAll('.pairing-done-btn').forEach((button) => {
    button.addEventListener('click', () => {
      pairingSession = null;
      renderPairingSession();
    });
  });
  document.getElementById('pairing-device-list')?.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const button = target.closest('[data-credential-id]') as HTMLElement | null;
    const credentialId = button?.getAttribute('data-credential-id');
    if (credentialId) {
      void handleRevokeTrustedDevice(credentialId);
    }
  });
}

async function pollStockItemProgress(): Promise<void> {
  if (!stockItemProgressPollActive || activeView !== 'stock-items' || stockItemProgressRequestInFlight) {
    return;
  }
  stockItemProgressRequestInFlight = true;
  try {
    const state = await window.budcomDesktop.getStockItems({
      query: stockItemQuery,
      page: stockItemPage,
      pageSize: stockItemPageSize,
    });
    if (state.progress) {
      renderModuleSyncProgress(
        'stock-item',
        state.progress.progress,
        state.statistics?.statistics.lastSyncedAt ?? null,
      );
      if (TERMINAL_SYNC_STATUSES.has(state.progress.progress.status)) {
        stopStockItemProgressPolling();
        return;
      }
    }
  } catch {
    setText('stock-item-progress-message', 'Progress temporarily unavailable.');
  } finally {
    stockItemProgressRequestInFlight = false;
  }
  if (stockItemProgressPollActive) {
    stockItemProgressPollTimer = window.setTimeout(
      () => void pollStockItemProgress(),
      SYNC_PROGRESS_POLL_INTERVAL_MS,
    );
  }
}

export function startStockItemProgressPolling(): void {
  if (stockItemProgressPollActive || activeView !== 'stock-items') {
    return;
  }
  stockItemProgressPollActive = true;
  stockItemProgressPollTimer = window.setTimeout(
    () => void pollStockItemProgress(),
    SYNC_PROGRESS_POLL_INTERVAL_MS,
  );
}

async function handleStockItemSync(): Promise<void> {
  if (stockItemSyncActionInFlight) {
    return;
  }
  stockItemSyncActionInFlight = true;
  const syncButton = document.getElementById('btn-sync-stock-items') as HTMLButtonElement | null;
  if (syncButton) {
    syncButton.disabled = true;
  }
  setText('stock-item-progress-message', 'Preparing synchronization…');
  setModuleProgressError('stock-item', null);
  startStockItemProgressPolling();
  try {
    const result = await window.budcomDesktop.syncStockItems(false);
    renderModuleSyncProgress('stock-item', result.progress, result.statistics.lastSyncedAt);
    await loadStockItems({ showLoading: false });
  } catch {
    const message = 'Stock item sync failed.';
    setModuleProgressError('stock-item', message);
    setBanner(message, 'error');
  } finally {
    stockItemSyncActionInFlight = false;
    stopStockItemProgressPolling();
    updateCompanyRequiredButton('btn-sync-stock-items');
  }
}

async function handleCancelStockItemSync(): Promise<void> {
  try {
    const result = await window.budcomDesktop.cancelStockItemSync();
    renderModuleSyncProgress('stock-item', result.progress, null);
    await loadStockItems({ showLoading: false });
    setBanner('Stock item sync cancellation requested.', 'information');
  } catch {
    const message = 'Unable to cancel stock item sync.';
    setModuleProgressError('stock-item', message);
    setBanner(message, 'error');
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
    // TD-014: an explicit manual refresh always fires immediately (loadCompanies()'s own
    // generation token already makes it win over any stale in-flight automatic retry); once it
    // settles, reconcile decides whether bounded recovery is still needed or can stand down.
    void loadCompanies().then(() => reconcileBoundedRecovery());
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
  document.getElementById('company-list')?.addEventListener('keydown', (event) => {
    const keyboardEvent = event as KeyboardEvent;
    const target = (event.target as HTMLElement).closest<HTMLButtonElement>('[role="radio"]');
    if (!target) {
      return;
    }
    const radios = Array.from(
      document.querySelectorAll<HTMLButtonElement>('#company-list [role="radio"]:not(:disabled)'),
    );
    const currentIndex = radios.indexOf(target);
    if (keyboardEvent.key === ' ' || keyboardEvent.key === 'Enter') {
      keyboardEvent.preventDefault();
      target.click();
      return;
    }
    const direction = keyboardEvent.key === 'ArrowDown' || keyboardEvent.key === 'ArrowRight'
      ? 1
      : keyboardEvent.key === 'ArrowUp' || keyboardEvent.key === 'ArrowLeft'
        ? -1
        : 0;
    if (direction === 0 || radios.length === 0) {
      return;
    }
    keyboardEvent.preventDefault();
    const next = radios[(currentIndex + direction + radios.length) % radios.length];
    radios.forEach((radio) => {
      radio.tabIndex = radio === next ? 0 : -1;
    });
    next?.focus();
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

// TD-025: guards against a theoretical re-entrant renderStorageGate() call — e.g. a second
// desktop:status-updated event arriving while an earlier one is still awaiting the user's response
// to the storage-unavailable overlay. Real-world loss/recovery is a single transition edge (the
// watchdog stops itself before firing once), so this is a narrow safety net, not a fix for an
// observed double-invocation.
let storageGateCheckInFlight = false;

async function ensureStorageGateReady(): Promise<void> {
  if (storageGateCheckInFlight) return;
  storageGateCheckInFlight = true;
  try {
    const status = await window.budcomDesktop.getStorageStatus();
    if (status.kind !== 'ready') {
      await renderStorageGate();
    }
  } finally {
    storageGateCheckInFlight = false;
  }
}

export async function startDesktopShell(): Promise<void> {
  // Storage-mode decision (first run) or "storage not connected" must be resolved before the
  // normal dashboard flow queries the Connector — otherwise the very first thing a first-run
  // user would see is a confusing "cannot reach connector" error behind the setup prompt.
  // renderStorageGate() blocks (showing the appropriate screen) until resolved, then this
  // function re-enters itself once to run the normal flow below against the now-ready backend.
  const storageReady = await renderStorageGate();
  if (!storageReady) {
    await startDesktopShell();
    return;
  }

  bindNavigation();
  bindCompanyActions();
  bindLedgerActions();
  bindStockItemActions();
  bindLifecycleActions();
  bindSettingsActions();
  bindDiagnosticsActions();
  bindPairingActions();

  // Registered before the initial refreshUi()/loadCompanies() pull below (previously this was
  // wired up only after that pull completed) — a fast lifecycle transition on the main-process
  // side (e.g. a connector that reaches 'connected' within the same window as the very first
  // render) could push its desktop:status-updated event before any listener existed to receive
  // it, silently dropping it. If the session already had an active company selected (the common
  // case on relaunch), TD-014's bounded recovery below is satisfied on unrelated criteria and
  // never retries, so nothing else would ever re-poll and the renderer could stay stuck on a
  // stale state (e.g. "Starting connector…") indefinitely. A push arriving during the pull below
  // now simply coalesces into it via refreshUi()'s own trailingRefreshQueued guard.
  window.budcomDesktop.onStatusUpdated(() => {
    // TD-014: a real lifecycle transition refreshes BOTH dashboard and company state together
    // (never just the Connection card), then reconciles — a fresh transition always takes
    // priority over whatever bounded-recovery cycle (if any) was already in flight.
    void (async () => {
      // TD-025: this event also fires when the main-process private-storage watchdog detects
      // mid-session loss (it stops the Connector and marks storageGateState 'unavailable' before
      // calling notifyRenderer()) — previously this branch went straight to the ordinary
      // refreshUi()/loadCompanies() path, which only ever showed a generic "Disconnected" status
      // built from the now-stopped Connector's health, indistinguishable from an unrelated
      // network/Connector problem. Re-running the exact same blocking renderStorageGate() flow
      // used at startup reuses the purpose-built "Private BUDCOM storage is not connected" screen
      // (Retry re-resolves the vault, including at a new drive letter; Locate reopens the guarded
      // TD-033 setup flow) instead of a misleading generic message — and blocks the ordinary
      // refresh from running at all until storage is genuinely ready again, so no stale/misleading
      // Connector status is ever shown underneath it.
      await ensureStorageGateReady();
      await refreshUi({ showLoading: false });
      await loadCompanies();
      reconcileBoundedRecovery();
    })();
  });

  await refreshUi({ showLoading: false });
  await loadCompanies();
  // TD-014: the initial pair above can transiently fail with no further lifecycle transition to
  // hang a re-check off of (the defect's exact root cause) — reconcile decides right away
  // whether bounded automatic recovery is needed.
  reconcileBoundedRecovery();
  window.addEventListener('beforeunload', () => {
    disposeSyncProgressPolling();
    stopBoundedRecovery();
  }, { once: true });
}

if (typeof window !== 'undefined' && window.budcomDesktop) {
  void startDesktopShell();
}
