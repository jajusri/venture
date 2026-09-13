import {
  AuditFileRotator,
  createNodeAuditFileOperations,
  type AuditFileOperations,
  type AuditLifecycleCode,
} from '../../tally/safety/audit-file-rotator.js';
import type { Logger } from '../../infrastructure/logging/logger.js';

/**
 * A structural-only, privacy-safe record of a Voucher sync failure -- the coarse
 * external bucket, the granular reasonCode, and whatever structural facts the failure
 * carried (which Tally operation, response byte length, a correlation-only response
 * hash, illegal-character sanitization telemetry, and -- for XML parse failures -- the
 * specific XmlParseError reason plus line/column/byteOffset-style facts). `details` is
 * exactly the AppError.details bag already threaded through voucher-extractor.ts, which
 * by construction never contains raw XML, narration, party names, amounts, or any other
 * business content -- only fixed enum strings and numbers.
 */
export interface VoucherSyncFailureAuditEntry {
  readonly timestamp: string;
  readonly runId: string;
  readonly companyId: string;
  readonly phase: string | null;
  readonly failureReason: string;
  readonly repositoryFailureCode: string | null;
  readonly details: Readonly<Record<string, unknown>> | null;
}

export interface VoucherSyncFailureAuditorOptions {
  readonly auditPath: string;
  readonly enabled: boolean;
  readonly maxBytes: number;
  readonly maxFiles: number;
  readonly logger: Logger;
  readonly fs?: AuditFileOperations;
}

export class VoucherSyncFailureAuditor {
  private writeChain: Promise<void> = Promise.resolve();
  private readonly rotator: AuditFileRotator | null;
  private rotatorPromise: Promise<AuditFileRotator> | null = null;

  constructor(private readonly options: VoucherSyncFailureAuditorOptions) {
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

  async record(entry: VoucherSyncFailureAuditEntry): Promise<void> {
    if (!this.options.enabled) return;
    this.writeChain = this.writeChain.then(() => this.writeEntry(entry)).catch(() => undefined);
    await this.writeChain;
  }

  private async getRotator(): Promise<AuditFileRotator> {
    if (this.rotator) return this.rotator;
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

  private async writeEntry(entry: VoucherSyncFailureAuditEntry): Promise<void> {
    const rotator = await this.getRotator();
    await rotator.appendLine(`${JSON.stringify(entry)}\n`);
  }

  private logLifecycleIssue(code: AuditLifecycleCode): void {
    try {
      this.options.logger.warn('voucher_sync_failure_audit_lifecycle', { code });
    } catch {
      // Logging is observational and must never affect synchronization.
    }
  }
}
