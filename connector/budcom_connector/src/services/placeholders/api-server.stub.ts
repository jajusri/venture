import type { Server } from 'node:http';

import { createExpressApp } from '../../api/server.js';
import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { HealthService } from '../health/health-service.js';
import type { ApiServerService } from '../interfaces/api-server.js';

export class ApiServerStub implements ApiServerService {
  private server: Server | null = null;
  private running = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly logger: Logger,
    private readonly getHealthService: () => HealthService,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;

    const app = createExpressApp({
      logger: this.logger,
      healthService: this.getHealthService(),
    });

    await new Promise<void>((resolve) => {
      this.server = app.listen(this.config.port, this.config.host, () => {
        this.running = true;
        this.logger.info('API server listening', {
          host: this.config.host,
          port: this.config.port,
        });
        resolve();
      });
    });
  }

  async stop(): Promise<void> {
    if (!this.server || !this.running) return;

    await new Promise<void>((resolve, reject) => {
      this.server?.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        this.running = false;
        this.server = null;
        this.logger.info('API server stopped');
        resolve();
      });
    });
  }

  isRunning(): boolean {
    return this.running;
  }

  getServer(): Server | null {
    return this.server;
  }

  getStatus() {
    return {
      name: 'ApiServer',
      running: this.running,
      ready: this.running,
      message: this.running ? 'Listening' : 'Stopped',
    };
  }
}
