import type { Logger } from '../../infrastructure/logging/logger.js';
import type { LicensingService } from '../interfaces/licensing.js';
import { PlaceholderService } from './base-placeholder.js';

export class LicensingStub extends PlaceholderService implements LicensingService {
  constructor(logger: Logger) {
    super('Licensing', logger);
  }
}
