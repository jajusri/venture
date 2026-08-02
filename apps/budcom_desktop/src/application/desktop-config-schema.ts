export const DESKTOP_CONFIG_SCHEMA_VERSION = 1 as const;

export type DesktopLogLevel = 'debug' | 'info' | 'warn' | 'error';
export type ConnectorBindMode = 'local-only' | 'trusted-lan';

export interface DesktopConfigV1 {
  readonly schemaVersion: typeof DESKTOP_CONFIG_SCHEMA_VERSION;
  readonly connectorBindMode: ConnectorBindMode;
  readonly connectorHost: string;
  readonly connectorPort: number;
  readonly autoStartConnector: boolean;
  readonly healthPollIntervalMs: number;
  readonly startupTimeoutMs: number;
  readonly shutdownGraceMs: number;
  readonly maxRestartAttempts: number;
  readonly reconnectBaseDelayMs: number;
  readonly logLevel: DesktopLogLevel;
  readonly diagnosticsRetentionDays: number;
  readonly tallyHost: string;
  readonly tallyPort: number;
}

export type PersistedDesktopConfig = DesktopConfigV1;

export interface ConfigValidationError {
  readonly field: string;
  readonly message: string;
}

export interface ConfigValidationResult {
  readonly ok: boolean;
  readonly config: DesktopConfigV1 | null;
  readonly errors: readonly ConfigValidationError[];
}

const LOG_LEVELS: readonly DesktopLogLevel[] = ['debug', 'info', 'warn', 'error'];
const CONNECTOR_BIND_MODES: readonly ConnectorBindMode[] = ['local-only', 'trusted-lan'];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(value: unknown, field: string, errors: ConfigValidationError[]): string | null {
  if (typeof value !== 'string' || value.trim().length === 0) {
    errors.push({ field, message: `${field} must be a non-empty string.` });
    return null;
  }
  return value.trim();
}

function readNumber(
  value: unknown,
  field: string,
  errors: ConfigValidationError[],
  min: number,
  max: number,
): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
    errors.push({ field, message: `${field} must be a number between ${min} and ${max}.` });
    return null;
  }
  return value;
}

function readBoolean(value: unknown, field: string, errors: ConfigValidationError[]): boolean | null {
  if (typeof value !== 'boolean') {
    errors.push({ field, message: `${field} must be a boolean.` });
    return null;
  }
  return value;
}

function isPrivateIpv4Literal(value: string): boolean {
  const parts = value.split('.');
  if (parts.length !== 4) {
    return false;
  }
  const bytes = parts.map((part) => Number.parseInt(part, 10));
  if (bytes.some((byte) => !Number.isInteger(byte) || byte < 0 || byte > 255)) {
    return false;
  }
  const [a, b] = bytes;
  if (a === undefined || b === undefined) {
    return false;
  }
  return (
    a === 10
    || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168)
  );
}

export function buildConnectorBaseUrl(host: string, port: number): string {
  return `http://${host}:${port}`;
}

export function parseConnectorHostPort(baseUrl: string): { host: string; port: number } | null {
  try {
    const parsed = new URL(baseUrl);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return null;
    }
    const host = parsed.hostname;
    const port = parsed.port
      ? Number.parseInt(parsed.port, 10)
      : parsed.protocol === 'https:' ? 443 : 80;
    if (!host || !Number.isFinite(port)) {
      return null;
    }
    return { host, port };
  } catch {
    return null;
  }
}

