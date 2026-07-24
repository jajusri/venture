import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

import { loadConfig } from '../src/config/index.js';
import { createLogger } from '../src/infrastructure/logging/logger.js';
import { TallyReadGateway } from '../src/tally/gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../src/tally/registry/operation-registry.js';
import { createTallyModule } from '../src/tally/tally-module.js';
import type { TallyConnectionManager } from '../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../src/tally/xml/request-builder.js';
import type { ConnectorConfig } from '../src/config/defaults.js';
import type { Logger } from '../src/infrastructure/logging/logger.js';
import {
  assertEvidencePrivacySafe,
  compareRepeatRuns,
  evaluateScenarioExpectation,
  scanTallyErrorShapeResponse,
  type ErrorShapeScanResult,
} from '../test/helpers/tally-error-shape-scanner.js';
import { validateEvidenceFile } from '../test/helpers/tally-error-shape-evidence-validation.js';
import {
  buildPrivacySafeCompanyRunContext,
  resolveRequestedCompanyContext,
  summarizePopulatedCompanyScenario,
} from '../test/helpers/tally-error-shape-company-context.js';
import {
  DISRUPTIVE_INVALID_REPORT_WARNING,
  assessValidCompanyPreflight,
  partitionPreflightAndFollowOnScenarios,
  resolveLiveScenarioPlan,
  shouldBlockEvidenceWrite,
  shouldStopAfterPreflightPhase,
} from '../test/helpers/tally-error-shape-run-plan.js';

const NONEXISTENT_COMPANY = 'BUDCOM-NONEXISTENT-COMPANY-VALIDATION';
const INVALID_REPORT_ID = 'BUDCOM-INVALID-REPORT-VALIDATION';
const VALIDATION_PORT = 8096;
const EVIDENCE_PATH = path.resolve('../../docs/diagnostics/m5b-tally-error-shape-validation.json');
const REPEAT_COUNT = 2;

interface ScenarioRunRecord {
  readonly runIndex: number;
  readonly result: ErrorShapeScanResult;
}

interface ScenarioEvidence {
  readonly scenarioId: string;
  readonly description: string;
  readonly runs: readonly ScenarioRunRecord[];
  readonly repeatStable: boolean;
  readonly repeatDifferences: readonly string[];
  readonly expectedOutcome: string;
  readonly observedOutcome: string;
  readonly contractCorrectForScenario: boolean;
}

function parseArgs(argv: readonly string[]): {
  writeEvidence: boolean;
  validateEvidenceOnly: boolean;
  allowEmptyValidCompany: boolean;
  runDisruptiveInvalidReport: boolean;
  scenarioFilter?: string;
} {
  return {
    writeEvidence: argv.includes('--write-evidence'),
    validateEvidenceOnly: argv.includes('--validate-evidence'),
    allowEmptyValidCompany: argv.includes('--allow-empty-valid-company'),
    runDisruptiveInvalidReport: argv.includes('--run-disruptive-invalid-report'),
    scenarioFilter: argv.find((arg) => arg.startsWith('--scenario='))?.split('=')[1],
  };
}

function findScenarioResult(
  scenarioEvidence: readonly ScenarioEvidence[],
  scenarioId: string,
): ErrorShapeScanResult | undefined {
  const scenario = scenarioEvidence.find((item) => item.scenarioId === scenarioId);
  const lastRun = scenario?.runs[scenario.runs.length - 1];
  return lastRun?.result;
}

function invalidateEvidenceFile(): void {
  if (fs.existsSync(EVIDENCE_PATH)) {
    fs.unlinkSync(EVIDENCE_PATH);
  }
}

async function exchangeXml(
  connectionManager: TallyConnectionManager,
  xml: string,
  metadata: { collectionId?: string; reportId?: string },
): Promise<{ transportStatus: number; rawXml: string; byteLength: number; durationMs: number }> {
  const started = Date.now();
  const result = await connectionManager.exchange(xml, metadata);
  return {
    transportStatus: 200,
    rawXml: result.rawXml,
    byteLength: result.response.byteLength,
    durationMs: Date.now() - started,
  };
}

