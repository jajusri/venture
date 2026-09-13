import { describe, expect, it, vi } from 'vitest';

import { HttpHealthChecker } from '../../src/application/connector-lifecycle-service.js';
import type { HealthResponse } from '../../src/application/types.js';

function fakeFetch(body: Partial<HealthResponse>): typeof fetch {
  const full: HealthResponse = {
    status: 'ok',
    schemaVersion: '1.0.0',
    connectorVersion: '0.4.6',
    tallyReachable: true,
    readOnly: true,
    bindHost: '127.0.0.1',
    bindPort: 8080,
    networkExposure: 'loopback',
    networkExposureWarning: null,
    networkPolicySatisfied: true,
    authenticatedLanAccessEnabled: false,
    connectorId: 'connector-abc',
    connectorName: 'TESTHOST',
    discoveryAdvertising: true,
    services: [],
    startupCorrelationId: null,
    repositoryAvailable: true,
    databaseAccessible: true,
    ...body,
  } as HealthResponse;

  return vi.fn(async () =>
    new Response(JSON.stringify(full), { status: 200, headers: { 'content-type': 'application/json' } }),
  ) as unknown as typeof fetch;
}

describe('HttpHealthChecker ownership (isOwnedHealth)', () => {
  it('is owned with no ownership constraints configured (matches validation-script usage)', async () => {
    const checker = new HttpHealthChecker('http://localhost:8080', fakeFetch({}));
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(true);
  });

  it('is not owned when the reported bindPort does not match expectedPort', async () => {
    const checker = new HttpHealthChecker('http://localhost:8080', fakeFetch({ bindPort: 9999 }), {
      expectedPort: 8080,
    });
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(false);
  });

  it('is owned when startupCorrelationId matches (the child this instance just spawned)', async () => {
    const checker = new HttpHealthChecker('http://localhost:8080', fakeFetch({ startupCorrelationId: 'run-1' }), {
      expectedPort: 8080,
      expectedCorrelationId: 'run-1',
      expectedConnectorId: 'connector-xyz',
    });
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(true);
  });

  it('is NOT owned when neither startupCorrelationId nor connectorId matches (fail-closed for a genuinely foreign process)', async () => {
    const checker = new HttpHealthChecker(
      'http://localhost:8080',
      fakeFetch({ startupCorrelationId: 'someone-elses-run', connectorId: 'someone-elses-connector' }),
      { expectedPort: 8080, expectedCorrelationId: 'run-1', expectedConnectorId: 'connector-xyz' },
    );
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(false);
  });

  it('is owned when connectorId matches even though startupCorrelationId does not (orphan recovery: a connector this installation spawned in an earlier run, surviving an abnormal exit)', async () => {
    const checker = new HttpHealthChecker(
      'http://localhost:8080',
      // This run's fresh correlation id can never have been known to a process that already
      // existed before this run generated it -- exactly the orphan scenario.
      fakeFetch({ startupCorrelationId: 'this-runs-fresh-id', connectorId: 'connector-xyz' }),
      { expectedPort: 8080, expectedCorrelationId: 'this-runs-fresh-id-not-yet-assigned', expectedConnectorId: 'connector-xyz' },
    );
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(true);
  });

  it('is NOT owned when connectorId matches but bindPort does not (port check still applies first)', async () => {
    const checker = new HttpHealthChecker(
      'http://localhost:8080',
      fakeFetch({ bindPort: 9999, connectorId: 'connector-xyz' }),
      { expectedPort: 8080, expectedConnectorId: 'connector-xyz' },
    );
    const details = await checker.checkHealthDetails();
    expect(details.owned).toBe(false);
  });
});
