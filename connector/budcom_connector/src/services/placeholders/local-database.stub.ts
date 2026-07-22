import type { Logger } from '../../infrastructure/logging/logger.js';
import type { LocalDatabaseService } from '../interfaces/local-database.js';
import { PlaceholderService } from './base-placeholder.js';

export class LocalDatabaseStub extends PlaceholderService implements LocalDatabaseService {
  constructor(logger: Logger) {
    super('LocalDatabase', logger);
  }
}
