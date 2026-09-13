import { loadConfig } from '../src/config/index.js';
import { TallyXmlRequestBuilder } from '../src/tally/xml/request-builder.js';
import { validateTallyRequestXml } from '../src/tally/safety/xml-request-validator.js';
import { MasterDataTemplates } from '../src/extraction/templates/master-data-templates.js';

const COMPANY = 'ESTIMATION';
const builder = new TallyXmlRequestBuilder();
const config = loadConfig({ env: 'test' });

const entities = [
  ['ledgerGroups', MasterDataTemplates.ledgerGroups(COMPANY)],
  ['ledgers', MasterDataTemplates.ledgers(COMPANY)],
  ['stockGroups', MasterDataTemplates.stockGroups(COMPANY)],
  ['stockCategories', MasterDataTemplates.stockCategories(COMPANY)],
  ['units', MasterDataTemplates.units(COMPANY)],
  ['godowns', MasterDataTemplates.godowns(COMPANY)],
  ['costCategories', MasterDataTemplates.costCategories(COMPANY)],
  ['costCentres', MasterDataTemplates.costCentres(COMPANY)],
  ['voucherTypes', MasterDataTemplates.voucherTypes(COMPANY)],
  ['gstRegistrations', MasterDataTemplates.gstRegistrations(COMPANY)],
  ['stockItems', MasterDataTemplates.stockItems(COMPANY)],
] as const;

for (const [name, template] of entities) {
  const xml = builder.build(template);
  const result = validateTallyRequestXml(xml, config.tallyMaxRequestBytes);
  console.log(JSON.stringify({ entity: name, valid: true, collectionId: result.collectionId, bytes: Buffer.byteLength(xml, 'utf8') }));
}
