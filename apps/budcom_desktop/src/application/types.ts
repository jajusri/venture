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

export type SessionValidationStatus =
  | 'SUCCESS'
  | 'NO_COMPANY_SELECTED'
  | 'SESSION_INVALID'
  | 'COMPANY_NOT_FOUND'
  | 'COMPANY_NOT_ACCESSIBLE'
  | 'SESSION_EXPIRED';

export interface SessionValidationResponse {
  readonly status: SessionValidationStatus | string;
  readonly session: ConnectorSessionDto;
  readonly reason?: string;
  readonly companyId?: string;
  readonly companyName?: string;
}

export type CompanyDiscoveryStatus =
  | 'SUCCESS'
  | 'EMPTY'
  | 'INCOMPLETE'
  | 'MALFORMED'
  | 'UNAVAILABLE'
  | 'DENIED'
  | 'TIMEOUT';

export interface CompanyListItemDto {
  readonly id: string;
  readonly name: string;
  readonly financialYear?: string;
  readonly booksFrom?: string;
  readonly baseCurrency?: string;
}

export interface CompanyListResult {
  readonly items: readonly CompanyListItemDto[];
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
  readonly contractVersion: string;
  readonly status: CompanyDiscoveryStatus;
  readonly tallyReachable: boolean;
  readonly reason?: string;
}

export type CompanySelectionStatus =
  | 'SUCCESS'
  | 'DUPLICATE_SELECTION'
  | 'EMPTY_SELECTION'
  | 'INVALID_COMPANY'
  | 'COMPANY_NOT_FOUND';

export interface CompanySelectionResult {
  readonly status: CompanySelectionStatus;
  readonly session: ConnectorSessionDto;
  readonly reason?: string;
}

export interface ConnectorErrorBody {
  readonly code?: string;
  readonly message?: string;
  readonly details?: Record<string, unknown>;
}

export type ConnectionIndicator =
  | 'connected'
  | 'waiting'
  | 'disconnected'
  | 'starting'
  | 'error'
  | 'unknown';

export type ConnectionLabel =
  | 'Connected'
  | 'Disconnected'
  | 'Starting'
  | 'Error'
  | 'Waiting'
  | 'Unknown';

export type SessionDisplayStatus =
  | 'NO_COMPANY_SELECTED'
  | 'ACTIVE'
  | 'INVALID'
  | 'DISCONNECTED'
  | 'ERROR';

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
  readonly apiVersion: string;
  readonly desktopVersion: string;
  readonly erpType: string;
  readonly erpName: string;
  readonly connectionIndicator: ConnectionIndicator;
  readonly connectionLabel: ConnectionLabel;
  readonly companyName: string;
  readonly companyId: string;
  readonly selectionTime: string;
  readonly sessionStatus: SessionDisplayStatus;
  readonly syncStatus: SyncDisplayStatus;
  readonly syncLabel: string;
  readonly lastSync: string;
  readonly lastRefresh: string;
  readonly licenseStatus: string;
  readonly connectorReachable: boolean;
  readonly healthStatus: string;
  readonly userMessage: string | null;
}

export interface SettingsState {
  readonly connectorUrl: string;
  readonly apiVersion: string;
  readonly desktopVersion: string;
  readonly erpType: string;
  readonly pollIntervalSeconds: number;
  readonly connectorExecutable: string;
  readonly connectorPort: number;
  readonly autoStartConnector: boolean;
}

export interface CompanySelectionOutcome {
  readonly ok: boolean;
  readonly status: CompanySelectionStatus | 'CONNECTOR_UNAVAILABLE';
  readonly userMessage: string;
  readonly session: ConnectorSessionDto | null;
}

export interface ConnectorClientConfig {
  readonly baseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly timeoutMs?: number;
  readonly maxAttempts?: number;
  readonly retryBaseDelayMs?: number;
}
