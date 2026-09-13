import type { Logger } from '../../infrastructure/logging/logger.js';
import type { LocalDatabaseService } from '../interfaces/local-database.js';
import { PlaceholderService } from './base-placeholder.js';

export class LocalDatabaseStub extends PlaceholderService implements LocalDatabaseService {
  constructor(logger: Logger) {
    super('LocalDatabase', logger);
  }

  getStorageStatus() {
    return {
      backend: 'sqlite' as const,
      schemaVersion: 0,
      databaseHealthy: this.isRunning(),
      migrationStatus: 'none' as const,
      message: this.isRunning() ? null : 'Storage is not running.',
    };
  }
}
