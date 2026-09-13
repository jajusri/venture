/** Connector `/health` response subset used by the desktop shell. */
import type { SafeDiagnosticLogEntry } from './diagnostic-allowlist.js';

export interface HealthResponse {
  readonly status: 'ok' | 'degraded' | 'unavailable';
  readonly schemaVersion: string;
  readonly connectorVersion: string;
  readonly tallyReachable: boolean;
  readonly readOnly: boolean;
  readonly bindPort?: number;
  /** Actual bind host the running Connector reports listening on (runtime integrity checks). */
  readonly bindHost?: string;
  readonly startupCorrelationId?: string | null;
  /** Stable Connector identity — not a secret, see docs/architecture. */
  readonly connectorId?: string;
  readonly connectorName?: string;
  readonly networkExposure?: 'loopback' | 'lan';
  readonly authenticatedLanAccessEnabled?: boolean;
  readonly discoveryAdvertising?: boolean;
  readonly services: readonly ServiceStatusDto[];
  /** ISO timestamp the running Connector process actually started (runtime integrity checks). */
  readonly processStartedAt?: string;
}

/** Connector `/device/list` response subset used by the Mobile Access status model. */
export interface DeviceListItemDto {
  readonly deviceRecordId: string;
  readonly friendlyName: string | null;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
}

export interface DeviceListResult {
  readonly items: readonly DeviceListItemDto[];
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
  readonly connectorBindMode: 'local-only' | 'trusted-lan';
  readonly reachableLanUrl: string | null;
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
  readonly secureMobilePairingEnabled: boolean;
}

export interface SettingsMutationResult {
  readonly ok: boolean;
  readonly message: string;
  readonly restartRequired: boolean;
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
  /** VERSION.txt packaged with Desktop (may differ from live /health until Connector starts). */
  readonly bundledConnectorVersion: string | null;
  readonly electronVersion: string;
  readonly nodeVersion: string;
  readonly platform: string;
  readonly osRelease: string;
  readonly architecture: string;
  readonly uptimeSeconds: number;
  readonly connectorBaseUrl: string;
  readonly connectorBindHost: string;
  readonly connectorNetworkExposure: 'loopback' | 'lan';
  readonly connectorNetworkExposureWarning: string | null;
  readonly connectorProcessState: string;
  readonly connectorOwnership: 'desktop-managed' | 'external' | 'none';
  readonly connectorPid: number | null;
  readonly healthStatus: string;
  readonly healthReachable: boolean;
  readonly lastSuccessfulHealthCheck: string | null;
  readonly tallyReachable: boolean | null;
  readonly sessionStatus: SessionDisplayStatus;
  readonly selectedCompanyPresent: boolean;
  /** Privacy-safe session label without company names (e.g. "ACTIVE · company selected"). */
  readonly sessionDisplayLabel: string;
  readonly configSource: string;
  readonly configStatus: string;
  readonly logFile: {
    readonly category: 'desktop-log';
    readonly basename: string | null;
    readonly available: boolean;
  };
  readonly fileLoggingAvailable: boolean;
  readonly recentLifecycleEvents: readonly SafeDiagnosticLogEntry[];
  readonly recentErrors: readonly SafeDiagnosticLogEntry[];
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
  /**
   * Sent on every request from this client instance — used solely for the Desktop control-token
   * header on pairing-control calls (see mobile-pairing-service.ts). Never used to carry a
   * permanent device credential or any renderer-supplied value.
   */
  readonly defaultHeaders?: Readonly<Record<string, string>>;
}

/** Connector `POST /device/pairing-session` response — Desktop main process only, never IPC. */
export interface PairingSessionCreateResult {
  readonly schemaVersion: string;
  readonly pairingSessionId: string;
  readonly connectorId: string;
  readonly connectorName: string;
  readonly host: string;
  readonly port: number;
  readonly expiresAt: string;
  /** One-time QR secret. Desktop main process only — never logged, persisted, or sent via IPC. */
  readonly secret: string;
  /** One-time human-enterable short code. Same handling rules as `secret`. */
  readonly shortCode: string;
  readonly transportProtocol?: 'https';
  readonly securePort?: number;
  readonly transportFingerprint?: string;
  readonly fingerprintAlgorithm?: string;
  readonly transportIdentityVersion?: number;
}

export interface PairingSessionStatusResult {
  readonly ok: boolean;
  readonly pairingSessionId: string;
  readonly connectorId: string;
  readonly connectorName: string;
  readonly host: string;
  readonly port: number;
  readonly schemaVersion: string;
  readonly createdAt: string;
  readonly expiresAt: string;
  readonly redeemed: boolean;
  readonly cancelled: boolean;
  readonly expired: boolean;
}

export interface PairingCredentialListItemDto {
  readonly credentialId: string;
  readonly deviceId: string | null;
  readonly deviceLabel: string | null;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
  readonly status: 'active' | 'revoked';
}

export interface PairingCredentialListResult {
  readonly items: readonly PairingCredentialListItemDto[];
}

/** What the renderer actually receives for the trusted-device list — see mobile-pairing-service.ts. */
export interface TrustedPairingDeviceSummary {
  readonly credentialId: string;
  readonly deviceLabel: string | null;
  readonly firstPairedAt: string;
  readonly lastUsedAt: string | null;
  readonly status: 'active' | 'revoked';
}

export type SecurePairingCapabilityState = 'disabled' | 'restart_required' | 'unavailable' | 'ready';

