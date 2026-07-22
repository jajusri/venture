import type { Logger } from '../../infrastructure/logging/logger.js';
import { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';
import type { XmlImportService } from '../interfaces/xml-import.js';
import { PlaceholderService } from './base-placeholder.js';

export class XmlImportStub extends PlaceholderService implements XmlImportService {
  private readonly parser = new TallyXmlResponseParser();

  constructor(logger: Logger) {
    super('XmlImport', logger);
  }

  getParser(): TallyXmlResponseParser {
    return this.parser;
  }
}
