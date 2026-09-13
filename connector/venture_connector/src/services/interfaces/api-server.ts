import type { Server } from 'node:http';

import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export interface ApiServerService extends ServiceLifecycle {
  getServer(): Server | null;
  getStatus(): ServiceStatus;
}
