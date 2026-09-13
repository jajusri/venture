import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../../../src/tally/registry/operation-registry.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { assertNoPrivatePreflightDetails } from '../../helpers/capability-preflight-helpers.js';
import { SAMPLE_LICENSE_INFO_RESPONSE } from '../../helpers/mock-fetch.js';

function envelope(tallyRequest: string, type: string, id: string): string {
  return `<ENVELOPE><HEADER><VERSION>1</VERSION><TALLYREQUEST>${tallyRequest}</TALLYREQUEST><TYPE>${type}</TYPE><ID>${id}</ID></HEADER></ENVELOPE>`;
}

async function makeManager() {
  let calls = 0;
  const fetchImpl = (async () => {
    calls += 1;
    return new Response(SAMPLE_LICENSE_INFO_RESPONSE, {
      status: 200,
      headers: { 'content-type': 'text/xml' },
    });
  }) as unknown as typeof fetch;
  const config = loadConfig({ env: 'test', tallySafeMode: true, tallyMinRequestIntervalMs: 0 });
  const manager = new TallyConnectionManager({
    config,
    logger: createLogger({ service: 'test', level: 'error' }),
    transport: new TallyHttpTransport({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      fetchImpl,
    }),
  });
  await manager.start();
  return { manager, getCalls: () => calls };
}

describe('capability preflight policy (1A-1C)', () => {
  it('1A denies unknown operation before transport with privacy-safe error', async () => {
    const { manager, getCalls } = await makeManager();
    try {
      await manager.exchange(envelope('Export', 'Collection', 'List of Zzz Nonexistent'), {
        collectionId: 'List of Zzz Nonexistent',
      });
      throw new Error('expected rejection');
    } catch (error) {
      expect(error).toMatchObject({ statusCode: 403, code: 'VALIDATION_ERROR' });
      assertNoPrivatePreflightDetails((error as { details?: unknown }).details);
    }
    expect(getCalls()).toBe(0);
    await manager.stop();
  });

  it('1B rejects write-capable IMPORT before transport', async () => {
    const { manager, getCalls } = await makeManager();
    await expect(
      manager.exchange(envelope('Import Data', 'Data', 'Vouchers'), { collectionId: 'Vouchers' }),
    ).rejects.toMatchObject({ statusCode: 400, code: 'VALIDATION_ERROR' });
    expect(getCalls()).toBe(0);
    await manager.stop();
  });

  it('1C allows approved read operation through guard to transport', async () => {
    const { manager, getCalls } = await makeManager();
    await manager.exchange(new TallyXmlRequestBuilder().buildConnectivityCheck(), {
      collectionId: 'License Info',
    });
    expect(getCalls()).toBe(1);
    await manager.stop();
  });
});

describe('capability preflight readiness (2A-2C)', () => {
  it('2A isReady reflects process readiness only while transport failure is typed separately', async () => {
    let calls = 0;
    const fetchImpl = (async () => {
      calls += 1;
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const config = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0 });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });
    await manager.start();
    expect(manager.isRunning()).toBe(true);

    const gateway = new TallyReadGateway({
      connectionManager: manager,
      requestBuilder: new TallyXmlRequestBuilder(),
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    expect(gateway.isReady()).toBe(true);

    await expect(
      gateway.executeApprovedRead({ operationId: ApprovedOperationId.HealthCheck }),
    ).rejects.toMatchObject({ statusCode: 503 });
    expect(calls).toBeGreaterThan(0);
    await manager.stop();
  });

  it('2B health ping success reports reachability without company or capability inference', async () => {
    const { manager } = await makeManager();
    const reachable = await manager.ping();
    expect(reachable).toBe(true);
    await manager.stop();
  });

  it('2C health ping failure returns false without throwing', async () => {
    const fetchImpl = (async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const config = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0 });
    const manager = new TallyConnectionManager({
      config,
      logger: createLogger({ service: 'test', level: 'error' }),
      transport: new TallyHttpTransport({
        config,
        logger: createLogger({ service: 'test', level: 'error' }),
        fetchImpl,
      }),
    });
    await manager.start();
    await expect(manager.ping()).resolves.toBe(false);
    await manager.stop();
  });
});
