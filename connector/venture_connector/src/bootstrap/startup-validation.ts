import fs from 'node:fs';
import path from 'node:path';

import type { ConnectorConfig } from '../config/defaults.js';

export class StartupValidationError extends Error {
  constructor(readonly diagnostics: readonly string[]) {
    super(`Startup validation failed: ${diagnostics.join('; ')}`);
    this.name = 'StartupValidationError';
  }
}

export function validateStartupConfiguration(config: ConnectorConfig): void {
  const diagnostics: string[] = [];

  requireText(config.databasePath, 'database path', diagnostics);
  requireText(config.host, 'API bind host', diagnostics);
  requireText(config.tallyHost, 'Tally host', diagnostics);
  requireInteger(config.port, 1, 65_535, 'API port', diagnostics);
  requireInteger(config.tallyPort, 1, 65_535, 'Tally port', diagnostics);
  requireInteger(config.tallyTimeoutMs, 1, Number.MAX_SAFE_INTEGER, 'Tally timeout', diagnostics);
  requireInteger(
    config.tallyPoolMaxConnections,
    1,
    Number.MAX_SAFE_INTEGER,
    'Tally connection pool size',
    diagnostics,
  );
  requireInteger(
    config.tallyMaxRequestBytes,
    1,
    Number.MAX_SAFE_INTEGER,
    'Tally request limit',
    diagnostics,
  );
  requireInteger(
    config.tallyMaxResponseBytes,
    1,
    Number.MAX_SAFE_INTEGER,
    'Tally response limit',
    diagnostics,
  );
  requireInteger(config.gracefulShutdownMs, 1, Number.MAX_SAFE_INTEGER, 'shutdown timeout', diagnostics);
  requireInteger(config.sessionTtlMs, 1, Number.MAX_SAFE_INTEGER, 'session TTL', diagnostics);

  requireInteger(config.secureTransportPort, 1, 65_535, 'secure transport port', diagnostics);

  if (diagnostics.length === 0) {
    validateWritableDirectory(config.databasePath, 'database directory', diagnostics);
    if (config.tallyRequestAuditEnabled) {
      validateWritableDirectory(
        path.dirname(path.resolve(config.tallyRequestAuditPath)),
        'Tally request audit directory',
        diagnostics,
      );
    }
    if (config.secureTransportEnabled) {
      validateWritableDirectory(
        path.resolve(config.transportIdentityDir),
        'transport identity directory',
        diagnostics,
      );
    }
  }

  if (diagnostics.length > 0) throw new StartupValidationError(Object.freeze(diagnostics));
}

function requireText(value: string, label: string, diagnostics: string[]): void {
  if (!value.trim()) diagnostics.push(`${label} must not be empty`);
}

function requireInteger(
  value: number,
  min: number,
  max: number,
  label: string,
  diagnostics: string[],
): void {
  if (!Number.isInteger(value) || value < min || value > max) {
    diagnostics.push(`${label} must be an integer between ${min} and ${max}`);
  }
}

function validateWritableDirectory(directory: string, label: string, diagnostics: string[]): void {
  try {
    fs.mkdirSync(directory, { recursive: true });
    const stats = fs.statSync(directory);
    if (!stats.isDirectory()) {
      diagnostics.push(`${label} is not a directory: ${directory}`);
      return;
    }
    fs.accessSync(directory, fs.constants.R_OK | fs.constants.W_OK);
  } catch (error) {
    diagnostics.push(
      `${label} is unavailable at ${directory}: ${
        error instanceof Error ? error.message : String(error)
      }`,
    );
  }
}
