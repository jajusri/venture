import { describe, expect, it } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { loadConfig } from '../../../src/config/index.js';
import { ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyReadAdapter } from '../../../src/tally/adapter/tally-read-adapter.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { CompanyDiscoveryParser } from '../../../src/tally/discovery/company-discovery-parser.js';
import { GroupsParser } from '../../../src/tally/groups/groups-parser.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../../../src/tally/registry/operation-registry.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import {
  GROUP_CYCLE,
  GROUP_EMPTY_LIST,
  GROUP_MISSING_PARENT,
  GROUP_MULTIPLE_HIERARCHY,
  GROUP_ONE_ROOT,
  GROUP_UNEXPECTED_ENVELOPE,
} from '../../helpers/groups-fixtures.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import { SAMPLE_LICENSE_INFO_RESPONSE } from '../../helpers/mock-fetch.js';

async function makeAdapter(handler: (body: string) => { status?: number; body: string }) {
  const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
    const body = typeof init?.body === 'string' ? init.body : '';
    const result = handler(body);
    return new Response(result.body, {
      status: result.status ?? 200,
      headers: { 'content-type': 'text/xml' },
    });
  }) as unknown as typeof fetch;

  const config = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0 });
  const logger = createLogger({ service: 'test', level: 'error' });
  const responseParser = new TallyXmlResponseParser();
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
  const adapter = new TallyReadAdapter({
    gateway,
    responseParser,
    companyDiscoveryParser: new CompanyDiscoveryParser(responseParser),
    groupsParser: new GroupsParser(responseParser),
    logger,
  });
  return { adapter, connectionManager };
}

