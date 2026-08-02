import { describe, expect, it } from 'vitest';

import { MasterDataTemplates } from '../../../src/extraction/templates/master-data-templates.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { TallyXmlResponseParser, XmlParseError } from '../../../src/tally/xml/response-parser.js';

describe('MasterDataTemplates', () => {
  const builder = new TallyXmlRequestBuilder();

  const entities = [
    ['ledgerGroups', MasterDataTemplates.ledgerGroups('ESTIMATION')],
    ['ledgers', MasterDataTemplates.ledgers('ESTIMATION')],
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

  it('enriches ledgers collection with embedded read-only FETCH fields', () => {
    const xml = builder.build(MasterDataTemplates.ledgers('ESTIMATION'));
    expect(xml).toContain('<COLLECTION NAME="List of Ledgers" ISMODIFY="Yes">');
    expect(xml).toContain('Fetch : NAME, GUID, ALTERID, MASTERID, OPENINGBALANCE, CLOSINGBALANCE, ISBILLWISEON');
    expect(xml).not.toMatch(/Fetch\s*:[^<]*\bPARENT\b/);
    expect(xml).not.toContain('<DESC>List of Ledgers</DESC>');
  });

  it('does not request master fields proven to emit XML 1.0-invalid sentinel references', () => {
    const parser = new TallyXmlResponseParser();
    const liveFailureShape = [
      '<ENVELOPE><BODY><DATA><COLLECTION>',
      '<LEDGER NAME="Profit &amp; Loss A/c"><PARENT>&#4; Primary</PARENT></LEDGER>',
      '<STOCKITEM NAME="Example"><GSTAPPLICABLE>&#4; Applicable</GSTAPPLICABLE>',
      '<PARENT>&#4; Primary</PARENT><BASEUNITS>&#4; Not Applicable</BASEUNITS></STOCKITEM>',
      '</COLLECTION></DATA></BODY></ENVELOPE>',
    ].join('');

    expect(() => parser.parse(liveFailureShape)).toThrowError(
      expect.objectContaining<Partial<XmlParseError>>({ reason: 'xml_illegal_character' }),
    );

    const stockXml = builder.build(MasterDataTemplates.stockItems('ESTIMATION'));
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bGSTAPPLICABLE\b/);
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bPARENT\b/);
    expect(stockXml).not.toMatch(/Fetch\s*:[^<]*\bBASEUNITS\b/);
  });
});
