import { ERP_TYPE_TALLY } from '../../src/erp/session/session-constants.js';
import { createEmptySession } from '../../src/services/session/session-validator.js';
import type { ConnectorSessionService } from '../../src/services/interfaces/connector-session.js';

export function createPermissiveSessionMock(
  overrides: Partial<ConnectorSessionService> = {},
): ConnectorSessionService {
  const session = createEmptySession({
    connectorVersion: '0.3.1',
    erpType: ERP_TYPE_TALLY,
    connectionStatus: 'connected',
    nowMs: Date.now(),
  });

  const selectedSession = {
    ...session,
    selectedCompany: { id: 'estimation', name: 'ESTIMATION' },
    selectedAt: new Date().toISOString(),
    lastValidatedAt: new Date().toISOString(),
  };

  return {
    start: async () => {},
    stop: async () => {},
    isRunning: () => true,
    getStatus: () => ({
      name: 'ConnectorSession',
      running: true,
      ready: true,
      message: 'test mock',
    }),
    getSession: () => ({ session: selectedSession, contractVersion: '1' }),
    selectCompany: async () => ({ status: 'SUCCESS', session: selectedSession }),
    clearSelection: () => ({ session, contractVersion: '1' }),
    validateForOperation: async () => ({
      status: 'SUCCESS',
      session: selectedSession,
      companyId: 'estimation',
      companyName: 'ESTIMATION',
    }),
    refreshValidation: async () => ({
      status: 'SUCCESS',
      session: selectedSession,
      companyId: 'estimation',
      companyName: 'ESTIMATION',
    }),
    ...overrides,
  };
}
