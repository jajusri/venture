import { describe, expect, it } from 'vitest';

import { DashboardService } from '../../src/application/dashboard-service.js';

describe('connection recovery', () => {
  it('recovers dashboard state after connector becomes reachable again', async () => {
    let reachable = false;
    const fetchImpl = async (url: string | URL, init?: RequestInit): Promise<Response> => {
      if (!reachable) {
        throw new TypeError('fetch failed');
      }
      const path = String(url);
      if (path.endsWith('/health')) {
        return new Response(
          JSON.stringify({
            status: 'ok',
            schemaVersion: '1.0.0',
            connectorVersion: '0.3.1',
            tallyReachable: true,
            readOnly: true,
            services: [
              { name: 'ApiServer', running: true, ready: true },
              { name: 'TallyConnection', running: true, ready: true, message: 'State: connected' },
            ],
          }),
          { status: 200 },
        );
      }
      if (path.endsWith('/session') && init?.method !== 'POST') {
        return new Response(
          JSON.stringify({
            contractVersion: '1',
            session: {
              sessionId: 'sess-1',
              selectedCompany: null,
              connectionStatus: 'connected',
              connectorVersion: '0.3.1',
              erpType: 'tally',
              selectedAt: null,
              lastValidatedAt: null,
              createdAt: '2026-07-23T00:00:00.000Z',
            },
          }),
          { status: 200 },
        );
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new DashboardService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    const disconnected = await service.getDashboardState();
    expect(disconnected.connectorReachable).toBe(false);
    expect(disconnected.connectionIndicator).toBe('disconnected');

    reachable = true;
    const recovered = await service.getDashboardState();
    expect(recovered.connectorReachable).toBe(true);
    expect(recovered.connectionIndicator).toBe('connected');
    expect(recovered.sessionStatus).toBe('NO_COMPANY_SELECTED');
  });
});
