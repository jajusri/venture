import { describe, expect, it } from 'vitest';

import { ConnectorHttpClient } from '../../src/application/connector-http-client.js';

describe('ConnectorHttpClient', () => {
  it('calls company discovery endpoint', async () => {
    const fetchImpl = async (url: string | URL): Promise<Response> => {
      expect(String(url)).toBe('http://localhost:8080/companies');
      return new Response(JSON.stringify({ items: [], status: 'EMPTY', contractVersion: '1', schemaVersion: '1.0.0', dataFreshnessAt: '2026-07-23T00:00:00.000Z', tallyReachable: true }), { status: 200 });
    };

    const client = new ConnectorHttpClient({
      baseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    const result = await client.getCompanies();
    expect(result.status).toBe('EMPTY');
  });

  it('parses selection responses on non-200 statuses', async () => {
    const fetchImpl = async (): Promise<Response> => {
      return new Response(
        JSON.stringify({
          status: 'COMPANY_NOT_FOUND',
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
          reason: 'Company not found: missing',
        }),
        { status: 404 },
      );
    };

    const client = new ConnectorHttpClient({
      baseUrl: 'http://localhost:8080',
      fetchImpl: fetchImpl as typeof fetch,
      maxAttempts: 1,
    });

    const result = await client.selectCompany('missing');
    expect(result.status).toBe('COMPANY_NOT_FOUND');
  });
});
