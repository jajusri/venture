import type { NormalizedLedger } from '../../src/extraction/core/types.js';
import type { AmountSide, NormalizedAmount } from '../../src/extraction/normalization/amounts.js';

export function sampleNormalizedAmount(
  amount: string,
  side: AmountSide = 'Dr',
  currencyCode = 'INR',
): NormalizedAmount {
  return { amount, currencyCode, side };
}

export function sampleNormalizedLedger(overrides: Partial<NormalizedLedger> = {}): NormalizedLedger {
  return {
    id: 'cash',
    name: 'Cash',
    normalizedName: 'cash',
    ...overrides,
  };
}
