import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';

export abstract class PlaceholderService implements ServiceLifecycle {
  protected running = false;

  constructor(
    protected readonly name: string,
    protected readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    this.running = true;
    this.logger.info(`${this.name} placeholder started`);
  }

  async stop(): Promise<void> {
    this.running = false;
    this.logger.info(`${this.name} placeholder stopped`);
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: this.name,
      running: this.running,
      ready: this.running,
      message: 'Placeholder — not implemented',
    };
  }
}
