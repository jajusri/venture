import fs from 'node:fs';
import path from 'node:path';

import { registerServices, startApplication, stopApplication } from '../src/bootstrap/register-services.js';
import { ServiceTokens } from '../src/core/tokens.js';
import type { ConnectorSessionService } from '../src/services/interfaces/connector-session.js';
import type { StockItemDetails } from '../src/erp/stock-item/stock-item-domain.js';
import type { StockItemSyncService } from '../src/services/stock-item/stock-item-sync.service.js';
import type { SqliteStorageService } from '../src/storage/sqlite/storage-service.js';

interface ScenarioResult {
  readonly scenario: string;
  readonly status: 'PASS' | 'FAIL' | 'SKIP' | 'BLOCKED';
  readonly detail: string;
}

interface IdentityCoverage {
  readonly totalItems: number;
  readonly withGuidField: number;
  readonly withAlterIdField: number;
  readonly withBothFields: number;
  readonly guidIdentityCount: number;
  readonly alterIdentityCount: number;
  readonly nameFallbackCount: number;
  readonly guidCoveragePct: number;
  readonly alterIdCoveragePct: number;
  readonly nameFallbackPct: number;
  readonly duplicateDisplayNameCount: number;
  readonly fallbackCollisionCount: number;
  readonly blankIdentityCount: number;
}

interface FieldCoverage {
  readonly withBaseUnit: number;
  readonly withParentGroup: number;
  readonly withAlias: number;
  readonly withPartNumber: number;
  readonly withHsn: number;
  readonly withGst: number;
  readonly incompleteData: number;
  readonly baseUnitCoveragePct: number;
  readonly parentGroupCoveragePct: number;
  readonly aliasCoveragePct: number;
  readonly partNumberCoveragePct: number;
}

function pct(numerator: number, denominator: number): number {
  if (denominator === 0) return 0;
  return Math.round((numerator / denominator) * 10_000) / 100;
}

function measureIdentityCoverage(items: readonly StockItemDetails[]): IdentityCoverage {
  const withGuidField = items.filter((item) => Boolean(item.guid?.trim())).length;
  const withAlterIdField = items.filter((item) => Boolean(item.alterId?.trim())).length;
  const withBothFields = items.filter((item) => Boolean(item.guid?.trim()) && Boolean(item.alterId?.trim())).length;
  const guidIdentityCount = items.filter((item) => item.id.startsWith('guid:')).length;
  const alterIdentityCount = items.filter((item) => item.id.startsWith('alter:')).length;
  const nameFallbackCount = items.filter((item) => item.id.startsWith('name:')).length;
  const blankIdentityCount = items.filter((item) => !item.id || item.id === 'name:').length;

  const namesByNormalized = new Map<string, number>();
  for (const item of items) {
    namesByNormalized.set(item.normalizedName, (namesByNormalized.get(item.normalizedName) ?? 0) + 1);
  }
  const duplicateDisplayNameCount = [...namesByNormalized.values()].filter((count) => count > 1).length;

  const idsByNameSlug = new Map<string, Set<string>>();
  for (const item of items.filter((entry) => entry.id.startsWith('name:'))) {
    const slug = item.id.slice('name:'.length);
    const set = idsByNameSlug.get(slug) ?? new Set<string>();
    set.add(item.id);
    idsByNameSlug.set(slug, set);
  }
  const fallbackCollisionCount = [...idsByNameSlug.values()].filter((set) => set.size > 1).length;

  const totalItems = items.length;
  return {
    totalItems,
    withGuidField,
    withAlterIdField,
    withBothFields,
    guidIdentityCount,
    alterIdentityCount,
    nameFallbackCount,
    guidCoveragePct: pct(withGuidField, totalItems),
    alterIdCoveragePct: pct(withAlterIdField, totalItems),
    nameFallbackPct: pct(nameFallbackCount, totalItems),
    duplicateDisplayNameCount,
    fallbackCollisionCount,
    blankIdentityCount,
  };
}

