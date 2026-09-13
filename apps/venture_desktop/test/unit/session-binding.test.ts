import { describe, expect, it } from 'vitest';

import {
  mapSessionDisplayStatus,
  resolveCompanyId,
  resolveCompanyName,
  resolveErpName,
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
    expect(resolveErpName(session)).toBe('Tally');
  });

  it('maps successful validation to ACTIVE', () => {
    expect(
      mapSessionDisplayStatus(true, session, {
        status: 'SUCCESS',
        session: session.session,
      }),
    ).toBe('ACTIVE');
  });

  it('reports no company selected when session is empty', () => {
    expect(mapSessionDisplayStatus(true, null, null)).toBe('NO_COMPANY_SELECTED');
  });

  it('reports disconnected when connector is unreachable', () => {
    expect(mapSessionDisplayStatus(false, session, null)).toBe('DISCONNECTED');
  });

  it('maps invalid validation to INVALID', () => {
    expect(
      mapSessionDisplayStatus(true, session, {
        status: 'SESSION_EXPIRED',
        session: session.session,
      }),
    ).toBe('INVALID');
  });
});
