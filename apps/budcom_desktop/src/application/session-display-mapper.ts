import type { SessionSnapshotResponse, SessionValidationResponse } from './types.js';

export function formatSelectionTime(selectedAt: string | null): string {
  return selectedAt ?? 'Not selected';
}

export function resolveSessionStatus(
  session: SessionSnapshotResponse | null,
  validation: SessionValidationResponse | null,
): string {
  if (validation?.status) {
    return validation.status;
  }
  if (!session?.session.selectedCompany) {
    return 'NO_COMPANY_SELECTED';
  }
  return session.session.connectionStatus.toUpperCase();
}

export function resolveCompanyName(session: SessionSnapshotResponse | null): string {
  return session?.session.selectedCompany?.name ?? '—';
}

export function resolveCompanyId(session: SessionSnapshotResponse | null): string {
  return session?.session.selectedCompany?.id ?? '—';
}
