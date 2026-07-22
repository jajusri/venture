import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';
import { validateTallyRequestXml } from '../../../src/tally/safety/xml-request-validator.js';
import { MasterDataTemplates } from '../../../src/extraction/templates/master-data-templates.js';

/** Controlled validation: XML pre-checks for entities that passed live testing. */
describe('M3 controlled validation — passed entity XML', () => {
  const builder = new TallyXmlRequestBuilder();
  const config = loadConfig({ env: 'test' });
  const company = 'ESTIMATION';

  const passedEntities = [
    ['ledgerGroups', MasterDataTemplates.ledgerGroups(company), 'List of Groups'],
    ['ledgers', MasterDataTemplates.ledgers(company), 'List of Ledgers'],
    ['stockGroups', MasterDataTemplates.stockGroups(company), 'List of Stock Groups'],
    ['stockCategories', MasterDataTemplates.stockCategories(company), 'List of Stock Categories'],
  ] as const;

  it.each(passedEntities)('%s template is valid Tally XML', (_name, template, collectionId) => {
    const xml = builder.build(template);
    const result = validateTallyRequestXml(xml, config.tallyMaxRequestBytes);
    expect(result.collectionId).toBe(collectionId);
    expect(xml).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(xml).toContain(`<ID>${collectionId}</ID>`);
    expect(xml).toContain('<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY>');
  });
});

describe('M3 controlled validation — units derived from stock items', () => {
  it('does not use List of Units collection template for live extraction', () => {
    const xml = new TallyXmlRequestBuilder().build(MasterDataTemplates.stockItems('ESTIMATION'));
    const result = validateTallyRequestXml(xml, loadConfig({ env: 'test' }).tallyMaxRequestBytes);
    expect(result.collectionId).toBe('List of Stock Items');
  });
});
