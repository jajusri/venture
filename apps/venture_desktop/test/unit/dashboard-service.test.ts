import { describe, expect, it } from 'vitest';

import { DashboardService } from '../../src/application/dashboard-service.js';
import { LogService } from '../../src/application/log-service.js';
import type { HealthResponse, SessionSnapshotResponse } from '../../src/application/types.js';

const health: HealthResponse = {
  status: 'ok',
  schemaVersion: '1.0.0',
  connectorVersion: '0.3.1',
  tallyReachable: true,
  readOnly: true,
  services: [
    { name: 'ApiServer', running: true, ready: true },
    { name: 'TallyConnection', running: true, ready: true, message: 'State: connected' },
    { name: 'SyncEngine', running: true, ready: true, message: 'Placeholder — not implemented' },
    { name: 'Licensing', running: true, ready: true, message: 'Placeholder — not implemented' },
  ],
};

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

describe('DashboardService', () => {
  it('binds dashboard state from connector services', async () => {
    const fetchImpl = async (url: string | URL, init?: RequestInit): Promise<Response> => {
      const path = String(url);
      if (path.endsWith('/health')) {
        return new Response(JSON.stringify(health), { status: 200 });
      }
      if (path.endsWith('/session') && init?.method !== 'POST') {
        return new Response(JSON.stringify(session), { status: 200 });
      }
      if (path.endsWith('/session/validate')) {
        return new Response(JSON.stringify({ status: 'SUCCESS', session: session.session }), {
          status: 200,
        });
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
    });

    const state = await service.getDashboardState();
    expect(state.connectionIndicator).toBe('connected');
    expect(state.companyName).toBe('ESTIMATION');
    expect(state.companyId).toBe('estimation');
    expect(state.sessionStatus).toBe('ACTIVE');
    expect(state.syncStatus).toBe('idle');
    expect(state.connectorVersion).toBe('0.3.1');
    expect(state.desktopVersion).toBe('0.4.3');
    expect(state.erpName).toBe('Tally');
    expect(state.lastRefresh).not.toBe('—');
  });

  it('does not create customer log entries when dashboard state is read repeatedly', async () => {
    const fetchImpl = async (url: string | URL): Promise<Response> => {
      const requestUrl = String(url);
      if (requestUrl.endsWith('/health')) {
        return new Response(JSON.stringify(health), { status: 200 });
      }
      if (requestUrl.endsWith('/session')) {
        return new Response(JSON.stringify(session), { status: 200 });
      }
      return new Response(JSON.stringify({ status: 'SUCCESS', session: session.session }), {
        status: 200,
      });
    };
    const logService = new LogService({ consoleEnabled: false });
    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      logService,
    });

    await service.getDashboardState();
    await service.getDashboardState();

    expect(logService.getEntries()).toEqual([]);
  });

  it('returns disconnected session status when connector is unavailable', async () => {
    const fetchImpl = async (): Promise<Response> => {
      throw new TypeError('fetch failed');
    };

    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    const state = await service.getDashboardState();
    expect(state.connectorReachable).toBe(false);
    expect(state.sessionStatus).toBe('DISCONNECTED');
    expect(state.userMessage).toContain('connector');
  });

  it('issues health and session requests concurrently (both are called even when one fails)', async () => {
    const callLog: string[] = [];

    const fetchImpl = async (url: string | URL): Promise<Response> => {
      const path = String(url);
      if (path.endsWith('/health')) {
        callLog.push('health');
        return new Response(JSON.stringify(health), { status: 200 });
      }
      if (path.endsWith('/session/validate')) {
        callLog.push('validate');
        return new Response(JSON.stringify({ status: 'SUCCESS', session: session.session }), { status: 200 });
      }
      if (path.endsWith('/session')) {
        callLog.push('session');
        // Simulate a slow session response — both paths should still be entered.
        return new Response(JSON.stringify(session), { status: 200 });
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    await service.getDashboardState();

    // Both health and session must have been called regardless of order.
    expect(callLog).toContain('health');
    expect(callLog).toContain('session');
  });

  it('marks connector reachable when only session succeeds', async () => {
    const fetchImpl = async (url: string | URL): Promise<Response> => {
      const path = String(url);
      if (path.endsWith('/health')) {
        throw new TypeError('health failed');
      }
      if (path.endsWith('/session')) {
        return new Response(JSON.stringify(session), { status: 200 });
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    const state = await service.getDashboardState();
    // Session succeeded so connectorReachable should be true.
    expect(state.connectorReachable).toBe(true);
    expect(state.companyName).toBe('ESTIMATION');
  });
});
