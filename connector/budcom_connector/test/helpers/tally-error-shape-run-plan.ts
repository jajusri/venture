import type { ErrorShapeScanResult } from './tally-error-shape-scanner.js';
import { passesPopulatedCompanyGate } from './tally-error-shape-company-context.js';

export const SAFE_DEFAULT_SCENARIOS = ['E1', 'E2', 'E3', 'E5', 'E6'] as const;
export const DISRUPTIVE_INVALID_REPORT_SCENARIO = 'E4' as const;

export const DISRUPTIVE_INVALID_REPORT_WARNING =
  'WARNING: --run-disruptive-invalid-report may open a blocking Tally modal ' +
  '(Error in TDL / Could not find description) and stall the HTTP server until dismissed. ' +
  'Run only after safe scenarios succeed and never during evidence capture.';

export interface LiveScenarioPlanInput {
  readonly scenarioFilter?: string;
  readonly runDisruptiveInvalidReport: boolean;
  readonly writeEvidence: boolean;
}

export interface LiveScenarioPlan {
  readonly scenarios: readonly string[];
  readonly includesDisruptiveE4: boolean;
  readonly evidenceWritePermitted: boolean;
}

export interface ValidCompanyPreflightAssessment {
  readonly passed: boolean;
  readonly status: 'PASS' | 'VALID_COMPANY_PREFLIGHT_FAILED';
  readonly reasons: readonly string[];
  readonly likelyTallyModalBlocking: boolean;
}

export function resolveLiveScenarioPlan(input: LiveScenarioPlanInput): LiveScenarioPlan {
  if (input.scenarioFilter === DISRUPTIVE_INVALID_REPORT_SCENARIO && !input.runDisruptiveInvalidReport) {
    throw new Error(
      'E4 requires --run-disruptive-invalid-report because it may open a blocking Tally modal.',
    );
  }

  let scenarios: string[];
  if (input.scenarioFilter) {
    scenarios = [input.scenarioFilter];
  } else {
    scenarios = [...SAFE_DEFAULT_SCENARIOS];
    if (input.runDisruptiveInvalidReport) {
      scenarios = [...scenarios, DISRUPTIVE_INVALID_REPORT_SCENARIO];
    }
  }

  if (input.writeEvidence && scenarios.includes(DISRUPTIVE_INVALID_REPORT_SCENARIO)) {
    throw new Error('Evidence writing cannot include disruptive E4 invalid-report validation.');
  }

  return {
    scenarios,
    includesDisruptiveE4: scenarios.includes(DISRUPTIVE_INVALID_REPORT_SCENARIO),
    evidenceWritePermitted: input.writeEvidence,
  };
}

export function isValidCompanyPreflightFailure(result: ErrorShapeScanResult): boolean {
  return (
    result.transportStatus === null ||
    result.responseByteLength === 0 ||
    result.parserOutcome === 'not_applicable' ||
    !result.collectionPresent
  );
}

export function assessValidCompanyPreflight(
  e1: ErrorShapeScanResult | undefined,
  e2: ErrorShapeScanResult | undefined,
  options: { readonly disruptiveProbeCompleted?: boolean } = {},
): ValidCompanyPreflightAssessment {
  const reasons: string[] = [];

  if (!e1) reasons.push('E1 did not run.');
  if (!e2) reasons.push('E2 did not run.');

  if (e1 && isValidCompanyPreflightFailure(e1)) {
    reasons.push('E1 failed valid-company preflight.');
  }
  if (e2 && isValidCompanyPreflightFailure(e2)) {
    reasons.push('E2 failed valid-company preflight.');
  }

  const zeroBytePreflight =
    (e1?.responseByteLength === 0 || e2?.responseByteLength === 0) &&
    (isValidCompanyPreflightFailure(e1 ?? ({} as ErrorShapeScanResult)) ||
      isValidCompanyPreflightFailure(e2 ?? ({} as ErrorShapeScanResult)));

  const likelyTallyModalBlocking =
    zeroBytePreflight &&
    (options.disruptiveProbeCompleted === true ||
      e1?.transportErrorCode === 'TRANSPORT_OR_GATEWAY_FAILURE' ||
      e2?.transportErrorCode === 'TRANSPORT_OR_GATEWAY_FAILURE');

  return {
    passed: reasons.length === 0,
    status: reasons.length === 0 ? 'PASS' : 'VALID_COMPANY_PREFLIGHT_FAILED',
    reasons,
    likelyTallyModalBlocking,
  };
}

export function shouldStopAfterPreflightPhase(
  preflight: ValidCompanyPreflightAssessment,
): boolean {
  return !preflight.passed;
}

export function shouldBlockEvidenceWrite(input: {
  readonly writeEvidence: boolean;
  readonly allowEmptyValidCompany: boolean;
  readonly preflight: ValidCompanyPreflightAssessment;
  readonly e1: ErrorShapeScanResult | undefined;
  readonly e2: ErrorShapeScanResult | undefined;
}): { readonly blocked: boolean; readonly reason?: string } {
  if (!input.writeEvidence) {
    return { blocked: false };
  }

  if (!input.preflight.passed) {
    return { blocked: true, reason: 'VALID_COMPANY_PREFLIGHT_FAILED' };
  }

  if (
    !input.allowEmptyValidCompany &&
    (!input.e1 || !input.e2 || !passesPopulatedCompanyGate(input.e1, input.e2))
  ) {
    return { blocked: true, reason: 'POPULATED_COMPANY_GATE_BLOCKED' };
  }

  return { blocked: false };
}

export function partitionPreflightAndFollowOnScenarios(
  scenarios: readonly string[],
): { readonly preflightScenarios: readonly string[]; readonly followOnScenarios: readonly string[] } {
  const preflightScenarios: string[] = [];
  const followOnScenarios: string[] = [];

  for (const scenarioId of scenarios) {
    if (scenarioId === 'E1' || scenarioId === 'E2') {
      preflightScenarios.push(scenarioId);
    } else {
      followOnScenarios.push(scenarioId);
    }
  }

  return { preflightScenarios, followOnScenarios };
}
