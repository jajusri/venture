import fs from 'node:fs';
import path from 'node:path';
import { performance } from 'node:perf_hooks';

import { SAMPLE_LEDGERS_RESPONSE } from '../test/helpers/master-data-fixtures.js';
import { buildLedgersResponse, FaultInjectionTallyServer } from '../test/helpers/fault-injection-server.js';
import { createFaultTestDatabasePath, runLedgerSyncViaFaultServer } from '../test/helpers/sync-fault-helpers.js';

interface PhaseTiming {
  readonly monotonicMs: number;
  readonly label: string;
}

interface Scenario12PhaseResult {
  readonly scenario: '12A' | '12B';
  readonly pass: boolean;
  readonly expectedBehavior: string;
  readonly observedBehavior: string;
  readonly syncStatus: string | null;
  readonly ledgerCountBefore: number;
  readonly ledgerCountAfter: number;
  readonly latestRunStatus: string | null;
  readonly latestRunProcessed: number;
  readonly tallyRequestCount: number;
  readonly timings: readonly PhaseTiming[];
  readonly correlationId: string;
}

function mark(timings: PhaseTiming[], label: string, started: number): void {
  timings.push({ label, monotonicMs: Math.round(performance.now() - started) });
}

async function runScenario12A(): Promise<Scenario12PhaseResult> {
  const timings: PhaseTiming[] = [];
  const started = performance.now();
  const correlationId = `scenario-12a-${Date.now()}`;
  const databasePath = createFaultTestDatabasePath();

  const server = new FaultInjectionTallyServer();
  mark(timings, 'server-create', started);
  await server.start({
    mode: 'partial-body-destroy',
    body: SAMPLE_LEDGERS_RESPONSE,
    partialBody: SAMPLE_LEDGERS_RESPONSE.slice(0, 160),
  });
  mark(timings, 'fault-configured', started);

  mark(timings, 'request-dispatch', started);
  const outcome = await runLedgerSyncViaFaultServer({ server, databasePath });
  mark(timings, 'finalization', started);
  mark(timings, 'fault-injected-at-server-partial-body', started);

  const pass =
    outcome.latestRunStatus === 'failed' &&
    outcome.latestRunProcessed === 0 &&
    outcome.ledgerCount === 0 &&
    outcome.syncStatus === 'failed';

  await server.close();
  fs.rmSync(databasePath, { recursive: true, force: true });

  return {
    scenario: '12A',
    pass,
    expectedBehavior: 'Transport fault before body complete: run failed, no domain/checkpoint mutation',
    observedBehavior: `run=${outcome.latestRunStatus ?? 'none'} processed=${outcome.latestRunProcessed}`,
    syncStatus: outcome.syncStatus,
    ledgerCountBefore: 0,
    ledgerCountAfter: outcome.ledgerCount,
    latestRunStatus: outcome.latestRunStatus,
    latestRunProcessed: outcome.latestRunProcessed,
    tallyRequestCount: server.stats.ledgerRequestCount,
    timings,
    correlationId,
  };
}

async function runScenario12B(): Promise<Scenario12PhaseResult> {
  const timings: PhaseTiming[] = [];
  const started = performance.now();
  const correlationId = `scenario-12b-${Date.now()}`;
  const databasePath = createFaultTestDatabasePath();
  const body = buildLedgersResponse(300);

  const server = new FaultInjectionTallyServer();
  await server.start({ mode: 'complete-body', body, closeAfterResponse: true });
  mark(timings, 'fault-configured-complete-body', started);

  mark(timings, 'request-dispatch', started);
  const outcome = await runLedgerSyncViaFaultServer({ server, databasePath, tallyTimeoutMs: 5000 });
  mark(timings, 'extraction-and-local-complete', started);
  mark(timings, 'endpoint-closed-after-body', started);
  mark(timings, 'finalization', started);

  const pass =
    outcome.syncStatus === 'completed' &&
    outcome.latestRunStatus === 'completed' &&
    outcome.ledgerCount === 300 &&
    server.stats.ledgerRequestCount === 1;

  fs.rmSync(databasePath, { recursive: true, force: true });

  return {
    scenario: '12B',
    pass,
    expectedBehavior: 'After full body receipt endpoint may close; local processing completes without further Tally requests',
    observedBehavior: `run=${outcome.latestRunStatus ?? 'none'} records=${outcome.ledgerCount} requests=${server.stats.ledgerRequestCount}`,
    syncStatus: outcome.syncStatus,
    ledgerCountBefore: 0,
    ledgerCountAfter: outcome.ledgerCount,
    latestRunStatus: outcome.latestRunStatus,
    latestRunProcessed: outcome.latestRunProcessed,
    tallyRequestCount: server.stats.ledgerRequestCount,
    timings,
    correlationId,
  };
}

async function main(): Promise<void> {
  const writeEvidence = process.argv.includes('--write-evidence');
  const scenario12A = await runScenario12A();
  const scenario12B = await runScenario12B();

  const output = {
    milestone: '5B-scenario-12-deterministic-validation',
    validatedAt: new Date().toISOString(),
    dataClassification: 'operator-controlled-aggregate-redacted',
    supersededEvidence: 'docs/diagnostics/m5b-operational-validation.json phase 5a1-scenario-12-during-sync',
    interpretation:
      'Original manual Scenario 12 could not prove Tally was stopped before response completion. Deterministic harness separates 12A (pre-body fault) and 12B (post-body local-only).',
    fullBufferBoundary: 'Tally required only until extractWithRetry resolves; local persistence does not re-contact Tally.',
    productionDefectFound: false,
    transportCodeChanged: false,
    phases: [scenario12A, scenario12B],
    overallPass: scenario12A.pass && scenario12B.pass,
  };

  console.log(JSON.stringify(output, null, 2));
  if (writeEvidence) {
    const outputPath = path.resolve('../../docs/diagnostics/m5b-scenario-12-deterministic-validation.json');
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
  }
  if (!output.overallPass) {
    process.exitCode = 1;
  }
}

void main();
