import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyRequestGuard } from '../../../src/tally/safety/tally-request-guard.js';

describe('TallyRequestGuard', () => {
  const builder = new TallyXmlRequestBuilder();
  const logger = createLogger({ service: 'test', level: 'error' });

  it('serializes requests in safe mode', async () => {
    const guard = new TallyRequestGuard({
      config: loadConfig({ env: 'test', tallySafeMode: true, tallyMinRequestIntervalMs: 50 }),
      logger,
    });
    const xml = builder.buildConnectivityCheck();
    const order: string[] = [];

    const first = guard.prepare({
      correlationId: 'first',
      collectionId: 'License Info',
      xml,
    });
    const second = guard
      .prepare({
        correlationId: 'second',
        collectionId: 'License Info',
        xml,
      })
      .then(() => order.push('second'));

    await first;
    order.push('first-prepared');
    await guard.recordSuccess({ correlationId: 'first', collectionId: 'License Info', xml }, 128);
    await second;

    expect(order).toEqual(['first-prepared', 'second']);
  });

  it('opens circuit after repeated failures in safe mode', async () => {
    const guard = new TallyRequestGuard({
      config: loadConfig({
        env: 'test',
        tallySafeMode: true,
        tallyCircuitBreakerFailureThreshold: 1,
      }),
      logger,
    });
    const xml = builder.buildConnectivityCheck();
    const context = { correlationId: 'a', collectionId: 'License Info', xml };

    await guard.prepare(context);
    await guard.recordFailure(context, new Error('fetch failed'));

    await expect(
      guard.prepare({ correlationId: 'b', collectionId: 'License Info', xml }),
    ).rejects.toMatchObject({ statusCode: 503 });
  });
});
