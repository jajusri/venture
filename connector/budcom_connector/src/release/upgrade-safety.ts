import { STORAGE_SCHEMA_VERSION } from '../storage/sqlite/schema.js';

export interface SchemaVersionState {
  readonly appliedVersion: number;
  readonly expectedVersion: number;
}

export type UpgradeSafetyOutcome =
  | { readonly kind: 'current' }
  | { readonly kind: 'upgrade_required'; readonly fromVersion: number; readonly toVersion: number }
  | { readonly kind: 'future_schema'; readonly appliedVersion: number; readonly expectedVersion: number }
  | { readonly kind: 'downgrade_blocked'; readonly appliedVersion: number; readonly expectedVersion: number };

export function assessUpgradeSafety(appliedVersion: number, expectedVersion = STORAGE_SCHEMA_VERSION): UpgradeSafetyOutcome {
  if (appliedVersion > expectedVersion) {
    return {
      kind: 'future_schema',
      appliedVersion,
      expectedVersion,
    };
  }
  if (appliedVersion === expectedVersion) {
    return { kind: 'current' };
  }
  if (appliedVersion < expectedVersion) {
    return {
      kind: 'upgrade_required',
      fromVersion: appliedVersion,
      toVersion: expectedVersion,
    };
  }
  return {
    kind: 'downgrade_blocked',
    appliedVersion,
    expectedVersion,
  };
}

export function migrationFailureMessage(outcome: Exclude<UpgradeSafetyOutcome, { kind: 'current' | 'upgrade_required' }>): string {
  switch (outcome.kind) {
    case 'future_schema':
      return `Local database schema version ${outcome.appliedVersion} is newer than this connector supports (${outcome.expectedVersion}). Upgrade the application before opening this database.`;
    case 'downgrade_blocked':
      return `Database schema version ${outcome.appliedVersion} is incompatible with connector schema ${outcome.expectedVersion}. Downgrade is not supported.`;
    default:
      return 'Database schema is incompatible with this connector version.';
  }
}
