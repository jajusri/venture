import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { TallyXmlResponseParser } from '../../tally/xml/response-parser.js';

export interface XmlImportService extends ServiceLifecycle {
  getParser(): TallyXmlResponseParser;
  getStatus(): ServiceStatus;
}
