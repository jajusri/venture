import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../src/config/index.js';
import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { TallyConnectionManager } from '../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../src/tally/xml/request-builder.js';
import { SAMPLE_LICENSE_INFO_RESPONSE } from '../helpers/mock-fetch.js';

function countingFetch() {
  let calls = 0;
  const fetchImpl = (async () => {
    calls += 1;
    return new Response(SAMPLE_LICENSE_INFO_RESPONSE, {
      status: 200,
      headers: { 'content-type': 'text/xml' },
    });
  }) as unknown as typeof fetch;
  return { fetchImpl, getCalls: () => calls };
}

function envelope(tallyRequest: string, type: string, id: string): string {
  return `<ENVELOPE><HEADER><VERSION>1</VERSION><TALLYREQUEST>${tallyRequest}</TALLYREQUEST><TYPE>${type}</TYPE><ID>${id}</ID></HEADER></ENVELOPE>`;
}

async function makeManager(safeMode: boolean) {
  const { fetchImpl, getCalls } = countingFetch();
  const config = loadConfig({ env: 'test', tallySafeMode: safeMode, tallyMinRequestIntervalMs: 0 });
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
  return { manager, getCalls };
}

describe('security guard blocks forbidden egress before transport', () => {
  it('allows an approved health export and reaches transport', async () => {
    const { manager, getCalls } = await makeManager(true);
    await manager.exchange(new TallyXmlRequestBuilder().buildConnectivityCheck(), {
      collectionId: 'License Info',
    });
    expect(getCalls()).toBe(1);
    await manager.stop();
  });

  it('denies an unregistered collection before any transport call', async () => {
    const { manager, getCalls } = await makeManager(true);
    await expect(
      manager.exchange(envelope('Export', 'Collection', 'List of Zzz Nonexistent'), {
        collectionId: 'List of Zzz Nonexistent',
      }),
    ).rejects.toMatchObject({ statusCode: 403 });
    expect(getCalls()).toBe(0);
    await manager.stop();
  });

  it('denies the forbidden List of Units collection before transport', async () => {
    const { manager, getCalls } = await makeManager(true);
    await expect(
      manager.exchange(envelope('Export', 'Collection', 'List of Units'), {
        collectionId: 'List of Units',
      }),
    ).rejects.toMatchObject({ statusCode: 403 });
    expect(getCalls()).toBe(0);
    await manager.stop();
  });

  it('denies an IMPORT request before transport', async () => {
    const { manager, getCalls } = await makeManager(true);
    await expect(
      manager.exchange(envelope('Import Data', 'Data', 'Vouchers'), { collectionId: 'Vouchers' }),
    ).rejects.toMatchObject({ statusCode: 400 });
    expect(getCalls()).toBe(0);
    await manager.stop();
  });

  it('still blocks forbidden egress when SAFE_MODE is disabled', async () => {
    const { manager, getCalls } = await makeManager(false);
    await expect(
      manager.exchange(envelope('Export', 'Collection', 'List of Units'), {
        collectionId: 'List of Units',
      }),
    ).rejects.toMatchObject({ statusCode: 403 });
    await expect(
      manager.exchange(envelope('Execute', 'Function', 'DoThing'), { collectionId: 'DoThing' }),
    ).rejects.toBeTruthy();
    expect(getCalls()).toBe(0);
    await manager.stop();
  });
});
