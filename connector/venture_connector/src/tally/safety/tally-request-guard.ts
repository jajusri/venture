import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { TallyCircuitBreaker } from './tally-circuit-breaker.js';
import { TallyRequestAuditor } from './tally-request-auditor.js';
import { validateTallyRequestXml } from './xml-request-validator.js';
import { TallyCapability } from '../security/capabilities.js';
import {
  findApprovedOperationByRequest,
  toPolicyOperation,
} from '../registry/operation-registry.js';
import { findForbiddenOperation } from '../registry/forbidden-registry.js';
import { decidePolicy } from '../../erp/policy/policy-engine.js';

export interface TallyRequestGuardOptions {
  readonly config: ConnectorConfig;
  readonly logger: Logger;
  readonly auditor?: TallyRequestAuditor;
}

export interface GuardedRequestContext {
  readonly correlationId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly xml: string;
}

export interface TallyRuntimeLimits {
  readonly poolMaxConnections: number;
  readonly retryMaxAttempts: number;
  readonly minRequestIntervalMs: number;
  readonly maxRequestBytes: number;
  readonly maxResponseBytes: number;
  readonly circuitBreakerEnabled: boolean;
  readonly autoReconnect: boolean;
  readonly maxReconnectAttempts: number;
}

/**
 * Mandatory runtime controls. These are enforced regardless of SAFE_MODE and
 * cannot be weakened by configuration. SAFE_MODE may only make behaviour MORE
 * restrictive (currently: a longer minimum inter-request interval).
 *
 * This closes review finding W3 ("SAFE_MODE=false as a master off-switch"):
 *  - single-flight concurrency is always enforced (poolMaxConnections = 1)
 *  - automatic business retries are always disabled (retryMaxAttempts = 1)
 *  - the circuit breaker is always enabled
 *  - automatic reconnect storms are always disabled
 */
const MANDATORY_LIMITS = {
  poolMaxConnections: 1 as const,
  retryMaxAttempts: 1 as const,
  circuitBreakerEnabled: true as const,
  autoReconnect: false as const,
  maxReconnectAttempts: 0 as const,
};

/** Hard ceiling on request size that configuration can only lower, never raise. */
const HARD_MAX_REQUEST_BYTES = 262_144;

export function resolveTallyRuntimeLimits(config: ConnectorConfig): TallyRuntimeLimits {
  const minRequestIntervalMs = config.tallySafeMode
    ? Math.max(config.tallyMinRequestIntervalMs, 2_000)
    : config.tallyMinRequestIntervalMs;

  return {
    ...MANDATORY_LIMITS,
    minRequestIntervalMs,
    maxRequestBytes: Math.min(config.tallyMaxRequestBytes, HARD_MAX_REQUEST_BYTES),
    maxResponseBytes: config.tallyMaxResponseBytes,
  };
}

export class TallyRequestGuard {
  private readonly limits: TallyRuntimeLimits;
  private readonly circuitBreaker: TallyCircuitBreaker;
  private lastRequestFinishedAt = 0;
  private inFlight = false;
  private queue: Array<() => void> = [];
  lastRequest?: {
    correlationId: string;
    collectionId?: string;
    reportId?: string;
    sentAt: string;
    outcome?: string;
  };

  constructor(private readonly options: TallyRequestGuardOptions) {
    this.limits = resolveTallyRuntimeLimits(options.config);
    this.circuitBreaker = new TallyCircuitBreaker({
      failureThreshold: options.config.tallyCircuitBreakerFailureThreshold,
      cooldownMs: options.config.tallyCircuitBreakerCooldownMs,
    });
  }

  get circuitState() {
    return this.circuitBreaker.getState();
  }

  get runtimeLimits(): TallyRuntimeLimits {
    return this.limits;
  }

