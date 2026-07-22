import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { createMasterDataMockFetch } from '../helpers/mock-fetch.js';
import { createTestApp, createTestContext, startTestServices } from '../helpers/test-context.js';

describe('master data extraction routes', () => {
  const companyId = 'estimation';

  async function setupApp() {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context, { selectCompanyId: 'estimation' });
    return createTestApp(context);
  }

  it('GET /companies/:companyId returns company info', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}`);
    expect(response.status).toBe(200);
    expect(response.body.company.name).toBe('ESTIMATION');
    expect(response.body.company.gstin).toBe('27AAAAA0000A1Z5');
  });

  it('GET /companies/:companyId/ledger-groups returns paginated groups', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}/ledger-groups?page=1&pageSize=10`);
    expect(response.status).toBe(200);
    expect(response.body.items).toHaveLength(2);
    expect(response.body.pagination.totalItems).toBe(2);
  });

  it('GET /companies/:companyId/ledgers returns paginated ledgers', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}/ledgers`);
    expect(response.status).toBe(200);
    expect(response.body.items.length).toBeGreaterThan(0);
    expect(response.body.items[0].closingBalance.side).toBeDefined();
  });

  it('GET /companies/:companyId/stock-items returns stock items', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}/stock-items`);
    expect(response.status).toBe(200);
    expect(response.body.items[0].name).toBe('Widget A');
  });

  it('GET /companies/:companyId/units returns units', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}/units`);
    expect(response.status).toBe(200);
    expect(response.body.items[0].name).toBe('Nos');
  });

  it('GET /companies/:companyId/stock-categories returns empty list gracefully', async () => {
    const app = await setupApp();
    const response = await request(app).get(`/companies/${companyId}/stock-categories`);
    expect(response.status).toBe(200);
    expect(response.body.items).toEqual([]);
  });

  it('GET /diagnostics/extraction returns extractor diagnostics', async () => {
    const app = await setupApp();
    await request(app).get(`/companies/${companyId}/ledgers`);
    const response = await request(app).get('/diagnostics/extraction');
    expect(response.status).toBe(200);
    expect(response.body.extractors.length).toBe(11);
    const ledgerDiag = response.body.extractors.find(
      (d: { entityType: string }) => d.entityType === 'ledgers',
    );
    expect(ledgerDiag.totalExtractions).toBeGreaterThan(0);
  });

  it('returns 403 when requested company does not match session selection', async () => {
    const app = await setupApp();
    const response = await request(app).get('/companies/unknown-co/ledgers');
    expect(response.status).toBe(403);
    expect(response.body.details.sessionStatus).toBe('COMPANY_NOT_ACCESSIBLE');
  });

  it('returns 400 when no company is selected', async () => {
    const { fetchImpl } = createMasterDataMockFetch({ pingOk: true });
    const context = createTestContext({ fetchImpl, tallyRetryMaxAttempts: 1 });
    await startTestServices(context);
    const app = createTestApp(context);
    const response = await request(app).get(`/companies/${companyId}/ledgers`);
    expect(response.status).toBe(400);
    expect(response.body.details.sessionStatus).toBe('NO_COMPANY_SELECTED');
  });

  it('returns 503 when master data service not started', async () => {
    const response = await request(createTestApp()).get('/companies/estimation/ledgers');
    expect(response.status).toBe(503);
  });
});
