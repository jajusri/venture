import { ConnectorHttpClient } from './connector-http-client.js';
import {
  getConnectionLabel,
  mapConnectionIndicator,
} from './connection-status-mapper.js';
import type { DashboardState, HealthResponse, SessionSnapshotResponse, SessionValidationResponse } from './types.js';
import { LogService } from './log-service.js';
import {
  formatSelectionTime,
  resolveCompanyId,
  resolveCompanyName,
  resolveSessionStatus,
} from './session-display-mapper.js';
import {
  formatLastSync,
  getSyncLabel,
  mapSyncDisplayStatus,
} from './sync-status-mapper.js';

export const DESKTOP_WINDOW_TITLE = 'Business OS Tally Connector';

export interface DashboardServiceOptions {
  readonly connectorBaseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly logService?: LogService;
}

export class DashboardService {
  private readonly client: ConnectorHttpClient;
  private readonly logService: LogService;

  constructor(options: DashboardServiceOptions) {
    this.client = new ConnectorHttpClient({
      baseUrl: options.connectorBaseUrl,
      fetchImpl: options.fetchImpl,
    });
    this.logService = options.logService ?? new LogService();
  }

  getLogService(): LogService {
    return this.logService;
  }

  async getDashboardState(): Promise<DashboardState> {
    let connectorReachable = false;
    let health: HealthResponse | null = null;
    let session: SessionSnapshotResponse | null = null;

    try {
      health = await this.client.getHealth();
      connectorReachable = true;
      this.logService.append('information', `Health status: ${health.status}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
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
        const message = error instanceof Error ? error.message : String(error);
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
        const message = error instanceof Error ? error.message : String(error);
        this.logService.append('warning', `Session validation failed: ${message}`);
      }
    }

    const connectionIndicator = mapConnectionIndicator(connectorReachable, health);
    const syncStatus = mapSyncDisplayStatus(health?.services ?? []);
    const licensing = health?.services.find((service) => service.name === 'Licensing');

    return {
      windowTitle: DESKTOP_WINDOW_TITLE,
      connectorVersion: health?.connectorVersion ?? session?.session.connectorVersion ?? '—',
      erpType: session?.session.erpType ?? 'tally',
      connectionIndicator,
      connectionLabel: getConnectionLabel(connectionIndicator),
      companyName: resolveCompanyName(session),
      companyId: resolveCompanyId(session),
      selectionTime: formatSelectionTime(session?.session.selectedAt ?? null),
      sessionStatus: resolveSessionStatus(session, validation),
      syncStatus,
      syncLabel: getSyncLabel(syncStatus),
      lastSync: formatLastSync(session?.session.lastValidatedAt ?? null),
      licenseStatus: licensing?.message?.includes('Placeholder') ? 'Evaluation' : (licensing?.message ?? 'Unknown'),
      connectorReachable,
      healthStatus: health?.status ?? 'unknown',
    };
  }
}
