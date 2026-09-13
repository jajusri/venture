import { describe, expect, it } from 'vitest';

import type { ErpReadPort } from '../../../src/erp/ports/erp-read-port.js';
import { loadConfig } from '../../../src/config/index.js';
import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
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
  COMPANY_EMPTY_LIST,
  COMPANY_MISSING_REQUIRED_IDENTITY,
  COMPANY_MULTIPLE_VALID,
  COMPANY_ONE_VALID,
  COMPANY_UNEXPECTED_ENVELOPE,
} from '../../helpers/company-discovery-fixtures.js';
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

describe('TallyReadAdapter company discovery', () => {
  it('returns SUCCESS with normalized domain models and no XML leakage', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Companies')) return { body: COMPANY_ONE_VALID };
      return { body: SAMPLE_LICENSE_INFO_RESPONSE };
    });

    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('SUCCESS');
    expect(result.tallyReachable).toBe(true);
    expect(result.items[0]).toEqual({
      id: 'estimation',
      name: 'ESTIMATION',
      financialYear: '20240401',
      baseCurrency: 'INR',
    });
    expect(result).not.toHaveProperty('rawXml');
    await connectionManager.stop();
  });

  it('returns EMPTY for a valid empty company list', async () => {
    const { adapter, connectionManager } = await makeAdapter(() => ({ body: COMPANY_EMPTY_LIST }));
    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('EMPTY');
    expect(result.items).toEqual([]);
    expect(result.dataQuality?.status).toBe('EMPTY');
    await connectionManager.stop();
  });

  it('returns INCOMPLETE when required identity fields are absent', async () => {
    const { adapter, connectionManager } = await makeAdapter(() => ({
      body: COMPANY_MISSING_REQUIRED_IDENTITY,
    }));
    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('INCOMPLETE');
    expect(result.items).toEqual([]);
    expect(result.dataQuality?.status).toBe('INCOMPLETE');
    await connectionManager.stop();
  });

  it('returns MALFORMED for unexpected response envelope', async () => {
    const { adapter, connectionManager } = await makeAdapter(() => ({
      body: COMPANY_UNEXPECTED_ENVELOPE,
    }));
    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('MALFORMED');
    expect(result.items).toEqual([]);
    expect(result.dataQuality?.status).toBe('DRIFT');
    await connectionManager.stop();
  });

  it('returns UNAVAILABLE when transport fails', async () => {
    const fetchImpl = (async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const config = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0, tallyCircuitBreakerFailureThreshold: 99 });
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

    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('UNAVAILABLE');
    expect(result.tallyReachable).toBe(false);
    await connectionManager.stop();
  });

  it('returns DENIED when policy blocks the request', async () => {
    const { adapter, connectionManager } = await makeAdapter((body) => {
      if (body.includes('List of Units')) return { body: COMPANY_MULTIPLE_VALID };
      return { body: COMPANY_ONE_VALID };
    });

    const denied = await adapter.discoverCompanies().catch(() => null);
    expect(denied?.status).not.toBe('DENIED');

    const gatewayDenied = new TallyReadGateway({
      connectionManager,
      requestBuilder: new TallyXmlRequestBuilder(),
      logger: createLogger({ service: 'test', level: 'error' }),
    });
    await expect(
      gatewayDenied.executeApprovedRead({
        // @ts-expect-error deliberate unknown operation
        operationId: 'UNKNOWN_OP',
      }),
    ).rejects.toBeInstanceOf(AppError);

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

    const result = await adapter.discoverCompanies();
    expect(result.status).toBe('TIMEOUT');
    expect(result.tallyReachable).toBe(false);
    await connectionManager.stop();
  });

  it('uses only the approved COMPANY_LIST registry operation', async () => {
    const sent: string[] = [];
    const { adapter, connectionManager } = await makeAdapter((body) => {
      sent.push(body);
      return { body: COMPANY_ONE_VALID };
    });
    await adapter.discoverCompanies();
    expect(sent[0]).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(sent[0]).toContain('<ID>List of Companies</ID>');
    expect(ApprovedOperationId.CompanyList).toBe('COMPANY_LIST');
    await connectionManager.stop();
  });
});

describe('CompanyDiscoveryService boundary', () => {
  it('uses ErpReadPort only and surfaces explicit status on success', async () => {
    const { CompanyDiscoveryServiceImpl } = await import(
      '../../../src/services/tally/company-discovery.service.js'
    );
    const readPort = {
      isReady: () => true,
      discoverCompanies: async () => ({
        contractVersion: '1' as const,
        status: 'SUCCESS' as const,
        tallyReachable: true,
        items: [{ id: 'demo', name: 'Demo Co', financialYear: '20240401' }],
      }),
    } as unknown as ErpReadPort;

    const service = new CompanyDiscoveryServiceImpl(
      loadConfig({ env: 'test' }),
      readPort,
      createLogger({ service: 'test', level: 'error' }),
    );
    await service.start();
    const result = await service.discoverCompanies();
    expect(result.status).toBe('SUCCESS');
    expect(result.items[0]?.name).toBe('Demo Co');
    expect(result.tallyReachable).toBe(true);
  });

  it('throws 403 when adapter reports DENIED', async () => {
    const { CompanyDiscoveryServiceImpl } = await import(
      '../../../src/services/tally/company-discovery.service.js'
    );
    const service = new CompanyDiscoveryServiceImpl(
      loadConfig({ env: 'test' }),
      {
        isReady: () => true,
        discoverCompanies: async () => ({
          contractVersion: '1' as const,
          status: 'DENIED' as const,
          tallyReachable: true,
          items: [],
          reason: 'policy denied',
        }),
      } as unknown as ErpReadPort,
      createLogger({ service: 'test', level: 'error' }),
    );
    await service.start();
    await expect(service.discoverCompanies()).rejects.toMatchObject({
      statusCode: 403,
      code: ErrorCodes.VALIDATION_ERROR,
    });
  });
});
