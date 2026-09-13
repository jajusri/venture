import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

import { registerServices } from '../../../src/bootstrap/register-services.js';
import { loadConfig } from '../../../src/config/index.js';
import { ServiceTokens } from '../../../src/core/tokens.js';
import type { VoucherReadPort } from '../../../src/erp/ports/vouchers.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { VoucherExtractionService } from '../../../src/services/voucher/voucher-extraction.service.js';
import { VoucherApplicationServiceImpl } from '../../../src/services/voucher/voucher-application.service.js';
import { TallyReadAdapter } from '../../../src/tally/adapter/tally-read-adapter.js';
import { TallyReadGateway } from '../../../src/tally/gateway/tally-read-gateway.js';
import {
  ApprovedOperationId,
  getApprovedOperation,
} from '../../../src/tally/registry/operation-registry.js';
import { createTallyModule } from '../../../src/tally/tally-module.js';
import { TallyVoucherExtractor } from '../../../src/tally/voucher/voucher-extractor.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';

describe('production Voucher composition', () => {
  it('exposes the production VoucherReadPort without exposing XML infrastructure', () => {
    const module = createTallyModule({
      config: loadConfig({ env: 'test' }),
      logger: createLogger({ service: 'voucher-composition-test', level: 'error' }),
    });

    expect(module.voucherReadPort).toBeInstanceOf(TallyVoucherExtractor);
    const dependencies = module.voucherReadPort as unknown as Record<string, unknown>;
    expect(dependencies.gateway).toBeInstanceOf(TallyReadGateway);
    expect(dependencies.parser).toBeInstanceOf(VoucherCollectionParser);
    expect(dependencies.mapper).toBeInstanceOf(VoucherXmlMapper);
    expect(module.readPort).toBeInstanceOf(TallyReadAdapter);
    expect(Object.keys(module).sort()).toEqual([
      'connectionManager',
      'readPort',
      'voucherExtractor',
      'voucherMapper',
      'voucherParser',
      'voucherReadPort',
    ]);
    expect(module.voucherReadPort).not.toHaveProperty('rawXml');
    expect(module.voucherReadPort).not.toHaveProperty('responseParser');
    expect(module.voucherReadPort).not.toHaveProperty('transport');
  });

  it('registers the parser, mapper, concrete extractor, port, and application service', () => {
    const context = registerServices({ env: 'test', logLevel: 'error' });
    const parser = context.container.resolve<VoucherCollectionParser>(
      ServiceTokens.VoucherParser,
    );
    const mapper = context.container.resolve<VoucherXmlMapper>(
      ServiceTokens.VoucherMapper,
    );
    const extractor = context.container.resolve<TallyVoucherExtractor>(
      ServiceTokens.TallyVoucherExtractor,
    );
    const source = context.container.resolve<VoucherReadPort>(
      ServiceTokens.VoucherReadPort,
    );
    const service = context.container.resolve<VoucherExtractionService>(
      ServiceTokens.VoucherExtraction,
    );

    expect(parser).toBeInstanceOf(VoucherCollectionParser);
    expect(mapper).toBeInstanceOf(VoucherXmlMapper);
    expect(extractor).toBeInstanceOf(TallyVoucherExtractor);
    expect(source).toBe(extractor);
    expect(extractor).toHaveProperty('parser', parser);
    expect(extractor).toHaveProperty('mapper', mapper);
    expect(service).toBeInstanceOf(VoucherExtractionService);
    expect(context.container.resolve(ServiceTokens.VoucherExtraction)).toBe(service);
    expect(context.container.has(ServiceTokens.VoucherSync)).toBe(true);
    expect(context.container.has(ServiceTokens.VoucherSynchronization)).toBe(true);
    expect(
      context.container.resolve(ServiceTokens.VoucherApplication),
    ).toBeInstanceOf(VoucherApplicationServiceImpl);
  });

  it('keeps discovery and prohibited runtime surfaces outside the production graph', () => {
    const tokenValues = Object.values(ServiceTokens);
    expect(tokenValues).not.toEqual(expect.arrayContaining([
      'VoucherDiscovery',
      'VoucherDiscoveryExecutor',
      'VoucherFixtureManifest',
      'VoucherEvidenceSanitizer',
      'VoucherEvidenceWriter',
    ]));

    expect(tokenValues).not.toContain('VoucherIpc');
    expect(tokenValues).not.toContain('VoucherUi');
    expect(tokenValues).not.toContain('VoucherScheduler');
    expect(tokenValues).not.toContain('VoucherBackgroundJob');

    for (const productionModule of [
      '../../../src/bootstrap/register-services.ts',
      '../../../src/tally/tally-module.ts',
      '../../../src/services/voucher/voucher-extraction.service.ts',
    ]) {
      const source = readFileSync(new URL(productionModule, import.meta.url), 'utf8');
      expect(source).not.toMatch(/voucher-discovery|discovery\/cli|voucher-evidence|fixture-manifest/);
    }
  });

  it('keeps the production operation bounded, read-only, and internally executable', () => {
    const operation = getApprovedOperation(ApprovedOperationId.Vouchers);

    expect(operation).toMatchObject({
      operationId: 'VOUCHERS',
      tallyRequest: 'Export',
      requestKind: 'Collection',
      classification: 'VERIFIED_SAFE',
      rolloutStatus: 'production',
      requiresCompany: true,
      requiresDateRange: true,
      maximumRangeDays: 366,
      maxRequestBytes: 65_536,
      maxResponseBytes: 5_242_880,
      timeoutMs: 30_000,
    });
    expect('renderEmbeddedCollection' in operation).toBe(true);
    expect(JSON.stringify(operation).toUpperCase()).not.toMatch(
      /IMPORT|EXECUTE|ALTER|DELETE|CREATE/,
    );
  });
});
