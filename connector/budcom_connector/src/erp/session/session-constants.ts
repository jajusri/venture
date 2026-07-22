/** ERP-neutral connector session contract version. */
export const SESSION_CONTRACT_VERSION = '1' as const;

/** Supported ERP adapter identifier for the active connector session. */
export const ERP_TYPE_TALLY = 'tally' as const;

export type ErpType = typeof ERP_TYPE_TALLY;

/** Connection status exposed on the connector session surface. */
export type SessionConnectionStatus = 'connected' | 'disconnected' | 'degraded';

/** Outcome of validating a connector session before an ERP operation. */
export type SessionValidationStatus =
  | 'SUCCESS'
  | 'NO_COMPANY_SELECTED'
  | 'COMPANY_NOT_FOUND'
  | 'COMPANY_NOT_ACCESSIBLE'
  | 'SESSION_INVALID'
  | 'SESSION_EXPIRED';

/** Outcome of selecting a company for the connector session. */
export type CompanySelectionStatus =
  | 'SUCCESS'
  | 'COMPANY_NOT_FOUND'
  | 'INVALID_COMPANY'
  | 'DUPLICATE_SELECTION'
  | 'EMPTY_SELECTION';
