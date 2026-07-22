import type { Logger } from '../../infrastructure/logging/logger.js';
import type { XmlImportService } from '../interfaces/xml-import.js';
import { PlaceholderService } from './base-placeholder.js';

export class XmlImportStub extends PlaceholderService implements XmlImportService {
  constructor(logger: Logger) {
    super('XmlImport', logger);
  }
}
