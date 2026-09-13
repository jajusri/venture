import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';

import { COMPANY_EMPTY_LIST, COMPANY_ONE_VALID } from '../helpers/company-discovery-fixtures.js';
import { createMasterDataMockFetch, createTallyMockFetch } from '../helpers/mock-fetch.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('connector session integration', () => {
  async function setupContext(companiesXml = COMPANY_ONE_VALID) {
    const { fetchImpl } = createTallyMockFetch({
      pingOk: true,
      companiesXml,
    });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    return context;
  }

  it('returns empty session before company selection', async () => {
    const context = await setupContext();
    const response = await request(createTestApp(context)).get('/session');
    expect(response.status).toBe(200);
    expect(response.body.contractVersion).toBe('1');
    expect(response.body.session.selectedCompany).toBeNull();
    expect(response.body.session.erpType).toBe('tally');
  });

  it('selects a valid company', async () => {
    const context = await setupContext();
    const app = createTestApp(context);
    const response = await request(app)
      .post('/session/company')
      .send({ companyId: 'estimation' });
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.session.selectedCompany).toEqual({
      id: 'estimation',
      name: 'ESTIMATION',
    });
  });

  it('rejects invalid company selection', async () => {
    const context = await setupContext();
    const response = await request(createTestApp(context))
      .post('/session/company')
      .send({ companyId: 'missing-co' });
    expect(response.status).toBe(404);
    expect(response.body.status).toBe('COMPANY_NOT_FOUND');
  });

  it('rejects empty company selection', async () => {
    const context = await setupContext();
    const response = await request(createTestApp(context))
      .post('/session/company')
      .send({ companyId: '   ' });
    expect(response.status).toBe(400);
    expect(response.body.status).toBe('EMPTY_SELECTION');
  });

  it('accepts duplicate company selection as an idempotent session renewal', async () => {
    const context = await setupContext();
    const app = createTestApp(context);
    const first = await request(app).post('/session/company').send({ companyId: 'estimation' });
    const response = await request(app).post('/session/company').send({ companyId: 'estimation' });
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('DUPLICATE_SELECTION');
    expect(Date.parse(response.body.session.selectedAt)).toBeGreaterThanOrEqual(
      Date.parse(first.body.session.selectedAt),
    );
  });

  it('renews an expired same-company selection before validation', async () => {
    let nowMs = Date.parse('2026-08-09T12:00:00.000Z');
    const nowSpy = vi.spyOn(Date, 'now').mockImplementation(() => nowMs);
    try {
      const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
      const context = createTestContext({
        fetchImpl,
        tallyRetryMaxAttempts: 1,
        sessionTtlMs: 50,
      });
      await startTestServices(context);
      const app = createTestApp(context);
      const initial = await request(app).post('/session/company').send({ companyId: 'estimation' });
      nowMs += 51;

      const expired = await request(app).post('/session/validate');
      expect(expired.status).toBe(410);
      expect(expired.body.status).toBe('SESSION_EXPIRED');

      nowMs += 1;
      const renewed = await request(app).post('/session/company').send({ companyId: 'estimation' });
      expect(renewed.status).toBe(200);
      expect(renewed.body.status).toBe('DUPLICATE_SELECTION');
      expect(Date.parse(renewed.body.session.selectedAt)).toBeGreaterThan(
        Date.parse(initial.body.session.selectedAt),
      );

      const validation = await request(app).post('/session/validate');
      expect(validation.status).toBe(200);
      expect(validation.body.status).toBe('SUCCESS');
    } finally {
      nowSpy.mockRestore();
    }
  });

  it('clears selected company', async () => {
    const context = await setupContext();
    const app = createTestApp(context);
    await request(app).post('/session/company').send({ companyId: 'estimation' });
    const response = await request(app).delete('/session/company');
    expect(response.status).toBe(200);
    expect(response.body.session.selectedCompany).toBeNull();
  });

  it('validates a healthy session', async () => {
    const context = await setupContext();
    const app = createTestApp(context);
    await request(app).post('/session/company').send({ companyId: 'estimation' });
    const response = await request(app).post('/session/validate');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('SUCCESS');
    expect(response.body.companyId).toBe('estimation');
  });

  it('reports NO_COMPANY_SELECTED during validation when empty', async () => {
    const context = await setupContext();
    const response = await request(createTestApp(context)).post('/session/validate');
    expect(response.status).toBe(400);
    expect(response.body.status).toBe('NO_COMPANY_SELECTED');
  });

  it('reports unavailable company when discovery is empty', async () => {
    const context = await setupContext(COMPANY_EMPTY_LIST);
    const response = await request(createTestApp(context))
      .post('/session/company')
      .send({ companyId: 'estimation' });
    expect(response.status).toBe(404);
    expect(response.body.status).toBe('COMPANY_NOT_FOUND');
  });

  it('reuses session across repeated ERP calls after selection', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    const app = createTestApp(context);

    const first = await request(app).get('/session');
    const ledgers = await request(app).get('/companies/estimation/ledgers');
    const second = await request(app).get('/session');

    expect(ledgers.status).toBe(200);
    expect(first.body.session.sessionId).toBe(second.body.session.sessionId);
    expect(second.body.session.selectedCompany.id).toBe('estimation');
  });

  it('returns 503 when Tally is unavailable during selection', async () => {
    const fetchImpl: typeof fetch = async () => {
      throw new TypeError('fetch failed');
    };
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    await startTestServices(context);

    const discovery = await request(createTestApp(context)).get('/companies');
    expect(discovery.status).toBe(503);

    const selection = await request(createTestApp(context))
      .post('/session/company')
      .send({ companyId: 'estimation' });
    expect(selection.status).toBe(400);
    expect(selection.body.status).toBe('INVALID_COMPANY');
  });

  it('reports SESSION_EXPIRED for stale sessions', async () => {
    const { fetchImpl } = createTallyMockFetch({ pingOk: true, companiesXml: COMPANY_ONE_VALID });
    const context = createTestContext({
      fetchImpl,
      tallyRetryMaxAttempts: 1,
      sessionTtlMs: 1,
    });
    await startTestServices(context);
    const app = createTestApp(context);
    await request(app).post('/session/company').send({ companyId: 'estimation' });
    await new Promise((resolve) => setTimeout(resolve, 5));

    const response = await request(app).post('/session/validate');
    expect(response.status).toBe(410);
    expect(response.body.status).toBe('SESSION_EXPIRED');
  });
});
