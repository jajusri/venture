import fs from 'node:fs';
import path from 'node:path';

import type { PrivateStorageLocatorV1 } from './private-storage-types.js';

export const PRIVATE_STORAGE_LOCATOR_FILE_NAME = 'private-storage-locator.json';

export interface PrivateStorageLocatorPaths {
  readonly filePath: string;
  readonly tempPath: string;
  readonly backupPath: string;
}

export function resolvePrivateStorageLocatorPaths(userDataDir: string): PrivateStorageLocatorPaths {
  const filePath = path.join(userDataDir, PRIVATE_STORAGE_LOCATOR_FILE_NAME);
  return {
    filePath,
    tempPath: `${filePath}.tmp`,
    backupPath: path.join(userDataDir, 'private-storage-locator.backup.json'),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Validates untrusted JSON read from disk before trusting it as a locator record. */
export function parsePrivateStorageLocator(value: unknown): PrivateStorageLocatorV1 | null {
  if (!isRecord(value)) return null;
  if (value.schemaVersion !== 1) return null;
  if (value.mode !== 'standard' && value.mode !== 'private-removable') return null;
  const optionalString = (field: unknown): string | null => (typeof field === 'string' ? field : null);
  if (typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') return null;
  return {
    schemaVersion: 1,
    mode: value.mode,
    vaultId: optionalString(value.vaultId),
    lastKnownDriveLetter: optionalString(value.lastKnownDriveLetter),
    lastKnownVolumeLabel: optionalString(value.lastKnownVolumeLabel),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  };
}

export type PrivateStorageLocatorFsPort = Pick<
  typeof fs,
  'existsSync' | 'readFileSync' | 'writeFileSync' | 'renameSync' | 'mkdirSync' | 'copyFileSync'
>;

/**
 * Small, dedicated store for the "where is VENTURE's private business data" record — deliberately
 * NOT part of DesktopConfigStore/DesktopConfigV1 (see private-storage-types.ts doc comment).
 * Absence of this file is the definitive "first run, no storage-mode decision made yet" signal.
 * Uses the same atomic-write-with-backup pattern as DesktopConfigStore.
 */
export class PrivateStorageLocatorStore {
  private readonly paths: PrivateStorageLocatorPaths;
  private readonly fsImpl: PrivateStorageLocatorFsPort;

  constructor(userDataDir: string, fsImpl: PrivateStorageLocatorFsPort = fs) {
    this.paths = resolvePrivateStorageLocatorPaths(userDataDir);
    this.fsImpl = fsImpl;
  }

  exists(): boolean {
    return this.fsImpl.existsSync(this.paths.filePath);
  }

  load(): PrivateStorageLocatorV1 | null {
    if (!this.fsImpl.existsSync(this.paths.filePath)) return null;
    try {
      const raw = this.fsImpl.readFileSync(this.paths.filePath, 'utf8');
      return parsePrivateStorageLocator(JSON.parse(raw));
    } catch {
      // A corrupt locator must never be treated as "no decision made" (which would re-trigger
      // first-run and risk a second vault being created) nor as a resolvable private vault. The
      // caller's resolver treats null-with-file-present as "needs attention", not "first run".
      return null;
    }
  }

  save(record: PrivateStorageLocatorV1): { ok: true } | { ok: false; message: string } {
    try {
      this.fsImpl.mkdirSync(path.dirname(this.paths.filePath), { recursive: true });
      const payload = `${JSON.stringify(record, null, 2)}\n`;
      this.fsImpl.writeFileSync(this.paths.tempPath, payload, 'utf8');
      if (this.fsImpl.existsSync(this.paths.filePath)) {
        this.fsImpl.copyFileSync(this.paths.filePath, this.paths.backupPath);
      }
      this.fsImpl.renameSync(this.paths.tempPath, this.paths.filePath);
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return { ok: false, message: `Failed to save private-storage locator: ${message}` };
    }
  }
}
