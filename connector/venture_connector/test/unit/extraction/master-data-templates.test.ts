import { describe, expect, it } from 'vitest';

import { MasterDataTemplates } from '../../../src/extraction/templates/master-data-templates.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';

describe('MasterDataTemplates', () => {
  const builder = new TallyXmlRequestBuilder();

  const entities = [
    ['ledgerGroups', MasterDataTemplates.ledgerGroups('ESTIMATION')],
    ['ledgers', MasterDataTemplates.ledgers('ESTIMATION')],
    ['ledgersContactDetails', MasterDataTemplates.ledgersContactDetails('ESTIMATION')],
    ['stockGroups', MasterDataTemplates.stockGroups('ESTIMATION')],
    ['stockCategories', MasterDataTemplates.stockCategories('ESTIMATION')],
    ['stockItems', MasterDataTemplates.stockItems('ESTIMATION')],
    ['units', MasterDataTemplates.units('ESTIMATION')],
    ['godowns', MasterDataTemplates.godowns('ESTIMATION')],
    ['costCategories', MasterDataTemplates.costCategories('ESTIMATION')],
    ['costCentres', MasterDataTemplates.costCentres('ESTIMATION')],
    ['voucherTypes', MasterDataTemplates.voucherTypes('ESTIMATION')],
    ['gstRegistrations', MasterDataTemplates.gstRegistrations('ESTIMATION')],
    ['companyInfo', MasterDataTemplates.companyInfo('ESTIMATION')],
  ] as const;

  it.each(entities)('builds XML for %s', (_name, spec) => {
    const xml = builder.build(spec);
    expect(xml).toContain('<ENVELOPE>');
    expect(xml).toContain('<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY>');
    expect(xml).toContain('<SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>');
  });

  it('enriches stock items collection with TDL FETCH fields', () => {
    const xml = builder.build(MasterDataTemplates.stockItems('ESTIMATION'));
    expect(xml).toContain('<COLLECTION NAME="List of Stock Items" ISMODIFY="Yes">');
    expect(xml).toContain('Fetch : GUID, ALTERID, NAME, ALIAS, PARTNUMBER, HSNCODE');
    expect(xml).not.toContain('<DESC>List of Stock Items</DESC>');
  });

  it('enriches ledgers collection with embedded read-only FETCH fields, including PARENT (TD-035) and ALIAS', () => {
    const xml = builder.build(MasterDataTemplates.ledgers('ESTIMATION'));
    expect(xml).toContain('<COLLECTION NAME="List of Ledgers" ISMODIFY="Yes">');
    expect(xml).toContain(
      'Fetch : NAME, GUID, ALTERID, MASTERID, ALIAS, PARENT, OPENINGBALANCE, CLOSINGBALANCE, ISBILLWISEON',
    );
    expect(xml).not.toContain('<DESC>List of Ledgers</DESC>');
  });

  it('builds the bulk contact-details fetch on the same collection as ledgers, with a separate additive FETCH list', () => {
    const xml = builder.build(MasterDataTemplates.ledgersContactDetails('ESTIMATION'));
    expect(xml).toContain('<COLLECTION NAME="List of Ledgers" ISMODIFY="Yes">');
    expect(xml).toContain(
      'Fetch : NAME, GUID, MASTERID, MAILINGNAME, ADDRESS, STATENAME, COUNTRYNAME, PINCODE, ' +
        'EMAIL, PHONENUMBER, MOBILENUMBER, PARTYGSTIN, GSTIN, GSTREGISTRATIONTYPE, APPLICABLEFROM',
    );
    // Deliberately does NOT request the routine sync's own fields -- confirms the two Fetch lists
    // stay independent rather than accidentally merging.
    expect(xml).not.toMatch(/Fetch\s*:[^<]*\bALIAS\b/);
    expect(xml).not.toMatch(/Fetch\s*:[^<]*\bOPENINGBALANCE\b/);
  });

  // TD-001 fix (2026-08-16): this exact "&#4;" shape -- observed live on ESTIMATION --
  // used to cause a hard parser rejection (xml_illegal_character); the shared parser now
  // sanitizes it instead, for every collection unconditionally (proven directly below).
  //
  // TD-035 (2026-08-19): Ledgers' own PARENT field has since been restored to the FETCH list
  // (see master-data-templates.ts) now that this sanitizer proves the original risk is fully
  // neutralized -- see entity-mappers.test.ts's "mapLedger PARENT adversarial coverage
  // (TD-035)" for the full live-pipeline regression proof. StockItems' PARENT/BASEUNITS/
  // GSTAPPLICABLE remain deliberately excluded -- that decision was not investigated or
  // reversed by TD-035, which was scoped to Ledgers/Connect classification only.
  it('does not request StockItems fields proven to emit XML 1.0 sentinel references, even though they are now sanitized rather than rejected', () => {
    const parser = new TallyXmlResponseParser();
    const liveFailureShape = [
      '<ENVELOPE><BODY><DATA><COLLECTION>',
      '<LEDGER NAME="Profit &amp; Loss A/c"><PARENT>&#4; Primary</PARENT></LEDGER>',
      '<STOCKITEM NAME="Example"><GSTAPPLICABLE>&#4; Applicable</GSTAPPLICABLE>',
      '<PARENT>&#4; Primary</PARENT><BASEUNITS>&#4; Not Applicable</BASEUNITS></STOCKITEM>',
      '</COLLECTION></DATA></BODY></ENVELOPE>',
    ].join('');

    const document = parser.parse(liveFailureShape);
    expect(document.illegalCharactersSanitized).toBe(4);
    expect(parser.findFirst(document, 'PARENT')?.text).toBe('Primary');

    const stockXml = builder.build(MasterDataTemplates.stockItems('ESTIMATION'));
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bGSTAPPLICABLE\b/);
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bPARENT\b/);
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bBASEUNITS\b/);
  });
});