export interface SecurePairingCapability {
  readonly state: SecurePairingCapabilityState;
  readonly connectorName: string | null;
  readonly transportFingerprint: string | null;
  readonly trustedDeviceCount: number | null;
  readonly userMessage: string | null;
}

export type PairingSessionUiState =
  | 'idle'
  | 'creating'
  | 'active'
  | 'redeemed'
  | 'expired'
  | 'cancelled'
  | 'failed';

/** Sanitized session status the renderer is allowed to see — never the secret/shortCode/token. */
export interface ActivePairingSessionView {
  readonly state: PairingSessionUiState;
  readonly pairingSessionId: string | null;
  readonly qrDataUrl: string | null;
  readonly shortCode: string | null;
  readonly expiresAt: string | null;
  readonly redeemedDeviceLabel: string | null;
  readonly userMessage: string | null;
}

export interface LedgerSummaryDto {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly alias?: string;
  readonly parentGroup?: string;
  readonly status: string;
  readonly balanceNature: string;
  readonly guid?: string;
  readonly alterId?: string;
  readonly syncedAt: string;
}

export interface LedgerListResult {
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
  readonly items: readonly LedgerSummaryDto[];
  readonly pagination: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalItems: number;
    readonly totalPages: number;
  };
}

export interface LedgerStatisticsDto {
  readonly totalLedgers: number;
  readonly activeLedgers: number;
  readonly inactiveLedgers: number;
  readonly reservedLedgers: number;
  readonly deletedLedgers: number;
  readonly withGst: number;
  readonly withOpeningBalance: number;
  readonly lastSyncedAt: string | null;
}

export interface LedgerStatisticsResult {
  readonly schemaVersion: string;
  readonly statistics: LedgerStatisticsDto;
}

export interface LedgerSyncProgressDto {
  readonly status: string;
  readonly syncRunId?: string | null;
  readonly totalExpected?: number | null;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly durationMs: number | null;
  readonly itemsProcessed: number;
  readonly itemsAdded: number;
  readonly itemsUpdated: number;
  readonly itemsSkipped: number;
  readonly itemsFailed: number;
  readonly lastError: string | null;
  readonly cancelRequested: boolean;
  readonly storageBackend?: string;
  readonly migrationStatus?: string;
}

export interface StorageStatusDto {
  readonly backend: string;
  readonly schemaVersion: number;
  readonly databaseHealthy: boolean;
  readonly migrationStatus: string;
  readonly message: string | null;
}

/**
 * The Connector's own adaptive-sync scheduler stage for one resource kind — raw internal state,
 * never shown to the user directly (see `deriveFreshnessCheckingLabel()` in app.ts, which maps
 * this to the two honest, non-technical phrases the UI actually displays: "Checking regularly" /
 * "Checking occasionally"). `null` means the Connector has no scheduler (older Connector version)
 * or nothing selected yet — the UI must degrade gracefully, not treat this as an error.
 */
export interface SchedulerStateDto {
  readonly stage: 'active_window' | 'backoff_15' | 'backoff_30' | 'backoff_60';
  readonly nextCheckDueAt: string;
}

export interface LedgerSyncProgressResult {
  readonly schemaVersion: string;
  readonly progress: LedgerSyncProgressDto;
  readonly storage?: StorageStatusDto;
  readonly schedulerState?: SchedulerStateDto | null;
}

export interface LedgerSyncResult {
  readonly schemaVersion: string;
  readonly status: string;
  readonly statistics: LedgerStatisticsDto;
  readonly progress: LedgerSyncProgressDto;
  readonly validationIssueCount: number;
}

export interface LedgerPageState {
  readonly ok: boolean;
  readonly list: LedgerListResult | null;
  readonly statistics: LedgerStatisticsResult | null;
  readonly progress: LedgerSyncProgressResult | null;
  readonly storage: StorageStatusDto | null;
  readonly userMessage: string | null;
}

export interface StockItemSummaryDto {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
  readonly parentGroup?: string;
  readonly category?: string;
  readonly baseUnit?: string;
  readonly dataQuality: string;
  readonly syncedAt: string;
}

export interface StockItemListResult {
  readonly schemaVersion: string;
  readonly dataFreshnessAt: string;
  readonly items: readonly StockItemSummaryDto[];
  readonly pagination: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalItems: number;
    readonly totalPages: number;
  };
}

export interface StockItemStatisticsDto {
  readonly totalStockItems: number;
  readonly withBaseUnit: number;
  readonly incompleteData: number;
  readonly withHsn: number;
  readonly withGst: number;
  readonly withOpeningBalance: number;
  readonly deletedStockItems: number;
  readonly lastSyncedAt: string | null;
}

export interface StockItemStatisticsResult {
  readonly schemaVersion: string;
  readonly statistics: StockItemStatisticsDto;
}

export interface StockItemSyncProgressResult {
  readonly schemaVersion: string;
  readonly progress: LedgerSyncProgressDto;
  readonly storage?: StorageStatusDto;
  readonly schedulerState?: SchedulerStateDto | null;
}

export interface StockItemSyncResult {
  readonly schemaVersion: string;
  readonly status: string;
  readonly statistics: StockItemStatisticsDto;
  readonly progress: LedgerSyncProgressDto;
  readonly validationIssueCount: number;
}

export interface StockItemPageState {
  readonly ok: boolean;
  readonly list: StockItemListResult | null;
  readonly statistics: StockItemStatisticsResult | null;
  readonly progress: StockItemSyncProgressResult | null;
  readonly storage: StorageStatusDto | null;
  readonly userMessage: string | null;
}
