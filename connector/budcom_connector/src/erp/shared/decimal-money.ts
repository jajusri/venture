/**
 * Exact decimal arithmetic for monetary amounts, expressed as a bigint coefficient + scale
 * (never floating point). Shared by any code that must sum/compare Tally amount strings
 * without rounding drift — e.g. voucher ledger-entry reconciliation and Ledger statement
 * balance computation.
 */

export interface DecimalValue {
  readonly coefficient: bigint;
  readonly scale: number;
}

export type AmountSide = 'debit' | 'credit';

export const ZERO_DECIMAL: DecimalValue = { coefficient: 0n, scale: 0 };

export function parseDecimal(value: string): DecimalValue {
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [whole, fraction = ''] = unsigned.split('.');
  const coefficient = BigInt(`${whole}${fraction}` || '0') * (negative ? -1n : 1n);
  return { coefficient, scale: fraction.length };
}

export function rescale(value: DecimalValue, scale: number): bigint {
  return value.coefficient * (10n ** BigInt(scale - value.scale));
}

/** Formats a (possibly negative) coefficient/scale pair back into a plain decimal string. */
export function formatDecimal(coefficient: bigint, scale: number): string {
  const negative = coefficient < 0n;
  const magnitude = negative ? -coefficient : coefficient;
  if (scale === 0) return negative && magnitude !== 0n ? `-${magnitude}` : magnitude.toString();
  const digits = magnitude.toString().padStart(scale + 1, '0');
  const formatted = `${digits.slice(0, -scale)}.${digits.slice(-scale)}`;
  const trimmed = formatted.replace(/\.?0+$/, '') || '0';
  return negative && trimmed !== '0' ? `-${trimmed}` : trimmed;
}

export function addDecimals(a: DecimalValue, b: DecimalValue): DecimalValue {
  const scale = Math.max(a.scale, b.scale);
  return { coefficient: rescale(a, scale) + rescale(b, scale), scale };
}

export function subtractDecimals(a: DecimalValue, b: DecimalValue): DecimalValue {
  const scale = Math.max(a.scale, b.scale);
  return { coefficient: rescale(a, scale) - rescale(b, scale), scale };
}

export function sumDecimals(values: readonly DecimalValue[]): DecimalValue {
  return values.reduce((sum, value) => addDecimals(sum, value), ZERO_DECIMAL);
}

export function negateDecimal(value: DecimalValue): DecimalValue {
  return { coefficient: -value.coefficient, scale: value.scale };
}

/**
 * Converts a magnitude + accounting side into a signed decimal using this codebase's
 * established convention (see voucher-ledger-reconciler.ts): debit/Dr is positive,
 * credit/Cr is negative. Accepts both casings since master-ledger balances use 'Dr'/'Cr'
 * text suffixes while voucher ledger entries use lowercase 'debit'/'credit'.
 */
export function signedFromSide(amount: string, side: AmountSide | 'Dr' | 'Cr'): DecimalValue {
  const magnitude = parseDecimal(amount);
  const isCredit = side === 'credit' || side === 'Cr';
  return isCredit ? negateDecimal(magnitude) : magnitude;
}

/** Inverse of {@link signedFromSide} — zero and positive values are reported as debit. */
export function sideFromSigned(value: DecimalValue): { readonly amount: string; readonly side: AmountSide } {
  if (value.coefficient < 0n) {
    return { amount: formatDecimal(-value.coefficient, value.scale), side: 'credit' };
  }
  return { amount: formatDecimal(value.coefficient, value.scale), side: 'debit' };
}
