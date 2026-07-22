import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface SyncEngineService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
}
