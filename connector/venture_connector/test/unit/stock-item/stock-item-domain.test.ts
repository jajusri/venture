import { describe, expect, it } from 'vitest';

import { mapNormalizedStockItemToDomain } from '../../../src/erp/stock-item/stock-item-mapper.js';
import { validateStockItemCollection } from '../../../src/erp/stock-item/stock-item-validation.js';
import { sampleNormalizedStockItem, sampleStockItemDetails } from '../../helpers/stock-item-fixtures.js';

describe('mapNormalizedStockItemToDomain', () => {
  it('maps complete stock items with base unit', () => {
    const mapped = mapNormalizedStockItemToDomain(
      sampleNormalizedStockItem({ name: 'Widget', baseUnit: 'Nos' }),
      '2026-01-01T00:00:00.000Z',
    );
    expect(mapped.dataQuality).toBe('complete');
    expect(mapped.baseUnit).toBe('Nos');
  });

  it('marks items without base unit as incomplete', () => {
    const mapped = mapNormalizedStockItemToDomain(
      sampleNormalizedStockItem({ baseUnit: undefined }),
      '2026-01-01T00:00:00.000Z',
    );
    expect(mapped.dataQuality).toBe('incomplete');
    expect(mapped.metadata?.incompleteReason).toContain('BASEUNITS');
  });
});

describe('validateStockItemCollection', () => {
  it('reports duplicate names and incomplete unit warnings', () => {
    const result = validateStockItemCollection([
      sampleStockItemDetails({ id: 'a', name: 'Widget' }),
      sampleStockItemDetails({ id: 'b', name: 'Widget' }),
      sampleStockItemDetails({ id: 'c', name: 'Gadget', dataQuality: 'incomplete', baseUnit: undefined }),
    ]);
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.code === 'DUPLICATE_NAME')).toBe(true);
    expect(result.issues.some((issue) => issue.code === 'INCOMPLETE_UNIT')).toBe(true);
  });
});
