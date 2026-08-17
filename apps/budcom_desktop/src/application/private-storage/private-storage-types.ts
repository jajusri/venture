export type StorageMode = 'standard' | 'private-removable';

/**
 * Persisted once at first-run (or when the user later completes a controlled Standard->Private
 * migration, not implemented this session) and read at every subsequent startup. This is
 * deliberately a small, separate record from DesktopConfigV1 — "where is the data" is a
 * different concern from general app settings, and keeping it separate avoids widening the
 * heavily-validated DesktopConfigV1 schema with fields that only apply in one mode.
 */
export interface PrivateStorageLocatorV1 {
  readonly schemaVersion: 1;
  readonly mode: StorageMode;
  /** Present only when mode === 'private-removable'. Matches the marker file's own vaultId. */
  readonly vaultId: string | null;
  /** Best-effort last-known drive letter, e.g. "E:\\" — checked first on every resolve, not trusted alone. */
  readonly lastKnownDriveLetter: string | null;
  /** Best-effort volume label at the time it was last resolved, for display/troubleshooting only. */
  readonly lastKnownVolumeLabel: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** The marker file BUDCOM writes onto the selected removable volume itself, at `<drive>\BudcomPrivate\vault.json`. */
export interface PrivateStorageVaultMarker {
  readonly schemaVersion: 1;
  readonly vaultId: string;
  readonly createdAt: string;
}

export const BUDCOM_PRIVATE_ROOT_FOLDER = 'BudcomPrivate';
export const BUDCOM_VAULT_MARKER_FILE = 'vault.json';

export type StorageResolution =
  | { readonly status: 'standard' }
  | { readonly status: 'resolved'; readonly driveLetter: string; readonly vaultId: string; readonly dataRoot: string; readonly volumeLabel: string | null }
  | { readonly status: 'missing' }
  | { readonly status: 'mismatched'; readonly driveLetter: string; readonly foundVaultId: string | null };

/** Renderer-facing storage-gate state — see main.ts's resolveStorageGate()/startPrivateStorageWatchdog(). */
export type StorageGateState =
  | { readonly kind: 'resolving' }
  | { readonly kind: 'first-run' }
  | { readonly kind: 'ready'; readonly mode: 'standard' }
  | { readonly kind: 'ready'; readonly mode: 'private-removable'; readonly driveLetter: string; readonly volumeLabel: string | null }
  | { readonly kind: 'unavailable'; readonly reason: 'missing' | 'mismatched' };

export interface ChooseStorageModeResult {
  readonly ok: boolean;
  readonly message?: string;
  readonly state?: StorageGateState;
  /**
   * TD-033: true when this choice would switch away from an already-configured storage mode
   * (not first-run) and the caller did not set `confirmSwitch`. `ok` is false in this case — no
   * locator/vault state has been written. The caller must show `message` to the user and resubmit
   * the identical choice with `confirmSwitch: true` to proceed.
   */
  readonly requiresConfirmation?: boolean;
}
