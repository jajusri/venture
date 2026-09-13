import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { StorageStatus } from '../../erp/ledger/ledger-domain.js';

export interface LocalDatabaseService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
  getStorageStatus(): StorageStatus;
}
