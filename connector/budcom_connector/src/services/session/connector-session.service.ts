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
import {
  createEmptySession,
  selectCompany,
  validateSession,
  withClearedSelection,
  withConnectionStatus,
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
    this.session = withConnectionStatus(this.session, this.resolveConnectionStatus());
    this.logger.info('Connector session service started', {
      sessionId: this.session.sessionId,
    });
  }

  async stop(): Promise<void> {
    this.running = false;
    this.session = withClearedSelection(
      withConnectionStatus(this.session, 'disconnected'),
    );
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
    const context = await this.loadSelectionContext();
    const result = selectCompany({
      session: this.session,
      companyId,
      discoveredCompanies: context.companies,
      tallyReachable: context.tallyReachable,
      connectorConnected: context.connectorConnected,
      nowMs: Date.now(),
    });

    if (result.status === 'SUCCESS') {
      this.session = result.session;
      this.logger.info('Company selected for connector session', {
        sessionId: this.session.sessionId,
        companyId: this.session.selectedCompany?.id,
        companyName: this.session.selectedCompany?.name,
      });
    }

    return result;
  }

  clearSelection(): ConnectorSessionSnapshot {
    this.assertRunning();
    this.session = withClearedSelection(this.session);
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
        connectorConnected,
        connectionStatus,
      };
    } catch {
      const pingOk = await this.tallyConnection.ping().catch(() => false);
      return {
        companies: [],
        tallyReachable: pingOk,
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
