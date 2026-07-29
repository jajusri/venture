import { describe, expect, it, vi } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { ApprovedOperationId } from '../../../src/tally/registry/operation-registry.js';
import { SAMPLE_LICENSE_INFO_RESPONSE } from '../../helpers/mock-fetch.js';

async function makeGateway() {
  const sent: string[] = [];
  const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
    sent.push(typeof init?.body === 'string' ? init.body : '');
    return new Response(SAMPLE_LICENSE_INFO_RESPONSE, {
      status: 200,
      headers: { 'content-type': 'text/xml' },
    });
  }) as unknown as typeof fetch;

  const config = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0 });
  const logger = createLogger({ service: 'test', level: 'error' });
  const connectionManager = new TallyConnectionManager({
    config,
    logger,
    transport: new TallyHttpTransport({ config, logger, fetchImpl }),
  });
  await connectionManager.start();
  const gateway = new TallyReadGateway({
    connectionManager,
    requestBuilder: new TallyXmlRequestBuilder(),
    logger,
  });
  return { gateway, connectionManager, sent };
}

describe('TallyReadGateway', () => {
  it('accepts only strongly typed approved operation ids', async () => {
    const { gateway, connectionManager } = await makeGateway();
    await gateway.executeApprovedRead({ operationId: ApprovedOperationId.HealthCheck });
    await connectionManager.stop();
  });

  it('builds XML internally from the immutable contract', async () => {
    const { gateway, connectionManager, sent } = await makeGateway();
    await gateway.executeApprovedRead({ operationId: ApprovedOperationId.CompanyList });
    expect(sent[0]).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(sent[0]).toContain('<ID>List of Companies</ID>');
    await connectionManager.stop();
  });

  it('rejects an unknown operation id (fail closed) without transport', async () => {
    const { gateway, connectionManager, sent } = await makeGateway();
    await expect(
      // @ts-expect-error deliberately invalid operation id
      gateway.executeApprovedRead({ operationId: 'DROP_TABLE' }),
    ).rejects.toBeTruthy();
    expect(sent).toHaveLength(0);
    await connectionManager.stop();
  });

  it('exposes no generic raw-XML send method', () => {
    const proto = TallyReadGateway.prototype as unknown as Record<string, unknown>;
    expect(typeof proto.send).toBe('undefined');
    expect(typeof proto.exchange).toBe('undefined');
    expect(typeof proto.executeApprovedRead).toBe('function');
  });
});

describe('DI does not expose raw transport or bypass paths to application code', () => {
  it('has no service token for raw transport, connection manager, or TallyModule', async () => {
    const { ServiceTokens } = await import('../../../src/core/tokens.js');
    const tokenValues = Object.values(ServiceTokens);
    expect(tokenValues).not.toContain('Transport');
    expect(tokenValues).not.toContain('TallyTransport');
    expect(tokenValues).not.toContain('TallyConnectionManager');
    expect(tokenValues).not.toContain('TallyModule');
    expect(tokenValues).toContain('ErpReadPort');
  });

  it('TallyConnectionService implementation has no raw-XML exchange method', async () => {
    const { TallyConnectionServiceImpl } = await import(
      '../../../src/services/tally/tally-connection.service.js'
    );
    const proto = TallyConnectionServiceImpl.prototype as unknown as Record<string, unknown>;
    expect(typeof proto.exchange).toBe('undefined');
    expect(typeof proto.ping).toBe('function');
    vi.restoreAllMocks();
  });

  it('TallyModule exposes only ERP-neutral read ports and connectionManager', async () => {
    const { createTallyModule } = await import('../../../src/tally/tally-module.js');
    const mod = createTallyModule({
      config: loadConfig({ env: 'test' }),
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    expect('transport' in mod).toBe(false);
    expect('requestBuilder' in mod).toBe(false);
    expect('readGateway' in mod).toBe(false);
    expect('responseParser' in mod).toBe(false);
    expect('readPort' in mod).toBe(true);
    expect('voucherReadPort' in mod).toBe(true);
    expect('connectionManager' in mod).toBe(true);
    vi.restoreAllMocks();
  });
});
