import { normalizeName } from '../../extraction/normalization/strings.js';
import { isLegacyLedgerId } from '../../extraction/core/ledger-identity.js';
import type { LedgerDetails } from '../../erp/ledger/ledger-domain.js';

export type LegacyMigrationCoverageReason =
  | 'empty_extraction'
  | 'partial_extraction'
  | 'invalid_extraction'
  | 'validation_failure'
  | 'incoming_identity_incomplete'
  | 'legacy_coverage_incomplete'
  | 'legacy_coverage_ambiguous';

export interface LegacyMigrationRow {
  readonly id: string;
  readonly name: string;
  readonly normalizedName: string;
}

export interface LegacyMigrationCoverageResult {
  readonly existingLegacyCount: number;
  readonly matchedLegacyCount: number;
  readonly unmatchedLegacyCount: number;
  readonly ambiguousMatchCount: number;
  readonly incomingCount: number;
  readonly safeToReplace: boolean;
  readonly reason?: LegacyMigrationCoverageReason;
  readonly message?: string;
}

/** Canonical migration correspondence key — same normalizeName() used at extraction time. */
export function resolveLedgerMigrationKey(name: string): string {
  const key = normalizeName(name);
  if (!key) {
    throw new Error('Ledger migration key could not be derived from name.');
  }
  return key;
}

export function toLegacyMigrationRow(ledger: Pick<LedgerDetails, 'id' | 'name' | 'normalizedName'>): LegacyMigrationRow | null {
  if (!isLegacyLedgerId(ledger.id)) {
    return null;
  }
  return {
    id: ledger.id,
    name: ledger.name,
    normalizedName: ledger.normalizedName,
  };
}

function incomingHasGuidFirstIdentity(ledger: LedgerDetails): boolean {
  return Boolean(ledger.guid?.trim()) && ledger.identitySource === 'guid' && ledger.id.startsWith('guid:');
}

export function assessLegacyMigrationCoverage(
  existingLegacy: readonly LegacyMigrationRow[],
  incoming: readonly LedgerDetails[],
): LegacyMigrationCoverageResult {
  const base = {
    existingLegacyCount: existingLegacy.length,
    matchedLegacyCount: 0,
    unmatchedLegacyCount: 0,
    ambiguousMatchCount: 0,
    incomingCount: incoming.length,
    safeToReplace: false,
  } satisfies Omit<LegacyMigrationCoverageResult, 'reason' | 'message'>;

  if (existingLegacy.length === 0) {
    return { ...base, safeToReplace: true };
  }

  for (const ledger of incoming) {
    if (!incomingHasGuidFirstIdentity(ledger)) {
      return {
        ...base,
        reason: 'incoming_identity_incomplete',
        message:
          'Ledger identity migration blocked: incoming extraction must contain GUID-first identities for all rows.',
      };
    }
  }

  const incomingByKey = new Map<string, number>();
  for (const ledger of incoming) {
    const key = resolveLedgerMigrationKey(ledger.name);
    incomingByKey.set(key, (incomingByKey.get(key) ?? 0) + 1);
  }

  const legacyByKey = new Map<string, number>();
  for (const legacy of existingLegacy) {
    const key = resolveLedgerMigrationKey(legacy.name);
    legacyByKey.set(key, (legacyByKey.get(key) ?? 0) + 1);
  }

  let ambiguousMatchCount = 0;
  for (const count of incomingByKey.values()) {
    if (count > 1) {
      ambiguousMatchCount += count;
    }
  }
  for (const count of legacyByKey.values()) {
    if (count > 1) {
      ambiguousMatchCount += count;
    }
  }

  let matchedLegacyCount = 0;
  let unmatchedLegacyCount = 0;
  for (const legacy of existingLegacy) {
    const key = resolveLedgerMigrationKey(legacy.name);
    const legacyKeyCount = legacyByKey.get(key) ?? 0;
    const incomingKeyCount = incomingByKey.get(key) ?? 0;

    if (legacyKeyCount > 1 || incomingKeyCount > 1) {
      continue;
    }
    if (incomingKeyCount === 1) {
      matchedLegacyCount += 1;
    } else {
      unmatchedLegacyCount += 1;
    }
  }

  if (ambiguousMatchCount > 0) {
    return {
      ...base,
      matchedLegacyCount,
      unmatchedLegacyCount,
      ambiguousMatchCount,
      reason: 'legacy_coverage_ambiguous',
      message: `Ledger identity migration blocked: ${ambiguousMatchCount} ambiguous legacy-to-incoming correspondence conflict(s).`,
    };
  }

  if (unmatchedLegacyCount > 0) {
    return {
      ...base,
      matchedLegacyCount,
      unmatchedLegacyCount,
      ambiguousMatchCount,
      reason: 'legacy_coverage_incomplete',
      message: `Ledger identity migration deferred: ${unmatchedLegacyCount} legacy row(s) have no matching incoming ledger (rename or incomplete export).`,
    };
  }

  if (matchedLegacyCount !== existingLegacy.length) {
    return {
      ...base,
      matchedLegacyCount,
      unmatchedLegacyCount,
      ambiguousMatchCount,
      reason: 'legacy_coverage_incomplete',
      message: 'Ledger identity migration blocked: legacy coverage incomplete.',
    };
  }

  return {
    ...base,
    matchedLegacyCount,
    unmatchedLegacyCount,
    ambiguousMatchCount,
    safeToReplace: true,
  };
}

export function assertLegacyMigrationCoverage(
  existingLegacy: readonly LegacyMigrationRow[],
  incoming: readonly LedgerDetails[],
): LegacyMigrationCoverageResult {
  const result = assessLegacyMigrationCoverage(existingLegacy, incoming);
  if (!result.safeToReplace) {
    throw new Error(result.message ?? 'Ledger identity migration coverage check failed.');
  }
  return result;
}

export function toPrivacySafeMigrationCoverage(
  result: LegacyMigrationCoverageResult,
): Record<string, number | boolean | string | undefined> {
  return {
    existingLegacyCount: result.existingLegacyCount,
    matchedLegacyCount: result.matchedLegacyCount,
    unmatchedLegacyCount: result.unmatchedLegacyCount,
    ambiguousMatchCount: result.ambiguousMatchCount,
    incomingCount: result.incomingCount,
    safeToReplace: result.safeToReplace,
    reason: result.reason,
  };
}
