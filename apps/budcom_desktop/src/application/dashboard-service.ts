import { toUserMessage } from './connector-error.js';
import { ConnectorHttpClient } from './connector-http-client.js';
import {
  getConnectionLabel,
  mapConnectionIndicator,
} from './connection-status-mapper.js';
import type { DashboardState, HealthResponse, SessionSnapshotResponse, SessionValidationResponse, SettingsState } from './types.js';
import { LogService } from './log-service.js';
import {
  formatSelectionTime,
  formatTimestamp,
  mapSessionDisplayStatus,
  resolveCompanyId,
  resolveCompanyName,
  resolveErpName,
} from './session-display-mapper.js';
import {
  formatLastSync,
  getSyncLabel,
  mapSyncDisplayStatus,
} from './sync-status-mapper.js';

export const DESKTOP_WINDOW_TITLE = 'Business OS Tally Connector';
export const DESKTOP_VERSION = '0.4.2';
export const POLL_INTERVAL_SECONDS = 5;

export interface DashboardServiceOptions {
  readonly connectorBaseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly logService?: LogService;
  readonly maxAttempts?: number;
  readonly retryBaseDelayMs?: number;
}

export class DashboardService {
  private readonly client: ConnectorHttpClient;
  private readonly logService: LogService;
  private readonly connectorBaseUrl: string;
  private lastRefreshAt: string | null = null;

  constructor(options: DashboardServiceOptions) {
    this.connectorBaseUrl = options.connectorBaseUrl;
    this.client = new ConnectorHttpClient({
      baseUrl: options.connectorBaseUrl,
      fetchImpl: options.fetchImpl,
      maxAttempts: options.maxAttempts,
      retryBaseDelayMs: options.retryBaseDelayMs,
    });
    this.logService = options.logService ?? new LogService();
  }

  getLogService(): LogService {
    return this.logService;
  }

  getSettingsState(overrides: Partial<SettingsState> = {}): SettingsState {
    return {
      connectorUrl: this.connectorBaseUrl,
      apiVersion: '1.0.0',
      desktopVersion: DESKTOP_VERSION,
      erpType: 'tally',
      pollIntervalSeconds: POLL_INTERVAL_SECONDS,
      connectorExecutable: '—',
      connectorPort: 8080,
      autoStartConnector: true,
      ...overrides,
    };
  }

  async getDashboardState(): Promise<DashboardState> {
    let connectorReachable = false;
    let health: HealthResponse | null = null;
    let session: SessionSnapshotResponse | null = null;
    let userMessage: string | null = null;

    try {
      health = await this.client.getHealth();
      connectorReachable = true;
      this.logService.append('information', `Health status: ${health.status}`);
    } catch (error) {
      const message = toUserMessage(error);
      userMessage = message;
      this.logService.append('error', `Connector health request failed: ${message}`);
    }

    if (connectorReachable) {
      try {
        session = await this.client.getSession();
        this.logService.append(
          'information',
          session.session.selectedCompany
            ? `Session company: ${session.session.selectedCompany.name}`
            : 'No company selected in session',
        );
      } catch (error) {
        const message = toUserMessage(error);
        userMessage = message;
        this.logService.append('warning', `Session request failed: ${message}`);
      }
    }

    let validation: SessionValidationResponse | null = null;
    if (connectorReachable && session?.session.selectedCompany) {
      try {
        validation = await this.client.validateSession();
        if (validation.status !== 'SUCCESS') {
          this.logService.append('warning', `Session validation: ${validation.status}`);
        }
      } catch (error) {
        const message = toUserMessage(error);
        this.logService.append('warning', `Session validation failed: ${message}`);
      }
    }

    this.lastRefreshAt = new Date().toISOString();

    const connectionIndicator = mapConnectionIndicator(connectorReachable, health);
    const syncStatus = mapSyncDisplayStatus(health?.services ?? []);
    const licensing = health?.services.find((service) => service.name === 'Licensing');

    return {
      windowTitle: DESKTOP_WINDOW_TITLE,
      connectorVersion: health?.connectorVersion ?? session?.session.connectorVersion ?? '—',
      apiVersion: health?.schemaVersion ?? '—',
      desktopVersion: DESKTOP_VERSION,
      erpType: session?.session.erpType ?? 'tally',
      erpName: resolveErpName(session),
      connectionIndicator,
      connectionLabel: getConnectionLabel(connectionIndicator),
      companyName: resolveCompanyName(session),
      companyId: resolveCompanyId(session),
      selectionTime: formatSelectionTime(session?.session.selectedAt ?? null),
      sessionStatus: mapSessionDisplayStatus(connectorReachable, session, validation),
      syncStatus,
      syncLabel: getSyncLabel(syncStatus),
      lastSync: formatLastSync(session?.session.lastValidatedAt ?? null),
      lastRefresh: formatTimestamp(this.lastRefreshAt),
      licenseStatus: licensing?.message?.includes('Placeholder') ? 'Evaluation' : (licensing?.message ?? 'Unknown'),
      connectorReachable,
      healthStatus: health?.status ?? 'unknown',
      userMessage,
    };
  }
}
