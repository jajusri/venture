import type { DashboardState, LogEntry } from '../../application/types.js';

export interface DesktopBridge {
  getDashboardState(): Promise<DashboardState>;
  getLogs(): Promise<readonly LogEntry[]>;
  getConnectorUrl(): Promise<string>;
  onStatusUpdated(listener: () => void): () => void;
}

declare global {
  interface Window {
    budcomDesktop: DesktopBridge;
  }
}

export type DesktopView = 'dashboard' | 'connection' | 'logs' | 'settings' | 'about';

export function setText(id: string, value: string): void {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
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
  setText('dashboard-sync', state.syncLabel);
  setText('dashboard-last-sync', state.lastSync);
  setText('dashboard-version', state.connectorVersion);
  setText('dashboard-erp', state.erpType);

  setText('connection-detail-indicator', state.connectionIndicator);
  setText('connection-detail-label', state.connectionLabel);
  setText('connection-detail-reachable', String(state.connectorReachable));
  setText('connection-detail-health', state.healthStatus);

  setText('footer-version', state.connectorVersion);
  setText('footer-erp', state.erpType);
  setText('footer-license', state.licenseStatus);
}

export function renderLogs(entries: readonly LogEntry[]): void {
  const container = document.getElementById('log-list');
  if (!container) {
    return;
  }
  container.innerHTML = entries
    .map(
      (entry) =>
        `<div class="log-entry log-${entry.level}"><span>[${entry.timestamp}]</span> ${entry.message}</div>`,
    )
    .join('');
}

export function activateView(view: DesktopView): void {
  document.querySelectorAll('.view').forEach((element) => element.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach((button) => button.classList.remove('active'));

  document.getElementById(`view-${view}`)?.classList.add('active');
  document.querySelector(`.nav-btn[data-view="${view}"]`)?.classList.add('active');
}

export async function refreshUi(): Promise<void> {
  const bridge = window.budcomDesktop;
  const [state, logs, connectorUrl] = await Promise.all([
    bridge.getDashboardState(),
    bridge.getLogs(),
    bridge.getConnectorUrl(),
  ]);
  renderDashboard(state);
  renderLogs(logs);
  setText('settings-connector-url', connectorUrl);
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
  await refreshUi();
  window.budcomDesktop.onStatusUpdated(() => {
    void refreshUi();
  });
}

if (typeof window !== 'undefined' && window.budcomDesktop) {
  void startDesktopShell();
}
