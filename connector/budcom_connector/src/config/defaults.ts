import type { ConnectorNetworkExposure } from './network-binding.js';

export const CONNECTOR_VERSION = '0.4.5';
export const SCHEMA_VERSION = '1.0.0';

export interface ConnectorConfig {
  readonly env: 'development' | 'production' | 'test';
  /** Connector API bind host (not Tally host). Defaults to loopback-only. */
  readonly host: string;
  readonly port: number;
  readonly networkExposure: ConnectorNetworkExposure;
  readonly networkExposureWarning: string | null;
  /** Explicit operator acknowledgement for non-loopback bind without authentication. */
  readonly lanModeAcknowledged: boolean;
  readonly logLevel: 'debug' | 'info' | 'warn' | 'error';
  readonly tallyHost: string;
  readonly tallyPort: number;
  readonly tallyTimeoutMs: number;
  readonly tallyPoolMaxConnections: number;
  readonly tallyRetryMaxAttempts: number;
  readonly tallyRetryBaseDelayMs: number;
  readonly tallyRetryMaxDelayMs: number;
  readonly tallyRetryJitterRatio: number;
  readonly tallyAutoReconnect: boolean;
  readonly tallyReconnectDelayMs: number;
  readonly tallySafeMode: boolean;
  readonly tallyMinRequestIntervalMs: number;
  readonly tallyMaxRequestBytes: number;
  readonly tallyMaxResponseBytes: number;
  readonly tallyCircuitBreakerEnabled: boolean;
  readonly tallyCircuitBreakerFailureThreshold: number;
  readonly tallyCircuitBreakerCooldownMs: number;
  readonly tallyRequestAuditEnabled: boolean;
  readonly tallyRequestAuditPath: string;
  readonly tallyRequestAuditMaxBytes: number;
  readonly tallyRequestAuditMaxFiles: number;
  /**
   * Structural-only, privacy-safe audit of Voucher parser/extraction failures --
   * failure bucket, granular reasonCode, XML parse classification (line/column/
   * byteOffset-style facts, never surrounding text), which Tally operation, response
   * byte length, a correlation-only response hash, and sanitization telemetry. Never
   * raw XML, narration, party names, amounts, or any other business content. Exists
   * because the connector's own info/debug logs are discarded entirely in the packaged
   * Desktop runtime (child process spawned with stdout ignored) and error/warn logs are
   * bounded to a small shared byte budget for the process's whole lifetime -- this
   * writes directly to its own file instead, following the same pattern as
   * tallyRequestAudit*, so it survives regardless of either constraint.
   */
  readonly voucherSyncFailureAuditEnabled: boolean;
  readonly voucherSyncFailureAuditPath: string;
  readonly databasePath: string;
  readonly gracefulShutdownMs: number;
  readonly connectorVersion: string;
  readonly schemaVersion: string;
  /** Maximum age of a connector session selection before SESSION_EXPIRED. */
  readonly sessionTtlMs: number;
  /** Maximum terminal sync-run history rows retained per company and resource kind. */
  readonly syncRunHistoryMaxCount: number;
  /** Maximum age in days for non-protected terminal sync-run rows outside the count window. */
  readonly syncRunHistoryMaxAgeDays: number;
  /** Bounded per-launch identifier exposed in /health diagnostics when set by desktop supervisor. */
  readonly startupCorrelationId: string | null;
  /**
   * Gate for the trusted-device bearer-token enforcement milestone. Defaults to false so every
   * existing installation's request handling is byte-for-byte unchanged. Only takes effect when
   * also bound off-loopback (networkExposure === 'lan'). See docs/architecture — this flag stays
   * off until the Android pairing UI (QR/code + Keystore) ships; flipping it on before that would
   * make LAN installs unusable, not more secure.
   */
  readonly requireDeviceAuthForLan: boolean;
  /**
   * Shared secret Desktop presents (header X-Budcom-Desktop-Control-Token) to start/cancel a
   * pairing session. Defaults to null (no token configured), which makes
   * requireDesktopControlToken fail closed on LAN — there is no legitimate caller until Desktop
   * generates and sends one, which is deferred to the Desktop integration phase.
   */
  readonly desktopControlToken: string | null;
  /**
   * Gate for the entire secure local pairing (QR/one-time-code) bootstrap surface. Defaults to
   * false so every pairing-session/-credential route responds with a consistent disabled result
   * and no existing installation's behavior changes. Independent of requireDeviceAuthForLan,
   * which gates business-route enforcement, not pairing-bootstrap availability.
   */
  readonly securePairingEnabled: boolean;
  /**
   * Gate for the pinned-HTTPS transport listener. Defaults to false so the existing HTTP-only
   * runtime behavior is byte-for-byte unchanged. Independent of securePairingEnabled and
   * requireDeviceAuthForLan — this flag controls whether a second, TLS-terminated listener
   * exists at all, not pairing-bootstrap availability or business-route enforcement. Never
   * auto-enabled merely because secure pairing is enabled.
   */
  readonly secureTransportEnabled: boolean;
  /** Bind port for the optional HTTPS listener, on the same host as the HTTP listener. */
  readonly secureTransportPort: number;
  /**
   * Directory (created if missing) holding the Connector's persisted transport keypair and
   * self-signed certificate. Separate from databasePath — the private key must never be stored
   * in SQLite (see services/transport/connector-transport-identity.ts).
   */
  readonly transportIdentityDir: string;
  /**
   * Gate for the dormant secure LAN business-route policy (see
   * api/middleware/require-business-route-auth.ts). Defaults to false so every existing
   * installation's request handling toward companies/session/master-data/ledgers/stock-items/
   * vouchers/api-stubs is byte-for-byte unchanged. Deliberately a separate flag from
   * requireDeviceAuthForLan (the pre-existing, still-independent legacy gate this flag
   * supersedes only when true), securePairingEnabled (bootstrap-surface availability, not
   * business-route enforcement), and secureTransportEnabled (whether the HTTPS listener exists
   * at all). Only takes effect when also bound off-loopback (networkExposure === 'lan'); once
   * active there, protected routes additionally require HTTPS regardless of
   * secureTransportEnabled's value — see the middleware's own doc comment.
   */
  readonly secureLanRouteProtectionEnabled: boolean;
  /**
   * Defense-in-depth guard for Private Removable Storage mode. When both this and
   * privateStorageMarkerPath are set, SqliteStorageService refuses to open/create the database
   * unless the marker file at privateStorageMarkerPath exists and its own `vaultId` matches this
   * value exactly — see storage/private-storage-guard.ts. Null (the default) means no check runs
   * at all, so every existing Standard-storage installation's behavior is byte-for-byte
   * unchanged. This check exists independently of whatever presence-checking Desktop does before
   * ever spawning the Connector: it is the Connector's own last line of defense against silently
   * opening/creating a fresh database on a wrong or lookalike drive that happens to have been
   * assigned the expected drive letter.
   */
  readonly privateStorageExpectedVaultId: string | null;
  /** Path to the vault marker file checked against privateStorageExpectedVaultId. Null (default) disables the check. */
  readonly privateStorageMarkerPath: string | null;
}

