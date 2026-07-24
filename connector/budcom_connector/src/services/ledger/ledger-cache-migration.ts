import { LEDGER_IDENTITY_VERSION } from '../../extraction/core/ledger-identity.js';
import type { LedgerDetails } from '../../erp/ledger/ledger-domain.js';
import type { LedgerExtractionAssessment } from '../../erp/ledger/ledger-extraction-quality.js';
import type { LedgerValidationResult } from '../../erp/ledger/ledger-validation.js';

export const LEDGER_IDENTITY_META_PREFIX = 'ledger_identity_version:' as const;

export function ledgerIdentityMetaKey(companyId: string): string {
  return `${LEDGER_IDENTITY_META_PREFIX}${companyId}`;
}

export function parseLedgerIdentityVersion(value: string | null | undefined): number {
  if (!value) {
    return 1;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : 1;
}

export function companyNeedsLedgerIdentityMigration(
  identityVersion: number,
  hasLegacyLedgerIds: boolean,
  ledgerCount: number,
): boolean {
  if (hasLegacyLedgerIds) {
    return true;
  }
  return ledgerCount > 0 && identityVersion < LEDGER_IDENTITY_VERSION;
}

export interface LedgerRebuildPrecheckInput {
  readonly assessment: LedgerExtractionAssessment;
  readonly validation: LedgerValidationResult;
  readonly ledgers: readonly LedgerDetails[];
}

export function assertLedgerRebuildPrecheck(input: LedgerRebuildPrecheckInput): void {
  if (input.assessment.quality === 'invalid') {
    throw new Error(input.assessment.reason ?? 'Invalid ledger extraction for cache rebuild.');
  }
  if (!input.validation.ok) {
    throw new Error('Ledger collection validation failed; cache rebuild aborted.');
  }
  const duplicateIds = new Set<string>();
  for (const ledger of input.ledgers) {
    if (duplicateIds.has(ledger.id)) {
      throw new Error('Duplicate resolved ledger identities detected; cache rebuild aborted.');
    }
    duplicateIds.add(ledger.id);
  }
}
