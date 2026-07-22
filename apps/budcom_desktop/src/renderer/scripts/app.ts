import type {
  CompanyListItemDto,
  DashboardState,
  LogEntry,
  SettingsState,
} from '../../application/types.js';
import type { ConnectorLifecycleStatus } from '../../application/connector-lifecycle-types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getSettings(): Promise<SettingsState>;
  getLifecycleStatus(): Promise<ConnectorLifecycleStatus>;
  startConnector(): Promise<ConnectorLifecycleStatus>;
  stopConnector(): Promise<ConnectorLifecycleStatus>;
  restartConnector(): Promise<ConnectorLifecycleStatus>;
  getCompanies(): Promise<{ items: readonly CompanyListItemDto[]; status: string; reason?: string }>;
  selectCompany(companyId: string): Promise<{ ok: boolean; userMessage: string }>;
  clearCompany(): Promise<unknown>;
  onStatusUpdated(listener: () => void): () => void;
}

declare global {
  interface Window {
    budcomDesktop: DesktopBridge;
  }
}

export type DesktopView = 'dashboard' | 'connection' | 'logs' | 'settings' | 'about';

export interface UiLoadingState {
  readonly dashboard: boolean;
  readonly companies: boolean;
  readonly selecting: boolean;
}

let refreshInFlight = false;

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
  const active = Boolean(state.dashboard || state.companies || state.selecting);
  bar.className = active ? 'loading-bar' : 'loading-bar hidden';
  if (state.selecting) {
    label.textContent = 'Selecting company…';
  } else if (state.companies) {
    label.textContent = 'Fetching companies…';
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

export function renderSettings(settings: SettingsState): void {
  setText('settings-connector-url', settings.connectorUrl);
  setText('settings-api-version', settings.apiVersion);
  setText('settings-desktop-version', settings.desktopVersion);
  setText('settings-erp', settings.erpType);
  setText('settings-poll-interval', `${settings.pollIntervalSeconds} seconds`);
  setText('settings-connector-executable', settings.connectorExecutable);
  setText('settings-connector-port', String(settings.connectorPort));
  setText('settings-auto-start', settings.autoStartConnector ? 'Enabled' : 'Disabled');
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

export function renderLogs(entries: readonly LogEntry[]): void {
  const container = document.getElementById('log-list');
  if (!container) {
    return;
  }
  container.innerHTML = entries
    .map(
      (entry) =>
        `<div class="log-entry log-${entry.level}"><span class="log-time">[${entry.timestamp}]</span> <span class="log-level">${entry.level.toUpperCase()}</span> ${entry.message}</div>`,
    )
    .join('');
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
    renderSettings(settings);
    renderLifecycle(lifecycle);
  } finally {
    setLoading({ dashboard: false });
    refreshInFlight = false;
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
  bindLifecycleActions();
  await refreshUi();
  await loadCompanies();
  window.budcomDesktop.onStatusUpdated(() => {
    void refreshUi();
  });
}

if (typeof window !== 'undefined' && window.budcomDesktop) {
  void startDesktopShell();
}
