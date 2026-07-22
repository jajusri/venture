import { appendFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

import type { Logger } from '../../infrastructure/logging/logger.js';
import { redactTallyRequestXml } from './xml-request-validator.js';

export interface TallyRequestAuditEntry {
  readonly timestamp: string;
  readonly correlationId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly requestByteLength: number;
  readonly outcome: 'sent' | 'failed' | 'blocked';
  readonly errorMessage?: string;
  readonly redactedXml: string;
}

export class TallyRequestAuditor {
  constructor(
    private readonly auditPath: string,
    private readonly enabled: boolean,
    private readonly logger: Logger,
  ) {}

  async record(entry: Omit<TallyRequestAuditEntry, 'redactedXml'> & { readonly xml: string }): Promise<void> {
    if (!this.enabled) return;

    const record: TallyRequestAuditEntry = {
      timestamp: entry.timestamp,
      correlationId: entry.correlationId,
      collectionId: entry.collectionId,
      reportId: entry.reportId,
      requestByteLength: entry.requestByteLength,
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
