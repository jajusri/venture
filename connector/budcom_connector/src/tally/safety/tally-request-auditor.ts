import { appendFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname } from 'node:path';

import type { Logger } from '../../infrastructure/logging/logger.js';
import { redactTallyRequestXml } from './xml-request-validator.js';

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
  readonly errorMessage?: string;
  readonly redactedXml: string;
}

export class TallyRequestAuditor {
  constructor(
    private readonly auditPath: string,
    private readonly enabled: boolean,
    private readonly logger: Logger,
  ) {}

  async record(
    entry: Omit<TallyRequestAuditEntry, 'redactedXml' | 'requestHash'> & { readonly xml: string },
  ): Promise<void> {
    if (!this.enabled) return;

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
      errorMessage: entry.errorMessage,
      redactedXml: redactTallyRequestXml(entry.xml),
    };

    try {
      await mkdir(dirname(this.auditPath), { recursive: true });
      await appendFile(this.auditPath, `${JSON.stringify(record)}\n`, 'utf8');
    } catch (error) {
      this.logger.warn('Failed to write Tally request audit record', {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
}
