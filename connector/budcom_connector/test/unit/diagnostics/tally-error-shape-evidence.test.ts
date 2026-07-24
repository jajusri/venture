import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import {
  assertEvidencePrivacySafe,
  compareRepeatRuns,
  evaluateScenarioExpectation,
  scanTallyErrorShapeResponse,
} from '../../helpers/tally-error-shape-scanner.js';
import { validateEvidenceFile } from '../../helpers/tally-error-shape-evidence-validation.js';
import {
  passesPopulatedCompanyGate,
  resolveRequestedCompanyContext,
} from '../../helpers/tally-error-shape-company-context.js';
import {
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_LEDGER_GROUPS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import {
  SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
} from '../../helpers/inbound-xml-fixtures.js';

const EVIDENCE_PATH = path.resolve('../../docs/diagnostics/m5b-tally-error-shape-validation.json');

describe('tally error-shape scanner', () => {
  it('classifies synthetic LINEERROR as blocking ledger TALLY_ERROR', () => {
    const result = scanTallyErrorShapeResponse({
      scenarioId: 'fixture-lineerror',
      requestKind: 'synthetic',
      transportStatus: 200,
      responseByteLength: SYNTHETIC_TALLY_LINEERROR_RESPONSE.length,
      rawXml: SYNTHETIC_TALLY_LINEERROR_RESPONSE,
      durationMs: 1,
      correlationId: 'test-lineerror',
      contractKind: 'ledger',
    });
    expect(result.lineErrorPresent).toBe(true);
    expect(result.contractStatus).toBe('TALLY_ERROR');
    expect(result.contractReasonCode).toBe('tally_line_error');
  });

  it('does not treat standalone STATUS=0 without LINEERROR as TALLY_ERROR', () => {
    const result = scanTallyErrorShapeResponse({
      scenarioId: 'fixture-status-zero-only',
      requestKind: 'synthetic',
      transportStatus: 200,
      responseByteLength: SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE.length,
      rawXml: SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE,
      durationMs: 1,
      correlationId: 'test-status-zero',
      contractKind: 'ledger',
    });
    expect(result.lineErrorPresent).toBe(false);
    expect(result.contractStatus).not.toBe('TALLY_ERROR');
  });

  it('reports GROUP-only collection as unexpected entities for ledger contract', () => {
    const result = scanTallyErrorShapeResponse({
      scenarioId: 'fixture-groups',
      requestKind: 'synthetic',
      transportStatus: 200,
      responseByteLength: SAMPLE_LEDGER_GROUPS_RESPONSE.length,
      rawXml: SAMPLE_LEDGER_GROUPS_RESPONSE,
      durationMs: 1,
      correlationId: 'test-groups',
      contractKind: 'ledger',
    });
    expect(result.requestedEntityNodeCount).toBe(0);
    expect(result.unexpectedEntityNodeCount).toBeGreaterThan(0);
  });

  it('accepts committed ledger fixture with collection present', () => {
    const result = scanTallyErrorShapeResponse({
      scenarioId: 'fixture-ledgers',
      requestKind: 'synthetic',
      transportStatus: 200,
      responseByteLength: SAMPLE_LEDGERS_RESPONSE.length,
      rawXml: SAMPLE_LEDGERS_RESPONSE,
      durationMs: 1,
      correlationId: 'test-ledgers',
      contractKind: 'ledger',
    });
    expect(result.parserOutcome).toBe('accepted');
    expect(result.collectionPresent).toBe(true);
    expect(result.requestedEntityNodeCount).toBeGreaterThan(0);
  });

  it('detects stable repeat runs for identical fixture scans', () => {
    const input = {
      scenarioId: 'fixture-ledgers',
      requestKind: 'synthetic',
      transportStatus: 200,
      responseByteLength: SAMPLE_LEDGERS_RESPONSE.length,
      rawXml: SAMPLE_LEDGERS_RESPONSE,
      durationMs: 1,
      correlationId: 'repeat-a',
      contractKind: 'ledger' as const,
    };
    const first = scanTallyErrorShapeResponse(input);
    const second = scanTallyErrorShapeResponse({ ...input, correlationId: 'repeat-b' });
    expect(compareRepeatRuns(first, second).stable).toBe(true);
  });

  it('flags transport-only scenario with no XML as pass for E6 expectation', () => {
    const result = evaluateScenarioExpectation(
      scanTallyErrorShapeResponse({
        scenarioId: 'E6',
        requestKind: 'transport-unavailable-probe',
        transportStatus: null,
        responseByteLength: 0,
        durationMs: 2,
        correlationId: 'e6-test',
        contractKind: 'none',
        transportErrorCode: 'CONNECTION_REFUSED_OR_TIMEOUT',
      }),
      { expectTransportFailure: true },
    );
    expect(result.overallPass).toBe(true);
    expect(result.parserOutcome).toBe('not_applicable');
  });
});

describe('tally error-shape company context', () => {
  it('strips one surrounding bracket pair from env company names', () => {
    const context = resolveRequestedCompanyContext('[BUDCOM-TEST-01]');
    expect(context.normalizedName).toBe('BUDCOM-TEST-01');
    expect(context.bracketNormalized).toBe(true);
    expect(context.charLength).toBe(14);
  });

  it('keeps plain company names unchanged', () => {
    const context = resolveRequestedCompanyContext('BUDCOM-TEST-01');
    expect(context.normalizedName).toBe('BUDCOM-TEST-01');
    expect(context.bracketNormalized).toBe(false);
  });

  it('requires populated E1 and E2 entity counts for evidence gate', () => {
    expect(
      passesPopulatedCompanyGate({ requestedEntityNodeCount: 923 }, { requestedEntityNodeCount: 1502 }),
    ).toBe(true);
    expect(
      passesPopulatedCompanyGate({ requestedEntityNodeCount: 0 }, { requestedEntityNodeCount: 1502 }),
    ).toBe(false);
  });
});

describe('m5b tally error-shape evidence file', () => {
  it('passes privacy-safe validation when evidence file exists', () => {
    if (!fs.existsSync(EVIDENCE_PATH)) {
      expect(true).toBe(true);
      return;
    }
    const validation = validateEvidenceFile(EVIDENCE_PATH);
    expect(validation.valid, validation.errors.join('; ')).toBe(true);
    const payload = JSON.parse(fs.readFileSync(EVIDENCE_PATH, 'utf8'));
    expect(assertEvidencePrivacySafe(payload)).toEqual([]);
  });
});