describe('TallyReadAdapter groups extraction', () => {
  it('returns SUCCESS with normalized domain models and no XML leakage', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Groups')) return { body: GROUP_MULTIPLE_HIERARCHY };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });

    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('SUCCESS');
    expect(result.tallyReachable).toBe(true);
    expect(result.items.length).toBeGreaterThan(0);
    expect(result.items[0]).toHaveProperty('isPrimary');
    expect(result).not.toHaveProperty('rawXml');
    await connectionManager.stop();
  });

  it('returns EMPTY for a valid empty group list', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Groups')) return { body: GROUP_EMPTY_LIST };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('EMPTY');
    expect(result.dataQuality?.status).toBe('EMPTY');
    await connectionManager.stop();
  });

  it('returns INCOMPLETE for hierarchy integrity failures', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Groups')) return { body: GROUP_CYCLE };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('INCOMPLETE');
    expect(result.hierarchyIssues?.length).toBeGreaterThan(0);
    await connectionManager.stop();
  });

  it('returns MALFORMED for unexpected response envelope', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Groups')) return { body: GROUP_UNEXPECTED_ENVELOPE };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('MALFORMED');
    await connectionManager.stop();
  });

  it('returns COMPANY_UNAVAILABLE when company name is empty', async () => {
    const { adapter, connectionManager } = await makeAdapter(() => ({
      body: GROUP_ONE_ROOT,
    }));
    const result = await adapter.getGroups('   ');
    expect(result.status).toBe('COMPANY_UNAVAILABLE');
    await connectionManager.stop();
  });

  it('returns UNAVAILABLE when transport fails', async () => {
    const fetchImpl = (async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const config = loadConfig({
      env: 'test',
      tallyMinRequestIntervalMs: 0,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    const logger = createLogger({ service: 'test', level: 'error' });
    const responseParser = new TallyXmlResponseParser();
    const connectionManager = new TallyConnectionManager({
      config,
      logger,
      transport: new TallyHttpTransport({ config, logger, fetchImpl }),
    });
    await connectionManager.start();
    const adapter = new TallyReadAdapter({
      gateway: new TallyReadGateway({
        connectionManager,
        requestBuilder: new TallyXmlRequestBuilder(),
        logger,
      }),
      responseParser,
      companyDiscoveryParser: new CompanyDiscoveryParser(responseParser),
      groupsParser: new GroupsParser(responseParser),
      logger,
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('UNAVAILABLE');
    await connectionManager.stop();
  });

  it('returns TIMEOUT when transport reports a timeout', async () => {
    const fetchImpl = (async () => {
      throw new Error('Request timed out after 50ms');
    }) as unknown as typeof fetch;
    const config = loadConfig({
      env: 'test',
      tallyMinRequestIntervalMs: 0,
      tallyCircuitBreakerFailureThreshold: 99,
    });
    const logger = createLogger({ service: 'test', level: 'error' });
    const responseParser = new TallyXmlResponseParser();
    const connectionManager = new TallyConnectionManager({
      config,
      logger,
      transport: new TallyHttpTransport({ config, logger, fetchImpl }),
    });
    await connectionManager.start();
    const adapter = new TallyReadAdapter({
      gateway: new TallyReadGateway({
        connectionManager,
        requestBuilder: new TallyXmlRequestBuilder(),
        logger,
      }),
      responseParser,
      companyDiscoveryParser: new CompanyDiscoveryParser(responseParser),
      groupsParser: new GroupsParser(responseParser),
      logger,
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('TIMEOUT');
    await connectionManager.stop();
  });

  it('uses only the approved LEDGER_GROUPS registry operation', async () => {
    const sent: string[] = [];
    const { adapter, connectionManager } = await makeAdapter((body) => {
      sent.push(body);
      return { body: GROUP_ONE_ROOT };
    });
    await adapter.getGroups('ESTIMATION');
    expect(sent[0]).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(sent[0]).toContain('<ID>List of Groups</ID>');
    expect(ApprovedOperationId.LedgerGroups).toBe('LEDGER_GROUPS');
    await connectionManager.stop();
  });

  it('returns INCOMPLETE for missing parent via adapter', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Groups')) return { body: GROUP_MISSING_PARENT };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });
    const result = await adapter.getGroups('ESTIMATION');
    expect(result.status).toBe('INCOMPLETE');
    await connectionManager.stop();
  });
});

describe('MasterDataService groups boundary', () => {
  it('uses ErpReadPort.getGroups only and surfaces explicit status', async () => {
    const { MasterDataServiceImpl } = await import(
      '../../../src/services/extraction/master-data.service.js'
    );
    const readPort = {
      isReady: () => true,
      getGroups: async () => ({
        contractVersion: '1' as const,
        status: 'SUCCESS' as const,
        tallyReachable: true,
        items: [
          {
            id: 'sales',
            name: 'Sales Accounts',
            parentName: 'Primary',
            isPrimary: true,
          },
        ],
      }),
    } as unknown as ErpReadPort;

    const service = new MasterDataServiceImpl(
      loadConfig({ env: 'test' }),
      readPort,
      {
        resolveName: async (id: string) => id,
      } as never,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
    );
    await service.start();
    const result = await service.getLedgerGroups('estimation', { page: 1, pageSize: 10 });
    expect(result.status).toBe('SUCCESS');
    expect(result.items[0]?.name).toBe('Sales Accounts');
    expect(result.contractVersion).toBe('1');
  });

  it('throws 403 when adapter reports DENIED', async () => {
    const { MasterDataServiceImpl } = await import(
      '../../../src/services/extraction/master-data.service.js'
    );
    const service = new MasterDataServiceImpl(
      loadConfig({ env: 'test' }),
      {
        isReady: () => true,
        getGroups: async () => ({
          contractVersion: '1' as const,
          status: 'DENIED' as const,
          tallyReachable: true,
          items: [],
          reason: 'policy denied',
        }),
      } as unknown as ErpReadPort,
      { resolveName: async () => 'ESTIMATION' } as never,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
    );
    await service.start();
    await expect(
      service.getLedgerGroups('estimation', { page: 1, pageSize: 10 }),
    ).rejects.toMatchObject({
      statusCode: 403,
      code: ErrorCodes.VALIDATION_ERROR,
    });
  });
});
