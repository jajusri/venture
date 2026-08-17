import {
  assertWriteTargetContained,
  rejectPathWithNullBytes,
} from './path-containment.js';

export const MAX_IPC_QUERY_LENGTH = 256;
export const MAX_IPC_PAYLOAD_BYTES = 64 * 1024;

export const ALLOWED_IPC_CHANNELS = [
  'desktop:get-dashboard',
  'desktop:get-logs',
  'desktop:get-settings',
  'desktop:save-settings',
  'desktop:restore-default-settings',
  'desktop:validate-settings',
  'desktop:get-lifecycle-status',
  'desktop:get-mobile-access-status',
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
  'desktop:get-stock-items',
  'desktop:sync-stock-items',
  'desktop:cancel-stock-item-sync',
  'desktop:get-stock-item-statistics',
  'desktop:clear-stock-item-cache',
  'desktop:get-secure-pairing-capability',
  'desktop:enable-secure-pairing',
  'desktop:disable-secure-pairing',
  'desktop:start-pairing',
  'desktop:get-pairing-status',
  'desktop:cancel-pairing',
  'desktop:list-trusted-pairing-devices',
  'desktop:revoke-trusted-pairing-device',
  'desktop:get-storage-status',
  'desktop:list-removable-volumes',
  'desktop:choose-storage-mode',
  'desktop:retry-storage-connection',
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

export function validateCredentialId(value: unknown): string {
  if (typeof value !== 'string') {
    throw new Error('Credential id must be a string.');
  }
  const trimmed = value.trim();
  if (!/^[a-zA-Z0-9._-]{1,128}$/.test(trimmed)) {
    throw new Error('Credential id contains invalid characters.');
  }
  return trimmed;
}

export function validateSettingsInput(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Settings payload must be an object.');
  }
  return value as Record<string, unknown>;
}

export { isPathContainedInRoot } from './path-containment.js';

export function validateExportDirectory(value: unknown, ownedExportRoot: string): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error('Export directory must be a non-empty string.');
  }
  rejectPathWithNullBytes(value);
  return assertWriteTargetContained({
    candidatePath: value.trim(),
    rootPath: ownedExportRoot,
  });
}

export function assertBoundedIpcPayload(
  args: readonly unknown[],
  maxBytes: number = MAX_IPC_PAYLOAD_BYTES,
): void {
  const serialized = JSON.stringify(args);
  if (serialized.length > maxBytes) {
    throw new Error('IPC payload exceeds the allowed size limit.');
  }
}

export function validateSyncOptions(value: unknown): { incremental: boolean } {
  if (value === undefined || value === null) {
    return { incremental: false };
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Sync options must be an object.');
  }
  const input = value as Record<string, unknown>;
  if ('incremental' in input && typeof input.incremental !== 'boolean') {
    throw new Error('Sync incremental flag must be a boolean.');
  }
  return { incremental: input.incremental === true };
}

export function validateLedgerQuery(value: unknown): { query: string; page: number; pageSize: number } {
  if (value === undefined || value === null) {
    return { query: '', page: 1, pageSize: 25 };
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Ledger query payload must be an object.');
  }
  const input = value as Record<string, unknown>;
  const query = typeof input.query === 'string' ? input.query.slice(0, MAX_IPC_QUERY_LENGTH) : '';
  const page = Math.max(1, Number.parseInt(String(input.page ?? '1'), 10) || 1);
  const pageSize = Math.min(100, Math.max(1, Number.parseInt(String(input.pageSize ?? '25'), 10) || 25));
  return { query, page, pageSize };
}

export function validateStockItemQuery(value: unknown): { query: string; page: number; pageSize: number } {
  return validateLedgerQuery(value);
}

export type ChooseStorageModeInput =
  | { readonly mode: 'standard'; readonly confirmSwitch: boolean }
  | { readonly mode: 'private-removable'; readonly driveLetter: string; readonly confirmSwitch: boolean };

const DRIVE_LETTER_PATTERN = /^[A-Za-z]:\\?$/;

export function validateChooseStorageModeInput(value: unknown): ChooseStorageModeInput {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Storage mode selection payload must be an object.');
  }
  const input = value as Record<string, unknown>;
  const confirmSwitch = input.confirmSwitch === true;
  if (input.mode === 'standard') {
    return { mode: 'standard', confirmSwitch };
  }
  if (input.mode === 'private-removable') {
    if (typeof input.driveLetter !== 'string' || !DRIVE_LETTER_PATTERN.test(input.driveLetter.trim())) {
      throw new Error('driveLetter must look like "E:\\".');
    }
    const normalized = input.driveLetter.trim().replace(/\\?$/, '\\');
    return { mode: 'private-removable', driveLetter: normalized, confirmSwitch };
  }
  throw new Error('mode must be "standard" or "private-removable".');
}
