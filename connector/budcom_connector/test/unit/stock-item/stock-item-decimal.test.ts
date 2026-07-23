import { describe, expect, it } from 'vitest';

import { normalizeAmount } from '../../../src/extraction/normalization/amounts.js';
import { computeStockItemFingerprint } from '../../../src/services/stock-item/stock-item-fingerprint.js';
import { sampleStockItemDetails } from '../../helpers/stock-item-fixtures.js';

describe('stock item decimal safety', () => {
  it('stores opening amounts as normalized strings not JS floats', () => {
    const amount = normalizeAmount('0.1000 Dr');
    expect(amount?.amount).toBe('0.1');
    expect(typeof amount?.amount).toBe('string');
  });

  it('normalizes comma-formatted and unit-attached quantity text', () => {
    const amount = normalizeAmount('1,234.500 Dr');
    expect(amount?.amount).toBe('1234.5');
    expect(amount?.side).toBe('Dr');
  });

  it('produces identical fingerprints for equivalent decimal text', () => {
    const a = sampleStockItemDetails({
      openingBalance: { amount: '0.1', currencyCode: 'INR', side: 'Dr' },
    });
    const b = sampleStockItemDetails({
      openingBalance: { amount: '0.10', currencyCode: 'INR', side: 'Dr' },
    });
    expect(computeStockItemFingerprint(a)).toBe(computeStockItemFingerprint(b));
  });

  it('produces different fingerprints for materially different decimals', () => {
    const a = sampleStockItemDetails({
      openingBalance: { amount: '0.1', currencyCode: 'INR', side: 'Dr' },
    });
    const b = sampleStockItemDetails({
      openingBalance: { amount: '0.2', currencyCode: 'INR', side: 'Dr' },
    });
    expect(computeStockItemFingerprint(a)).not.toBe(computeStockItemFingerprint(b));
  });
});
