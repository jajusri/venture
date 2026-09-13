import type { PolicyInput, PolicyOperation, PolicyResult } from './policy-types.js';

/**
 * The single mandatory, ERP-neutral policy decision point for every live ERP
 * request. It knows nothing about Tally, XML, capabilities names, or operation
 * ids — only the neutral {@link PolicyOperation} shape the adapter provides.
 *
 * Fail-closed guarantees:
 *  - UNKNOWN (unregistered) => DENY
 *  - FORBIDDEN => DENY
 *  - EXPERIMENTAL_DISABLED => DENY
 *  - No parameter, flag, or environment can convert a DENY into ALLOW.
 */
function deny(reason: string, op?: PolicyOperation): PolicyResult {
  return {
    decision: 'DENY',
    reason,
    operationId: op?.operationId,
    classification: op?.classification,
  };
}

export function decidePolicy(input: PolicyInput): PolicyResult {
  if (input.forbidden) {
    return {
      decision: 'DENY',
      reason: `FORBIDDEN operation (${input.forbidden.reason}): ${input.forbidden.operationId}`,
    };
  }

  const op = input.operation;
  if (!op) {
    return { decision: 'DENY', reason: 'UNKNOWN operation is not registered (UNKNOWN=DENY)' };
  }

  switch (op.classification) {
    case 'FORBIDDEN':
      return deny('Operation classified FORBIDDEN', op);
    case 'UNKNOWN':
      return deny('Operation classified UNKNOWN (UNKNOWN=DENY)', op);
    case 'EXPERIMENTAL_DISABLED':
      return deny('Operation is EXPERIMENTAL_DISABLED and cannot run in production', op);
    case 'CONDITIONAL':
      if (!op.autoApproveConditional) {
        return {
          decision: 'REQUIRE_MANUAL_APPROVAL',
          reason: 'CONDITIONAL operation requires explicit manual approval',
          operationId: op.operationId,
          classification: op.classification,
        };
      }
      break;
    case 'VERIFIED_SAFE':
      break;
    default:
      return deny('Unrecognized classification (fail closed)', op);
  }

  if (op.rolloutStatus !== 'production') {
    return deny(`Operation rollout status is ${op.rolloutStatus}`, op);
  }

  if (input.requestBytes > op.maxRequestBytes) {
    return deny(`Request size ${input.requestBytes}B exceeds max ${op.maxRequestBytes}B`, op);
  }

  switch (input.circuitState) {
    case 'open':
      return {
        decision: 'QUARANTINE',
        reason: 'Circuit is OPEN; business requests are blocked',
        operationId: op.operationId,
        classification: op.classification,
      };
    case 'half_open':
      if (input.isHealthProbe && op.isHealthProbe) {
        break;
      }
      return {
        decision: 'REQUIRE_MANUAL_APPROVAL',
        reason: 'Half-open circuit admits only the approved health probe',
        operationId: op.operationId,
        classification: op.classification,
      };
    case 'closed':
      break;
    default:
      return deny('Unknown circuit state (fail closed)', op);
  }

  return {
    decision: 'ALLOW',
    reason: 'Approved read operation permitted',
    operationId: op.operationId,
    classification: op.classification,
  };
}
