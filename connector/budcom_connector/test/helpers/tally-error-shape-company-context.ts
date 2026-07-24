export interface RequestedCompanyContext {
  readonly supplied: boolean;
  readonly normalizedName: string;
  readonly bracketNormalized: boolean;
  readonly charLength: number;
}

/**
 * Resolve BUDCOM_TALLY_TEST_COMPANY for harness requests.
 * Strips a single surrounding [ ... ] pair when present so operators may paste
 * either bracketed display labels or plain names; Tally SVCURRENTCOMPANY uses
 * the normalized value only.
 */
export function resolveRequestedCompanyContext(rawEnv: string | undefined): RequestedCompanyContext {
  const trimmed = rawEnv?.trim() ?? '';
  if (!trimmed) {
    throw new Error(
      'Set BUDCOM_TALLY_TEST_COMPANY to the dedicated test company display name before running live scenarios.',
    );
  }

  let normalizedName = trimmed;
  let bracketNormalized = false;
  if (trimmed.startsWith('[') && trimmed.endsWith(']') && trimmed.length >= 2) {
    normalizedName = trimmed.slice(1, -1).trim();
    bracketNormalized = true;
  }

  if (!normalizedName) {
    throw new Error('BUDCOM_TALLY_TEST_COMPANY normalized to an empty company name.');
  }

  return {
    supplied: true,
    normalizedName,
    bracketNormalized,
    charLength: normalizedName.length,
  };
}

export function buildPrivacySafeCompanyRunContext(
  context: RequestedCompanyContext,
): Record<string, boolean | number> {
  return {
    requestedCompanyContextSupplied: context.supplied,
    requestedCompanyContextBracketNormalized: context.bracketNormalized,
    requestedCompanyContextCharLength: context.charLength,
    doesNotVerifyTallySelectedCompany: true,
  };
}

export function summarizePopulatedCompanyScenario(result: {
  readonly scenarioId: string;
  readonly responseByteLength: number;
  readonly requestedEntityNodeCount: number;
  readonly collectionPresent: boolean;
  readonly parserOutcome: string;
  readonly contractStatus?: string;
}): Record<string, string | number | boolean> {
  return {
    scenarioId: result.scenarioId,
    responseByteLength: result.responseByteLength,
    requestedEntityNodeCount: result.requestedEntityNodeCount,
    collectionPresent: result.collectionPresent,
    parserOutcome: result.parserOutcome,
    contractStatus: result.contractStatus ?? 'n/a',
  };
}

export function passesPopulatedCompanyGate(
  e1: { readonly requestedEntityNodeCount: number },
  e2: { readonly requestedEntityNodeCount: number },
): boolean {
  return e1.requestedEntityNodeCount > 0 && e2.requestedEntityNodeCount > 0;
}
