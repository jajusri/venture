import { describe, expect, it } from 'vitest';

import {
  resolveCompanyId,
  resolveCompanyName,
  resolveSessionStatus,
} from '../../src/application/session-display-mapper.js';
import type { SessionSnapshotResponse } from '../../src/application/types.js';

const session: SessionSnapshotResponse = {
  contractVersion: '1',
  session: {
    sessionId: 'sess-1',
    selectedCompany: { id: 'estimation', name: 'ESTIMATION' },
    connectionStatus: 'connected',
    connectorVersion: '0.3.1',
    erpType: 'tally',
    selectedAt: '2026-07-22T17:00:00.000Z',
    lastValidatedAt: '2026-07-22T17:05:00.000Z',
    createdAt: '2026-07-22T16:00:00.000Z',
  },
};

describe('session binding display', () => {
  it('reads company from authoritative session snapshot', () => {
    expect(resolveCompanyName(session)).toBe('ESTIMATION');
    expect(resolveCompanyId(session)).toBe('estimation');
  });

  it('uses validation status when available', () => {
    expect(
      resolveSessionStatus(session, {
        status: 'SUCCESS',
        session: session.session,
      }),
    ).toBe('SUCCESS');
  });

  it('reports no company selected when session is empty', () => {
    expect(resolveSessionStatus(null, null)).toBe('NO_COMPANY_SELECTED');
  });
});
