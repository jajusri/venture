import type { Logger } from '../../infrastructure/logging/logger.js';
import type { SchedulerService } from '../interfaces/scheduler.js';
import { PlaceholderService } from './base-placeholder.js';

export class SchedulerStub extends PlaceholderService implements SchedulerService {
  constructor(logger: Logger) {
    super('Scheduler', logger);
  }
}
