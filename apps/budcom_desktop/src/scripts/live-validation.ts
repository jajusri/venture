/**
 * Milestone 4B live validation — exercises desktop application layer against live connector.
 * Run: node dist/scripts/live-validation.js
 */
import { CompanyService } from '../application/company-service.js';
import { DashboardService } from '../application/dashboard-service.js';
import { LogService } from '../application/log-service.js';

const CONNECTOR_URL = process.env.BUDCOM_CONNECTOR_URL ?? 'http://localhost:8080';

interface ScenarioResult {
  readonly scenario: string;
  readonly status: 'PASS' | 'FAIL';
  readonly detail: string;
}

async function run(): Promise<void> {
  const logService = new LogService();
  const dashboard = new DashboardService({ connectorBaseUrl: CONNECTOR_URL, logService, maxAttempts: 1 });
  const company = new CompanyService({ connectorBaseUrl: CONNECTOR_URL, logService, maxAttempts: 1 });
  const results: ScenarioResult[] = [];

  const record = (scenario: string, ok: boolean, detail: string): void => {
    results.push({ scenario, status: ok ? 'PASS' : 'FAIL', detail });
    console.log(`${ok ? 'PASS' : 'FAIL'} — ${scenario}: ${detail}`);
  };

  // Scenario 1 — Connector Health
  const healthState = await dashboard.getDashboardState();
  record(
    'S1 Connector Health',
    healthState.connectorReachable && healthState.connectionLabel === 'Connected' && healthState.healthStatus === 'ok',
    `reachable=${healthState.connectorReachable} label=${healthState.connectionLabel} health=${healthState.healthStatus}`,
  );

  // Scenario 2 — Company Discovery
  const companies = await company.discoverCompanies();
  const liveCompany = companies.items.find((item) => item.id === 'estimation') ?? companies.items[0];
  record(
    'S2 Company Discovery',
    companies.status === 'SUCCESS' && companies.items.length > 0 && Boolean(liveCompany),
    `status=${companies.status} count=${companies.items.length} company=${liveCompany?.name ?? 'none'} id=${liveCompany?.id ?? 'none'}`,
  );

  if (!liveCompany) {
    console.log(JSON.stringify({ results, connectorUrl: CONNECTOR_URL }, null, 2));
    process.exit(1);
  }

  // Scenario 3 — Company Selection
  const selection = await company.selectCompany(liveCompany.id);
  const afterSelect = await dashboard.getDashboardState();
  record(
    'S3 Company Selection',
    selection.ok &&
      afterSelect.companyName === liveCompany.name &&
      afterSelect.companyId === liveCompany.id &&
      afterSelect.sessionStatus === 'ACTIVE' &&
      afterSelect.selectionTime !== 'Not selected' &&
      afterSelect.lastRefresh !== '—',
    `selection=${selection.status} company=${afterSelect.companyName} session=${afterSelect.sessionStatus}`,
  );

  // Scenario 4 — Session Validation (authoritative connector session)
  const afterValidate = await dashboard.getDashboardState();
  record(
    'S4 Session Validation',
    afterValidate.sessionStatus === 'ACTIVE' && afterValidate.companyId === liveCompany.id,
    `session=${afterValidate.sessionStatus} companyId=${afterValidate.companyId}`,
  );

  // Scenario 5 — Clear Selection
  await company.clearSelection();
  const afterClear = await dashboard.getDashboardState();
  record(
    'S5 Clear Selection',
    afterClear.sessionStatus === 'NO_COMPANY_SELECTED' && afterClear.companyName === '—',
    `session=${afterClear.sessionStatus} company=${afterClear.companyName}`,
  );

  // Scenario 6 — Reselect
  const reselect = await company.selectCompany(liveCompany.id);
  const afterReselect = await dashboard.getDashboardState();
  record(
    'S6 Reselect Company',
    reselect.ok && afterReselect.sessionStatus === 'ACTIVE' && afterReselect.companyId === liveCompany.id,
    `selection=${reselect.status} session=${afterReselect.sessionStatus}`,
  );

  // Scenario 7 — Desktop Restart (simulated: new service instances, same connector session)
  const restartDashboard = new DashboardService({ connectorBaseUrl: CONNECTOR_URL, maxAttempts: 1 });
  const afterRestart = await restartDashboard.getDashboardState();
  record(
    'S7 Desktop Restart',
    afterRestart.companyId === liveCompany.id && afterRestart.sessionStatus === 'ACTIVE',
    `persisted company=${afterRestart.companyName} session=${afterRestart.sessionStatus} (connector in-memory session)`,
  );

  console.log(JSON.stringify({
    validatedAt: new Date().toISOString(),
    connectorUrl: CONNECTOR_URL,
    connectorVersion: afterRestart.connectorVersion,
    desktopVersion: afterRestart.desktopVersion,
    liveCompany: { id: liveCompany.id, name: liveCompany.name },
    results,
  }, null, 2));
}

void run().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error('Live validation failed:', message);
  process.exit(1);
});
