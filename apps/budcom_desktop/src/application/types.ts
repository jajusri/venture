/** Connector `/health` response subset used by the desktop shell. */
export interface HealthResponse {
  readonly status: 'ok' | 'degraded' | 'unavailable';
  readonly schemaVersion: string;
  readonly connectorVersion: string;
  readonly tallyReachable: boolean;
  readonly readOnly: boolean;
  readonly services: readonly ServiceStatusDto[];
}

export interface ServiceStatusDto {
  readonly name: string;
  readonly running: boolean;
  readonly ready: boolean;
  readonly message?: string;
}

/** Connector `/session` response — authoritative session source. */
export interface SessionSnapshotResponse {
  readonly session: ConnectorSessionDto;
  readonly contractVersion: string;
}

export interface ConnectorSessionDto {
  readonly sessionId: string;
  readonly selectedCompany: SelectedCompanyDto | null;
  readonly connectionStatus: 'connected' | 'disconnected' | 'degraded';
  readonly connectorVersion: string;
  readonly erpType: string;
  readonly selectedAt: string | null;
  readonly lastValidatedAt: string | null;
  readonly createdAt: string;
}

export interface SelectedCompanyDto {
  readonly id: string;
  readonly name: string;
}

export interface SessionValidationResponse {
  readonly status: string;
  readonly session: ConnectorSessionDto;
  readonly reason?: string;
  readonly companyId?: string;
  readonly companyName?: string;
}

export type ConnectionIndicator = 'connected' | 'waiting' | 'disconnected' | 'unknown';

export type SyncDisplayStatus = 'ready' | 'idle' | 'syncing' | 'paused' | 'failed';

export type LogLevel = 'information' | 'warning' | 'error';

export interface LogEntry {
  readonly id: string;
  readonly timestamp: string;
  readonly level: LogLevel;
  readonly message: string;
}

export interface DashboardState {
  readonly windowTitle: string;
  readonly connectorVersion: string;
  readonly erpType: string;
  readonly connectionIndicator: ConnectionIndicator;
  readonly connectionLabel: string;
  readonly companyName: string;
  readonly companyId: string;
  readonly selectionTime: string;
  readonly sessionStatus: string;
  readonly syncStatus: SyncDisplayStatus;
  readonly syncLabel: string;
  readonly lastSync: string;
  readonly licenseStatus: string;
  readonly connectorReachable: boolean;
  readonly healthStatus: string;
}

export interface ConnectorClientConfig {
  readonly baseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly timeoutMs?: number;
}
