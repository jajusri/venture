import { describe, expect, it, vi } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { LEDGER_RICH_FETCH_FIELDS } from '../../../src/extraction/core/ledger-identity.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import {
  ApprovedOperationId,
  getApprovedOperation,
  LEGACY_SHALLOW_LEDGER_MAX_RESPONSE_BYTES,
  RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
} from '../../../src/tally/registry/operation-registry.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { decidePolicy } from '../../../src/erp/policy/policy-engine.js';
import { toPolicyOperation } from '../../../src/tally/registry/operation-registry.js';
import { SAMPLE_LICENSE_INFO_RESPONSE } from '../../helpers/mock-fetch.js';

function syntheticXmlPayload(byteLength: number): string {
  const header = '<ENVELOPE><HEADER></HEADER><BODY>';
  const footer = '</BODY></ENVELOPE>';
  const padLen = Math.max(0, byteLength - Buffer.byteLength(header + footer, 'utf8'));
  return `${header}${'X'.repeat(padLen)}${footer}`;
}

async function makeLedgerGateway(responseBody: string) {
  const fetchImpl = (async () =>
    new Response(responseBody, {
      status: 200,
      headers: { 'content-type': 'text/xml' },
    })) as unknown as typeof fetch;

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
  return { gateway, connectionManager };
}

describe('ledger response limit policy', () => {
  it('renders ledgers via MasterDataTemplates with all eight approved FETCH fields', () => {
    const spec = getApprovedOperation(ApprovedOperationId.Ledgers).render({
      companyName: 'Synthetic Co',
    });
    expect(spec.id).toBe('List of Ledgers');
    expect(spec.collectionModifyFetch).toEqual([...LEDGER_RICH_FETCH_FIELDS]);
  });

  it('shares the rich master collection cap with stock items', () => {
    const ledgers = getApprovedOperation(ApprovedOperationId.Ledgers);
    const stockItems = getApprovedOperation(ApprovedOperationId.StockItems);
    expect(ledgers.maxResponseBytes).toBe(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    expect(stockItems.maxResponseBytes).toBe(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    expect(LEGACY_SHALLOW_LEDGER_MAX_RESPONSE_BYTES).toBe(524_288);
  });

  it('accepts a ledger response above the legacy shallow cap but below the rich cap', async () => {
    const aboveLegacy = LEGACY_SHALLOW_LEDGER_MAX_RESPONSE_BYTES + 64_000;
    expect(aboveLegacy).toBeLessThan(RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES);
    const { gateway, connectionManager } = await makeLedgerGateway(syntheticXmlPayload(aboveLegacy));
    const result = await gateway.executeApprovedRead({
      operationId: ApprovedOperationId.Ledgers,
      companyName: 'Synthetic Co',
    });
    expect(result.byteLength).toBe(aboveLegacy);
    await connectionManager.stop();
  });

  it('rejects a ledger response above the rich master cap with typed SERVICE_UNAVAILABLE', async () => {
    const overCap = RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES + 1;
    const { gateway, connectionManager } = await makeLedgerGateway(syntheticXmlPayload(overCap));
    await expect(
      gateway.executeApprovedRead({
        operationId: ApprovedOperationId.Ledgers,
        companyName: 'Synthetic Co',
      }),
    ).rejects.toMatchObject({
      code: ErrorCodes.SERVICE_UNAVAILABLE,
      statusCode: 503,
      message: expect.stringContaining('LEDGERS'),
    });
    await connectionManager.stop();
  });

  it('does not expose raw XML or accounting field values when rejecting oversize responses', async () => {
    const secretMarker = 'SENSITIVE-Ledger-Name-99999.50Dr';
    const header = '<ENVELOPE><HEADER></HEADER><BODY>';
    const footer = '</BODY></ENVELOPE>';
    const targetSize = RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES + 512;
    const padLen = Math.max(0, targetSize - Buffer.byteLength(header + footer + secretMarker, 'utf8'));
    const body = `${header}${secretMarker}${'X'.repeat(padLen)}${footer}`;
    const { gateway, connectionManager } = await makeLedgerGateway(body);
    try {
      await gateway.executeApprovedRead({
        operationId: ApprovedOperationId.Ledgers,
        companyName: 'Synthetic Co',
      });
      throw new Error('Expected oversize rejection');
    } catch (error) {
      expect(error).toBeInstanceOf(AppError);
      const appError = error as AppError;
      expect(appError.message).not.toContain(secretMarker);
      expect(appError.message).not.toContain('<ENVELOPE>');
      expect(JSON.stringify(appError.details ?? {})).not.toContain(secretMarker);
    }
    await connectionManager.stop();
  });

  it('leaves non-rich operation limits unchanged', () => {
    expect(getApprovedOperation(ApprovedOperationId.LedgerGroups).maxResponseBytes).toBe(128_000);
    expect(getApprovedOperation(ApprovedOperationId.HealthCheck).maxResponseBytes).toBe(65_536);
  });

  it('keeps EXPORT-only policy engine safeguards for ledgers', () => {
    const op = toPolicyOperation(getApprovedOperation(ApprovedOperationId.Ledgers));
    expect(
      decidePolicy({
        operation: op,
        requestBytes: 500,
        circuitState: 'closed',
        isHealthProbe: false,
      }).decision,
    ).toBe('ALLOW');
    expect(getApprovedOperation(ApprovedOperationId.Ledgers).tallyRequest).toBe('Export');
  });
});

describe('TallyReadGateway health probe unchanged', () => {
  it('still accepts the license info probe', async () => {
    const fetchImpl = (async () =>
      new Response(SAMPLE_LICENSE_INFO_RESPONSE, {
        status: 200,
        headers: { 'content-type': 'text/xml' },
      })) as unknown as typeof fetch;
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
    await gateway.executeApprovedRead({ operationId: ApprovedOperationId.HealthCheck });
    await connectionManager.stop();
    vi.restoreAllMocks();
  });
});
