import { randomUUID } from 'node:crypto';
import { basename } from 'node:path';

import type { SessionDisplayStatus } from './types.js';
import type { LogEntry } from './types.js';
import type { ConfigSource } from './desktop-config-resolver.js';
import type { DesktopConfigV1, DesktopLogLevel } from './desktop-config-schema.js';
import { getConnectorNetworkExposure } from './connector-network-binding.js';
import { redactString, stripEnvironmentVariables } from './log-redaction.js';

export const DIAGNOSTIC_ALLOWLIST_VERSION = 1 as const;

export const DIAGNOSTIC_LIMITS = {
  maxBundleBytes: 256_000,
  maxStringLength: 500,
  maxLogEntries: 25,
  maxStackFrames: 20,
  maxArrayItems: 50,
  maxRecursionDepth: 4,
} as const;

const REDACTED = '[REDACTED]';
const TRUNCATED_SUFFIX = '… [truncated]';

const GSTIN_PATTERN = /\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b/gi;
const PHONE_PATTERN = /\b(?:\+?\d{1,3}[-.\s]?)?(?:\(?\d{2,4}\)?[-.\s]?)?\d{6,12}\b/g;
const AMOUNT_PATTERN = /\b\d{1,3}(?:,\d{3})*(?:\.\d{1,4})?\s*(?:Dr|Cr|INR|Rs\.?)?\b/gi;
const XML_BLOCK_PATTERN = /<[A-Za-z!?][^>]*>[\s\S]*?<\/[A-Za-z][^>]*>/gi;
const XML_SELF_CLOSING_PATTERN = /<[A-Za-z!?][^>]*\/>/gi;
const XML_TAG_PATTERN = /<\/?[A-Z][A-Z0-9._:-]*/gi;
const WINDOWS_PATH_PATTERN = /[A-Za-z]:\\(?:[^\\:*?"<>|\r\n]+\\)+[^\\:*?"<>|\r\n]*/g;
const UNIX_PATH_PATTERN = /\/(?:home|Users|tmp|var|private)\/[^\s"'<>]+/gi;
const SESSION_COMPANY_PATTERN = /Session company:\s*.+/gi;
const SELECTED_COMPANY_PATTERN = /Selected company:\s*.+/gi;
const BEARER_HEADER_PATTERN = /authorization\s*:\s*\S+/gi;
const API_TOKEN_PATTERN = /\b(?:sk|pk|api)[-_][A-Za-z0-9_-]{8,}\b/gi;
const INLINE_TOKEN_PATTERN = /\btoken\s+[A-Za-z0-9._-]{8,}\b/gi;

/** Sentinel values used by privacy absence tests — must never appear in exported diagnostics. */
export const DIAGNOSTIC_PRIVACY_SENTINELS = {
  companyName: 'JAJU SANITATIONS PRIVATE LIMITED',
  ledgerName: 'Sensitive Debtor Ledger',
  gstin: '29AABCU9603R1ZM',
  voucherNumber: 'VCH-2026-004821',
  phone: '+91-9876543210',
  email: 'finance@example-company.test',
  amount: '125000.50 Dr',
  xmlSnippet: '<LEDGER NAME="Secret Co"><AMOUNT>999.00</AMOUNT></LEDGER>',
  secretToken: 'sk-live-diagnostic-leak-test-token',
  windowsPath: 'C:\\Users\\ContosoUser\\Documents\\JAJU SANITATIONS\\budcom.db',
  nestedCauseBody: '{"responseBody":"<STOCKITEM/>","headers":{"Authorization":"Bearer leak"}}',
  stockItemName: 'Sensitive Stock Item Alpha',
  postalAddress: '42 Industrial Estate, Bangalore 560001',
  licenceId: 'LIC-BUDCOM-ENTERPRISE-998877',
  rawGuid: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  rawAlterId: 'AlterID:48291',
  rawMasterId: 'MasterID:109283',
} as const;

export interface SafeDiagnosticSession {
  readonly status: SessionDisplayStatus | string;
  readonly selectedCompanyPresent: boolean;
  readonly displayLabel: string;
}

export interface SafeDiagnosticLogEntry {
  readonly id: string;
  readonly timestamp: string;
  readonly level: LogEntry['level'];
  readonly message: string;
  readonly event: string | null;
  readonly component: string | null;
}

export interface SafeDiagnosticErrorEntry {
  readonly code: string;
  readonly category: string;
  readonly message: string;
  readonly retryable: boolean;
  readonly stage: string | null;
}

export interface SafeDiagnosticLogFileRef {
  readonly category: 'desktop-log';
  readonly basename: string | null;
  readonly available: boolean;
}

export interface SafeDiagnosticConnectorConfig {
  readonly hostScope: 'loopback' | 'lan';
  readonly port: number;
  readonly autoStart: boolean;
}

export interface SafeDiagnosticTallyConfig {
  readonly hostScope: 'loopback' | 'lan';
  readonly port: number;
}

export interface SafeDiagnosticLifecycleConfig {
  readonly healthPollIntervalMs: number;
  readonly startupTimeoutMs: number;
  readonly shutdownGraceMs: number;
  readonly maxRestartAttempts: number;
  readonly reconnectBaseDelayMs: number;
}

export interface SafeDiagnosticLoggingConfig {
  readonly level: DesktopLogLevel;
  readonly diagnosticsRetentionDays: number;
}

export interface SafeDiagnosticConfigSourceSummary {
  readonly environmentOverrideCount: number;
  readonly hasEnvironmentOverrides: boolean;
}

export interface SafeDiagnosticConfigurationV1 {
  readonly status: string;
  readonly schemaVersion: number;
  readonly connector: SafeDiagnosticConnectorConfig;
  readonly tally: SafeDiagnosticTallyConfig;
  readonly lifecycle: SafeDiagnosticLifecycleConfig;
  readonly logging: SafeDiagnosticLoggingConfig;
  readonly sourceSummary: SafeDiagnosticConfigSourceSummary;
}

export const APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS = [
  'status',
  'schemaVersion',
  'connector',
  'tally',
  'lifecycle',
  'logging',
  'sourceSummary',
] as const;

export const APPROVED_DIAGNOSTIC_CONNECTOR_CONFIG_KEYS = ['hostScope', 'port', 'autoStart'] as const;
export const APPROVED_DIAGNOSTIC_TALLY_CONFIG_KEYS = ['hostScope', 'port'] as const;
export const APPROVED_DIAGNOSTIC_LIFECYCLE_CONFIG_KEYS = [
  'healthPollIntervalMs',
  'startupTimeoutMs',
  'shutdownGraceMs',
  'maxRestartAttempts',
  'reconnectBaseDelayMs',
] as const;
export const APPROVED_DIAGNOSTIC_LOGGING_CONFIG_KEYS = ['level', 'diagnosticsRetentionDays'] as const;
export const APPROVED_DIAGNOSTIC_SOURCE_SUMMARY_KEYS = [
  'environmentOverrideCount',
  'hasEnvironmentOverrides',
] as const;

export interface BuildSafeDiagnosticConfigurationInput {
  readonly effective: DesktopConfigV1;
  readonly sources: Partial<Record<keyof DesktopConfigV1, ConfigSource>>;
  readonly status: string;
}

const DESKTOP_LOG_LEVELS: readonly DesktopLogLevel[] = ['debug', 'info', 'warn', 'error'];

function safeDesktopLogLevel(level: DesktopLogLevel): DesktopLogLevel {
  return DESKTOP_LOG_LEVELS.includes(level) ? level : 'info';
}

export function buildSafeDiagnosticConfiguration(
  input: BuildSafeDiagnosticConfigurationInput,
): SafeDiagnosticConfigurationV1 {
  const effective = input.effective;
  const environmentOverrideCount = Object.keys(input.sources).length;
  return {
    status: sanitizeDiagnosticText(input.status, 120),
    schemaVersion: effective.schemaVersion,
    connector: {
      hostScope: getConnectorNetworkExposure(effective.connectorHost),
      port: effective.connectorPort,
      autoStart: effective.autoStartConnector,
    },
    tally: {
      hostScope: getConnectorNetworkExposure(effective.tallyHost),
      port: effective.tallyPort,
    },
    lifecycle: {
      healthPollIntervalMs: effective.healthPollIntervalMs,
      startupTimeoutMs: effective.startupTimeoutMs,
      shutdownGraceMs: effective.shutdownGraceMs,
      maxRestartAttempts: effective.maxRestartAttempts,
      reconnectBaseDelayMs: effective.reconnectBaseDelayMs,
    },
    logging: {
      level: safeDesktopLogLevel(effective.logLevel),
      diagnosticsRetentionDays: effective.diagnosticsRetentionDays,
    },
    sourceSummary: {
      environmentOverrideCount,
      hasEnvironmentOverrides: environmentOverrideCount > 0,
    },
  };
}

export interface SafeDiagnosticBundleV1 {
  readonly bundleVersion: typeof DIAGNOSTIC_ALLOWLIST_VERSION;
  readonly generatedAt: string;
  readonly correlationId: string;
  readonly truncated: boolean;
  readonly versions: {
    readonly desktop: string;
    readonly connector: string | null;
    /** VERSION.txt packaged with Desktop (install label). */
    readonly bundledConnector: string | null;
    readonly electron: string;
    readonly node: string;
  };
  readonly runtime: {
    readonly platform: string;
    readonly osRelease: string;
    readonly architecture: string;
    readonly uptimeSeconds: number;
  };
  readonly connector: {
    readonly baseUrl: string;
    readonly bindHost: string;
    readonly networkExposure: 'loopback' | 'lan';
    readonly networkExposureWarning: string | null;
    readonly processState: string;
    readonly ownership: string;
    readonly pid: number | null;
    readonly healthStatus: string;
    readonly healthReachable: boolean;
    readonly lastSuccessfulHealthCheck: string | null;
    readonly tallyReachable: boolean | null;
  };
  readonly session: SafeDiagnosticSession;
  readonly configuration: SafeDiagnosticConfigurationV1;
  readonly environment: Record<string, string>;
  readonly logs: {
    readonly recentLifecycleEvents: readonly SafeDiagnosticLogEntry[];
    readonly recentErrors: readonly SafeDiagnosticLogEntry[];
  };
  readonly privacyPolicy: {
    readonly allowlistVersion: typeof DIAGNOSTIC_ALLOWLIST_VERSION;
    readonly exportSurface: 'desktop-diagnostics-bundle-v1';
  };
}

export interface BuildSafeDiagnosticBundleInput {
  readonly generatedAt: string;
  readonly desktopVersion: string;
  readonly connectorVersion: string | null;
  readonly bundledConnectorVersion?: string | null;
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
  readonly connectorOwnership: string;
  readonly connectorPid: number | null;
  readonly healthStatus: string;
  readonly healthReachable: boolean;
  readonly lastSuccessfulHealthCheck: string | null;
  readonly tallyReachable: boolean | null;
  readonly sessionStatus: SessionDisplayStatus | string;
  readonly selectedCompanyPresent: boolean;
  readonly configuration: BuildSafeDiagnosticConfigurationInput;
  readonly environment: NodeJS.ProcessEnv;
  readonly recentLifecycleEvents: readonly LogEntry[];
  readonly recentErrors: readonly LogEntry[];
  readonly logFilePath: string | null;
  readonly fileLoggingAvailable: boolean;
}

export function buildSafeSessionDisplay(
  sessionStatus: SessionDisplayStatus | string,
  selectedCompanyPresent: boolean,
): SafeDiagnosticSession {
  const companyClause = selectedCompanyPresent ? 'company selected' : 'no company selected';
  return {
    status: sessionStatus,
    selectedCompanyPresent,
    displayLabel: `${sessionStatus} · ${companyClause}`,
  };
}

const VOUCHER_PATTERN = /\bVCH[-\s]?[0-9A-Z-]+\b/gi;
const COMPANY_LEGAL_NAME_PATTERN =
  /\b[A-Z][A-Z0-9&.'-]*(?:\s+[A-Z0-9][A-Z0-9&.'-]*)*\s+(?:PRIVATE LIMITED|PVT\.?\s*LTD\.?|LIMITED|LLP|INC\.?|CORP\.?)\b/g;
const FOR_CLAUSE_PATTERN = /\bfor\s+[^,.;\n]+/gi;
const LEDGER_NAME_PATTERN = /\b[A-Za-z0-9][A-Za-z0-9\s&.'-]{1,}\s+Ledger\b/gi;
const STOCK_ITEM_NAME_PATTERN = /\b[A-Za-z0-9][A-Za-z0-9\s&.'-]{1,}\s+Stock Item(?:\s+[A-Za-z0-9][A-Za-z0-9\s&.'-]*)?\b/gi;
const ENTITY_LABEL_PATTERN = /\b(?:ledger|party|customer|supplier|stock item|company|debtor|creditor)\s*:\s*[^,.;\n]+/gi;
const GUID_PATTERN = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const GUID_PREFIX_PATTERN = /\bguid:[0-9a-f-]+/gi;
const ALTER_MASTER_ID_PATTERN = /\b(?:AlterID|MasterID)\s*[:=]?\s*\d+\b/gi;
const LICENCE_PATTERN = /\b(?:LIC|LICENCE|LICENSE)[-_][A-Z0-9-]{6,}\b/gi;
const ADDRESS_PATTERN = /\b\d{1,4}\s+[A-Za-z0-9\s,.-]{5,}\b(?:\d{5,6})?\b/g;

export function sanitizeDiagnosticText(value: string, maxLength: number = DIAGNOSTIC_LIMITS.maxStringLength): string {
  let result = value;
  result = result.replace(BEARER_HEADER_PATTERN, `Authorization: ${REDACTED}`);
  result = result.replace(API_TOKEN_PATTERN, REDACTED);
  result = result.replace(INLINE_TOKEN_PATTERN, `token ${REDACTED}`);
  result = result.replace(SESSION_COMPANY_PATTERN, 'Session company: [REDACTED]');
  result = result.replace(SELECTED_COMPANY_PATTERN, 'Selected company: [REDACTED]');
  result = result.replace(COMPANY_LEGAL_NAME_PATTERN, REDACTED);
  result = result.replace(FOR_CLAUSE_PATTERN, 'for [REDACTED]');
  result = result.replace(LEDGER_NAME_PATTERN, REDACTED);
  result = result.replace(STOCK_ITEM_NAME_PATTERN, REDACTED);
  result = result.replace(ENTITY_LABEL_PATTERN, (match) => `${match.split(':')[0]}: ${REDACTED}`);
  result = result.replace(VOUCHER_PATTERN, REDACTED);
  result = result.replace(GSTIN_PATTERN, REDACTED);
  result = result.replace(XML_BLOCK_PATTERN, REDACTED);
  result = result.replace(XML_SELF_CLOSING_PATTERN, REDACTED);
  result = result.replace(XML_TAG_PATTERN, REDACTED);
  result = result.replace(WINDOWS_PATH_PATTERN, REDACTED);
  result = result.replace(UNIX_PATH_PATTERN, REDACTED);
  result = result.replace(PHONE_PATTERN, REDACTED);
  result = result.replace(AMOUNT_PATTERN, REDACTED);
  result = result.replace(GUID_PREFIX_PATTERN, REDACTED);
  result = result.replace(GUID_PATTERN, REDACTED);
  result = result.replace(ALTER_MASTER_ID_PATTERN, REDACTED);
  result = result.replace(LICENCE_PATTERN, REDACTED);
  result = result.replace(ADDRESS_PATTERN, REDACTED);
  result = redactString(result);
  if (result.length > maxLength) {
    return `${result.slice(0, maxLength - TRUNCATED_SUFFIX.length)}${TRUNCATED_SUFFIX}`;
  }
  return result;
}

export function sanitizeDiagnosticLogEntry(entry: LogEntry): SafeDiagnosticLogEntry {
  return {
    id: entry.id,
    timestamp: entry.timestamp,
    level: entry.level,
    message: sanitizeDiagnosticText(entry.message),
    event: entry.event ? sanitizeDiagnosticText(entry.event, 120) : null,
    component: entry.component ? sanitizeDiagnosticText(entry.component, 120) : null,
  };
}

export function sanitizeDiagnosticLogEntries(
  entries: readonly LogEntry[],
  limit = DIAGNOSTIC_LIMITS.maxLogEntries,
): readonly SafeDiagnosticLogEntry[] {
  return entries.slice(0, limit).map(sanitizeDiagnosticLogEntry);
}

export function sanitizeDiagnosticLogFileRef(
  filePath: string | null,
  available: boolean,
): SafeDiagnosticLogFileRef {
  if (!filePath) {
    return { category: 'desktop-log', basename: null, available: false };
  }
  const logBasename = basename(filePath);
  return {
    category: 'desktop-log',
    basename: sanitizeDiagnosticText(logBasename, 120),
    available,
  };
}

export function mapUnknownErrorToSafeDiagnostic(
  error: unknown,
  stage: string | null = null,
): SafeDiagnosticErrorEntry {
  if (error instanceof Error) {
    const safeCode = error.name && /^[A-Z0-9_]+$/.test(error.name) ? error.name : 'UNKNOWN_ERROR';
    return {
      code: safeCode,
      category: 'internal',
      message: sanitizeDiagnosticText('An unexpected error occurred.'),
      retryable: false,
      stage,
    };
  }
  return {
    code: 'UNKNOWN_ERROR',
    category: 'internal',
    message: 'An unexpected error occurred.',
    retryable: false,
    stage,
  };
}

export function buildSafeDiagnosticBundle(input: BuildSafeDiagnosticBundleInput): SafeDiagnosticBundleV1 {
  const session = buildSafeSessionDisplay(input.sessionStatus, input.selectedCompanyPresent);
  return {
    bundleVersion: DIAGNOSTIC_ALLOWLIST_VERSION,
    generatedAt: input.generatedAt,
    correlationId: randomUUID(),
    truncated: false,
    versions: {
      desktop: input.desktopVersion,
      connector: input.connectorVersion,
      bundledConnector: input.bundledConnectorVersion ?? null,
      electron: input.electronVersion,
      node: input.nodeVersion,
    },
    runtime: {
      platform: input.platform,
      osRelease: input.osRelease,
      architecture: input.architecture,
      uptimeSeconds: input.uptimeSeconds,
    },
    connector: {
      baseUrl: input.connectorBaseUrl,
      bindHost: input.connectorBindHost,
      networkExposure: input.connectorNetworkExposure,
      networkExposureWarning: input.connectorNetworkExposureWarning
        ? sanitizeDiagnosticText(input.connectorNetworkExposureWarning)
        : null,
      processState: sanitizeDiagnosticText(input.connectorProcessState, 120),
      ownership: input.connectorOwnership,
      pid: input.connectorPid,
      healthStatus: sanitizeDiagnosticText(input.healthStatus, 120),
      healthReachable: input.healthReachable,
      lastSuccessfulHealthCheck: input.lastSuccessfulHealthCheck,
      tallyReachable: input.tallyReachable,
    },
    session,
    configuration: buildSafeDiagnosticConfiguration(input.configuration),
    environment: stripEnvironmentVariables(input.environment),
    logs: {
      recentLifecycleEvents: sanitizeDiagnosticLogEntries(input.recentLifecycleEvents),
      recentErrors: sanitizeDiagnosticLogEntries(input.recentErrors),
    },
    privacyPolicy: {
      allowlistVersion: DIAGNOSTIC_ALLOWLIST_VERSION,
      exportSurface: 'desktop-diagnostics-bundle-v1',
    },
  };
}

export function serializeSafeDiagnosticBundle(bundle: SafeDiagnosticBundleV1): {
  readonly json: string;
  readonly truncated: boolean;
} {
  let truncated = bundle.truncated;
  let working: SafeDiagnosticBundleV1 = bundle;
  let json = `${JSON.stringify(working, null, 2)}\n`;

  if (json.length > DIAGNOSTIC_LIMITS.maxBundleBytes) {
    truncated = true;
    working = {
      bundleVersion: working.bundleVersion,
      generatedAt: working.generatedAt,
      correlationId: working.correlationId,
      truncated: true,
      versions: working.versions,
      runtime: working.runtime,
      connector: working.connector,
      session: working.session,
      configuration: working.configuration,
      environment: working.environment,
      logs: {
        recentLifecycleEvents: working.logs.recentLifecycleEvents.slice(0, 5),
        recentErrors: working.logs.recentErrors.slice(0, 5),
      },
      privacyPolicy: working.privacyPolicy,
    };
    json = `${JSON.stringify(working, null, 2)}\n`;
  }

  if (json.length > DIAGNOSTIC_LIMITS.maxBundleBytes) {
    truncated = true;
    working = {
      bundleVersion: working.bundleVersion,
      generatedAt: working.generatedAt,
      correlationId: working.correlationId,
      truncated: true,
      versions: working.versions,
      runtime: working.runtime,
      connector: working.connector,
      session: working.session,
      configuration: working.configuration,
      environment: working.environment,
      logs: {
        recentLifecycleEvents: [],
        recentErrors: [],
      },
      privacyPolicy: working.privacyPolicy,
    };
    json = `${JSON.stringify(working, null, 2)}\n`;
  }

  return { json, truncated };
}

export function formatSafeDiagnosticSummary(bundle: SafeDiagnosticBundleV1, logFile: SafeDiagnosticLogFileRef): string {
  return [
    'Budcom Desktop Diagnostics Summary',
    `Generated: ${bundle.generatedAt}`,
    `Correlation: ${bundle.correlationId}`,
    `Desktop: ${bundle.versions.desktop}`,
    `Connector: ${bundle.versions.connector ?? 'unavailable'}`,
    `Bundled connector: ${bundle.versions.bundledConnector ?? 'unavailable'}`,
    `Electron: ${bundle.versions.electron}`,
    `Node: ${bundle.versions.node}`,
    `OS: ${bundle.runtime.platform} ${bundle.runtime.osRelease} (${bundle.runtime.architecture})`,
    `Uptime: ${bundle.runtime.uptimeSeconds}s`,
    `Connector URL: ${bundle.connector.baseUrl}`,
    `Connector bind host: ${bundle.connector.bindHost} (${bundle.connector.networkExposure})`,
    bundle.connector.networkExposureWarning
      ? `Security warning: ${bundle.connector.networkExposureWarning}`
      : 'Connector network exposure: loopback-only (secure default).',
    `Process State: ${bundle.connector.processState}`,
    `Ownership: ${bundle.connector.ownership}`,
    `PID: ${bundle.connector.pid ?? 'n/a'}`,
    `Health: ${bundle.connector.healthStatus} (reachable=${bundle.connector.healthReachable})`,
    `Session: ${bundle.session.displayLabel}`,
    `Log file: ${logFile.available ? (logFile.basename ?? 'available') : 'unavailable'}`,
    bundle.truncated ? 'Note: bundle truncated to privacy/size limits.' : '',
  ]
    .filter(Boolean)
    .join('\n');
}

export function assertDiagnosticOutputExcludesSentinels(serialized: string, extraSentinels: readonly string[] = []): void {
  const haystack = serialized.toLowerCase();
  const prohibited = [
    ...Object.values(DIAGNOSTIC_PRIVACY_SENTINELS),
    ...extraSentinels,
  ];
  for (const sentinel of prohibited) {
    if (haystack.includes(sentinel.toLowerCase())) {
      throw new Error(`Prohibited diagnostic sentinel leaked: ${sentinel.slice(0, 32)}`);
    }
  }
}
