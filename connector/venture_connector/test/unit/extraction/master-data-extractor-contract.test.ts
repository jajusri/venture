import { afterEach, describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { AppError } from '../../../src/infrastructure/errors/app-error.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyConnectionManager } from '../../../src/tally/connection/tally-connection-manager.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { MasterDataExtractor } from '../../../src/extraction/extractors/master-data-extractor.js';
import { MasterDataEntityType } from '../../../src/extraction/core/types.js';
import { ApprovedOperationId } from '../../../src/tally/registry/operation-registry.js';
import { mapLedger, mapStockItem } from '../../../src/extraction/parsers/entity-mappers.js';
import {
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_SHALLOW_LEDGERS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
} from '../../helpers/master-data-fixtures.js';
import {
  buildMalformedEntityLedgerEnvelope,
  SYNTHETIC_MISSING_COLLECTION_RESPONSE,
  SYNTHETIC_NON_XML_RESPONSE,
  SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
  SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE,
} from '../../helpers/inbound-xml-fixtures.js';

async function makeContractExtractor<T extends { id: string }>(
  body: string,
  config: {
    entityType: (typeof MasterDataEntityType)[keyof typeof MasterDataEntityType];
    operationId: ApprovedOperationId;
    nodeName: string;
    masterDataContract: 'ledger' | 'stock-item';
    mapNode: (parser: import('../../../src/extraction/parsers/entity-mappers.js').CollectionEntityParser, node: import('../../../src/tally/xml/response-parser.js').ParsedXmlNode) => T | undefined;
  },
) {
  const fetchImpl = (async () =>
    new Response(body, { status: 200, headers: { 'content-type': 'text/xml' } })) as unknown as typeof fetch;
  const appConfig = loadConfig({ env: 'test', tallyMinRequestIntervalMs: 0 });
  const logger = createLogger({ service: 'test', level: 'error' });
  const connectionManager = new TallyConnectionManager({
    config: appConfig,
    logger,
    transport: new TallyHttpTransport({ config: appConfig, logger, fetchImpl }),
  });
  await connectionManager.start();
  const gateway = new TallyReadGateway({
    connectionManager,
    requestBuilder: new TallyXmlRequestBuilder(),
    logger,
  });
  const extractor = new MasterDataExtractor(
    config,
    gateway,
    new TallyXmlResponseParser(),
    logger,
  );
  return {
    extractor,
    stop: () => connectionManager.stop(),
  };
}

describe('MasterDataExtractor contract path', () => {
  afterEach(async () => {
    // no shared state
  });

  it('extracts scoped ledger records with metrics on valid response', async () => {
    const { extractor, stop } = await makeContractExtractor(SAMPLE_LEDGERS_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toHaveLength(2);
    expect(result.contract?.status).toBe('SUCCESS');
    expect(result.extractionMetrics?.candidateNodeCount).toBe(2);
    await stop();
  });

  it('does not reject standalone HEADER/STATUS=0 without LINEERROR', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toHaveLength(1);
    expect(result.contract?.status).toBe('SUCCESS');
    await stop();
  });

  it('rejects synthetic LINEERROR without returning success items', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_TALLY_LINEERROR_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    await expect(extractor.extract('Demo Company')).rejects.toSatisfy((error: unknown) => {
      const appError = error as AppError;
      expect(appError.statusCode).toBe(502);
      expect(appError.details).toMatchObject({ contractStatus: 'TALLY_ERROR' });
      expect(JSON.stringify(appError.details)).not.toMatch(/LINEERROR|Could not find/i);
      return true;
    });
    await stop();
  });

  it('rejects missing requested collection for ledgers', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_MISSING_COLLECTION_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    await expect(extractor.extract('Demo Company')).rejects.toMatchObject({
      statusCode: 502,
      details: expect.objectContaining({ contractStatus: 'COLLECTION_MISSING' }),
    });
    await stop();
  });

  it('accepts valid empty ledger collection', async () => {
    const { extractor, stop } = await makeContractExtractor(SAMPLE_EMPTY_COLLECTION_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toEqual([]);
    expect(result.contract?.status).toBe('EMPTY');
    await stop();
  });

  it('ignores unrelated subtree ledgers under contract extraction', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toEqual([]);
    expect(result.contract?.status).toBe('EMPTY');
    await stop();
  });

  it('rejects shallow ledger export at extraction boundary', async () => {
    const { extractor, stop } = await makeContractExtractor(SAMPLE_SHALLOW_LEDGERS_RESPONSE, {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    await expect(extractor.extract('Demo Company')).rejects.toMatchObject({
      statusCode: 502,
      details: expect.objectContaining({ contractStatus: 'ALL_UNMAPPABLE' }),
    });
    await stop();
  });

  it('accepts partial malformed ledger subset with INCOMPLETE contract', async () => {
    const { extractor, stop } = await makeContractExtractor(buildMalformedEntityLedgerEnvelope(), {
      entityType: MasterDataEntityType.Ledger,
      operationId: ApprovedOperationId.Ledgers,
      nodeName: 'LEDGER',
      masterDataContract: 'ledger',
      mapNode: mapLedger,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toHaveLength(1);
    expect(result.contract?.status).toBe('INCOMPLETE');
    expect(result.extractionMetrics?.droppedRecordCount).toBeGreaterThan(0);
    await stop();
  });

  it('rejects non-XML transport body as parser failure', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_NON_XML_RESPONSE, {
      entityType: MasterDataEntityType.StockItem,
      operationId: ApprovedOperationId.StockItems,
      nodeName: 'STOCKITEM',
      masterDataContract: 'stock-item',
      mapNode: mapStockItem,
    });
    await expect(extractor.extract('Demo Company')).rejects.toBeInstanceOf(AppError);
    await stop();
  });

  it('extracts stock items with contract on valid response', async () => {
    const { extractor, stop } = await makeContractExtractor(SAMPLE_STOCK_ITEMS_RESPONSE, {
      entityType: MasterDataEntityType.StockItem,
      operationId: ApprovedOperationId.StockItems,
      nodeName: 'STOCKITEM',
      masterDataContract: 'stock-item',
      mapNode: mapStockItem,
    });
    const result = await extractor.extract('Demo Company');
    expect(result.items).toHaveLength(1);
    expect(result.contract?.status).toBe('SUCCESS');
    await stop();
  });

  it('rejects explicit stock LINEERROR without mutation path', async () => {
    const { extractor, stop } = await makeContractExtractor(SYNTHETIC_TALLY_LINEERROR_RESPONSE, {
      entityType: MasterDataEntityType.StockItem,
      operationId: ApprovedOperationId.StockItems,
      nodeName: 'STOCKITEM',
      masterDataContract: 'stock-item',
      mapNode: mapStockItem,
    });
    await expect(extractor.extract('Demo Company')).rejects.toMatchObject({
      statusCode: 502,
      details: expect.objectContaining({ contractStatus: 'TALLY_ERROR' }),
    });
    await stop();
  });
});
