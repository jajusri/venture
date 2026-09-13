import fs from 'node:fs';
import path from 'node:path';

import { registerServices, startApplication, stopApplication } from '../src/bootstrap/register-services.js';
import { ServiceTokens } from '../src/core/tokens.js';
import type { ConnectorSessionService } from '../src/services/interfaces/connector-session.js';
import type { LedgerSyncService } from '../src/services/ledger/ledger-sync.service.js';

interface ScenarioResult {
  readonly scenario: string;
  readonly status: 'PASS' | 'FAIL' | 'SKIP';
  readonly detail: string;
}

async function runScenario(
  name: string,
  action: () => Promise<void>,
): Promise<ScenarioResult> {
  try {
    await action();
    return { scenario: name, status: 'PASS', detail: 'Completed successfully.' };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    if (detail.toLowerCase().includes('fetch failed') || detail.toLowerCase().includes('unavailable')) {
      return { scenario: name, status: 'SKIP', detail: `Tally unavailable: ${detail}` };
    }
    return { scenario: name, status: 'FAIL', detail };
  }
}

async function main(): Promise<void> {
  const context = registerServices({ env: 'development', logLevel: 'error', tallyRetryMaxAttempts: 2 });
  await startApplication(context);

  const session = context.container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession);
  const ledgerSync = context.container.resolve<LedgerSyncService>(ServiceTokens.LedgerSync);
  const results: ScenarioResult[] = [];

  const companies = await context.container.resolve(ServiceTokens.CompanyDiscovery).discoverCompanies();
  if (!companies.tallyReachable || companies.items.length === 0) {
    results.push({
      scenario: 'Tally reachable',
      status: 'SKIP',
      detail: 'Tally is not reachable or returned no companies.',
    });
  } else {
    results.push({ scenario: 'Tally reachable', status: 'PASS', detail: `${companies.items.length} companies discovered.` });
    const companyId = companies.items[0].id;
    await session.selectCompany(companyId);

    results.push(
      await runScenario('Initial full sync', async () => {
        const sync = await ledgerSync.syncLedgers();
        if (sync.statistics.totalLedgers <= 0) {
          throw new Error('Expected at least one ledger after sync.');
        }
      }),
    );

    results.push(
      await runScenario('Repeated sync', async () => {
        const sync = await ledgerSync.syncLedgers({ incremental: true });
        if (sync.status !== 'completed') {
          throw new Error(`Expected completed status, got ${sync.status}`);
        }
      }),
    );

    results.push(
      await runScenario('Search and pagination', async () => {
        const page = await ledgerSync.getLedgers({ page: 1, pageSize: 10 });
        if (page.items.length === 0) {
          throw new Error('Expected ledger page items.');
        }
      }),
    );

    results.push(
      await runScenario('Statistics', async () => {
        const stats = await ledgerSync.getStatistics();
        if (stats.totalLedgers <= 0) {
          throw new Error('Expected ledger statistics.');
        }
      }),
    );
  }

  await stopApplication(context);

  const output = {
    validatedAt: new Date().toISOString(),
    milestone: '5A',
    connectorVersion: context.config.connectorVersion,
    results,
  };

  const outputPath = path.resolve('../../docs/diagnostics/m5a-live-validation.json');
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(output, null, 2));
}

void main();
