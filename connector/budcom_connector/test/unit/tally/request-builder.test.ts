import { describe, expect, it } from 'vitest';

import { TallyXmlRequestBuilder } from '../../../src/tally/xml/request-builder.js';

describe('TallyXmlRequestBuilder', () => {
  const builder = new TallyXmlRequestBuilder();

  it('builds connectivity check request', () => {
    const xml = builder.buildConnectivityCheck();
    expect(xml).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(xml).toContain('<ID>License Info</ID>');
  });

  it('builds company list request', () => {
    const xml = builder.buildCompanyListRequest();
    expect(xml).toContain('<ID>List of Companies</ID>');
    expect(xml).toContain('<SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>');
  });

  it('escapes XML special characters', () => {
    const xml = builder.build({
      tallyRequest: 'Export',
      type: 'Data',
      id: 'Test & Co',
      description: 'A <B> "C"',
    });
    expect(xml).toContain('<ID>Test &amp; Co</ID>');
    expect(xml).toContain('<DESC>A &lt;B&gt; &quot;C&quot;</DESC>');
  });
});
