import { createHash } from 'node:crypto';

import type { Logger } from '../../infrastructure/logging/logger.js';
import {
  normalizeAuditErrorReason,
  type AuditErrorReasonCode,
} from '../../infrastructure/privacy/audit-error-normalizer.js';
import {
  AuditFileRotator,
  createNodeAuditFileOperations,
  type AuditFileOperations,
  type AuditLifecycleCode,
} from './audit-file-rotator.js';

export type TallyRequestOutcome = 'intent' | 'sent' | 'failed' | 'blocked';

export interface TallyRequestAuditEntry {
  readonly timestamp: string;
  readonly correlationId: string;
  readonly operationId?: string;
  readonly capability?: string;
  readonly policyDecision?: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly requestByteLength: number;
  /** Canonical SHA-256 of the exact request bytes, for forensic correlation. */
  readonly requestHash: string;
  readonly circuitStateBefore?: string;
  readonly outcome: TallyRequestOutcome;
  readonly errorReasonCode?: AuditErrorReasonCode;
}

export interface TallyRequestAuditorOptions {
  readonly auditPath: string;
  readonly enabled: boolean;
  readonly maxBytes: number;
  readonly maxFiles: number;
  readonly logger: Logger;
  readonly fs?: AuditFileOperations;
}

export class TallyRequestAuditor {
  private writeChain: Promise<void> = Promise.resolve();
  private readonly rotator: AuditFileRotator | null;
  private rotatorPromise: Promise<AuditFileRotator> | null = null;

  constructor(private readonly options: TallyRequestAuditorOptions) {
    if (options.fs) {
      this.rotator = options.enabled
        ? new AuditFileRotator(
            {
              basePath: options.auditPath,
              maxBytes: options.maxBytes,
              maxFiles: options.maxFiles,
            },
            options.fs,
            (code) => this.logLifecycleIssue(code),
          )
        : null;
    } else {
      this.rotator = null;
    }
  }

  async record(
    entry: Omit<TallyRequestAuditEntry, 'requestHash' | 'errorReasonCode'> & {
      readonly xml: string;
      readonly error?: unknown;
    },
  ): Promise<void> {
    if (!this.options.enabled) return;

    const record: TallyRequestAuditEntry = {
      timestamp: entry.timestamp,
      correlationId: entry.correlationId,
      operationId: entry.operationId,
      capability: entry.capability,
      policyDecision: entry.policyDecision,
      collectionId: entry.collectionId,
      reportId: entry.reportId,
      requestByteLength: entry.requestByteLength,
      requestHash: createHash('sha256').update(entry.xml, 'utf8').digest('hex'),
      circuitStateBefore: entry.circuitStateBefore,
      outcome: entry.outcome,
      errorReasonCode: normalizeAuditErrorReason(entry.outcome, entry.error),
    };

    this.writeChain = this.writeChain.then(() => this.writeRecord(record)).catch(() => undefined);
    await this.writeChain;
  }

  private async getRotator(): Promise<AuditFileRotator> {
    if (this.rotator) {
      return this.rotator;
    }
    if (!this.rotatorPromise) {
      this.rotatorPromise = createNodeAuditFileOperations().then(
        (fs) =>
          new AuditFileRotator(
            {
              basePath: this.options.auditPath,
              maxBytes: this.options.maxBytes,
              maxFiles: this.options.maxFiles,
            },
            fs,
            (code) => this.logLifecycleIssue(code),
          ),
      );
    }
    return this.rotatorPromise;
  }

  private async writeRecord(record: TallyRequestAuditEntry): Promise<void> {
    const rotator = await this.getRotator();
    await rotator.appendLine(`${JSON.stringify(record)}\n`);
  }

  private logLifecycleIssue(code: AuditLifecycleCode): void {
    this.options.logger.warn('tally_request_audit_lifecycle', {
      component: 'tally-request-auditor',
      reasonCode: code,
    });
  }
}
