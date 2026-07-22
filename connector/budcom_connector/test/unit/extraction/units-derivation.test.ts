import { describe, expect, it } from 'vitest';

import { deriveUnitsFromStockItems } from '../../../src/extraction/units/units-derivation.js';

describe('deriveUnitsFromStockItems', () => {
  it('derives unique units from stock item baseUnit values', () => {
    const units = deriveUnitsFromStockItems([
      { id: 'a', name: 'Item A', normalizedName: 'item a', baseUnit: 'Nos' },
      { id: 'b', name: 'Item B', normalizedName: 'item b', baseUnit: 'Nos' },
      { id: 'c', name: 'Item C', normalizedName: 'item c', baseUnit: 'Kg' },
    ]);
    expect(units).toHaveLength(2);
    expect(units.map((u) => u.name).sort()).toEqual(['Kg', 'Nos']);
  });

  it('returns empty list when no base units present', () => {
    expect(deriveUnitsFromStockItems([])).toEqual([]);
  });
});
