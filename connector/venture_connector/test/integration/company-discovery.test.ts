import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createTallyMockFetch } from '../helpers/mock-fetch.js';
import { COMPANY_EMPTY_LIST, COMPANY_MULTIPLE_VALID } from '../helpers/company-discovery-fixtures.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('company discovery integration', () => {
  it('returns SUCCESS with multiple companies through the secure path', async () => {
    const { fetchImpl } = createTallyMockFetch({
      pingOk: true,
      companiesXml: COMPANY_MULTIPLE_VALID,
    });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);

    const response = await request(createTestApp(context)).get('/companies');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.items).toHaveLength(2);
    expect(response.body.tallyReachable).toBe(true);
  });

  it('returns EMPTY with explicit status when no companies are available', async () => {
    const { fetchImpl } = createTallyMockFetch({
      pingOk: true,
      companiesXml: COMPANY_EMPTY_LIST,
    });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);

    const response = await request(createTestApp(context)).get('/companies');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('EMPTY');
    expect(response.body.items).toEqual([]);
    expect(response.body.dataQuality?.status).toBe('EMPTY');
  });

  it('returns 503 when Tally is unavailable', async () => {
    const fetchImpl: typeof fetch = async () => {
      throw new TypeError('fetch failed');
    };
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    await startTestServices(context);

    const response = await request(createTestApp(context)).get('/companies');
    expect(response.status).toBe(503);
    expect(response.body.code).toBe('SERVICE_UNAVAILABLE');
  });
});
