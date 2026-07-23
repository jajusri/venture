import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../../core/types.js';
import type {
  LedgerChange,
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
  LedgerSyncProgress,
  LedgerSyncResult,
} from '../../erp/ledger/ledger-domain.js';
import { mapNormalizedLedgerToDomain } from '../../erp/ledger/ledger-mapper.js';
import { validateLedgerCollection } from '../../erp/ledger/ledger-validation.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import type { CompanyResolver } from '../extraction/company-resolver.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import type { SyncEngineService } from '../interfaces/sync-engine.js';
import { assertSessionValidation } from '../session/session-error-mapper.js';
import { LedgerRepository } from './ledger-repository.js';

export interface LedgerSyncService extends SyncEngineService {
  getLedgers(params: LedgerSearchParams): Promise<LedgerSearchResult>;
  getLedgerById(ledgerId: string): Promise<LedgerDetails | null>;
  getStatistics(): Promise<LedgerStatistics>;
  getSyncProgress(): LedgerSyncProgress;
  syncLedgers(options?: { incremental?: boolean; maxAttempts?: number }): Promise<LedgerSyncResult>;
  cancelSync(): Promise<LedgerSyncProgress>;
  clearCache(): Promise<void>;
}

export class LedgerSyncServiceImpl implements LedgerSyncService {
  private running = false;
  private readonly repository: LedgerRepository;
  private progress: LedgerSyncProgress = createIdleProgress();
  private cancelRequested = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly readPort: ErpReadPort,
    private readonly companyResolver: CompanyResolver,
    private readonly connectorSession: ConnectorSessionService,
    private readonly logger: Logger,
    repository?: LedgerRepository,
  ) {
    this.repository =
      repository ??
      new LedgerRepository({
        basePath: `${config.databasePath}/ledgers`,
      });
  }

  async start(): Promise<void> {
    this.running = true;
  }

  async stop(): Promise<void> {
    this.cancelRequested = true;
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'LedgerSync',
      running: this.running,
      ready: this.running,
      message: this.progress.status,
    };
  }

  async getLedgers(params: LedgerSearchParams): Promise<LedgerSearchResult> {
    const companyId = await this.requireCompanyId();
    return this.repository.search(companyId, params);
  }

  async getLedgerById(ledgerId: string): Promise<LedgerDetails | null> {
    const companyId = await this.requireCompanyId();
    return this.repository.findById(companyId, ledgerId);
  }

  async getStatistics(): Promise<LedgerStatistics> {
    const companyId = await this.requireCompanyId();
    return this.repository.getStatistics(companyId);
  }

  getSyncProgress(): LedgerSyncProgress {
    return { ...this.progress };
  }

  async cancelSync(): Promise<LedgerSyncProgress> {
    this.cancelRequested = true;
    this.progress = {
      ...this.progress,
      cancelRequested: true,
      status: this.progress.status === 'running' ? 'cancelled' : this.progress.status,
    };
    return this.getSyncProgress();
  }

  async clearCache(): Promise<void> {
    const companyId = await this.requireCompanyId();
    await this.repository.clearCompany(companyId);
    this.logger.info('ledger_cache_cleared', { component: 'ledger-sync', companyId });
  }

  async syncLedgers(options: { incremental?: boolean; maxAttempts?: number } = {}): Promise<LedgerSyncResult> {
    const startedAt = Date.now();
    const companyId = await this.requireCompanyId();
    const companyName = await this.companyResolver.resolveName(companyId);
    const maxAttempts = options.maxAttempts ?? this.config.tallyRetryMaxAttempts;
    this.cancelRequested = false;
    this.progress = {
      status: 'running',
      startedAt: new Date(startedAt).toISOString(),
      completedAt: null,
      durationMs: null,
      itemsProcessed: 0,
      itemsAdded: 0,
      itemsUpdated: 0,
      itemsSkipped: 0,
      itemsFailed: 0,
      lastError: null,
      cancelRequested: false,
    };

    const changes: LedgerChange[] = [];

    try {
      const extraction = await this.extractWithRetry(companyName, maxAttempts);
      const syncedAt = new Date().toISOString();
      const mapped = extraction.items.map((item) => mapNormalizedLedgerToDomain(item, syncedAt));
      const validation = validateLedgerCollection(mapped);

      if (!validation.ok) {
        this.logger.warn('ledger_validation_issues', {
          component: 'ledger-sync',
          count: validation.issues.length,
        });
      }

      for (const ledger of mapped) {
        if (this.cancelRequested) {
          break;
        }

        this.progress = { ...this.progress, itemsProcessed: this.progress.itemsProcessed + 1 };
        const existing = await this.repository.findById(companyId, ledger.id);

        if (!existing) {
          await this.repository.insert(companyId, ledger);
          this.progress = { ...this.progress, itemsAdded: this.progress.itemsAdded + 1 };
          changes.push({ ledgerId: ledger.id, changeType: 'added' });
          continue;
        }

        if (options.incremental && !hasLedgerChanged(existing, ledger)) {
          this.progress = { ...this.progress, itemsSkipped: this.progress.itemsSkipped + 1 };
          changes.push({ ledgerId: ledger.id, changeType: 'skipped', reason: 'unchanged' });
          continue;
        }

        await this.repository.update(companyId, { ...ledger, syncedAt });
        this.progress = { ...this.progress, itemsUpdated: this.progress.itemsUpdated + 1 };
        changes.push({ ledgerId: ledger.id, changeType: 'updated' });
      }

      const finalStatus = this.cancelRequested ? 'cancelled' : 'completed';
      this.progress = {
        ...this.progress,
        status: finalStatus,
        completedAt: new Date().toISOString(),
        durationMs: Date.now() - startedAt,
      };

      const statistics = await this.repository.getStatistics(companyId);
      this.logger.info('ledger_sync_completed', {
        component: 'ledger-sync',
        durationMs: this.progress.durationMs,
        ledgerCount: statistics.totalLedgers,
        itemsProcessed: this.progress.itemsProcessed,
        itemsAdded: this.progress.itemsAdded,
        itemsUpdated: this.progress.itemsUpdated,
        itemsSkipped: this.progress.itemsSkipped,
        itemsFailed: this.progress.itemsFailed,
      });

      return {
        status: finalStatus,
        statistics,
        progress: this.getSyncProgress(),
        changes,
        validationIssueCount: validation.issues.length,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.progress = {
        ...this.progress,
        status: 'failed',
        lastError: message,
        completedAt: new Date().toISOString(),
        durationMs: Date.now() - startedAt,
      };
      this.logger.error('ledger_sync_failed', { component: 'ledger-sync', message });
      throw error instanceof AppError
        ? error
        : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, message, 503, { feature: 'ledger-sync' });
    }
  }

  private async extractWithRetry(companyName: string, maxAttempts: number) {
    let lastError: unknown;
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      try {
        return await this.readPort.readLedgers(companyName);
      } catch (error) {
        lastError = error;
        if (attempt >= maxAttempts) {
          break;
        }
        const delayMs = this.config.tallyRetryBaseDelayMs * attempt;
        this.logger.warn('ledger_sync_retry', {
          component: 'ledger-sync',
          attempt,
          delayMs,
        });
        await sleep(delayMs);
      }
    }
    throw lastError instanceof Error
      ? lastError
      : new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Ledger extraction failed after retries.', 503);
  }

  private async requireCompanyId(): Promise<string> {
    if (!this.running) {
      throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Ledger sync service is not running.', 503);
    }
    const snapshot = this.connectorSession.getSession();
    const validation = await this.connectorSession.validateForOperation(snapshot.session.selectedCompany?.id);
    assertSessionValidation(validation);
    if (!snapshot.session.selectedCompany?.id) {
      throw new AppError(ErrorCodes.VALIDATION_ERROR, 'No company selected.', 400);
    }
    return snapshot.session.selectedCompany.id;
  }
}

function createIdleProgress(): LedgerSyncProgress {
  return {
    status: 'idle',
    startedAt: null,
    completedAt: null,
    durationMs: null,
    itemsProcessed: 0,
    itemsAdded: 0,
    itemsUpdated: 0,
    itemsSkipped: 0,
    itemsFailed: 0,
    lastError: null,
    cancelRequested: false,
  };
}

function hasLedgerChanged(existing: LedgerDetails, next: LedgerDetails): boolean {
  return (
    existing.alterId !== next.alterId
    || existing.guid !== next.guid
    || existing.name !== next.name
    || existing.parentGroup !== next.parentGroup
    || existing.closingBalance?.amount !== next.closingBalance?.amount
    || existing.openingBalance?.amount !== next.openingBalance?.amount
    || existing.status !== next.status
  );
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
