import { describe, expect, it } from 'vitest';

import { resolveStockItemStableId } from '../../../src/extraction/core/stock-item-identity.js';

describe('resolveStockItemStableId', () => {
  it('prefers GUID over AlterID and name', () => {
    expect(
      resolveStockItemStableId({ guid: 'ABC-123', alterId: '9', name: 'Widget' }),
    ).toBe('guid:abc-123');
  });

  it('uses AlterID when GUID is absent', () => {
    expect(resolveStockItemStableId({ alterId: '42', name: 'Widget' })).toBe('alter:42');
  });

  it('falls back to name slug when GUID and AlterID are absent', () => {
    expect(resolveStockItemStableId({ name: 'Widget A' })).toBe('name:widget-a');
  });

  it('produces stable identity regardless of source order', () => {
    const a = resolveStockItemStableId({ guid: 'X', name: 'First' });
    const b = resolveStockItemStableId({ name: 'Second', guid: 'X' });
    expect(a).toBe(b);
  });

  it('keeps distinct identities for same display name with different GUIDs', () => {
    const a = resolveStockItemStableId({ guid: 'g1', name: 'Widget' });
    const b = resolveStockItemStableId({ guid: 'g2', name: 'Widget' });
    expect(a).not.toBe(b);
  });
});
