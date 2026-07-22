import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface TallyConnectionService extends ServiceLifecycle {
  ping(): Promise<boolean>;
  getStatus(): ServiceStatus;
}
