export const ALLOWED_IPC_CHANNELS = [
  'desktop:get-dashboard',
  'desktop:get-logs',
  'desktop:get-settings',
  'desktop:save-settings',
  'desktop:restore-default-settings',
  'desktop:validate-settings',
  'desktop:get-lifecycle-status',
  'desktop:start-connector',
  'desktop:stop-connector',
  'desktop:restart-connector',
  'desktop:get-companies',
  'desktop:select-company',
  'desktop:clear-company',
  'desktop:get-diagnostics',
  'desktop:refresh-diagnostics',
  'desktop:copy-diagnostics-summary',
  'desktop:export-diagnostics-bundle',
  'desktop:open-logs-folder',
  'desktop:clear-nonessential-logs',
  'desktop:run-health-check',
  'desktop:reload-renderer',
  'desktop:get-ledgers',
  'desktop:sync-ledgers',
  'desktop:cancel-ledger-sync',
  'desktop:get-ledger-statistics',
  'desktop:clear-ledger-cache',
] as const;

export type AllowedIpcChannel = (typeof ALLOWED_IPC_CHANNELS)[number];

export const ALLOWED_PUSH_CHANNELS = ['desktop:status-updated'] as const;

export function isAllowedIpcChannel(channel: string): channel is AllowedIpcChannel {
  return (ALLOWED_IPC_CHANNELS as readonly string[]).includes(channel);
}

export function assertAllowedIpcChannel(channel: string): void {
  if (!isAllowedIpcChannel(channel)) {
    throw new Error(`Blocked IPC channel: ${channel}`);
  }
}

export function validateCompanyId(value: unknown): string {
  if (typeof value !== 'string') {
    throw new Error('Company id must be a string.');
  }
  const trimmed = value.trim();
  if (!/^[a-zA-Z0-9._-]{1,128}$/.test(trimmed)) {
    throw new Error('Company id contains invalid characters.');
  }
  return trimmed;
}

export function validateSettingsInput(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Settings payload must be an object.');
  }
  return value as Record<string, unknown>;
}

export function validateLedgerQuery(value: unknown): { query: string; page: number; pageSize: number } {
  if (value === undefined || value === null) {
    return { query: '', page: 1, pageSize: 25 };
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Ledger query payload must be an object.');
  }
  const input = value as Record<string, unknown>;
  const query = typeof input.query === 'string' ? input.query : '';
  const page = Math.max(1, Number.parseInt(String(input.page ?? '1'), 10) || 1);
  const pageSize = Math.min(100, Math.max(1, Number.parseInt(String(input.pageSize ?? '25'), 10) || 25));
  return { query, page, pageSize };
}

export function validateExportDirectory(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error('Export directory must be a non-empty string.');
  }
  return value.trim();
}
