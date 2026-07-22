import type { Logger } from '../../infrastructure/logging/logger.js';
import type { SyncEngineService } from '../interfaces/sync-engine.js';
import { PlaceholderService } from './base-placeholder.js';

export class SyncEngineStub extends PlaceholderService implements SyncEngineService {
  constructor(logger: Logger) {
    super('SyncEngine', logger);
  }
}
