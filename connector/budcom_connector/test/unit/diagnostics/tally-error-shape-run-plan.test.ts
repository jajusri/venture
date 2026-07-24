import { describe, expect, it } from 'vitest';

import { scanTallyErrorShapeResponse } from '../../helpers/tally-error-shape-scanner.js';
import {
  DISRUPTIVE_INVALID_REPORT_SCENARIO,
  SAFE_DEFAULT_SCENARIOS,
  assessValidCompanyPreflight,
  isValidCompanyPreflightFailure,
  partitionPreflightAndFollowOnScenarios,
  resolveLiveScenarioPlan,
  shouldBlockEvidenceWrite,
  shouldStopAfterPreflightPhase,
} from '../../helpers/tally-error-shape-run-plan.js';

describe('tally error-shape run plan', () => {
  it('default validation run does not invoke E4', () => {
    const plan = resolveLiveScenarioPlan({
      runDisruptiveInvalidReport: false,
      writeEvidence: false,
    });
    expect(plan.scenarios).toEqual([...SAFE_DEFAULT_SCENARIOS]);
    expect(plan.includesDisruptiveE4).toBe(false);
  });

  it('write-evidence mode does not invoke E4', () => {
    const plan = resolveLiveScenarioPlan({
      runDisruptiveInvalidReport: false,
      writeEvidence: true,
    });
    expect(plan.scenarios).not.toContain(DISRUPTIVE_INVALID_REPORT_SCENARIO);
  });

  it('E4 requires the explicit disruptive flag', () => {
    expect(() =>
      resolveLiveScenarioPlan({
        scenarioFilter: 'E4',
        runDisruptiveInvalidReport: false,
        writeEvidence: false,
      }),
    ).toThrow(/requires --run-disruptive-invalid-report/i);

    const plan = resolveLiveScenarioPlan({
      runDisruptiveInvalidReport: true,
      writeEvidence: false,
    });
    expect(plan.scenarios.at(-1)).toBe(DISRUPTIVE_INVALID_REPORT_SCENARIO);
  });

  it('rejects evidence writing when disruptive E4 would be included', () => {
    expect(() =>
      resolveLiveScenarioPlan({
        runDisruptiveInvalidReport: true,
        writeEvidence: true,
      }),
    ).toThrow(/Evidence writing cannot include disruptive E4/i);
  });

  it('runs E1 and E2 before follow-on scenarios', () => {
    const { preflightScenarios, followOnScenarios } = partitionPreflightAndFollowOnScenarios([
      ...SAFE_DEFAULT_SCENARIOS,
    ]);
    expect(preflightScenarios).toEqual(['E1', 'E2']);
    expect(followOnScenarios).toEqual(['E3', 'E5', 'E6']);
  });

  it('detects valid-company preflight failure and stops later scenarios', () => {
    const failedE1 = scanTallyErrorShapeResponse({
      scenarioId: 'E1',
      requestKind: 'approved-rich-ledgers',
      transportStatus: null,
      responseByteLength: 0,
      durationMs: 1,
      correlationId: 'e1-fail',
      contractKind: 'ledger',
      transportErrorCode: 'TRANSPORT_OR_GATEWAY_FAILURE',
    });
    const okE2 = scanTallyErrorShapeResponse({
      scenarioId: 'E2',
      requestKind: 'approved-rich-stock-items',
      transportStatus: 200,
      responseByteLength: 100,
      rawXml: '<ENVELOPE></ENVELOPE>',
      durationMs: 1,
      correlationId: 'e2-ok',
      contractKind: 'stock',
    });

    const preflight = assessValidCompanyPreflight(failedE1, okE2);
    expect(preflight.status).toBe('VALID_COMPANY_PREFLIGHT_FAILED');
    expect(shouldStopAfterPreflightPhase(preflight)).toBe(true);
    expect(isValidCompanyPreflightFailure(failedE1)).toBe(true);
  });

  it('blocks evidence writing for zero-byte failed preflight state', () => {
    const failedE1 = scanTallyErrorShapeResponse({
      scenarioId: 'E1',
      requestKind: 'approved-rich-ledgers',
      transportStatus: null,
      responseByteLength: 0,
      durationMs: 1,
      correlationId: 'e1-zero',
      contractKind: 'ledger',
    });
    const failedE2 = scanTallyErrorShapeResponse({
      scenarioId: 'E2',
      requestKind: 'approved-rich-stock-items',
      transportStatus: null,
      responseByteLength: 0,
      durationMs: 1,
      correlationId: 'e2-zero',
      contractKind: 'stock',
    });
    const preflight = assessValidCompanyPreflight(failedE1, failedE2, {
      disruptiveProbeCompleted: true,
    });

    const blocked = shouldBlockEvidenceWrite({
      writeEvidence: true,
      allowEmptyValidCompany: false,
      preflight,
      e1: failedE1,
      e2: failedE2,
    });

    expect(blocked.blocked).toBe(true);
    expect(blocked.reason).toBe('VALID_COMPANY_PREFLIGHT_FAILED');
    expect(preflight.likelyTallyModalBlocking).toBe(true);
  });
});
