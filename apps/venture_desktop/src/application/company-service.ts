import { mapDiscoveryUserMessage, mapSelectionUserMessage, toUserMessage } from './connector-error.js';
import { ConnectorHttpClient } from './connector-http-client.js';
import type {
  CompanyListResult,
  CompanySelectionOutcome,
  CompanySelectionStatus,
  ConnectorSessionDto,
} from './types.js';
import type { LogService } from './log-service.js';

export interface CompanyServiceOptions {
  readonly connectorBaseUrl: string;
  readonly fetchImpl?: typeof fetch;
  readonly logService: LogService;
  readonly maxAttempts?: number;
  readonly retryBaseDelayMs?: number;
}

export class CompanyService {
  private readonly client: ConnectorHttpClient;
  private readonly logService: LogService;

  constructor(options: CompanyServiceOptions) {
    this.client = new ConnectorHttpClient({
      baseUrl: options.connectorBaseUrl,
      fetchImpl: options.fetchImpl,
      maxAttempts: options.maxAttempts,
      retryBaseDelayMs: options.retryBaseDelayMs,
    });
    this.logService = options.logService;
  }

  async discoverCompanies(): Promise<CompanyListResult> {
    try {
      const result = await this.client.getCompanies();
      this.logService.append(
        'information',
        result.items.length > 0
          ? `Discovered ${result.items.length} companies`
          : `Company discovery returned ${result.status}`,
      );
      if (result.status !== 'SUCCESS' && result.status !== 'EMPTY' && result.status !== 'INCOMPLETE') {
        this.logService.append('warning', mapDiscoveryUserMessage(result.status, result.reason));
      }
      return result;
    } catch (error) {
      const message = toUserMessage(error);
      this.logService.append('error', `Company discovery failed: ${message}`);
      throw error;
    }
  }

  async selectCompany(companyId: string): Promise<CompanySelectionOutcome> {
    try {
      const result = await this.client.selectCompany(companyId);
      const userMessage = mapSelectionUserMessage(result.status, result.reason);
      const ok = result.status === 'SUCCESS' || result.status === 'DUPLICATE_SELECTION';

      this.logService.append(
        ok ? 'information' : 'warning',
        ok
          ? `Selected company: ${result.session.selectedCompany?.name ?? companyId}`
          : `Company selection failed: ${userMessage}`,
      );

      return {
        ok,
        status: result.status,
        userMessage,
        session: result.session,
      };
    } catch (error) {
      const userMessage = toUserMessage(error);
      this.logService.append('error', `Company selection failed: ${userMessage}`);
      return {
        ok: false,
        status: 'CONNECTOR_UNAVAILABLE',
        userMessage,
        session: null,
      };
    }
  }

  async clearSelection(): Promise<ConnectorSessionDto | null> {
    try {
      const result = await this.client.clearCompanySelection();
      this.logService.append('information', 'Cleared company selection');
      return result.session;
    } catch (error) {
      const message = toUserMessage(error);
      this.logService.append('error', `Clear company selection failed: ${message}`);
      throw error;
    }
  }

  static isSuccessfulSelection(status: CompanySelectionStatus | 'CONNECTOR_UNAVAILABLE'): boolean {
    return status === 'SUCCESS' || status === 'DUPLICATE_SELECTION';
  }
}
