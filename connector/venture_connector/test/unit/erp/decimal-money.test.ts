import { describe, expect, it } from 'vitest';

import {
  addDecimals,
  formatDecimal,
  parseDecimal,
  sideFromSigned,
  signedFromSide,
  subtractDecimals,
  sumDecimals,
  ZERO_DECIMAL,
} from '../../../src/erp/shared/decimal-money.js';

describe('decimal-money', () => {
  it('round-trips plain and fractional amounts through parse/format', () => {
    expect(formatDecimal(parseDecimal('100').coefficient, parseDecimal('100').scale)).toBe('100');
    expect(formatDecimal(parseDecimal('100.50').coefficient, parseDecimal('100.50').scale)).toBe('100.5');
    expect(formatDecimal(parseDecimal('0.10').coefficient, parseDecimal('0.10').scale)).toBe('0.1');
  });

  it('formats negative coefficients with a leading minus, and zero without one', () => {
    expect(formatDecimal(-12345n, 2)).toBe('-123.45');
    expect(formatDecimal(0n, 2)).toBe('0');
  });

  it('adds and subtracts values with different decimal scales exactly', () => {
    const a = parseDecimal('10.5');
    const b = parseDecimal('2.25');
    const sum = addDecimals(a, b);
    expect(formatDecimal(sum.coefficient, sum.scale)).toBe('12.75');
    const diff = subtractDecimals(a, b);
    expect(formatDecimal(diff.coefficient, diff.scale)).toBe('8.25');
  });

  it('sums a list of values, defaulting to zero for an empty list', () => {
    expect(sumDecimals([])).toEqual(ZERO_DECIMAL);
    const sum = sumDecimals([parseDecimal('1'), parseDecimal('2.5'), parseDecimal('-0.5')]);
    expect(formatDecimal(sum.coefficient, sum.scale)).toBe('3');
  });

  it('treats debit/Dr as positive and credit/Cr as negative when signing an amount', () => {
    expect(formatDecimal(signedFromSide('100', 'debit').coefficient, 0)).toBe('100');
    expect(formatDecimal(signedFromSide('100', 'Dr').coefficient, 0)).toBe('100');
    expect(formatDecimal(signedFromSide('100', 'credit').coefficient, 0)).toBe('-100');
    expect(formatDecimal(signedFromSide('100', 'Cr').coefficient, 0)).toBe('-100');
  });

  it('reports zero and positive signed values as debit, negative as credit', () => {
    expect(sideFromSigned({ coefficient: 0n, scale: 0 })).toEqual({ amount: '0', side: 'debit' });
    expect(sideFromSigned({ coefficient: 500n, scale: 2 })).toEqual({ amount: '5', side: 'debit' });
    expect(sideFromSigned({ coefficient: -500n, scale: 2 })).toEqual({ amount: '5', side: 'credit' });
  });

  it('round-trips signedFromSide/sideFromSigned for a debit and a credit amount', () => {
    expect(sideFromSigned(signedFromSide('250.75', 'debit'))).toEqual({ amount: '250.75', side: 'debit' });
    expect(sideFromSigned(signedFromSide('250.75', 'credit'))).toEqual({ amount: '250.75', side: 'credit' });
  });
});
