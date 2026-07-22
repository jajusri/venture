import type { ErpType, SessionConnectionStatus } from './session-constants.js';

/** Company bound to the active connector session. */
export interface SelectedCompany {
  readonly id: string;
  readonly name: string;
}

/**
 * Immutable connector session snapshot.
 *
 * Session state is replaced atomically on selection or validation updates;
 * callers receive new objects rather than mutating existing ones.
 */
export interface ConnectorSession {
  readonly sessionId: string;
  readonly selectedCompany: SelectedCompany | null;
  readonly connectionStatus: SessionConnectionStatus;
  readonly connectorVersion: string;
  readonly erpType: ErpType;
  readonly selectedAt: string | null;
  readonly lastValidatedAt: string | null;
  readonly createdAt: string;
}
