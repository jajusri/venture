import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import {
  InboundXmlEnvelopeService,
  calculateFingerprint,
  establishProvenance,
} from '../../../src/ingestion/inbound-xml-envelope.service.js';
import { InboundXmlReasonCode } from '../../../src/ingestion/inbound-xml-reason-codes.js';
import {
  InboundXmlDuplicateStatus,
  InboundXmlPersistenceStatus,
  InboundXmlResourceKind,
  InboundXmlSourceType,
  InboundXmlValidationStatus,
  isRejectedInboundEnvelope,
  isValidatedInboundEnvelope,
  type InboundXmlAcceptResult,
} from '../../../src/ingestion/inbound-xml-types.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { DEFAULT_XML_PARSER_MAX_BYTES } from '../../../src/tally/xml/response-parser-limits.js';
import {
  buildRichLedgerEnvelope,
  buildRichStockEnvelope,
  SYNTHETIC_MISSING_ENVELOPE_RESPONSE,
  SYNTHETIC_TALLY_LINEERROR_RESPONSE,
  SYNTHETIC_WRONG_ROOT_RESPONSE,
} from '../../helpers/inbound-xml-fixtures.js';
import { SAMPLE_LEDGERS_RESPONSE, SAMPLE_LEDGER_GROUPS_RESPONSE } from '../../helpers/master-data-fixtures.js';

const logger = createLogger({ service: 'test', level: 'error' });

function rejectReason(result: InboundXmlAcceptResult): string {
  expect(isRejectedInboundEnvelope(result)).toBe(true);
  if (!isRejectedInboundEnvelope(result)) {
    throw new Error('expected rejected envelope');
  }
  return result.reasonCode;
}

function makeService(options: { maxPackageBytes?: number } = {}) {
  return new InboundXmlEnvelopeService({
    logger,
    parser: new TallyXmlResponseParser(),
    ...options,
  });
}

function acceptLedger(xml: string, extra: Record<string, unknown> = {}) {
  return makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
    sourceType: InboundXmlSourceType.InlineBuffer,
    targetCompanyName: 'Pilot Co',
    ...extra,
  });
}

