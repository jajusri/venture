import { normalizeNumber } from './numbers.js';
import { normalizeText } from './strings.js';

export type AmountSide = 'Dr' | 'Cr';

export interface NormalizedAmount {
  readonly amount: string;
  readonly currencyCode: string;
  readonly side: AmountSide;
}

const AMOUNT_PATTERN = /^(-?\d[\d,]*(?:\.\d+)?)\s*(Dr|Cr)?$/i;

export function normalizeAmount(
  value: string | undefined | null,
  currencyCode = 'INR',
): NormalizedAmount | undefined {
  const text = normalizeText(value);
  if (!text) return undefined;

  const match = text.match(AMOUNT_PATTERN);
  if (!match) {
    const amount = normalizeNumber(text);
    if (!amount) return undefined;
    return { amount, currencyCode, side: inferSideFromSign(amount) };
  }

  const rawAmount = match[1]?.replace(/,/g, '') ?? '0';
  const explicitSide = match[2]?.toUpperCase();
  const side: AmountSide =
    explicitSide === 'CR' ? 'Cr' : explicitSide === 'DR' ? 'Dr' : inferSideFromSign(rawAmount);

  return {
    amount: normalizeAbsoluteAmount(rawAmount),
    currencyCode,
    side,
  };
}

function inferSideFromSign(value: string): AmountSide {
  return value.startsWith('-') ? 'Cr' : 'Dr';
}

function normalizeAbsoluteAmount(value: string): string {
  const abs = value.replace(/^-/, '');
  const parsed = Number(abs);
  return Number.isFinite(parsed) ? Math.abs(parsed).toString() : abs;
}
