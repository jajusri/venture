import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { TallyDiagnosticsSnapshot, TallyExchangeResult } from '../../tally/core/types.js';

export interface TallyConnectionService extends ServiceLifecycle {
  ping(): Promise<boolean>;
  exchange(
    xml: string,
    metadata?: { collectionId?: string; reportId?: string },
  ): Promise<TallyExchangeResult>;
  getDiagnostics(): TallyDiagnosticsSnapshot;
  getStatus(): ServiceStatus;
}
