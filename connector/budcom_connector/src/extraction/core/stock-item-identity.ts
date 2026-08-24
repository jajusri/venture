import { slugify } from '../normalization/strings.js';

/**
 * MVP-1.4 Catalogue prerequisite (docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md §1/
 * §16/§21 Milestone 0). `mapStockItem()` (`entity-mappers.ts`) has always parsed `PARENT`,
 * `CATEGORY`, `BASEUNITS`, `CLOSINGBALANCE` and `GSTAPPLICABLE` off the STOCKITEM XML node, but the
 * routine stock-item collection Fetch list (`MasterDataTemplates.stockItems`, unchanged by this
 * constant) has never requested any of them -- the identical missing-Fetch-field bug shape as
 * TD-035 (Ledgers PARENT) and TD-042 (Ledger Alias), tracked as TD-043. This is the field list a
 * *future*, separately live-validated enriched stock-item export would use; it deliberately does
 * NOT replace the existing, already-VERIFIED_SAFE `stockItems` Fetch list in
 * `master-data-templates.ts` -- doing so would send an unvalidated request shape to production
 * Tally. See `MasterDataTemplates.stockItemsEnrichedFields` and
 * `ApprovedOperationId.StockItemsEnrichedFields` (registered `EXPERIMENTAL_DISABLED`/`disabled`
 * pending live-validation evidence, mirroring `LEDGER_CONTACT_FETCH_FIELDS`/
 * `LedgersContactDetails`).
 */
export const STOCK_ITEM_RICH_FETCH_FIELDS = [
  'GUID',
  'ALTERID',
  'NAME',
  'ALIAS',
  'PARTNUMBER',
  'HSNCODE',
  'OPENINGBALANCE',
  'OPENINGRATE',
  'PARENT',
  'CATEGORY',
  'BASEUNITS',
  'CLOSINGBALANCE',
  'GSTAPPLICABLE',
] as const;

export interface StockItemIdentityInput {
  readonly guid?: string;
  readonly alterId?: string;
  readonly name: string;
}

/** Deterministic identity: GUID → AlterID → name slug fallback. */
export function resolveStockItemStableId(input: StockItemIdentityInput): string {
  const guid = input.guid?.trim().toLowerCase();
  if (guid) {
    return `guid:${guid}`;
  }
  const alterId = input.alterId?.trim();
  if (alterId) {
    return `alter:${alterId}`;
  }
  return `name:${slugify(input.name)}`;
}
