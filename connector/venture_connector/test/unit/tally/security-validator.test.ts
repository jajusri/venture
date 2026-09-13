import { describe, expect, it } from 'vitest';

import { AppError } from '../../../src/infrastructure/errors/app-error.js';
import { validateTallyRequestXml } from '../../../src/tally/safety/xml-request-validator.js';

function envelope(tallyRequest: string, type: string, id: string): string {
  return `<ENVELOPE><HEADER><VERSION>1</VERSION><TALLYREQUEST>${tallyRequest}</TALLYREQUEST><TYPE>${type}</TYPE><ID>${id}</ID></HEADER></ENVELOPE>`;
}

describe('validator is structurally read-only', () => {
  const MAX = 65_536;

  it('rejects IMPORT requests', () => {
    expect(() => validateTallyRequestXml(envelope('Import Data', 'Data', 'Vouchers'), MAX)).toThrow(
      AppError,
    );
  });

  it('rejects EXECUTE requests', () => {
    expect(() => validateTallyRequestXml(envelope('Execute', 'Function', 'DoThing'), MAX)).toThrow(
      AppError,
    );
  });

  it('rejects FUNCTION type requests', () => {
    expect(() =>
      validateTallyRequestXml(envelope('Export', 'Function', '$$Something'), MAX),
    ).toThrow(AppError);
  });

  it.each(['Create', 'Alter', 'Delete', 'Update'])('rejects %s verbs', (verb) => {
    expect(() => validateTallyRequestXml(envelope(verb, 'Object', 'Ledger'), MAX)).toThrow(AppError);
  });

  it('rejects a missing TALLYREQUEST', () => {
    const xml = '<ENVELOPE><HEADER><TYPE>Collection</TYPE><ID>List of Ledgers</ID></HEADER></ENVELOPE>';
    expect(() => validateTallyRequestXml(xml, MAX)).toThrow(AppError);
  });

  it('accepts an EXPORT collection request', () => {
    const result = validateTallyRequestXml(envelope('Export', 'Collection', 'List of Ledgers'), MAX);
    expect(result.requestType).toBe('EXPORT');
    expect(result.requestKind).toBe('COLLECTION');
    expect(result.collectionId).toBe('List of Ledgers');
  });
});
