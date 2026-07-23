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
  readonly event?: string | null;
  readonly component?: string | null;
  readonly metadata?: Record<string, string | number | boolean | null> | null;
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
  readonly connectorHost: string;
  readonly apiVersion: string;
  readonly desktopVersion: string;
  readonly erpType: string;
  readonly pollIntervalSeconds: number;
  readonly connectorExecutable: string;
  readonly connectorPort: number;
  readonly autoStartConnector: boolean;
  readonly healthPollIntervalMs: number;
  readonly startupTimeoutMs: number;
  readonly shutdownGraceMs: number;
  readonly maxRestartAttempts: number;
  readonly reconnectBaseDelayMs: number;
  readonly logLevel: string;
  readonly diagnosticsRetentionDays: number;
  readonly tallyHost: string;
  readonly tallyPort: number;
  readonly configSource: string;
  readonly configStatus: string;
  readonly restartRequired: boolean;
  readonly hasUnsavedChanges?: boolean;
}

export interface SettingsSaveResult {
  readonly ok: boolean;
  readonly message: string;
  readonly restartRequired: boolean;
  readonly settings: SettingsState | null;
}

export interface SettingsValidationResult {
  readonly ok: boolean;
  readonly errors: readonly { field: string; message: string }[];
}

export interface DiagnosticsSnapshot {
  readonly generatedAt: string;
  readonly desktopVersion: string;
  readonly connectorVersion: string | null;
  readonly electronVersion: string;
  readonly nodeVersion: string;
  readonly platform: string;
  readonly osRelease: string;
  readonly architecture: string;
  readonly uptimeSeconds: number;
  readonly connectorBaseUrl: string;
  readonly connectorProcessState: string;
  readonly connectorOwnership: 'desktop-managed' | 'external' | 'none';
  readonly connectorPid: number | null;
  readonly healthStatus: string;
  readonly healthReachable: boolean;
  readonly lastSuccessfulHealthCheck: string | null;
  readonly tallyReachable: boolean | null;
  readonly sessionSummary: string;
  readonly configSource: string;
  readonly configStatus: string;
  readonly logFilePath: string | null;
  readonly fileLoggingAvailable: boolean;
  readonly recentLifecycleEvents: readonly LogEntry[];
  readonly recentErrors: readonly LogEntry[];
}

export interface DiagnosticsExportResult {
  readonly ok: boolean;
  readonly message: string;
  readonly bundlePath: string | null;
  readonly cancelled?: boolean;
}

export interface HealthCheckResult {
  readonly ok: boolean;
  readonly reachable: boolean;
  readonly status: string;
  readonly message: string;
  readonly checkedAt: string;
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