  async prepare(context: GuardedRequestContext): Promise<void> {
    if (this.limits.circuitBreakerEnabled) {
      try {
        this.circuitBreaker.assertRequestAllowed();
      } catch (error) {
        await this.audit(context, 'blocked', error);
        throw new AppError(
          ErrorCodes.SERVICE_UNAVAILABLE,
          error instanceof Error ? error.message : 'Tally circuit breaker open',
          503,
        );
      }
    }

    const validation = validateTallyRequestXml(context.xml, this.limits.maxRequestBytes);

    // MANDATORY fail-closed policy decision point. Runs for EVERY request that
    // reaches the transport chokepoint, regardless of caller or SAFE_MODE.
    const requestBytes = Buffer.byteLength(context.xml, 'utf8');
    const operation = findApprovedOperationByRequest(
      validation.requestKind,
      validation.collectionId,
    );
    const forbidden = findForbiddenOperation(validation.requestKind, validation.collectionId);
    const isHealthProbe = operation?.capability === TallyCapability.HealthRead;
    const policy = decidePolicy({
      operation: operation ? toPolicyOperation(operation) : undefined,
      forbidden: forbidden
        ? { reason: forbidden.reason, operationId: forbidden.tallyId }
        : undefined,
      requestBytes,
      circuitState: this.circuitBreaker.getState(),
      isHealthProbe,
    });

    if (policy.decision !== 'ALLOW') {
      await this.audit(context, 'blocked');
      const status = policy.decision === 'QUARANTINE' ? 503 : 403;
      throw new AppError(
        policy.decision === 'QUARANTINE'
          ? ErrorCodes.SERVICE_UNAVAILABLE
          : ErrorCodes.VALIDATION_ERROR,
        `Tally request denied by policy [${policy.decision}]: ${policy.reason}`,
        status,
        {
          decision: policy.decision,
          operationId: policy.operationId,
          classification: policy.classification,
          tallyId: validation.collectionId,
          requestKind: validation.requestKind,
        },
      );
    }

    await this.acquireSingleFlight();
    await this.enforceRateLimit();

    // AUDIT INTENT BEFORE TRANSPORT (Phase 8): the attempted request is recorded
    // even if Tally subsequently hangs and never returns.
    await this.audit(context, 'intent', undefined, {
      operationId: operation?.operationId,
      capability: operation?.capability,
      policyDecision: policy.decision,
    });

    this.lastRequest = {
      correlationId: context.correlationId,
      collectionId: context.collectionId,
      reportId: context.reportId,
      sentAt: new Date().toISOString(),
    };

    this.options.logger.info('Tally request prepared', {
      correlationId: context.correlationId,
      collectionId: context.collectionId ?? context.reportId,
      requestBytes: Buffer.byteLength(context.xml, 'utf8'),
      safeMode: this.options.config.tallySafeMode,
      circuitState: this.circuitBreaker.getState(),
    });
  }

  async recordSuccess(context: GuardedRequestContext, responseBytes: number): Promise<void> {
    if (responseBytes > this.limits.maxResponseBytes) {
      const error = new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        `Tally response exceeds maximum size (${this.limits.maxResponseBytes} bytes)`,
        503,
        { responseBytes, maxResponseBytes: this.limits.maxResponseBytes },
      );
      await this.recordFailure(context, error);
      throw error;
    }

    this.circuitBreaker.recordSuccess();
    this.lastRequest = { ...this.lastRequest!, outcome: 'success' };
    await this.audit(context, 'sent');
    if (this.inFlight) {
      this.releaseSingleFlight();
    }
  }

  async recordFailure(context: GuardedRequestContext, error: unknown): Promise<void> {
    const message = error instanceof Error ? error.message : String(error);
    if (this.limits.circuitBreakerEnabled) {
      this.circuitBreaker.recordFailure(message);
    }
    this.lastRequest = { ...this.lastRequest!, outcome: 'failed' };
    await this.audit(context, 'failed', error);
    if (this.inFlight) {
      this.releaseSingleFlight();
    }
  }

  private async audit(
    context: GuardedRequestContext,
    outcome: 'intent' | 'sent' | 'failed' | 'blocked',
    error?: unknown,
    meta?: { operationId?: string; capability?: string; policyDecision?: string },
  ): Promise<void> {
    if (!this.options.auditor) return;
    await this.options.auditor.record({
      timestamp: new Date().toISOString(),
      correlationId: context.correlationId,
      operationId: meta?.operationId,
      capability: meta?.capability,
      policyDecision: meta?.policyDecision,
      collectionId: context.collectionId,
      reportId: context.reportId,
      requestByteLength: Buffer.byteLength(context.xml, 'utf8'),
      circuitStateBefore: this.circuitBreaker.getState(),
      outcome,
      error,
      xml: context.xml,
    });
  }

  private async enforceRateLimit(): Promise<void> {
    const elapsed = Date.now() - this.lastRequestFinishedAt;
    const waitMs = this.limits.minRequestIntervalMs - elapsed;
    if (waitMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, waitMs));
    }
  }

  private async acquireSingleFlight(): Promise<void> {
    // Single-flight is a mandatory control; there is deliberately no bypass.
    if (!this.inFlight) {
      this.inFlight = true;
      return;
    }
    await new Promise<void>((resolve) => {
      this.queue.push(resolve);
    });
    this.inFlight = true;
  }

  private releaseSingleFlight(): void {
    this.lastRequestFinishedAt = Date.now();
    const next = this.queue.shift();
    if (next) {
      next();
      return;
    }
    this.inFlight = false;
  }
}
