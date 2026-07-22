import { describe, expect, it } from 'vitest';

import { createLogger, type StructuredLogEntry } from '../../src/infrastructure/logging/logger.js';

describe('StructuredLogger', () => {
  it('writes structured JSON entries at or above configured level', () => {
    const entries: StructuredLogEntry[] = [];
    const logger = createLogger({
      service: 'test',
      level: 'warn',
      sink: (entry) => entries.push(entry),
    });

    logger.debug('hidden');
    logger.info('hidden');
    logger.warn('visible', { reason: 'test' });

    expect(entries).toHaveLength(1);
    expect(entries[0]).toMatchObject({
      level: 'warn',
      message: 'visible',
      service: 'test',
      context: { service: 'test', reason: 'test' },
    });
  });
});
