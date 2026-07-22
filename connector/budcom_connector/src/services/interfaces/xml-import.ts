import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface XmlImportService extends ServiceLifecycle {
  getStatus(): ServiceStatus;
}
