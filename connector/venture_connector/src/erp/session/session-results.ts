import type { ConnectorSession } from './connector-session.js';
import type { CompanySelectionStatus, SessionValidationStatus } from './session-constants.js';

/** Structured result of a company selection attempt — no exceptions for expected failures. */
export interface CompanySelectionResult {
  readonly status: CompanySelectionStatus;
  readonly session: ConnectorSession;
  readonly reason?: string;
}

/** Structured result of validating the connector session before an ERP operation. */
export interface SessionValidationResult {
  readonly status: SessionValidationStatus;
  readonly session: ConnectorSession;
  readonly reason?: string;
  readonly companyId?: string;
  readonly companyName?: string;
}
