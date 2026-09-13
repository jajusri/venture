/** Parse Tally numeric strings — handles commas, spaces, trailing Dr/Cr. */
export function normalizeNumber(value: string | undefined | null): string | undefined {
  if (value === undefined || value === null) return undefined;
  const cleaned = value.replace(/,/g, '').replace(/\s*(Dr|Cr)\s*$/i, '').trim();
  if (!cleaned) return undefined;
  const parsed = Number(cleaned);
  if (!Number.isFinite(parsed)) return undefined;
  return parsed.toString();
}

export function normalizeInteger(value: string | undefined | null): number | undefined {
  const num = normalizeNumber(value);
  if (num === undefined) return undefined;
  const parsed = Number.parseInt(num, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}
