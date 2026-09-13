/** Normalize text for display — preserves Unicode, trims whitespace. */
export function normalizeText(value: string | undefined | null): string | undefined {
  if (value === undefined || value === null) return undefined;
  const trimmed = value.normalize('NFC').trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

/** Slugify for stable entity IDs (ASCII-safe URL segment). */
export function slugify(value: string): string {
  const normalized = value.normalize('NFC').trim().toLowerCase();
  const asciiSlug = normalized.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  if (asciiSlug) return asciiSlug;
  return Buffer.from(normalized, 'utf8').toString('hex').slice(0, 24);
}

/** Lowercase normalized name for search/indexing. */
export function normalizeName(value: string): string {
  return value.normalize('NFC').trim().toLowerCase();
}

export function isCountMetadata(value: string): boolean {
  return /^\d+$/.test(value.trim());
}
