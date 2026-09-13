import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface SchedulerService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
}