export const TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT = 10 * 1024 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT = 5;
export const TALLY_REQUEST_AUDIT_MAX_BYTES_MIN = 64 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_BYTES_LIMIT = 100 * 1024 * 1024;
export const TALLY_REQUEST_AUDIT_MAX_FILES_MIN = 1;
export const TALLY_REQUEST_AUDIT_MAX_FILES_LIMIT = 20;

export const SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT = 100;
export const SYNC_RUN_HISTORY_MAX_COUNT_MIN = 20;
export const SYNC_RUN_HISTORY_MAX_COUNT_LIMIT = 500;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT = 90;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN = 7;
export const SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT = 365;

export const defaultConfig: ConnectorConfig = {
  env: 'development',
  host: '127.0.0.1',
  port: 8080,
  networkExposure: 'loopback',
  networkExposureWarning: null,
  lanModeAcknowledged: false,
  logLevel: 'info',
  tallyHost: 'localhost',
  tallyPort: 9000,
  tallyTimeoutMs: 120_000,
  tallyPoolMaxConnections: 1,
  tallyRetryMaxAttempts: 1,
  tallyRetryBaseDelayMs: 500,
  tallyRetryMaxDelayMs: 8_000,
  tallyRetryJitterRatio: 0.2,
  tallyAutoReconnect: true,
  tallyReconnectDelayMs: 2_000,
  tallySafeMode: true,
  tallyMinRequestIntervalMs: 2_000,
  tallyMaxRequestBytes: 65_536,
  tallyMaxResponseBytes: 10_485_760,
  tallyCircuitBreakerEnabled: true,
  tallyCircuitBreakerFailureThreshold: 2,
  tallyCircuitBreakerCooldownMs: 60_000,
  tallyRequestAuditEnabled: true,
  tallyRequestAuditPath: './diagnostics/tally-request-audit.jsonl',
  tallyRequestAuditMaxBytes: TALLY_REQUEST_AUDIT_MAX_BYTES_DEFAULT,
  tallyRequestAuditMaxFiles: TALLY_REQUEST_AUDIT_MAX_FILES_DEFAULT,
  voucherSyncFailureAuditEnabled: true,
  voucherSyncFailureAuditPath: './diagnostics/voucher-sync-failure-audit.jsonl',
  databasePath: './data/budcom-connector.db',
  gracefulShutdownMs: 10_000,
  connectorVersion: CONNECTOR_VERSION,
  schemaVersion: SCHEMA_VERSION,
  sessionTtlMs: 8 * 60 * 60 * 1000,
  syncRunHistoryMaxCount: SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT,
  syncRunHistoryMaxAgeDays: SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT,
  startupCorrelationId: null,
  requireDeviceAuthForLan: false,
  desktopControlToken: null,
  securePairingEnabled: false,
  secureTransportEnabled: false,
  secureTransportPort: 8443,
  transportIdentityDir: './data/transport',
  secureLanRouteProtectionEnabled: false,
  privateStorageExpectedVaultId: null,
  privateStorageMarkerPath: null,
};
