import type { Server } from 'node:http';

import { createExpressApp } from '../../api/server.js';
import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { CompanyDiscoveryService } from '../interfaces/company-discovery.js';
import type { HealthService } from '../health/health-service.js';
import type { TallyDiagnosticsService } from '../interfaces/tally-diagnostics.js';
import type { ConnectorSessionService } from '../interfaces/connector-session.js';
import type { MasterDataService } from '../extraction/master-data.service.js';
import type { LedgerSyncService } from '../ledger/ledger-sync.service.js';
import type { StockItemSyncService } from '../stock-item/stock-item-sync.service.js';
import type { ApiServerService } from '../interfaces/api-server.js';
import type { TrustedDeviceRepository } from '../device/trusted-device-repository.js';
import type { ConnectorIdentityRepository } from '../identity/connector-identity-repository.js';
import type { PairingSessionRepository } from '../pairing/pairing-session-repository.js';
import type { PairingDeviceCredentialRepository } from '../pairing/pairing-device-credential-repository.js';
import type {
  VoucherApplicationService,
  VoucherSnapshotSyncService,
} from '../voucher/voucher-application.interface.js';

export interface ApiServerDeps {
  readonly healthService: HealthService;
  readonly companyDiscovery: CompanyDiscoveryService;
  readonly connectorSession: ConnectorSessionService;
  readonly masterData: MasterDataService;
  readonly ledgerSync: LedgerSyncService;
  readonly stockItemSync: StockItemSyncService;
  readonly tallyDiagnostics: TallyDiagnosticsService;
  readonly voucherApplication: VoucherApplicationService;
  readonly voucherSynchronization?: VoucherSnapshotSyncService;
  readonly trustedDevices?: TrustedDeviceRepository;
  readonly connectorIdentity: ConnectorIdentityRepository;
  readonly pairingSessions?: PairingSessionRepository;
  readonly pairingCredentials?: PairingDeviceCredentialRepository;
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
      config: this.config,
      healthService: deps.healthService,
      companyDiscovery: deps.companyDiscovery,
      connectorSession: deps.connectorSession,
      masterData: deps.masterData,
      ledgerSync: deps.ledgerSync,
      stockItemSync: deps.stockItemSync,
      tallyDiagnostics: deps.tallyDiagnostics,
      voucherApplication: deps.voucherApplication,
      voucherSynchronization: deps.voucherSynchronization,
      trustedDevices: deps.trustedDevices,
      connectorIdentity: deps.connectorIdentity,
      pairingSessions: deps.pairingSessions,
      pairingCredentials: deps.pairingCredentials,
    });

    await new Promise<void>((resolve, reject) => {
      const server = app.listen(this.config.port, this.config.host, () => {
        server.off('error', reject);
        this.server = server;
        this.running = true;
        this.logger.info('API server listening', {
          host: this.config.host,
          port: this.config.port,
          networkExposure: this.config.networkExposure,
        });
        if (this.config.networkExposureWarning) {
          this.logger.warn('connector_network_exposed', {
            host: this.config.host,
            port: this.config.port,
            message: this.config.networkExposureWarning,
          });
        }
        resolve();
      });
      server.once('error', reject);
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
