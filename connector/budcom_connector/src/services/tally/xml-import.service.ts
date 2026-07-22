import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ServiceStatus } from '../../core/types.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import type { XmlImportService } from '../interfaces/xml-import.js';

export class XmlImportServiceImpl implements XmlImportService {
  private running = false;

  constructor(
    private readonly parser: TallyXmlResponseParser,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    this.running = true;
    this.logger.info('XML import framework started');
  }

  async stop(): Promise<void> {
    this.running = false;
    this.logger.info('XML import framework stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getParser(): TallyXmlResponseParser {
    return this.parser;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'XmlImport',
      running: this.running,
      ready: this.running,
      message: 'XML parser framework ready',
    };
  }
}