describe('InboundXmlEnvelopeService', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  describe('valid acceptance', () => {
    it('accepts valid UTF-8 ledger XML', () => {
      const result = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      if (!isValidatedInboundEnvelope(result)) return;
      expect(result.resourceKind).toBe(InboundXmlResourceKind.Ledgers);
      expect(result.nodeCount).toBeGreaterThan(0);
      expect(result.detectedEncoding).toBe('utf-8');
    });

    it('accepts UTF-8 BOM payloads', () => {
      const bom = Buffer.from([0xef, 0xbb, 0xbf]);
      const payload = Buffer.concat([bom, Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8')]);
      const result = makeService().acceptBuffer(payload, {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
    });

    it('accepts ledger groups with target company context', () => {
      const result = makeService().acceptBuffer(Buffer.from(SAMPLE_LEDGER_GROUPS_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      if (!isValidatedInboundEnvelope(result)) return;
      expect(result.resourceKind).toBe(InboundXmlResourceKind.LedgerGroups);
    });

    it('accepts stock items with target company', () => {
      const xml = buildRichStockEnvelope([
        { name: 'Widget', guid: 'stock-guid-000000000001' },
      ]);
      const result = makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      if (!isValidatedInboundEnvelope(result)) return;
      expect(result.resourceKind).toBe(InboundXmlResourceKind.StockItems);
    });

    it('accepts near-limit industrial ledger fixture', () => {
      const records = Array.from({ length: 50 }, (_, index) => ({
        name: `Ledger ${index}`,
        guid: `guid-${String(index).padStart(12, '0')}`,
      }));
      const xml = buildRichLedgerEnvelope(records);
      const result = acceptLedger(xml);
      expect(isValidatedInboundEnvelope(result)).toBe(true);
    });

    it('exposes provenance and fingerprint helpers', () => {
      const bytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
      const result = makeService().acceptBuffer(bytes, {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      if (!isValidatedInboundEnvelope(result)) return;
      expect(calculateFingerprint(bytes)).toBe(result.contentFingerprint);
      expect(establishProvenance(result).contentFingerprint).toBe(result.contentFingerprint);
    });
  });

  describe('encoding and payload rejection', () => {
    it('rejects empty payloads', () => {
      const result = makeService().acceptBuffer(Buffer.from('   ', 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(result.validationStatus).toBe(InboundXmlValidationStatus.Rejected);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.EmptyPayload);
    });

    it('rejects unsupported declared encoding', () => {
      const xml = `<?xml version="1.0" encoding="ISO-8859-1"?>${SAMPLE_LEDGERS_RESPONSE}`;
      const result = acceptLedger(xml);
      expect(result.validationStatus).toBe(InboundXmlValidationStatus.Rejected);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.UnsupportedEncoding);
    });

    it('rejects binary null bytes', () => {
      const bytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
      bytes[10] = 0x00;
      const result = makeService().acceptBuffer(bytes, {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.BinaryNullByte);
    });

    it('rejects oversized package before full domain work', () => {
      const service = makeService({ maxPackageBytes: 128 });
      const result = service.acceptBuffer(Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.OversizedPayload);
    });
  });

  describe('prohibited constructs and envelope policy', () => {
    it('rejects DOCTYPE declarations', () => {
      const xml = `<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>${SAMPLE_LEDGERS_RESPONSE}`;
      const result = acceptLedger(xml);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.ProhibitedConstruct);
    });

    it('rejects entity declarations', () => {
      const xml = `<!ENTITY x "y">${SAMPLE_LEDGERS_RESPONSE}`;
      const result = acceptLedger(xml);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.ProhibitedConstruct);
    });

    it('rejects IMPORT instructions', () => {
      const xml = `<ENVELOPE><HEADER><TALLYREQUEST>Import Data</TALLYREQUEST></HEADER>${SAMPLE_LEDGERS_RESPONSE.slice(9)}`;
      const result = acceptLedger(xml);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.MutationInstruction);
    });

    it('rejects EXECUTE instructions', () => {
      const xml = `<ENVELOPE><BODY><EXECUTE>Run</EXECUTE></BODY></ENVELOPE>`;
      const result = acceptLedger(xml);
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.MutationInstruction);
    });

    it('rejects unknown roots', () => {
      const result = makeService().acceptBuffer(Buffer.from(SYNTHETIC_WRONG_ROOT_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.EnvelopeDrift);
    });

    it('rejects non-envelope payloads', () => {
      const result = makeService().acceptBuffer(Buffer.from(SYNTHETIC_MISSING_ENVELOPE_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.EnvelopeDrift);
    });

    it('rejects explicit Tally line errors', () => {
      const result = makeService().acceptBuffer(Buffer.from(SYNTHETIC_TALLY_LINEERROR_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.TallyLineError);
    });

    it('rejects unknown resource kinds', () => {
      const xml = '<ENVELOPE><BODY><DATA><COLLECTION><ITEM>A</ITEM></COLLECTION></DATA></BODY></ENVELOPE>';
      const result = makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.UnknownResourceKind);
    });

    it('rejects malformed XML safely', () => {
      const result = acceptLedger('<ENVELOPE><BODY><DATA><COLLECTION><LEDGER></ENVELOPE>');
      expect(result.validationStatus).toBe(InboundXmlValidationStatus.Rejected);
    });
  });

  describe('company isolation', () => {
    it('rejects missing company identity for ledgers', () => {
      const result = makeService().acceptBuffer(Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.MissingCompanyIdentity);
    });

    it('rejects ambiguous company identity markers', () => {
      const xml = `<ENVELOPE><HEADER><SVCURRENTCOMPANY>Co A</SVCURRENTCOMPANY></HEADER><HEADER><SVCURRENTCOMPANY>Co B</SVCURRENTCOMPANY></HEADER>${SAMPLE_LEDGERS_RESPONSE.slice(9)}`;
      const result = makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.AmbiguousCompanyIdentity);
    });

    it('rejects target-company mismatch', () => {
      const xml = `<ENVELOPE><HEADER><SVCURRENTCOMPANY>Source Co</SVCURRENTCOMPANY></HEADER>${SAMPLE_LEDGERS_RESPONSE.slice(9)}`;
      const result = makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Different Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.CompanyMismatch);
    });

    it('accepts matching source and target company', () => {
      const xml = `<ENVELOPE><HEADER><SVCURRENTCOMPANY>Pilot Co</SVCURRENTCOMPANY></HEADER>${SAMPLE_LEDGERS_RESPONSE.slice(9)}`;
      const result = makeService().acceptBuffer(Buffer.from(xml, 'utf8'), {
        sourceType: InboundXmlSourceType.InlineBuffer,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
    });
  });

  describe('filesystem ingestion', () => {
    it('accepts a trusted internal file path', async () => {
      const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-inbound-'));
      tempDirs.push(dir);
      const filePath = path.join(dir, 'ledgers.xml');
      fs.writeFileSync(filePath, SAMPLE_LEDGERS_RESPONSE, 'utf8');
      const before = fs.readFileSync(filePath);
      const result = await makeService().acceptFile({
        sourceType: InboundXmlSourceType.TrustedInternalFile,
        filePath,
        targetCompanyName: 'Pilot Co',
      });
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      expect(fs.readFileSync(filePath).equals(before)).toBe(true);
    });

    it('rejects missing files', async () => {
      const result = await makeService().acceptFile({
        sourceType: InboundXmlSourceType.TrustedInternalFile,
        filePath: path.join(os.tmpdir(), 'missing-budcom-import.xml'),
        targetCompanyName: 'Pilot Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.FileMissing);
    });

    it('rejects directories', async () => {
      const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-inbound-dir-'));
      tempDirs.push(dir);
      const result = await makeService().acceptFile({
        sourceType: InboundXmlSourceType.TrustedInternalFile,
        filePath: dir,
        targetCompanyName: 'Pilot Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.NotRegularFile);
    });

    it('rejects files outside watched-folder root', async () => {
      const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-root-'));
      const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-outside-'));
      tempDirs.push(root, outside);
      const filePath = path.join(outside, 'ledgers.xml');
      fs.writeFileSync(filePath, SAMPLE_LEDGERS_RESPONSE, 'utf8');
      const result = await makeService().acceptFile({
        sourceType: InboundXmlSourceType.WatchedFolderFile,
        filePath,
        approvedRoot: root,
        targetCompanyName: 'Pilot Co',
      });
      expect(rejectReason(result)).toBe(InboundXmlReasonCode.PathOutsideRoot);
    });
  });

  describe('duplicate fingerprinting without persistence', () => {
    it('returns stable fingerprints for renamed identical content', () => {
      const bytes = Buffer.from(SAMPLE_LEDGERS_RESPONSE, 'utf8');
      const first = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      const second = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      expect(isValidatedInboundEnvelope(first)).toBe(true);
      expect(isValidatedInboundEnvelope(second)).toBe(true);
      if (!isValidatedInboundEnvelope(first) || !isValidatedInboundEnvelope(second)) return;
      expect(first.contentFingerprint).toBe(second.contentFingerprint);
      expect(first.contentFingerprint).toBe(calculateFingerprint(bytes));
    });

    it('treats changed content as a distinct fingerprint', () => {
      const first = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      const changed = acceptLedger(buildRichLedgerEnvelope([
        { name: 'Only Ledger', guid: 'only-guid-000000000001' },
      ]));
      expect(isValidatedInboundEnvelope(first)).toBe(true);
      expect(isValidatedInboundEnvelope(changed)).toBe(true);
      if (!isValidatedInboundEnvelope(first) || !isValidatedInboundEnvelope(changed)) return;
      expect(first.contentFingerprint).not.toBe(changed.contentFingerprint);
    });
  });

  describe('parser limit alignment', () => {
    it('uses operation-registry byte limits for ledger resources', () => {
      expect(DEFAULT_XML_PARSER_MAX_BYTES).toBeGreaterThan(500_000);
      const service = makeService({ maxPackageBytes: DEFAULT_XML_PARSER_MAX_BYTES });
      const result = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      expect(service.getParser()).toBeInstanceOf(TallyXmlResponseParser);
    });
  });

  describe('duplicate status defaults', () => {
    it('marks unique content when attempt recording is disabled', () => {
      const result = acceptLedger(SAMPLE_LEDGERS_RESPONSE);
      expect(isValidatedInboundEnvelope(result)).toBe(true);
      if (!isValidatedInboundEnvelope(result)) return;
      expect(result.duplicateStatus).toBe(InboundXmlDuplicateStatus.Unique);
      expect(result.persistenceStatus).toBe(InboundXmlPersistenceStatus.NotAttempted);
    });
  });
});
