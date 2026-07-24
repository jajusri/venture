import { randomUUID } from 'node:crypto';

import type { ConnectorSession, SelectedCompany } from '../../erp/session/connector-session.js';
import type {
  CompanySelectionStatus,
  ErpType,
  SessionConnectionStatus,
  SessionValidationStatus,
} from '../../erp/session/session-constants.js';
import type {
  CompanySelectionResult,
  SessionValidationResult,
} from '../../erp/session/session-results.js';
import { slugify } from '../../extraction/normalization/strings.js';

export interface DiscoveredCompanyRef {
  readonly id: string;
  readonly name: string;
}

export interface SessionValidatorInput {
  readonly session: ConnectorSession;
  readonly requestedCompanyId?: string;
  readonly discoveredCompanies: readonly DiscoveredCompanyRef[];
  readonly tallyReachable: boolean;
  readonly discoveryAvailable: boolean;
  readonly connectorConnected: boolean;
  readonly sessionTtlMs: number;
  readonly nowMs: number;
}

export interface CompanySelectionInput {
  readonly session: ConnectorSession;
  readonly companyId: string;
  readonly discoveredCompanies: readonly DiscoveredCompanyRef[];
  readonly tallyReachable: boolean;
  readonly discoveryAvailable: boolean;
  readonly connectorConnected: boolean;
  readonly nowMs: number;
}

export function createEmptySession(params: {
  readonly connectorVersion: string;
  readonly erpType: ErpType;
  readonly connectionStatus: SessionConnectionStatus;
  readonly nowMs: number;
}): ConnectorSession {
  const createdAt = new Date(params.nowMs).toISOString();
  return {
    sessionId: randomUUID(),
    selectedCompany: null,
    connectionStatus: params.connectionStatus,
    connectorVersion: params.connectorVersion,
    erpType: params.erpType,
    selectedAt: null,
    lastValidatedAt: null,
    createdAt,
  };
}

export function withSelectedCompany(
  session: ConnectorSession,
  company: SelectedCompany,
  nowMs: number,
): ConnectorSession {
  const selectedAt = new Date(nowMs).toISOString();
  return {
    ...session,
    selectedCompany: company,
    selectedAt,
    lastValidatedAt: selectedAt,
  };
}

export function withValidationTimestamp(
  session: ConnectorSession,
  nowMs: number,
): ConnectorSession {
  return {
    ...session,
    lastValidatedAt: new Date(nowMs).toISOString(),
  };
}

export function withConnectionStatus(
  session: ConnectorSession,
  connectionStatus: SessionConnectionStatus,
): ConnectorSession {
  return {
    ...session,
    connectionStatus,
  };
}

export function withClearedSelection(session: ConnectorSession): ConnectorSession {
  return {
    ...session,
    selectedCompany: null,
    selectedAt: null,
    lastValidatedAt: null,
  };
}

export function selectCompany(input: CompanySelectionInput): CompanySelectionResult {
  const trimmedId = input.companyId.trim();

  if (!trimmedId) {
    return {
      status: 'EMPTY_SELECTION',
      session: input.session,
      reason: 'Company id is required for selection',
    };
  }

  if (!input.connectorConnected) {
    return {
      status: 'INVALID_COMPANY',
      session: input.session,
      reason: 'Connector is not connected',
    };
  }

  if (!input.discoveryAvailable) {
    return {
      status: 'INVALID_COMPANY',
      session: input.session,
      reason: 'Company discovery is unavailable; reachability alone cannot establish company context',
    };
  }

  const company = findDiscoveredCompany(input.discoveredCompanies, trimmedId);
  if (!company) {
    if (!input.tallyReachable) {
      return {
        status: 'INVALID_COMPANY',
        session: input.session,
        reason: 'Company is not accessible because Tally is unreachable',
      };
    }
    return {
      status: 'COMPANY_NOT_FOUND',
      session: input.session,
      reason: `Company not found: ${trimmedId}`,
    };
  }

  if (
    input.session.selectedCompany &&
    input.session.selectedCompany.id === company.id
  ) {
    return {
      status: 'DUPLICATE_SELECTION',
      session: input.session,
      reason: `Company already selected: ${company.id}`,
    };
  }

  if (!input.tallyReachable) {
    return {
      status: 'INVALID_COMPANY',
      session: input.session,
      reason: 'Company is not accessible because Tally is unreachable',
    };
  }

  return {
    status: 'SUCCESS',
    session: withSelectedCompany(
      input.session,
      { id: company.id, name: company.name },
      input.nowMs,
    ),
  };
}

