import type { Server } from 'node:http';
import https from 'node:https';

import { createExpressApp } from '../../api/server.js';
import type { ConnectorTransportIdentityService } from '../transport/connector-transport-identity.js';
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
  private secureServer: https.Server | null = null;
  private running = false;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly logger: Logger,
    private readonly getDeps: () => ApiServerDeps,
    private readonly transportIdentity: ConnectorTransportIdentityService,
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
      transportIdentity: this.transportIdentity,
    });

    await new Promise<void>((resolve, reject) => {
      const server = app.listen(this.config.port, this.config.host, () => {
        server.off('error', reject);
        this.server = server;
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

    // Same Express app/routes as the HTTP listener above — this is deliberately not a second,
    // independent business-logic stack, just a second TLS-terminated entry point into it. Any
    // failure here (including a corrupt/mismatched persisted transport identity, surfaced by
    // getServerCredentials()) fails startup clearly rather than silently falling back to
    // HTTP-only. Since this is mid-flight inside this same start() call, the HTTP listener
    // opened just above would otherwise dangle uncleaned (the register-services.ts STARTUP_ORDER
    // unwind only closes services whose start() already *returned*, not one still throwing) —
    // so on failure here the HTTP listener is closed before rethrowing.
    if (this.config.secureTransportEnabled) {
      try {
        const credentials = await this.transportIdentity.getServerCredentials();
        await new Promise<void>((resolve, reject) => {
          const secureServer = https.createServer({ key: credentials.key, cert: credentials.cert }, app);
          secureServer.listen(this.config.secureTransportPort, this.config.host, () => {
            secureServer.off('error', reject);
            this.secureServer = secureServer;
            this.logger.info('secure API server listening', {
              host: this.config.host,
              port: this.config.secureTransportPort,
              networkExposure: this.config.networkExposure,
            });
            resolve();
          });
          secureServer.once('error', reject);
        });
      } catch (error) {
        await this.closeHttpListener();
        throw error;
      }
    }

    this.running = true;
  }

  async stop(): Promise<void> {
    if (!this.running) return;

    await this.closeHttpListener();
    await this.closeSecureListener();

    this.running = false;
    this.logger.info('API server stopped');
  }

  private async closeHttpListener(): Promise<void> {
    if (!this.server) return;
    await new Promise<void>((resolve, reject) => {
      this.server?.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        this.server = null;
        resolve();
      });
    });
  }

  private async closeSecureListener(): Promise<void> {
    if (!this.secureServer) return;
    await new Promise<void>((resolve, reject) => {
      this.secureServer?.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        this.secureServer = null;
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

  getSecureServer(): https.Server | null {
    return this.secureServer;
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
