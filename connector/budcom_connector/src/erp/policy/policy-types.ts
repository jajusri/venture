/**
 * ERP-neutral policy contracts.
 *
 * This module is the security core. It MUST NOT depend on any ERP-specific
 * type, capability name, operation id, or request kind. Adapters (Tally, and
 * any future BUSY/SAP/Zoho/ERPNext adapter) translate their own operation
 * metadata into these neutral shapes before asking the policy engine to decide.
 */

export type PolicyDecision = 'ALLOW' | 'DENY' | 'QUARANTINE' | 'REQUIRE_MANUAL_APPROVAL';

export type CircuitState = 'closed' | 'open' | 'half_open';

export type OperationClassification =
  | 'VERIFIED_SAFE'
  | 'CONDITIONAL'
  | 'EXPERIMENTAL_DISABLED'
  | 'FORBIDDEN'
  | 'UNKNOWN';

/**
 * Neutral description of an operation the adapter is asking to execute. The
 * adapter is responsible for producing this from its own registry entry.
 */
export interface PolicyOperation {
  readonly operationId: string;
  readonly classification: OperationClassification;
  readonly rolloutStatus: 'production' | 'disabled';
  readonly maxRequestBytes: number;
  /** True when this operation is the adapter's dedicated minimal health probe. */
  readonly isHealthProbe: boolean;
  /**
   * True when a CONDITIONAL operation has been explicitly approved by the
   * adapter for automatic execution (never enabled by configuration).
   */
  readonly autoApproveConditional: boolean;
}

/** Neutral match for a permanently blocked operation. */
export interface ForbiddenMatch {
  readonly reason: string;
  readonly operationId: string;
}

export interface PolicyInput {
  /** Resolved approved operation, or undefined when unknown/unregistered. */
  readonly operation?: PolicyOperation;
  /** Matched forbidden operation, if any. */
  readonly forbidden?: ForbiddenMatch;
  readonly requestBytes: number;
  readonly circuitState: CircuitState;
  /** True only when THIS request is the dedicated minimal health probe. */
  readonly isHealthProbe: boolean;
}

export interface PolicyResult {
  readonly decision: PolicyDecision;
  readonly reason: string;
  readonly operationId?: string;
  readonly classification?: OperationClassification;
}
