import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface LocalDatabaseService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
}
