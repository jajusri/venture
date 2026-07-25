export const DIAGNOSTIC_EXPORT_DIR_PREFIX = 'budcom-diagnostics-';

export const DIAGNOSTIC_EXPORT_BUNDLE_FILENAME = 'diagnostics-bundle.json';

const APP_OWNED_EXPORT_DIR_PATTERN =
  /^budcom-diagnostics-\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$/;

const ENCODED_TIMESTAMP_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2})-(\d{2})-(\d{2})-(\d{3})Z$/;

export function buildDiagnosticExportDirName(generatedAtIso: string): string {
  return `${DIAGNOSTIC_EXPORT_DIR_PREFIX}${generatedAtIso.replace(/[:.]/g, '-')}`;
}

export function isAppOwnedDiagnosticExportDirName(name: string): boolean {
  return APP_OWNED_EXPORT_DIR_PATTERN.test(name);
}

export function parseDiagnosticExportDirTimestampMs(dirName: string): number | null {
  if (!isAppOwnedDiagnosticExportDirName(dirName)) {
    return null;
  }
  const encoded = dirName.slice(DIAGNOSTIC_EXPORT_DIR_PREFIX.length);
  const match = ENCODED_TIMESTAMP_PATTERN.exec(encoded);
  if (!match) {
    return null;
  }
  const [, year, month, day, hour, minute, second, millis] = match;
  const iso = `${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z`;
  const parsed = Date.parse(iso);
  return Number.isFinite(parsed) ? parsed : null;
}

export function retentionPeriodMs(retentionDays: number): number {
  return retentionDays * 24 * 60 * 60 * 1000;
}
