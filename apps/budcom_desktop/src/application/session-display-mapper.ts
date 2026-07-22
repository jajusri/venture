import type {
  SessionDisplayStatus,
  SessionSnapshotResponse,
  SessionValidationResponse,
} from './types.js';

export function formatSelectionTime(selectedAt: string | null): string {
  return selectedAt ?? 'Not selected';
}

export function formatTimestamp(value: string | null): string {
  return value ?? '—';
}

export function resolveCompanyName(session: SessionSnapshotResponse | null): string {
  return session?.session.selectedCompany?.name ?? '—';
}

export function resolveCompanyId(session: SessionSnapshotResponse | null): string {
  return session?.session.selectedCompany?.id ?? '—';
}

export function resolveErpName(session: SessionSnapshotResponse | null): string {
  const erpType = session?.session.erpType ?? 'tally';
  return erpType.charAt(0).toUpperCase() + erpType.slice(1);
}

export function mapSessionDisplayStatus(
  connectorReachable: boolean,
  session: SessionSnapshotResponse | null,
  validation: SessionValidationResponse | null,
): SessionDisplayStatus {
  if (!connectorReachable) {
    return 'DISCONNECTED';
  }

  if (!session?.session.selectedCompany) {
    return 'NO_COMPANY_SELECTED';
  }

  if (session.session.connectionStatus === 'disconnected') {
    return 'DISCONNECTED';
  }

  const validationStatus = validation?.status;
  if (validationStatus === 'SUCCESS') {
    return 'ACTIVE';
  }

  if (
    validationStatus === 'SESSION_INVALID' ||
    validationStatus === 'SESSION_EXPIRED' ||
    validationStatus === 'COMPANY_NOT_FOUND' ||
    validationStatus === 'COMPANY_NOT_ACCESSIBLE'
  ) {
    return 'INVALID';
  }

  if (validationStatus === 'NO_COMPANY_SELECTED') {
    return 'NO_COMPANY_SELECTED';
  }

  if (session.session.connectionStatus === 'degraded') {
    return 'INVALID';
  }

  if (session.session.connectionStatus === 'connected') {
    return 'ACTIVE';
  }

  return 'ERROR';
}

/** @deprecated Use mapSessionDisplayStatus — kept for transitional tests if needed */
export function resolveSessionStatus(
  session: SessionSnapshotResponse | null,
  validation: SessionValidationResponse | null,
): string {
  return mapSessionDisplayStatus(true, session, validation);
}