async function runGatewayScenario(input: {
  scenarioId: string;
  requestKind: string;
  operationId: (typeof ApprovedOperationId)[keyof typeof ApprovedOperationId];
  companyName?: string;
  contractKind: 'ledger' | 'stock' | 'none';
  gateway: TallyReadGateway;
}): Promise<ErrorShapeScanResult> {
  const correlationId = randomUUID();
  const started = Date.now();
  try {
    const exchange = await input.gateway.executeApprovedRead({
      operationId: input.operationId,
      companyName: input.companyName,
    });
    return scanTallyErrorShapeResponse({
      scenarioId: input.scenarioId,
      requestKind: input.requestKind,
      transportStatus: 200,
      responseByteLength: exchange.byteLength,
      rawXml: exchange.rawXml,
      durationMs: Date.now() - started,
      correlationId,
      contractKind: input.contractKind,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return scanTallyErrorShapeResponse({
      scenarioId: input.scenarioId,
      requestKind: input.requestKind,
      transportStatus: null,
      responseByteLength: 0,
      durationMs: Date.now() - started,
      correlationId,
      contractKind: input.contractKind,
      transportErrorCode: 'TRANSPORT_OR_GATEWAY_FAILURE',
      transportErrorMessage: message.slice(0, 160),
    });
  }
}

async function sendDirectToTally(
  config: ConnectorConfig,
  logger: Logger,
  xml: string,
): Promise<{ transportStatus: number; rawXml: string; byteLength: number; durationMs: number }> {
  const started = Date.now();
  const transport = new TallyHttpTransport({
    config,
    logger: logger.child({ component: 'direct-transport' }),
  });
  const response = await transport.send({
    body: xml,
    contentType: 'text/xml',
    correlationId: randomUUID(),
    timeoutMs: config.tallyTimeoutMs,
  });
  return {
    transportStatus: response.statusCode,
    rawXml: response.body,
    byteLength: response.body.length,
    durationMs: Date.now() - started,
  };
}

async function runCustomXmlScenario(input: {
  scenarioId: string;
  requestKind: string;
  xml: string;
  contractKind: 'ledger' | 'stock' | 'none';
  connectionManager: TallyConnectionManager;
  metadata: { collectionId?: string; reportId?: string };
  directTransport?: { config: ConnectorConfig; logger: Logger };
}): Promise<ErrorShapeScanResult> {
  const correlationId = randomUUID();
  const started = Date.now();
  try {
    const exchange = input.directTransport
      ? await sendDirectToTally(input.directTransport.config, input.directTransport.logger, input.xml)
      : await exchangeXml(input.connectionManager, input.xml, input.metadata);
    return scanTallyErrorShapeResponse({
      scenarioId: input.scenarioId,
      requestKind: input.requestKind,
      transportStatus: exchange.transportStatus,
      responseByteLength: exchange.byteLength,
      rawXml: exchange.rawXml,
      durationMs: Date.now() - started,
      correlationId,
      contractKind: input.contractKind,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return scanTallyErrorShapeResponse({
      scenarioId: input.scenarioId,
      requestKind: input.requestKind,
      transportStatus: null,
      responseByteLength: 0,
      durationMs: Date.now() - started,
      correlationId,
      contractKind: input.contractKind,
      transportErrorCode: 'TRANSPORT_OR_GATEWAY_FAILURE',
      transportErrorMessage: message.slice(0, 160),
    });
  }
}

async function runTransportUnavailableScenario(fetchImpl: typeof fetch): Promise<ErrorShapeScanResult> {
  const correlationId = randomUUID();
  const started = Date.now();
  const url = 'http://127.0.0.1:9001';
  const builder = new TallyXmlRequestBuilder();
  const xml = builder.buildConnectivityCheck();
  try {
    const response = await fetchImpl(url, {
      method: 'POST',
      headers: { 'Content-Type': 'text/xml', Accept: 'text/xml' },
      body: xml,
      signal: AbortSignal.timeout(5_000),
    });
    const body = await response.text();
    return evaluateScenarioExpectation(
      scanTallyErrorShapeResponse({
        scenarioId: 'E6',
        requestKind: 'transport-unavailable-probe',
        transportStatus: response.status,
        responseByteLength: body.length,
        rawXml: body.length > 0 ? body : undefined,
        durationMs: Date.now() - started,
        correlationId,
        contractKind: 'none',
      }),
      { expectTransportFailure: true },
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return evaluateScenarioExpectation(
      scanTallyErrorShapeResponse({
        scenarioId: 'E6',
        requestKind: 'transport-unavailable-probe',
        transportStatus: null,
        responseByteLength: 0,
        durationMs: Date.now() - started,
        correlationId,
        contractKind: 'none',
        transportErrorCode: 'CONNECTION_REFUSED_OR_TIMEOUT',
        transportErrorMessage: message.slice(0, 160),
      }),
      { expectTransportFailure: true },
    );
  }
}

async function executeScenarioOnce(
  scenarioId: string,
  deps: {
    companyName: string;
    gateway: TallyReadGateway;
    connectionManager: TallyConnectionManager;
    builder: TallyXmlRequestBuilder;
    fetchImpl: typeof fetch;
    config: ConnectorConfig;
    logger: Logger;
  },
): Promise<ErrorShapeScanResult> {
  switch (scenarioId) {
    case 'E1': {
      const result = await runGatewayScenario({
        scenarioId: 'E1',
        requestKind: 'approved-rich-ledgers',
        operationId: ApprovedOperationId.Ledgers,
        companyName: deps.companyName,
        contractKind: 'ledger',
        gateway: deps.gateway,
      });
      return evaluateScenarioExpectation(result, {
        expectParserAccepted: true,
        expectLineError: false,
        expectCollectionPresent: true,
      });
    }
    case 'E2': {
      const result = await runGatewayScenario({
        scenarioId: 'E2',
        requestKind: 'approved-rich-stock-items',
        operationId: ApprovedOperationId.StockItems,
        companyName: deps.companyName,
        contractKind: 'stock',
        gateway: deps.gateway,
      });
      return evaluateScenarioExpectation(result, {
        expectParserAccepted: true,
        expectLineError: false,
        expectCollectionPresent: true,
      });
    }
    case 'E3': {
      return runGatewayScenario({
        scenarioId: 'E3',
        requestKind: 'approved-ledgers-nonexistent-company',
        operationId: ApprovedOperationId.Ledgers,
        companyName: NONEXISTENT_COMPANY,
        contractKind: 'ledger',
        gateway: deps.gateway,
      });
    }
    case 'E4': {
      const xml = deps.builder.build({
        tallyRequest: 'Export',
        type: 'Collection',
        id: INVALID_REPORT_ID,
        description: INVALID_REPORT_ID,
        staticVariables: {
          SVEXPORTFORMAT: '$$SysName:XML',
          SVCURRENTCOMPANY: deps.companyName,
        },
      });
      return runCustomXmlScenario({
        scenarioId: 'E4',
        requestKind: 'invalid-collection-export',
        xml,
        contractKind: 'ledger',
        connectionManager: deps.connectionManager,
        metadata: { collectionId: INVALID_REPORT_ID },
        directTransport: { config: deps.config, logger: deps.logger },
      });
    }
    case 'E5': {
      const result = await runGatewayScenario({
        scenarioId: 'E5',
        requestKind: 'approved-ledger-groups',
        operationId: ApprovedOperationId.LedgerGroups,
        companyName: deps.companyName,
        contractKind: 'ledger',
        gateway: deps.gateway,
      });
      return evaluateScenarioExpectation(result, {
        expectParserAccepted: true,
        expectLineError: false,
      });
    }
    case 'E6':
      return runTransportUnavailableScenario(deps.fetchImpl);
    default:
      throw new Error(`Unknown scenario ${scenarioId}`);
  }
}

function inferContractCorrectness(scenarioId: string, result: ErrorShapeScanResult): boolean {
  switch (scenarioId) {
    case 'E1':
    case 'E2':
      return (
        result.parserOutcome === 'accepted' &&
        !result.lineErrorPresent &&
        result.collectionPresent &&
        (result.contractStatus === 'SUCCESS' ||
          result.contractStatus === 'EMPTY' ||
          result.contractStatus === 'INCOMPLETE')
      );
    case 'E3':
    case 'E4':
      if (result.lineErrorPresent) {
        return result.contractStatus === 'TALLY_ERROR' && result.contractReasonCode === 'tally_line_error';
      }
      return result.contractBlocking !== false;
    case 'E5':
      return (
        result.parserOutcome === 'accepted' &&
        result.requestedEntityNodeCount === 0 &&
        result.unexpectedEntityNodeCount > 0 &&
        result.contractStatus !== 'SUCCESS'
      );
    case 'E6':
      return result.transportStatus === null && result.parserOutcome === 'not_applicable';
    default:
      return false;
  }
}

function scenarioDescription(scenarioId: string): string {
  const descriptions: Record<string, string> = {
    E1: 'Valid rich ledger request against dedicated test company.',
    E2: 'Valid rich stock-item request against dedicated test company.',
    E3: 'Ledger request with synthetic nonexistent company name.',
    E4: 'Read-only export with synthetic invalid collection/report identifier (disruptive; explicit flag only).',
    E5: 'Valid ledger-groups response classified by ledger contract (wrong entity scope).',
    E6: 'Transport unavailable before XML (no listener on probe port 9001).',
  };
  return descriptions[scenarioId] ?? scenarioId;
}

function expectedOutcome(scenarioId: string): string {
  const outcomes: Record<string, string> = {
    E1: 'Parser accepts; envelope/body/data/collection present; ledger contract SUCCESS/EMPTY/INCOMPLETE without LINEERROR.',
    E2: 'Parser accepts; stock contract SUCCESS/EMPTY/INCOMPLETE without LINEERROR.',
    E3: 'Observe real Tally error shape; contract should block if LINEERROR or missing collection.',
    E4: 'Observe invalid report response; may open blocking Tally modal (explicit operator flag only).',
    E5: 'Ledger contract must not false-success on GROUP-only collection.',
    E6: 'Connection failure; no XML; no contract classification.',
  };
  return outcomes[scenarioId] ?? 'Observe only.';
}

function observedOutcome(result: ErrorShapeScanResult): string {
  return [
    `transport=${result.transportStatus ?? 'none'}`,
    `bytes=${result.responseByteLength}`,
    `parser=${result.parserOutcome}`,
    `lineError=${result.lineErrorPresent}`,
    `headerStatus=${result.headerStatusClassifiedValue ?? 'absent'}`,
    `collection=${result.collectionPresent}`,
    `contract=${result.contractStatus ?? 'n/a'}`,
  ].join('; ');
}

async function runScenarioWithRepeats(
  scenarioId: string,
  deps: Parameters<typeof executeScenarioOnce>[1],
): Promise<ScenarioEvidence> {
  const runs: ScenarioRunRecord[] = [];
  let failed = false;

  for (let runIndex = 0; runIndex < REPEAT_COUNT; runIndex += 1) {
    const result = await executeScenarioOnce(scenarioId, deps);
    runs.push({ runIndex: runIndex + 1, result });
    if (!result.overallPass) failed = true;
  }

  const comparison =
    runs.length >= 2 ? compareRepeatRuns(runs[0].result, runs[1].result) : { stable: true, differences: [] };
  const last = runs[runs.length - 1]?.result;

  return {
    scenarioId,
    description: scenarioDescription(scenarioId),
    runs,
    repeatStable: comparison.stable,
    repeatDifferences: comparison.differences,
    expectedOutcome: expectedOutcome(scenarioId),
    observedOutcome: last ? observedOutcome(last) : 'none',
    contractCorrectForScenario: last ? inferContractCorrectness(scenarioId, last) : false,
  };
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));

  if (args.validateEvidenceOnly) {
    const result = validateEvidenceFile(EVIDENCE_PATH);
    if (!result.valid) {
      console.error(JSON.stringify({ status: 'FAIL', errors: result.errors }, null, 2));
      process.exit(1);
    }
    console.log(JSON.stringify({ status: 'PASS', evidencePath: EVIDENCE_PATH }, null, 2));
    return;
  }

  if (args.runDisruptiveInvalidReport) {
    console.error(DISRUPTIVE_INVALID_REPORT_WARNING);
  }

  const plan = resolveLiveScenarioPlan({
    scenarioFilter: args.scenarioFilter,
    runDisruptiveInvalidReport: args.runDisruptiveInvalidReport,
    writeEvidence: args.writeEvidence,
  });

  invalidateEvidenceFile();

  const config = loadConfig({
    env: 'development',
    logLevel: 'error',
    tallyRetryMaxAttempts: 1,
    host: '127.0.0.1',
    port: VALIDATION_PORT,
    tallyHost: '127.0.0.1',
    tallyPort: 9000,
    tallyRequestAuditEnabled: false,
  });
  const logger = createLogger({ service: 'error-shape-validation', level: config.logLevel });
  const fetchImpl = globalThis.fetch;
  const tallyModule = createTallyModule({ config, logger, fetchImpl });
  const builder = new TallyXmlRequestBuilder();
  const gateway = new TallyReadGateway({
    connectionManager: tallyModule.connectionManager,
    requestBuilder: builder,
    logger: logger.child({ component: 'read-gateway' }),
  });

  const requestedCompany = resolveRequestedCompanyContext(process.env.BUDCOM_TALLY_TEST_COMPANY);
  const companyName = requestedCompany.normalizedName;
  const scenarioEvidence: ScenarioEvidence[] = [];
  let failed = false;
  let disruptiveProbeCompleted = false;

  const deps = {
    companyName,
    gateway,
    connectionManager: tallyModule.connectionManager,
    builder,
    fetchImpl,
    config,
    logger,
  };

  const { preflightScenarios, followOnScenarios } = partitionPreflightAndFollowOnScenarios(plan.scenarios);

  await tallyModule.connectionManager.start();

  try {
    for (const scenarioId of preflightScenarios) {
      const evidence = await runScenarioWithRepeats(scenarioId, deps);
      scenarioEvidence.push(evidence);
      if (!evidence.runs.every((run) => run.result.overallPass)) failed = true;
    }

    const e1 = findScenarioResult(scenarioEvidence, 'E1');
    const e2 = findScenarioResult(scenarioEvidence, 'E2');
    const preflight = assessValidCompanyPreflight(e1, e2);

    if (shouldStopAfterPreflightPhase(preflight)) {
      invalidateEvidenceFile();
      console.error(
        JSON.stringify(
          {
            status: preflight.status,
            reasons: preflight.reasons,
            likelyTallyModalBlocking: preflight.likelyTallyModalBlocking,
            guidance:
              'Dismiss any open Tally modal, confirm the populated test company is selected, verify BUDCOM_TALLY_TEST_COMPANY, then rerun validation-only.',
            e1Sanity: e1 ? summarizePopulatedCompanyScenario(e1) : null,
            e2Sanity: e2 ? summarizePopulatedCompanyScenario(e2) : null,
          },
          null,
          2,
        ),
      );
      process.exit(1);
    }

    for (const scenarioId of followOnScenarios) {
      if (scenarioId === 'E4') {
        disruptiveProbeCompleted = true;
      }
      const evidence = await runScenarioWithRepeats(scenarioId, deps);
      scenarioEvidence.push(evidence);
      if (!evidence.runs.every((run) => run.result.overallPass)) failed = true;
    }
  } finally {
    await tallyModule.connectionManager.stop();
  }

  scenarioEvidence.sort((left, right) => left.scenarioId.localeCompare(right.scenarioId));

  const e1 = findScenarioResult(scenarioEvidence, 'E1');
  const e2 = findScenarioResult(scenarioEvidence, 'E2');
  const preflight = assessValidCompanyPreflight(e1, e2, { disruptiveProbeCompleted });
  const populatedCompanyGate = {
    e1Sanity: e1 ? summarizePopulatedCompanyScenario(e1) : null,
    e2Sanity: e2 ? summarizePopulatedCompanyScenario(e2) : null,
    populatedCompanyGatePassed: Boolean(e1 && e2 && e1.requestedEntityNodeCount > 0 && e2.requestedEntityNodeCount > 0),
    minimumRequestedEntityNodeCount: 1,
    validCompanyPreflight: preflight,
  };

  const evidenceBlock = shouldBlockEvidenceWrite({
    writeEvidence: args.writeEvidence,
    allowEmptyValidCompany: args.allowEmptyValidCompany,
    preflight,
    e1,
    e2,
  });

  if (evidenceBlock.blocked) {
    invalidateEvidenceFile();
    console.error(
      JSON.stringify(
        {
          status: evidenceBlock.reason,
          populatedCompanyGate,
          message:
            evidenceBlock.reason === 'VALID_COMPANY_PREFLIGHT_FAILED'
              ? 'Valid company preflight failed; evidence was not written.'
              : 'E1 and E2 must return non-zero requested entity counts before evidence may be written.',
        },
        null,
        2,
      ),
    );
    process.exit(1);
  }

  const payload = {
    validatedAt: new Date().toISOString(),
    validationType: 'm5b-tally-error-shape-live',
    evidenceStatus: args.writeEvidence ? 'committed-candidate' : 'validation-only',
    connectorHost: '127.0.0.1',
    connectorPort: VALIDATION_PORT,
    tallyHost: '127.0.0.1',
    tallyPort: 9000,
    testEnvironment: process.platform,
    companyLabel: '[REDACTED]',
    runContext: buildPrivacySafeCompanyRunContext(requestedCompany),
    populatedCompanyGate,
    dataClassification: 'operator-controlled-privacy-safe',
    connectorVersion: config.connectorVersion,
    tallyReleaseScope: 'TallyPrime (operator-recorded localhost scope)',
    repeatRunPolicy: 'E1/E2 preflight first; disruptive E4 excluded unless explicitly flagged.',
    evidenceLimitations: [
      'Requested company context is supplied via XML only; harness cannot verify which company Tally has open.',
      'E4 invalid-report validation excluded from default and evidence-writing runs.',
      'Single localhost Tally instance; not a cross-version catalogue.',
      'E6 uses port 9001 with no listener — transport-equivalent to closed HTTP endpoint.',
    ],
    scenarios: scenarioEvidence,
    privacyReview: {
      rawXmlExcluded: true,
      businessNamesExcluded: true,
      ledgerOrItemNamesExcluded: true,
      guidAndBalanceExcluded: true,
      onlyStructuralMarkersReported: true,
    },
    summary: {
      scenariosCompleted: scenarioEvidence.length,
      allRepeatStable: scenarioEvidence.every((item) => item.repeatStable),
      includesDisruptiveE4: disruptiveProbeCompleted,
      lineErrorObservedIn: scenarioEvidence
        .filter((item) => item.runs.some((run) => run.result.lineErrorPresent))
        .map((item) => item.scenarioId),
      headerStatusObservedIn: scenarioEvidence
        .filter((item) => item.runs.some((run) => run.result.headerStatusPresent))
        .map((item) => item.scenarioId),
    },
  };

  const privacyViolations = assertEvidencePrivacySafe(payload);
  if (privacyViolations.length > 0) {
    invalidateEvidenceFile();
    console.error(JSON.stringify({ status: 'PRIVACY_FAIL', violations: privacyViolations }, null, 2));
    process.exit(1);
  }

  if (args.writeEvidence) {
    fs.mkdirSync(path.dirname(EVIDENCE_PATH), { recursive: true });
    fs.writeFileSync(EVIDENCE_PATH, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
  }

  console.log(JSON.stringify(payload, null, 2));

  if (!args.writeEvidence) {
    console.error(JSON.stringify({ populatedCompanyGate }, null, 2));
  }

  if (failed) {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
