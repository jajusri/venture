import fs from 'node:fs';

import type { ConnectorConfig } from '../config/defaults.js';

/**
 * Thrown when Private Removable Storage mode is configured (both config fields set) but the
 * expected vault cannot be verified present. The caller (SqliteStorageService.start()) must
 * propagate this as a startup failure — never catch it and fall back to creating/opening a
 * database at any other location.
 */
export class PrivateStorageUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PrivateStorageUnavailableError';
  }
}

interface PrivateStorageMarkerFile {
  readonly vaultId?: unknown;
}

export interface PrivateStorageGuardFsPort {
  existsSync(path: string): boolean;
  readFileSync(path: string, encoding: 'utf8'): string;
}

/**
 * Defense-in-depth check for Private Removable Storage mode. When Desktop has configured a
 * private vault (both `privateStorageExpectedVaultId` and `privateStorageMarkerPath` set), this
 * refuses to let storage initialize unless the marker file at `privateStorageMarkerPath` exists
 * and its own `vaultId` matches exactly — closing the gap where a removable drive letter gets
 * silently reused by unrelated media (a "lookalike" drive) between Connector launches, which
 * would otherwise cause `SqliteStorageService.start()`'s own `fs.mkdirSync(..., {recursive:
 * true})` to silently create and open a brand-new, empty database on the wrong drive.
 *
 * When either config field is null (standard storage, the default for every existing
 * installation), this is a complete no-op — byte-for-byte unchanged behavior.
 *
 * This check is independent of whatever presence-checking Desktop performs before ever spawning
 * the Connector; it is the Connector's own last line of defense, not a replacement for it.
 */
export function assertPrivateStorageVaultPresent(
  config: Pick<ConnectorConfig, 'privateStorageExpectedVaultId' | 'privateStorageMarkerPath'>,
  fsImpl: PrivateStorageGuardFsPort = fs,
): void {
  const { privateStorageExpectedVaultId: expectedVaultId, privateStorageMarkerPath: markerPath } = config;
  if (!expectedVaultId || !markerPath) return;

  if (!fsImpl.existsSync(markerPath)) {
    throw new PrivateStorageUnavailableError(
      'Private VENTURE storage is not connected: vault marker not found at the configured location.',
    );
  }

  let parsed: PrivateStorageMarkerFile;
  try {
    parsed = JSON.parse(fsImpl.readFileSync(markerPath, 'utf8')) as PrivateStorageMarkerFile;
  } catch {
    throw new PrivateStorageUnavailableError('Private VENTURE storage vault marker could not be read.');
  }

  if (typeof parsed.vaultId !== 'string' || parsed.vaultId !== expectedVaultId) {
    throw new PrivateStorageUnavailableError(
      'Connected removable storage does not match the configured private VENTURE vault.',
    );
  }
}
