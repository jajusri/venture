import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { GROUP_EMPTY_LIST, GROUP_MULTIPLE_HIERARCHY } from '../helpers/groups-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('groups extraction integration', () => {
  const companyId = 'estimation';

  it('returns SUCCESS with hierarchy metadata through the secure path', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const originalHandler = fetchImpl as unknown as (url: unknown, init?: RequestInit) => Promise<Response>;
    const wrappedFetch = (async (url: unknown, init?: RequestInit) => {
      const body = typeof init?.body === 'string' ? init.body : '';
      if (body.includes('List of Groups')) {
        return new Response(GROUP_MULTIPLE_HIERARCHY, {
          status: 200,
          headers: { 'content-type': 'text/xml' },
        });
      }
      return originalHandler(url, init);
    }) as typeof fetch;

    const context = createTestContext({ fetchImpl: wrappedFetch, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: companyId });

    const response = await request(createTestApp(context)).get(
      `/companies/${companyId}/ledger-groups?page=1&pageSize=50`,
    );
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.contractVersion).toBe('1');
    expect(response.body.items.length).toBeGreaterThan(0);
    expect(response.body.items[0].isPrimary).toBeDefined();
  });

  it('returns EMPTY with explicit status when no groups are available', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const originalHandler = fetchImpl as unknown as (url: unknown, init?: RequestInit) => Promise<Response>;
    const wrappedFetch = (async (url: unknown, init?: RequestInit) => {
      const body = typeof init?.body === 'string' ? init.body : '';
      if (body.includes('List of Groups')) {
        return new Response(GROUP_EMPTY_LIST, {
          status: 200,
          headers: { 'content-type': 'text/xml' },
        });
      }
      return originalHandler(url, init);
    }) as typeof fetch;

    const context = createTestContext({ fetchImpl: wrappedFetch, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: companyId });

    const response = await request(createTestApp(context)).get(
      `/companies/${companyId}/ledger-groups`,
    );
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('EMPTY');
    expect(response.body.dataQuality?.status).toBe('EMPTY');
  });

  it('returns 503 when Tally is unavailable', async () => {
    const { fetchImpl: baseFetch } = createMasterDataMockFetch({ pingOk: true });
    const fetchImpl: typeof fetch = async (url, init) => {
      const body = typeof init?.body === 'string' ? init.body : '';
      if (body.includes('List of Companies') || body.includes('License Info')) {
        return baseFetch(url, init);
      }
      throw new TypeError('fetch failed');
    };
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    await startTestServices(context, { selectCompanyId: companyId });

    const response = await request(createTestApp(context)).get(
      `/companies/${companyId}/ledger-groups`,
    );
    expect(response.status).toBe(503);
    expect(response.body.code).toBe('SERVICE_UNAVAILABLE');
  });
});

describe('Milestone 3B company discovery regression', () => {
  it('GET /companies still returns discovery results', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const response = await request(createTestApp(context)).get('/companies');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.contractVersion).toBe('1');
  });
});
