import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

import type { RemovableVolumeEnumerator } from './removable-volume-enumerator.js';
import {
  BUDCOM_PRIVATE_ROOT_FOLDER,
  BUDCOM_VAULT_MARKER_FILE,
  type PrivateStorageLocatorV1,
  type PrivateStorageVaultMarker,
  type StorageResolution,
} from './private-storage-types.js';

export type PrivateStorageResolverFsPort = Pick<
  typeof fs,
  'existsSync' | 'readFileSync' | 'writeFileSync' | 'mkdirSync'
>;

function budcomRootOf(driveLetter: string): string {
  return path.join(driveLetter, BUDCOM_PRIVATE_ROOT_FOLDER);
}

function markerPathOf(driveLetter: string): string {
  return path.join(budcomRootOf(driveLetter), BUDCOM_VAULT_MARKER_FILE);
}

/** `<drive>\BudcomPrivate\<vaultId>\connector-data` — mirrors the AppData layout's connectorDataDir shape. */
export function privateConnectorDataDir(driveLetter: string, vaultId: string): string {
  return path.join(budcomRootOf(driveLetter), vaultId, 'connector-data');
}

/**
 * Reads whatever BUDCOM vault marker already exists on [driveLetter], if any — used when the
 * user picks a drive at setup time so an existing vault (e.g. from a prior interrupted setup, or
 * a Desktop reinstall pointed at the same USB) is adopted rather than a second vault being
 * created alongside it.
 */
export function readExistingVaultOnDrive(
  driveLetter: string,
  fsImpl: PrivateStorageResolverFsPort = fs,
): PrivateStorageVaultMarker | null {
  return readMarker(driveLetter, fsImpl);
}

function readMarker(driveLetter: string, fsImpl: PrivateStorageResolverFsPort): PrivateStorageVaultMarker | null {
  const markerPath = markerPathOf(driveLetter);
  if (!fsImpl.existsSync(markerPath)) return null;
  try {
    const parsed = JSON.parse(fsImpl.readFileSync(markerPath, 'utf8')) as Partial<PrivateStorageVaultMarker>;
    if (typeof parsed.vaultId !== 'string' || !parsed.vaultId) return null;
    return { schemaVersion: 1, vaultId: parsed.vaultId, createdAt: typeof parsed.createdAt === 'string' ? parsed.createdAt : '' };
  } catch {
    return null;
  }
}

/**
 * Creates a brand-new private vault on `driveLetter`: writes the vault marker and the
 * connector-data directory, but never touches any pre-existing BudcomPrivate folder on that
 * drive — if one already exists (e.g. the user picked a drive that already has a BUDCOM vault
 * from a different install), the caller must resolve/adopt the existing vault instead of calling
 * this, since overwriting an existing marker would orphan whatever data already lives under its
 * vaultId.
 */
export function createPrivateVault(
  driveLetter: string,
  fsImpl: PrivateStorageResolverFsPort = fs,
  vaultId: string = crypto.randomUUID(),
): { readonly vaultId: string; readonly dataRoot: string } {
  const existing = readMarker(driveLetter, fsImpl);
  if (existing) {
    throw new Error(
      `A BUDCOM private vault already exists on ${driveLetter} (vaultId ${existing.vaultId}). Choose a different drive or adopt the existing vault.`,
    );
  }
  const marker: PrivateStorageVaultMarker = { schemaVersion: 1, vaultId, createdAt: new Date().toISOString() };
  fsImpl.mkdirSync(budcomRootOf(driveLetter), { recursive: true });
  fsImpl.writeFileSync(markerPathOf(driveLetter), `${JSON.stringify(marker, null, 2)}\n`, 'utf8');
  const dataRoot = privateConnectorDataDir(driveLetter, vaultId);
  fsImpl.mkdirSync(dataRoot, { recursive: true });
  return { vaultId, dataRoot };
}

/**
 * Resolves the drive currently holding the configured private vault. Checks the last-known
 * drive letter first (cheap, common case); if that fails, performs a BOUNDED rediscovery over
 * only the drives [enumerator] reports as removable right now — never scans fixed disks, never
 * attaches to a drive whose marker vaultId doesn't match exactly (rejecting lookalike media).
 */
export async function resolvePrivateVault(
  locator: Pick<PrivateStorageLocatorV1, 'mode' | 'vaultId' | 'lastKnownDriveLetter'>,
  enumerator: RemovableVolumeEnumerator,
  fsImpl: PrivateStorageResolverFsPort = fs,
): Promise<StorageResolution> {
  if (locator.mode === 'standard') return { status: 'standard' };
  if (!locator.vaultId) return { status: 'missing' };
  const expectedVaultId = locator.vaultId;

  if (locator.lastKnownDriveLetter) {
    const marker = readMarker(locator.lastKnownDriveLetter, fsImpl);
    if (marker && marker.vaultId === expectedVaultId) {
      return {
        status: 'resolved',
        driveLetter: locator.lastKnownDriveLetter,
        vaultId: expectedVaultId,
        dataRoot: privateConnectorDataDir(locator.lastKnownDriveLetter, expectedVaultId),
        volumeLabel: null,
      };
    }
  }

  const volumes = await enumerator.listRemovableVolumes();
  for (const volume of volumes) {
    const marker = readMarker(volume.driveLetter, fsImpl);
    if (marker && marker.vaultId === expectedVaultId) {
      return {
        status: 'resolved',
        driveLetter: volume.driveLetter,
        vaultId: expectedVaultId,
        dataRoot: privateConnectorDataDir(volume.driveLetter, expectedVaultId),
        volumeLabel: volume.label,
      };
    }
    if (marker && marker.vaultId !== expectedVaultId && volume.driveLetter === locator.lastKnownDriveLetter) {
      // The last-known letter now holds a *different* BUDCOM vault (lookalike media) — worth
      // distinguishing from plain "missing" for diagnostics, even though the caller's UI
      // treatment (fail closed, do not attach) is the same as 'missing'.
      return { status: 'mismatched', driveLetter: volume.driveLetter, foundVaultId: marker.vaultId };
    }
  }

  return { status: 'missing' };
}

/** Cheap re-check for the hot-removal poll — does the already-resolved drive+vault still match? */
export function isPrivateVaultStillPresent(
  driveLetter: string,
  expectedVaultId: string,
  fsImpl: PrivateStorageResolverFsPort = fs,
): boolean {
  const marker = readMarker(driveLetter, fsImpl);
  return marker !== null && marker.vaultId === expectedVaultId;
}

export function privateStorageMarkerPath(driveLetter: string): string {
  return markerPathOf(driveLetter);
}

/**
 * True when a real Connector database already exists at the default Standard/AppData location.
 * The private-storage locator file is new with this feature, so its mere absence can't
 * distinguish a genuinely fresh install from an existing Standard-mode user upgrading into a
 * build that has this feature for the first time — both look identical to the caller otherwise.
 * An existing user must never be shown the storage picker: selecting Private there would point
 * the Connector at a brand-new empty vault while their real data sits untouched but unreachable
 * in AppData, which reads exactly like data loss.
 */
export function hasExistingStandardModeDatabase(
  connectorDataDir: string,
  fsImpl: Pick<PrivateStorageResolverFsPort, 'existsSync'> = fs,
): boolean {
  return fsImpl.existsSync(path.join(connectorDataDir, 'budcom-ledger.db'));
}
