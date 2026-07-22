import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ServiceStatus } from '../../core/types.js';
import type { TallyConnectionManager } from '../../tally/connection/tally-connection-manager.js';
import type { TallyExchangeResult } from '../../tally/core/types.js';
import type { TallyConnectionService } from '../interfaces/tally-connection.js';

export class TallyConnectionServiceImpl implements TallyConnectionService {
  constructor(
    private readonly connectionManager: TallyConnectionManager,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    await this.connectionManager.start();
    this.logger.info('Tally connection service started');
  }

  async stop(): Promise<void> {
    await this.connectionManager.stop();
    this.logger.info('Tally connection service stopped');
  }

  isRunning(): boolean {
    return this.connectionManager.isRunning();
  }

  async ping(): Promise<boolean> {
    return this.connectionManager.ping();
  }

  async exchange(
    xml: string,
    metadata: { collectionId?: string; reportId?: string } = {},
  ): Promise<TallyExchangeResult> {
    return this.connectionManager.exchange(xml, metadata);
  }

  getDiagnostics() {
    return this.connectionManager.getDiagnostics();
  }

  getStatus(): ServiceStatus {
    const state = this.connectionManager.getState();
    const running = this.connectionManager.isRunning();
    const ready = running && (state === 'connected' || state === 'degraded');
    return {
      name: 'TallyConnection',
      running,
      ready,
      message: `State: ${state}`,
    };
  }
}