export function validateSession(input: SessionValidatorInput): SessionValidationResult {
  const { session } = input;

  if (!input.connectorConnected) {
    return invalidSession(session, 'Connector is not connected');
  }

  if (!input.discoveryAvailable) {
    if (session.selectedCompany || input.requestedCompanyId?.trim()) {
      return {
        status: 'COMPANY_DISCOVERY_UNAVAILABLE',
        session,
        reason:
          'Company discovery is unavailable; reachability alone cannot establish company context',
      };
    }
    return {
      status: 'COMPANY_DISCOVERY_UNAVAILABLE',
      session,
      reason: 'Company discovery is unavailable',
    };
  }

  if (isSessionExpired(session, input.sessionTtlMs, input.nowMs)) {
    return {
      status: 'SESSION_EXPIRED',
      session,
      reason: 'Connector session has expired',
    };
  }

  if (!session.selectedCompany) {
    return {
      status: 'NO_COMPANY_SELECTED',
      session,
      reason: 'No company is selected for this connector session',
    };
  }

  const requestedId = input.requestedCompanyId?.trim();
  if (requestedId && requestedId !== session.selectedCompany.id) {
    return {
      status: 'COMPANY_NOT_ACCESSIBLE',
      session,
      reason: `Requested company '${requestedId}' does not match selected company '${session.selectedCompany.id}'`,
      companyId: session.selectedCompany.id,
      companyName: session.selectedCompany.name,
    };
  }

  if (!input.tallyReachable) {
    return {
      status: 'COMPANY_NOT_ACCESSIBLE',
      session,
      reason: 'Selected company is not accessible because Tally is unreachable',
      companyId: session.selectedCompany.id,
      companyName: session.selectedCompany.name,
    };
  }

  const discovered = findDiscoveredCompany(
    input.discoveredCompanies,
    session.selectedCompany.id,
  );
  if (!discovered) {
    return {
      status: 'COMPANY_NOT_FOUND',
      session,
      reason: `Selected company no longer exists: ${session.selectedCompany.id}`,
      companyId: session.selectedCompany.id,
      companyName: session.selectedCompany.name,
    };
  }

  if (discovered.name !== session.selectedCompany.name) {
    return {
      status: 'SESSION_INVALID',
      session,
      reason: 'Selected company metadata no longer matches discovery results',
      companyId: session.selectedCompany.id,
      companyName: session.selectedCompany.name,
    };
  }

  return {
    status: 'SUCCESS',
    session: withValidationTimestamp(session, input.nowMs),
    companyId: discovered.id,
    companyName: discovered.name,
  };
}

function invalidSession(
  session: ConnectorSession,
  reason: string,
): SessionValidationResult {
  return {
    status: 'SESSION_INVALID',
    session,
    reason,
  };
}

function isSessionExpired(
  session: ConnectorSession,
  sessionTtlMs: number,
  nowMs: number,
): boolean {
  if (!session.selectedAt || sessionTtlMs <= 0) {
    return false;
  }
  const selectedAtMs = Date.parse(session.selectedAt);
  if (Number.isNaN(selectedAtMs)) {
    return true;
  }
  return nowMs - selectedAtMs > sessionTtlMs;
}

function findDiscoveredCompany(
  companies: readonly DiscoveredCompanyRef[],
  companyId: string,
): DiscoveredCompanyRef | undefined {
  const normalizedId = companyId.trim();
  return companies.find(
    (company) =>
      company.id === normalizedId || slugify(company.name) === normalizedId,
  );
}

export function mapSelectionStatusToHttpStatus(status: CompanySelectionStatus): number {
  switch (status) {
    case 'SUCCESS':
      return 200;
    case 'DUPLICATE_SELECTION':
      return 409;
    case 'EMPTY_SELECTION':
    case 'INVALID_COMPANY':
      return 400;
    case 'COMPANY_NOT_FOUND':
      return 404;
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function mapValidationStatusToHttpStatus(status: SessionValidationStatus): number {
  switch (status) {
    case 'SUCCESS':
      return 200;
    case 'NO_COMPANY_SELECTED':
    case 'SESSION_INVALID':
      return 400;
    case 'COMPANY_NOT_FOUND':
      return 404;
    case 'COMPANY_NOT_ACCESSIBLE':
      return 403;
    case 'COMPANY_DISCOVERY_UNAVAILABLE':
      return 503;
    case 'SESSION_EXPIRED':
      return 410;
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}
