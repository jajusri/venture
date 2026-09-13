import { describe, expect, it } from 'vitest';

import { AppError } from '../../../src/infrastructure/errors/app-error.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { validateTallyRequestXml } from '../../../src/tally/safety/xml-request-validator.js';

function exportEnvelope(body = ''): string {
  return `<ENVELOPE><HEADER><VERSION>1</VERSION><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Collection</TYPE><ID>List of Ledgers</ID></HEADER><BODY>${body}</BODY></ENVELOPE>`;
}

describe('egress prohibited mutation constructs', () => {
  const builder = new TallyXmlRequestBuilder();
  const MAX = 65_536;

  it('accepts a valid registry-built EXPORT request', () => {
    const xml = builder.buildCompanyListRequest();
    expect(() => validateTallyRequestXml(xml, MAX)).not.toThrow();
  });

  it.each([
    ['IMPORT in TALLYREQUEST', '<ENVELOPE><HEADER><TALLYREQUEST>Import Data</TALLYREQUEST><TYPE>Data</TYPE><ID>Vouchers</ID></HEADER></ENVELOPE>'],
    ['EXECUTE in TALLYREQUEST', '<ENVELOPE><HEADER><TALLYREQUEST>Execute</TALLYREQUEST><TYPE>Function</TYPE><ID>DoThing</ID></HEADER></ENVELOPE>'],
    ['ALTER in TYPE', '<ENVELOPE><HEADER><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Alter</TYPE><ID>Ledger</ID></HEADER></ENVELOPE>'],
    ['DELETE in TYPE', '<ENVELOPE><HEADER><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Delete</TYPE><ID>Ledger</ID></HEADER></ENVELOPE>'],
  ])('rejects prohibited construct in header: %s', (_label, xml) => {
    expect(() => validateTallyRequestXml(xml, MAX)).toThrow(AppError);
  });

  it.each([
    ['IMPORT in body', '<IMPORT>Ledger</IMPORT>'],
    ['EXECUTE in body', '<EXECUTE>Action</EXECUTE>'],
    ['nested UPDATE', '<LEDGER><UPDATE>Balance</UPDATE></LEDGER>'],
    ['namespace-prefixed IMPORT', '<tally:IMPORT>Ledger</tally:IMPORT>'],
    ['whitespace-obfuscated EXECUTE tag', '<  EXECUTE  >Run</ EXECUTE >'],
    ['CANCEL in body', '<CANCEL>Voucher</CANCEL>'],
    ['REWRITE in body', '<REWRITE>Ledger</REWRITE>'],
    ['SET in body', '<SET>Name</SET>'],
  ])('rejects EXPORT headers with mutation in body: %s', (_label, body) => {
    expect(() => validateTallyRequestXml(exportEnvelope(body), MAX)).toThrow(AppError);
  });

  it('rejects DOCTYPE declarations', () => {
    const xml = `<!DOCTYPE foo [<!ENTITY x "1">]>${exportEnvelope()}`;
    expect(() => validateTallyRequestXml(xml, MAX)).toThrow(AppError);
  });

  it('rejects malformed envelopes missing ID', () => {
    const xml = '<ENVELOPE><HEADER><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Collection</TYPE></HEADER></ENVELOPE>';
    expect(() => validateTallyRequestXml(xml, MAX)).toThrow(AppError);
  });

  it('rejects mixed-case mutation tags in body', () => {
    expect(() => validateTallyRequestXml(exportEnvelope('<ImPoRt>Ledger</ImPoRt>'), MAX)).toThrow(AppError);
  });
});
