import { afterEach, describe, expect, it } from 'vitest';
import { buildTrustService } from '../services/trust/src/app.js';
const apps: ReturnType<typeof buildTrustService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));
describe('Trust Service foundation', () => {
  it('reports local service health without exposing configuration', async () => {
    const app = buildTrustService(); apps.push(app);
    const response = await app.inject({ method: 'GET', url: '/health' });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: 'ok', service: 'budcom-trust' });
  });
});