function measureFieldCoverage(items: readonly StockItemDetails[]): FieldCoverage {
  const totalItems = items.length;
  const withBaseUnit = items.filter((item) => Boolean(item.baseUnit?.trim())).length;
  const withParentGroup = items.filter((item) => Boolean(item.parentGroup?.trim())).length;
  const withAlias = items.filter((item) => Boolean(item.alias?.trim())).length;
  const withPartNumber = items.filter((item) => Boolean(item.partNumber?.trim())).length;
  const withHsn = items.filter((item) => Boolean(item.hsnCode?.trim())).length;
  const withGst = items.filter((item) => Boolean(item.gstRate?.trim())).length;
  const incompleteData = items.filter((item) => item.dataQuality === 'incomplete').length;
  return {
    withBaseUnit,
    withParentGroup,
    withAlias,
    withPartNumber,
    withHsn,
    withGst,
    incompleteData,
    baseUnitCoveragePct: pct(withBaseUnit, totalItems),
    parentGroupCoveragePct: pct(withParentGroup, totalItems),
    aliasCoveragePct: pct(withAlias, totalItems),
    partNumberCoveragePct: pct(withPartNumber, totalItems),
  };
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

async function loadAllStockItems(stockItemSync: StockItemSyncService): Promise<StockItemDetails[]> {
  const items: StockItemDetails[] = [];
  let page = 1;
  const pageSize = 100;
  while (true) {
    const result = await stockItemSync.getStockItems({ page, pageSize });
    for (const summary of result.items) {
      const detail = await stockItemSync.getStockItemById(summary.id);
      if (detail) items.push(detail);
    }
    if (page >= result.pagination.totalPages) break;
    page += 1;
  }
  return items;
}

async function main(): Promise<void> {
  const context = registerServices({ env: 'development', logLevel: 'error', tallyRetryMaxAttempts: 2 });
  await startApplication(context);

  const session = context.container.resolve<ConnectorSessionService>(ServiceTokens.ConnectorSession);
  const stockItemSync = context.container.resolve<StockItemSyncService>(ServiceTokens.StockItemSync);
  const storage = context.container.resolve<SqliteStorageService>(ServiceTokens.LocalDatabase);
  const results: ScenarioResult[] = [];

  const preflight = {
    validatedAt: new Date().toISOString(),
    connectorHost: context.config.host,
    connectorPort: context.config.port,
    tallyHost: context.config.tallyHost,
    tallyPort: context.config.tallyPort,
    requestCollection: 'List of Stock Items',
    testEnvironment: process.platform,
    companyLabel: '[REDACTED]',
    approximateSourceCount: null as number | null,
    dataClassification: 'operator-controlled',
  };

  const companies = await context.container.resolve(ServiceTokens.CompanyDiscovery).discoverCompanies();
  if (!companies.tallyReachable || companies.items.length === 0) {
    results.push({
      scenario: 'Preflight Tally reachable',
      status: 'BLOCKED',
      detail: 'Tally is not reachable or returned no companies.',
    });
  } else {
    results.push({
      scenario: 'Preflight Tally reachable',
      status: 'PASS',
      detail: `${companies.items.length} companies discovered.`,
    });
    preflight.companyLabel = `[COMPANY-${companies.items[0].id.length}]`;
    const companyId = companies.items[0].id;
    await session.selectCompany(companyId);

    let firstSyncSummary: Record<string, unknown> = {};
    let repeatSyncSummary: Record<string, unknown> = {};
    let identityCoverage: IdentityCoverage | null = null;
    let fieldCoverage: FieldCoverage | null = null;
    let rowCountAfterFirst = 0;

    results.push(
      await runScenario('Scenario A — initial stock item sync', async () => {
        await stockItemSync.clearCache();
        const started = Date.now();
        const sync = await stockItemSync.syncStockItems();
        if (sync.status !== 'completed') {
          throw new Error(`Expected completed status, got ${sync.status}`);
        }
        rowCountAfterFirst = await storage.getBundle().stockItemRepository.countByCompany(companyId);
        firstSyncSummary = {
          sourceItemCount: sync.progress.itemsProcessed,
          acceptedCount: sync.progress.itemsProcessed - sync.progress.itemsFailed,
          rejectedCount: sync.progress.itemsFailed,
          insertedCount: sync.progress.itemsAdded,
          updatedCount: sync.progress.itemsUpdated,
          skippedCount: sync.progress.itemsSkipped,
          durationMs: Date.now() - started,
          databaseRowCount: rowCountAfterFirst,
          validationIssueCount: sync.validationIssueCount,
        };
      }),
    );

    results.push(
      await runScenario('Scenario B — identity coverage', async () => {
        const items = await loadAllStockItems(stockItemSync);
        preflight.approximateSourceCount = items.length;
        identityCoverage = measureIdentityCoverage(items);
        if (identityCoverage.totalItems <= 0) {
          throw new Error('No stock items available for identity measurement.');
        }
      }),
    );

    results.push(
      await runScenario('Scenario C — field coverage', async () => {
        const items = await loadAllStockItems(stockItemSync);
        fieldCoverage = measureFieldCoverage(items);
      }),
    );

    results.push(
      await runScenario('Scenario D — repeat sync idempotency', async () => {
        const before = await loadAllStockItems(stockItemSync);
        const beforeIds = before.map((item) => item.id).sort();
        const sync = await stockItemSync.syncStockItems({ incremental: true });
        if (sync.status !== 'completed') {
          throw new Error(`Expected completed repeat status, got ${sync.status}`);
        }
        const after = await loadAllStockItems(stockItemSync);
        const afterIds = after.map((item) => item.id).sort();
        repeatSyncSummary = {
          insertedCount: sync.progress.itemsAdded,
          updatedCount: sync.progress.itemsUpdated,
          skippedCount: sync.progress.itemsSkipped,
          rowCountStable: after.length === before.length,
          identityStable: JSON.stringify(beforeIds) === JSON.stringify(afterIds),
        };
        if (!repeatSyncSummary.identityStable) {
          throw new Error('Stock item identities changed between identical syncs.');
        }
      }),
    );

    results.push(
      await runScenario('Scenario E — cancellation', async () => {
        const syncPromise = stockItemSync.syncStockItems();
        await new Promise((resolve) => setTimeout(resolve, 50));
        const progress = await stockItemSync.cancelSync();
        const result = await syncPromise;
        if (result.status === 'completed' && progress.status === 'cancelling') {
          throw new Error('Cancellation reported cancelling but sync completed.');
        }
      }),
    );

    results.push({
      scenario: 'Scenario F — Tally unavailable and recovery',
      status: 'BLOCKED',
      detail: 'Requires operator to stop Tally temporarily. Not auto-executed in unattended gate.',
    });

    await stopApplication(context);

    const output = {
      ...preflight,
      milestone: '5B',
      connectorVersion: context.config.connectorVersion,
      scenarios: results,
      firstSyncSummary,
      repeatSyncSummary,
      identityCoverage,
      fieldCoverage,
      identityVerdict:
        identityCoverage && identityCoverage.totalItems > 0
          ? identityCoverage.fallbackCollisionCount === 0
            ? identityCoverage.nameFallbackPct <= 5
              ? 'STABLE SOURCE IDENTITY CONFIRMED'
              : identityCoverage.guidCoveragePct >= 80 || identityCoverage.nameFallbackPct <= 20
                ? 'ACCEPTABLE FALLBACK WITH DOCUMENTED LIMITATION'
                : 'UNACCEPTABLE IDENTITY INSTABILITY'
            : 'UNACCEPTABLE IDENTITY INSTABILITY'
          : 'UNMEASURED — NO ITEMS EXTRACTED',
    };

    const outputPath = path.resolve('../../docs/diagnostics/m5b-live-validation.json');
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
    console.log(JSON.stringify(output, null, 2));
    return;
  }

  await stopApplication(context);

  const output = {
    ...preflight,
    milestone: '5B',
    connectorVersion: context.config.connectorVersion,
    scenarios: results,
    identityVerdict: 'UNMEASURED — LIVE ENVIRONMENT BLOCKED',
  };
  const outputPath = path.resolve('../../docs/diagnostics/m5b-live-validation.json');
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(output, null, 2));
}

void main();
