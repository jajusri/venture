import { slugify } from '../normalization/strings.js';

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
