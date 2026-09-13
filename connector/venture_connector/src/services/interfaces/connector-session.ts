import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { ConnectorSession } from '../../erp/session/connector-session.js';
import { SESSION_CONTRACT_VERSION } from '../../erp/session/session-constants.js';
import type {
  CompanySelectionResult,
  SessionValidationResult,
} from '../../erp/session/session-results.js';

export interface ConnectorSessionSnapshot {
  readonly session: ConnectorSession;
  readonly contractVersion: typeof SESSION_CONTRACT_VERSION;
}

export interface ConnectorSessionService extends ServiceLifecycle {
  getSession(): ConnectorSessionSnapshot;
  selectCompany(companyId: string): Promise<CompanySelectionResult>;
  clearSelection(): ConnectorSessionSnapshot;
  validateForOperation(requestedCompanyId?: string): Promise<SessionValidationResult>;
  refreshValidation(): Promise<SessionValidationResult>;
  getStatus(): ServiceStatus;
}
