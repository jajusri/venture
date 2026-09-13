import { describe, expect, it } from 'vitest';

import { normalizeAmount } from '../../../src/extraction/normalization/amounts.js';
import { normalizeDate } from '../../../src/extraction/normalization/dates.js';
import { normalizeNumber } from '../../../src/extraction/normalization/numbers.js';
import { normalizeName, normalizeText, slugify } from '../../../src/extraction/normalization/strings.js';

describe('normalization', () => {
  it('normalizes Tally dates', () => {
    expect(normalizeDate('20240401')).toBe('2024-04-01');
    expect(normalizeDate('01-04-2024')).toBe('2024-04-01');
  });

  it('normalizes amounts with Dr/Cr', () => {
    expect(normalizeAmount('1,000.00 Dr')).toEqual({
      amount: '1000',
      currencyCode: 'INR',
      side: 'Dr',
    });
    expect(normalizeAmount('500.00 Cr')).toEqual({
      amount: '500',
      currencyCode: 'INR',
      side: 'Cr',
    });
  });

  it('normalizes numbers with commas', () => {
    expect(normalizeNumber('1,234.56')).toBe('1234.56');
  });

  it('preserves unicode text', () => {
    expect(normalizeText('  ग्राहक  ')).toBe('ग्राहक');
    expect(normalizeName('Cash Account')).toBe('cash account');
  });

  it('slugifies ascii names', () => {
    expect(slugify('Acme Traders Pvt Ltd')).toBe('acme-traders-pvt-ltd');
  });
});
