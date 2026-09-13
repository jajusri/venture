import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface LicensingService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
}
