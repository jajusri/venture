let refreshInFlight = false;
let currentSettings = null;
let settingsDirty = false;
export function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}
export function setBanner(message, level = 'error') {
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
export function setLoading(state, message = 'Loading…') {
    const bar = document.getElementById('global-loading');
    const label = document.getElementById('loading-message');
    if (!bar || !label) {
        return;
    }
    const active = Boolean(state.dashboard || state.companies || state.selecting || state.settings || state.diagnostics);
    bar.className = active ? 'loading-bar' : 'loading-bar hidden';
    if (state.selecting) {
        label.textContent = 'Selecting company…';
    }
    else if (state.companies) {
        label.textContent = 'Fetching companies…';
    }
    else if (state.settings) {
        label.textContent = 'Saving settings…';
    }
    else if (state.diagnostics) {
        label.textContent = 'Refreshing diagnostics…';
    }
    else {
        label.textContent = message;
    }
}
export function renderDashboard(state) {
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
    }
    else if (state.userMessage) {
        setBanner(state.userMessage, 'warning');
    }
    else {
        setBanner(null);
    }
}
function setInputValue(id, value) {
    const element = document.getElementById(id);
    if (!element) {
        return;
    }
    if (element instanceof HTMLInputElement && element.type === 'checkbox') {
        element.checked = Boolean(value);
        return;
    }
    element.value = String(value);
}
export function renderSettingsForm(settings) {
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
export function renderSettingsStatus(message, isError = false) {
    const status = document.getElementById('settings-status-message');
    if (status) {
        status.textContent = message;
        status.className = isError ? 'panel-meta form-error' : 'panel-meta';
    }
}
export function renderValidationErrors(errors) {
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
function collectSettingsForm() {
    const host = document.getElementById('input-connector-host')?.value ?? '';
    const port = Number.parseInt(document.getElementById('input-connector-port')?.value ?? '8080', 10);
    const autoStart = document.getElementById('input-auto-start')?.checked ?? true;
    const healthPoll = Number.parseInt(document.getElementById('input-health-poll')?.value ?? '5000', 10);
    const startupTimeout = Number.parseInt(document.getElementById('input-startup-timeout')?.value ?? '30000', 10);
    const logLevel = document.getElementById('input-log-level')?.value ?? 'info';
    const tallyHost = document.getElementById('input-tally-host')?.value ?? 'localhost';
    const tallyPort = Number.parseInt(document.getElementById('input-tally-port')?.value ?? '9000', 10);
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
export function renderLifecycle(status) {
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
function renderLogList(containerId, entries) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }
    container.innerHTML = entries.length === 0
        ? '<p class="empty-state">No entries.</p>'
        : entries.map((entry) => `<div class="log-entry log-${entry.level}"><span class="log-time">[${entry.timestamp}]</span> <span class="log-level">${entry.level.toUpperCase()}</span> ${entry.message}</div>`).join('');
}
export function renderLogs(entries) {
    renderLogList('log-list', entries);
}
export function renderDiagnostics(snapshot, message) {
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
export function renderCompanyList(companies, selectedCompanyId, statusText) {
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
export function activateView(view) {
    document.querySelectorAll('.view').forEach((element) => element.classList.remove('active'));
    document.querySelectorAll('.nav-btn').forEach((button) => button.classList.remove('active'));
    document.getElementById(`view-${view}`)?.classList.add('active');
    document.querySelector(`.nav-btn[data-view="${view}"]`)?.classList.add('active');
    if (view === 'diagnostics') {
        void refreshDiagnostics();
    }
}
export async function refreshUi() {
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
    }
    finally {
        setLoading({ dashboard: false });
        refreshInFlight = false;
    }
}
export async function refreshDiagnostics() {
    setLoading({ diagnostics: true });
    try {
        const snapshot = await window.budcomDesktop.refreshDiagnostics();
        renderDiagnostics(snapshot, `Diagnostics refreshed at ${snapshot.generatedAt}`);
    }
    catch {
        setText('diagnostics-status-message', 'Unable to refresh diagnostics.');
    }
    finally {
        setLoading({ diagnostics: false });
    }
}
export async function loadCompanies() {
    setLoading({ companies: true });
    try {
        const bridge = window.budcomDesktop;
        const [companies, dashboard] = await Promise.all([
            bridge.getCompanies(),
            bridge.getDashboardState(),
        ]);
        renderCompanyList(companies.items, dashboard.companyId === '—' ? '' : dashboard.companyId, companies.items.length > 0
            ? `${companies.items.length} companies available`
            : `Discovery status: ${companies.status}`);
    }
    catch {
        setBanner('Unable to load companies from the connector.', 'error');
        renderCompanyList([], '', 'Company discovery failed.');
    }
    finally {
        setLoading({ companies: false });
    }
}
export async function handleCompanySelection(companyId) {
    setLoading({ selecting: true });
    try {
        const outcome = await window.budcomDesktop.selectCompany(companyId);
        await refreshUi();
        await loadCompanies();
        if (!outcome.ok) {
            setBanner(outcome.userMessage, 'warning');
        }
        else {
            setBanner(outcome.userMessage, 'information');
        }
    }
    catch {
        setBanner('Company selection failed. Please try again.', 'error');
    }
    finally {
        setLoading({ selecting: false });
    }
}
export async function handleClearCompany() {
    setLoading({ selecting: true });
    try {
        await window.budcomDesktop.clearCompany();
        setBanner('Company selection cleared.', 'information');
        await refreshUi();
        await loadCompanies();
    }
    catch {
        setBanner('Unable to clear company selection.', 'error');
    }
    finally {
        setLoading({ selecting: false });
    }
}
export function bindLifecycleActions() {
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
async function runLifecycleAction(message, action) {
    setLoading({ dashboard: true }, message);
    try {
        const status = await action();
        renderLifecycle(status);
        await refreshUi();
    }
    catch {
        setBanner('Connector lifecycle action failed.', 'error');
    }
    finally {
        setLoading({ dashboard: false });
    }
}
export function bindSettingsActions() {
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
async function saveSettings() {
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
    }
    catch {
        renderSettingsStatus('Settings save failed.', true);
    }
    finally {
        setLoading({ settings: false });
    }
}
async function restoreSettings() {
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
    }
    catch {
        renderSettingsStatus('Unable to restore defaults.', true);
    }
    finally {
        setLoading({ settings: false });
    }
}
export function bindDiagnosticsActions() {
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
async function copyDiagnostics() {
    try {
        const summary = await window.budcomDesktop.copyDiagnosticsSummary();
        await navigator.clipboard.writeText(summary);
        setText('diagnostics-status-message', 'Diagnostics summary copied to clipboard.');
    }
    catch {
        setText('diagnostics-status-message', 'Unable to copy diagnostics summary.');
    }
}
async function exportDiagnostics() {
    setLoading({ diagnostics: true });
    try {
        const result = await window.budcomDesktop.exportDiagnosticsBundle();
        setText('diagnostics-status-message', result.ok ? `${result.message} ${result.bundlePath ?? ''}` : result.message);
    }
    catch {
        setText('diagnostics-status-message', 'Diagnostics export failed.');
    }
    finally {
        setLoading({ diagnostics: false });
    }
}
async function runHealthCheckAction() {
    setLoading({ diagnostics: true });
    try {
        const result = await window.budcomDesktop.runHealthCheck();
        setText('diagnostics-status-message', result.message);
        await refreshDiagnostics();
    }
    finally {
        setLoading({ diagnostics: false });
    }
}
async function clearLogsAction() {
    const result = await window.budcomDesktop.clearNonessentialLogs();
    setText('diagnostics-status-message', result.message);
    await refreshUi();
}
export function bindCompanyActions() {
    document.getElementById('btn-refresh-companies')?.addEventListener('click', () => {
        void loadCompanies();
    });
    document.getElementById('btn-clear-company')?.addEventListener('click', () => {
        void handleClearCompany();
    });
    document.getElementById('company-list')?.addEventListener('click', (event) => {
        const target = event.target;
        const button = target.closest('[data-company-id]');
        const companyId = button?.getAttribute('data-company-id');
        if (companyId) {
            void handleCompanySelection(companyId);
        }
    });
}
export function bindNavigation() {
    document.querySelectorAll('.nav-btn').forEach((button) => {
        button.addEventListener('click', () => {
            const view = button.getAttribute('data-view');
            if (view) {
                activateView(view);
            }
        });
    });
}
export async function startDesktopShell() {
    bindNavigation();
    bindCompanyActions();
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
