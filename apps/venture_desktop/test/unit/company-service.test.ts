import { describe, expect, it } from 'vitest';

import { CompanyService } from '../../src/application/company-service.js';
import { LogService } from '../../src/application/log-service.js';
import type { CompanyListResult, CompanySelectionResult, SessionSnapshotResponse } from '../../src/application/types.js';

const companies: CompanyListResult = {
  items: [{ id: 'estimation', name: 'ESTIMATION' }],
  schemaVersion: '1.0.0',
  dataFreshnessAt: '2026-07-23T00:00:00.000Z',
  contractVersion: '1',
  status: 'SUCCESS',
  tallyReachable: true,
};

const session: SessionSnapshotResponse = {
  contractVersion: '1',
  session: {
    sessionId: 'sess-1',
    selectedCompany: { id: 'estimation', name: 'ESTIMATION' },
    connectionStatus: 'connected',
    connectorVersion: '0.3.1',
    erpType: 'tally',
    selectedAt: '2026-07-23T00:01:00.000Z',
    lastValidatedAt: '2026-07-23T00:01:00.000Z',
    createdAt: '2026-07-23T00:00:00.000Z',
  },
};

describe('CompanyService', () => {
  it('discovers companies from connector API', async () => {
    const fetchImpl = async (url: string | URL): Promise<Response> => {
      if (String(url).endsWith('/companies')) {
        return new Response(JSON.stringify(companies), { status: 200 });
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new CompanyService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      logService: new LogService(),
    });

    const result = await service.discoverCompanies();
    expect(result.items).toHaveLength(1);
    expect(result.items[0]?.name).toBe('ESTIMATION');
  });

  it('selects a company and returns user-friendly outcome', async () => {
    const selection: CompanySelectionResult = {
      status: 'SUCCESS',
      session: session.session,
    };

    const fetchImpl = async (url: string | URL, init?: RequestInit): Promise<Response> => {
      if (String(url).endsWith('/session/company') && init?.method === 'POST') {
        return new Response(JSON.stringify(selection), { status: 200 });
      }
      return new Response('Not found', { status: 404 });
    };

    const service = new CompanyService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      logService: new LogService(),
    });

    const outcome = await service.selectCompany('estimation');
    expect(outcome.ok).toBe(true);
    expect(outcome.session?.selectedCompany?.id).toBe('estimation');
  });

  it('returns connector unavailable outcome without throwing', async () => {
    const fetchImpl = async (): Promise<Response> => {
      throw new TypeError('fetch failed');
    };

    const service = new CompanyService({
      connectorBaseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      logService: new LogService(),
    });

    const outcome = await service.selectCompany('estimation');
    expect(outcome.ok).toBe(false);
    expect(outcome.status).toBe('CONNECTOR_UNAVAILABLE');
  });
});
