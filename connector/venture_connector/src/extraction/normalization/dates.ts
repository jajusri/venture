/** Tally date formats: YYYYMMDD, DD-MM-YYYY, DD/MM/YYYY, YYYY-MM-DD */
export function normalizeDate(value: string | undefined | null): string | undefined {
  const text = value?.trim();
  if (!text) return undefined;

  if (/^\d{8}$/.test(text)) {
    const y = text.slice(0, 4);
    const m = text.slice(4, 6);
    const d = text.slice(6, 8);
    return `${y}-${m}-${d}`;
  }

  const slashMatch = text.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
  if (slashMatch) {
    const [, d, m, y] = slashMatch;
    return `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`;
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    return text;
  }

  return text;
}
