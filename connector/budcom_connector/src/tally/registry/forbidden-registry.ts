/**
 * Forbidden operation registry.
 *
 * These request signatures are permanently blocked. They are proven-dangerous
 * (deadlock TallyPrime's HTTP handler) or structurally write/execute-capable.
 * No configuration, environment, or build flag may re-enable them.
 */

export type ForbiddenReason = 'DEADLOCK_EVIDENCE' | 'WRITE_OR_EXECUTE' | 'UNRESOLVABLE';

export interface ForbiddenOperation {
  readonly requestKind: string;
  readonly tallyId: string;
  readonly reason: ForbiddenReason;
  readonly evidence: string;
}

/**
 * Historical payloads that hung Tally during live investigation, plus write
 * verbs that must never leave the connector. Kept forever as regression anchors.
 */
export const FORBIDDEN_OPERATIONS: readonly ForbiddenOperation[] = Object.freeze([
  {
    requestKind: 'COLLECTION',
    tallyId: 'List of Units',
    reason: 'DEADLOCK_EVIDENCE',
    evidence:
      'Live 2026-07-22: Export/Collection "List of Units" wedged Tally HTTP handler (flat CPU/memory, no response, timeout).',
  },
  {
    requestKind: 'OBJECT',
    tallyId: 'Stock Item',
    reason: 'DEADLOCK_EVIDENCE',
    evidence:
      'Live 2026-07-22: single Stock Item object export probe wedged Tally HTTP server.',
  },
]);

const FORBIDDEN_INDEX = new Set(
  FORBIDDEN_OPERATIONS.map((op) => `${op.requestKind.toUpperCase()}::${op.tallyId.toLowerCase()}`),
);

export function findForbiddenOperation(
  requestKind: string | undefined,
  tallyId: string | undefined,
): ForbiddenOperation | undefined {
  if (!requestKind || !tallyId) return undefined;
  const key = `${requestKind.toUpperCase()}::${tallyId.trim().toLowerCase()}`;
  if (!FORBIDDEN_INDEX.has(key)) return undefined;
  return FORBIDDEN_OPERATIONS.find(
    (op) =>
      op.requestKind.toUpperCase() === requestKind.toUpperCase() &&
      op.tallyId.toLowerCase() === tallyId.trim().toLowerCase(),
  );
}

export function isForbiddenOperation(
  requestKind: string | undefined,
  tallyId: string | undefined,
): boolean {
  return findForbiddenOperation(requestKind, tallyId) !== undefined;
}
