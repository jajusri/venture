import { describe, expect, it } from 'vitest';

import { MasterDataTemplates } from '../../../src/extraction/templates/master-data-templates.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';

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
    expect(xml).toContain('Fetch : GUID, ALTERID, NAME, PARENT, BASEUNITS');
    expect(xml).not.toContain('<DESC>List of Stock Items</DESC>');
  });
});
