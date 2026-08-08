import type { ConnectorConfig } from '../../config/defaults.js';
import type { ServiceStatus } from '../../core/types.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import type { ConnectorSession } from '../../erp/session/connector-session.js';
import { ERP_TYPE_TALLY, SESSION_CONTRACT_VERSION } from '../../erp/session/session-constants.js';
import type {
  CompanySelectionResult,
  SessionValidationResult,
} from '../../erp/session/session-results.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type {
  ConnectorSessionService,
  ConnectorSessionSnapshot,
} from '../interfaces/connector-session.js';
import type { TallyConnectionService } from '../interfaces/tally-connection.js';
import type { CompanyResolver } from '../extraction/company-resolver.js';
import type { SelectedCompanyRepository } from './selected-company-repository.js';
import {
  createEmptySession,
  selectCompany,
  validateSession,
  withClearedSelection,
  withConnectionStatus,
  withRestoredSelection,
  type DiscoveredCompanyRef,
} from './session-validator.js';

export class ConnectorSessionServiceImpl implements ConnectorSessionService {
  private running = false;
  private session: ConnectorSession;

  constructor(
    private readonly config: ConnectorConfig,
    private readonly companyResolver: CompanyResolver,
    private readonly tallyConnection: TallyConnectionService,
    private readonly readPort: ErpReadPort,
    private readonly selectedCompanyRepository: SelectedCompanyRepository,
    private readonly logger: Logger,
  ) {
    this.session = createEmptySession({
      connectorVersion: config.connectorVersion,
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'disconnected',
      nowMs: Date.now(),
    });
  }

  async start(): Promise<void> {
    this.running = true;
    // Restore durable selection (TD-013) before this service is reachable for session
    // validation, so a Desktop/Connector restart never serves a "ready" state that has not
    // yet restored a legitimately persisted company selection.
    const restored = this.selectedCompanyRepository.load();
    if (restored) {
      this.session = withRestoredSelection(this.session, restored.company, restored.selectedAt);
    }
    this.session = withConnectionStatus(this.session, this.resolveConnectionStatus());
    this.logger.info('Connector session service started', {
      sessionId: this.session.sessionId,
      companySelectionRestored: restored !== null,
    });
  }

  async stop(): Promise<void> {
    this.running = false;
    // A normal stop is not a deselection: durable selection (TD-013) must survive an ordinary
    // Desktop/Connector restart. Only the connection status changes here.
    this.session = withConnectionStatus(this.session, 'disconnected');
    this.logger.info('Connector session service stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getSession(): ConnectorSessionSnapshot {
    this.assertRunning();
    return this.snapshot(this.session);
  }

  async selectCompany(companyId: string): Promise<CompanySelectionResult> {
    this.assertRunning();
    this.companyResolver.invalidateCache();
    const context = await this.loadSelectionContext();
    const result = selectCompany({
      session: this.session,
      companyId,
      discoveredCompanies: context.companies,
      tallyReachable: context.tallyReachable,
      discoveryAvailable: context.discoveryAvailable,
      connectorConnected: context.connectorConnected,
      nowMs: Date.now(),
    });

    if (result.status === 'SUCCESS') {
      this.session = result.session;
      if (this.session.selectedCompany && this.session.selectedAt) {
        this.selectedCompanyRepository.save(this.session.selectedCompany, this.session.selectedAt);
      }
      this.logger.info('Company selected for connector session', {
        sessionId: this.session.sessionId,
        companyId: this.session.selectedCompany?.id,
      });
    }

    return result;
  }

  clearSelection(): ConnectorSessionSnapshot {
    this.assertRunning();
    this.session = withClearedSelection(this.session);
    this.selectedCompanyRepository.clear();
    this.companyResolver.invalidateCache();
    this.logger.info('Connector session company selection cleared', {
      sessionId: this.session.sessionId,
    });
    return this.snapshot(this.session);
  }

  async validateForOperation(requestedCompanyId?: string): Promise<SessionValidationResult> {
    this.assertRunning();
    return this.runValidation(requestedCompanyId);
  }

  async refreshValidation(): Promise<SessionValidationResult> {
    this.assertRunning();
    return this.runValidation(this.session.selectedCompany?.id);
  }

  getStatus(): ServiceStatus {
    return {
      name: 'ConnectorSession',
      running: this.running,
      ready: this.running,
      message: this.running
        ? this.session.selectedCompany
          ? `Selected company: ${this.session.selectedCompany.name}`
          : 'No company selected'
        : 'Stopped',
    };
  }

  private async runValidation(requestedCompanyId?: string): Promise<SessionValidationResult> {
    const context = await this.loadSelectionContext();
    this.session = withConnectionStatus(this.session, context.connectionStatus);

    const result = validateSession({
      session: this.session,
      requestedCompanyId,
      discoveredCompanies: context.companies,
      tallyReachable: context.tallyReachable,
      discoveryAvailable: context.discoveryAvailable,
      connectorConnected: context.connectorConnected,
      sessionTtlMs: this.config.sessionTtlMs,
      nowMs: Date.now(),
    });

    if (result.status === 'SUCCESS') {
      this.session = result.session;
    }

    return result;
  }

  private async loadSelectionContext(): Promise<{
    readonly companies: readonly DiscoveredCompanyRef[];
    readonly tallyReachable: boolean;
    readonly discoveryAvailable: boolean;
    readonly connectorConnected: boolean;
    readonly connectionStatus: ConnectorSession['connectionStatus'];
  }> {
    const connectorConnected = this.readPort.isReady();
    const connectionStatus = this.resolveConnectionStatus();

    try {
      const discovery = await this.companyResolver.getDiscoverySnapshot();
      return {
        companies: discovery.companies,
        tallyReachable: discovery.tallyReachable,
        discoveryAvailable: true,
        connectorConnected,
        connectionStatus,
      };
    } catch {
      this.companyResolver.invalidateCache();
      const pingOk = await this.tallyConnection.ping().catch(() => false);
      return {
        companies: [],
        tallyReachable: pingOk,
        discoveryAvailable: false,
        connectorConnected,
        connectionStatus,
      };
    }
  }

  private resolveConnectionStatus(): ConnectorSession['connectionStatus'] {
    if (!this.readPort.isReady()) {
      return 'disconnected';
    }

    const diagnostics = this.tallyConnection.getDiagnostics();
    if (diagnostics.circuitState === 'open' || diagnostics.failedRequests > 0) {
      return 'degraded';
    }

    return 'connected';
  }

  private snapshot(session: ConnectorSession): ConnectorSessionSnapshot {
    return {
      session,
      contractVersion: SESSION_CONTRACT_VERSION,
    };
  }

  private assertRunning(): void {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Connector session service is not running',
        503,
      );
    }
  }
}
