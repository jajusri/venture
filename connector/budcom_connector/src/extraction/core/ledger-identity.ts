import { slugify } from '../normalization/strings.js';

export const LEDGER_IDENTITY_VERSION = 2 as const;

export type LedgerIdentitySource = 'guid' | 'name';

export interface LedgerIdentityInput {
  readonly guid?: string;
  readonly name: string;
}

export interface LedgerIdentityResult {
  readonly id: string;
  readonly identitySource: LedgerIdentitySource;
  readonly guid?: string;
}

/**
 * Approved rich-fetch field list for ledger collection export. `PARENT` restored 2026-08-19
 * (TD-035) -- see `docs/technical-debt/registry.md` and `master-data-templates.ts`'s own inline
 * comment for the full history (removed 2026-08-03 for TD-001, safe again since TD-001's
 * 2026-08-16 shared-parser sanitizer fix).
 */
export const LEDGER_RICH_FETCH_FIELDS = [
  'NAME',
  'GUID',
  'ALTERID',
  'MASTERID',
  'PARENT',
  'OPENINGBALANCE',
  'CLOSINGBALANCE',
  'ISBILLWISEON',
] as const;

export function normalizeLedgerGuid(value: string): string {
  return value.trim().toLowerCase();
}

/** True when the persisted local ID predates GUID/name-prefix identity (legacy slug-only). */
export function isLegacyLedgerId(ledgerId: string): boolean {
  return !ledgerId.startsWith('guid:') && !ledgerId.startsWith('name:');
}

/**
 * Deterministic ledger identity: GUID → name slug fallback.
 * AlterID and MasterID are intentionally excluded from the identity chain.
 */
export function resolveLedgerStableId(input: LedgerIdentityInput): LedgerIdentityResult {
  const guid = input.guid?.trim();
  if (guid) {
    const normalized = normalizeLedgerGuid(guid);
    return { id: `guid:${normalized}`, identitySource: 'guid', guid: normalized };
  }
  const name = input.name.trim();
  if (!name) {
    throw new Error('Ledger name is required for identity resolution.');
  }
  return { id: `name:${slugify(name)}`, identitySource: 'name' };
}