export function validateDesktopConfig(input: unknown): ConfigValidationResult {
  const errors: ConfigValidationError[] = [];
  if (!isRecord(input)) {
    return {
      ok: false,
      config: null,
      errors: [{ field: 'root', message: 'Configuration must be a JSON object.' }],
    };
  }

  if (input.schemaVersion !== DESKTOP_CONFIG_SCHEMA_VERSION) {
    errors.push({
      field: 'schemaVersion',
      message: `Unsupported schema version. Expected ${DESKTOP_CONFIG_SCHEMA_VERSION}.`,
    });
  }

  const connectorHost = readString(input.connectorHost, 'connectorHost', errors);
  let connectorBindMode: ConnectorBindMode | null = null;
  if (
    typeof input.connectorBindMode !== 'string'
    || !CONNECTOR_BIND_MODES.includes(input.connectorBindMode as ConnectorBindMode)
  ) {
    errors.push({
      field: 'connectorBindMode',
      message: `connectorBindMode must be one of: ${CONNECTOR_BIND_MODES.join(', ')}.`,
    });
  } else {
    connectorBindMode = input.connectorBindMode as ConnectorBindMode;
  }
  const connectorPort = readNumber(input.connectorPort, 'connectorPort', errors, 1, 65535);
  const autoStartConnector = readBoolean(input.autoStartConnector, 'autoStartConnector', errors);
  const healthPollIntervalMs = readNumber(input.healthPollIntervalMs, 'healthPollIntervalMs', errors, 1_000, 300_000);
  const startupTimeoutMs = readNumber(input.startupTimeoutMs, 'startupTimeoutMs', errors, 5_000, 300_000);
  const shutdownGraceMs = readNumber(input.shutdownGraceMs, 'shutdownGraceMs', errors, 1_000, 60_000);
  const maxRestartAttempts = readNumber(input.maxRestartAttempts, 'maxRestartAttempts', errors, 0, 20);
  const reconnectBaseDelayMs = readNumber(input.reconnectBaseDelayMs, 'reconnectBaseDelayMs', errors, 100, 60_000);
  const diagnosticsRetentionDays = readNumber(input.diagnosticsRetentionDays, 'diagnosticsRetentionDays', errors, 1, 90);

  let logLevel: DesktopLogLevel | null = null;
  if (typeof input.logLevel !== 'string' || !LOG_LEVELS.includes(input.logLevel as DesktopLogLevel)) {
    errors.push({ field: 'logLevel', message: `logLevel must be one of: ${LOG_LEVELS.join(', ')}.` });
  } else {
    logLevel = input.logLevel as DesktopLogLevel;
  }

  const tallyHost = readString(input.tallyHost, 'tallyHost', errors);
  const tallyPort = readNumber(input.tallyPort, 'tallyPort', errors, 1, 65535);

  if (connectorHost && !/^[a-zA-Z0-9.-]+$/.test(connectorHost)) {
    errors.push({ field: 'connectorHost', message: 'connectorHost contains invalid characters.' });
  }
  if (connectorHost && (connectorHost === '0.0.0.0' || connectorHost === '::')) {
    errors.push({
      field: 'connectorHost',
      message: 'connectorHost 0.0.0.0 is not permitted. Use 127.0.0.1 or a specific LAN address.',
    });
  }
  if (connectorBindMode === 'local-only' && connectorHost && connectorHost !== '127.0.0.1') {
    errors.push({
      field: 'connectorHost',
      message: 'Local-only mode requires connectorHost to be 127.0.0.1.',
    });
  }
  if (connectorBindMode === 'trusted-lan' && connectorHost && !isPrivateIpv4Literal(connectorHost)) {
    errors.push({
      field: 'connectorHost',
      message: 'Trusted-LAN mode requires connectorHost to be a private IPv4 address.',
    });
  }

  if (errors.length > 0) {
    return { ok: false, config: null, errors };
  }

  return {
    ok: true,
    config: {
      schemaVersion: DESKTOP_CONFIG_SCHEMA_VERSION,
      connectorBindMode: connectorBindMode!,
      connectorHost: connectorHost!,
      connectorPort: connectorPort!,
      autoStartConnector: autoStartConnector!,
      healthPollIntervalMs: healthPollIntervalMs!,
      startupTimeoutMs: startupTimeoutMs!,
      shutdownGraceMs: shutdownGraceMs!,
      maxRestartAttempts: maxRestartAttempts!,
      reconnectBaseDelayMs: reconnectBaseDelayMs!,
      logLevel: logLevel!,
      diagnosticsRetentionDays: diagnosticsRetentionDays!,
      tallyHost: tallyHost!,
      tallyPort: tallyPort!,
    },
    errors: [],
  };
}

export function validateSettingsPatch(
  input: unknown,
  base: DesktopConfigV1,
): ConfigValidationResult {
  if (!isRecord(input)) {
    return {
      ok: false,
      config: null,
      errors: [{ field: 'root', message: 'Settings payload must be an object.' }],
    };
  }

  return validateDesktopConfig({
    ...base,
    ...input,
    schemaVersion: DESKTOP_CONFIG_SCHEMA_VERSION,
  });
}

/** Fields that require desktop restart or lifecycle re-init to take full effect. */
export const RESTART_REQUIRED_FIELDS: readonly (keyof DesktopConfigV1)[] = [
  'connectorBindMode',
  'connectorHost',
  'connectorPort',
  'healthPollIntervalMs',
  'startupTimeoutMs',
  'shutdownGraceMs',
  'maxRestartAttempts',
  'reconnectBaseDelayMs',
  'tallyHost',
  'tallyPort',
];

export function requiresRestart(
  previous: DesktopConfigV1,
  next: DesktopConfigV1,
): boolean {
  return RESTART_REQUIRED_FIELDS.some((field) => previous[field] !== next[field]);
}
