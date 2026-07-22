import type { Logger } from '../../infrastructure/logging/logger.js';
import type { TallyConnectionService } from '../interfaces/tally-connection.js';
import { PlaceholderService } from './base-placeholder.js';

export class TallyConnectionStub extends PlaceholderService implements TallyConnectionService {
  constructor(logger: Logger) {
    super('TallyConnection', logger);
  }

  async ping(): Promise<boolean> {
    return false;
  }
}
