import type { Server } from 'node:http';

import { createExpressApp } from '../../api/server.js';
import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { CompanyDiscoveryService } from '../interfaces/company-discovery.js';
import type { HealthService } from '../health/health-service.js';
import type { TallyDiagnosticsService } from '../interfaces/tally-diagnostics.js';
import type { ApiServerService } from '../interfaces/api-server.js';

export interface ApiServerDeps {
  readonly healthService: HealthService;
  readonly companyDiscovery: CompanyDiscoveryService;
  readonly tallyDiagnostics: TallyDiagnosticsService;
}

export class ApiServerStub implements ApiServerService {
  private server: Server | null = null;
  private running = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly logger: Logger,
    private readonly getDeps: () => ApiServerDeps,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;

    const deps = this.getDeps();
    const app = createExpressApp({
      logger: this.logger,
      healthService: deps.healthService,
      companyDiscovery: deps.companyDiscovery,
      tallyDiagnostics: deps.tallyDiagnostics,
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
