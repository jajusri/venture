import { slugify } from '../normalization/strings.js';
import type { NormalizedStockItem, NormalizedUnit } from '../core/types.js';

/** Derive unit masters from stock item base-unit references — avoids Tally List of Units hang. */
export function deriveUnitsFromStockItems(items: readonly NormalizedStockItem[]): NormalizedUnit[] {
  const byName = new Map<string, NormalizedUnit>();
  for (const item of items) {
    const baseUnit = item.baseUnit?.trim();
    if (!baseUnit) continue;
    const id = slugify(baseUnit);
    if (byName.has(id)) continue;
    byName.set(id, {
      id,
      name: baseUnit,
      symbol: baseUnit,
    });
  }
  return [...byName.values()].sort((a, b) => a.name.localeCompare(b.name));
}
