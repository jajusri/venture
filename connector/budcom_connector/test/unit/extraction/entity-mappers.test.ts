import { describe, expect, it } from 'vitest';

import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { CollectionEntityParser, mapLedger, mapLedgerGroup, mapStockItem } from '../../../src/extraction/parsers/entity-mappers.js';
import {
  SAMPLE_LEDGER_GROUPS_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_LEDGERS_WITH_LANGUAGENAME_ALIAS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
  SAMPLE_UNICODE_LEDGER_RESPONSE,
} from '../../helpers/master-data-fixtures.js';

describe('entity mappers', () => {
  const responseParser = new TallyXmlResponseParser();
  const collectionParser = new CollectionEntityParser(responseParser);

  it('maps ledger groups and ignores CMPINFO counts', () => {
    const document = collectionParser.parseDocument(SAMPLE_LEDGER_GROUPS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'GROUP' });
    const groups = nodes.map((n) => mapLedgerGroup(collectionParser, n)).filter(Boolean);
    expect(groups).toHaveLength(2);
    expect(groups[0]).toMatchObject({ name: 'Sundry Debtors', parentName: 'Current Assets' });
  });

  it('maps ledgers with normalized balances and GUID-first identity', () => {
    const document = collectionParser.parseDocument(SAMPLE_LEDGERS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const ledgers = nodes.map((n) => mapLedger(collectionParser, n)).filter(Boolean);
    expect(ledgers[0]?.closingBalance).toEqual({
      amount: '2500.5',
      currencyCode: 'INR',
      side: 'Dr',
    });
    expect(ledgers[1]?.closingBalance?.side).toBe('Cr');
    expect(ledgers[0]?.id).toBe('guid:aaaaaaaa-bbbb-cccc-dddd-000000000001');
    expect(ledgers[0]?.masterId).toBe('2001');
    expect(ledgers[0]?.isBillWiseOn).toBe(false);
    expect(ledgers[1]?.isBillWiseOn).toBe(true);
  });

  /**
   * Bulk contact-details sync (Connect address/email/GSTIN auto-population): mapLedger() is
   * reused UNCHANGED for this path -- it already parses these tags, they've just never been
   * requested by the routine sync's own Fetch list. This proves it degrades gracefully (no
   * throw, undefined for absent fields) when balance/alias tags are absent from the response,
   * exactly the shape LEDGER_CONTACT_FETCH_FIELDS' response will have.
   */
  it('maps mailing/contact/gst fields from a contact-only response (no balances/alias present)', () => {
    const contactOnlyResponse = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Acme Corp">
          <NAME>Acme Corp</NAME>
          <GUID TYPE="String">aaaaaaaa-bbbb-cccc-dddd-000000000002</GUID>
          <MASTERID TYPE="Number">2002</MASTERID>
          <MAILINGNAME>Acme Corp Pvt Ltd</MAILINGNAME>
          <ADDRESS>123 MG Road</ADDRESS>
          <STATENAME>Karnataka</STATENAME>
          <COUNTRYNAME>India</COUNTRYNAME>
          <PINCODE>560001</PINCODE>
          <EMAIL>accounts@acme.example</EMAIL>
          <PHONENUMBER>08012345678</PHONENUMBER>
          <MOBILENUMBER>9876543210</MOBILENUMBER>
          <PARTYGSTIN>29AABCU9603R1ZM</PARTYGSTIN>
          <GSTREGISTRATIONTYPE>Regular</GSTREGISTRATIONTYPE>
          <APPLICABLEFROM>20200401</APPLICABLEFROM>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
    const document = collectionParser.parseDocument(contactOnlyResponse);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const ledger = mapLedger(collectionParser, nodes[0]);

    expect(ledger?.mailingName).toBe('Acme Corp Pvt Ltd');
    expect(ledger?.address).toBe('123 MG Road');
    expect(ledger?.state).toBe('Karnataka');
    expect(ledger?.country).toBe('India');
    expect(ledger?.pincode).toBe('560001');
    expect(ledger?.email).toBe('accounts@acme.example');
    expect(ledger?.phone).toBe('08012345678');
    expect(ledger?.mobile).toBe('9876543210');
    expect(ledger?.gstin).toBe('29AABCU9603R1ZM');
    expect(ledger?.gstRegistrationType).toBe('Regular');
    expect(ledger?.gstApplicableFrom).toBe('20200401');
    // Balance/alias tags absent from this response -- confirms no throw, graceful undefined.
    expect(ledger?.openingBalance).toBeUndefined();
    expect(ledger?.closingBalance).toBeUndefined();
    expect(ledger?.alias).toBeUndefined();
  });

  /**
   * 2026-08-23: real ESTIMATION ledgers never export a flat `<ALIAS>` tag at all -- their Alias
   * value(s) arrive as extra `<NAME>` siblings inside `LANGUAGENAME.LIST/NAME.LIST`, alongside
   * the primary name as the first entry. See `resolveLedgerAlias` in entity-mappers.ts and
   * SAMPLE_LEDGERS_WITH_LANGUAGENAME_ALIAS_RESPONSE's own doc comment for the full investigation.
   */
  describe('mapLedger resolves Alias from LANGUAGENAME.LIST (2026-08-23)', () => {
    const document = collectionParser.parseDocument(SAMPLE_LEDGERS_WITH_LANGUAGENAME_ALIAS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const byName = (name: string) => mapLedger(collectionParser, nodes.find((n) => n.attributes.NAME === name)!);

    it('picks the single extra name as the alias when there is exactly one', () => {
      expect(byName('4m Plywood & Hw')?.alias).toBe('8309814428');
    });

    it('prefers the phone-shaped candidate when a ledger has both a mobile and a short shortcut alias', () => {
      expect(byName('Balaji Kowkoor')?.alias).toBe('7877685616');
    });

    it('falls back to the only candidate when none of them look like a phone number', () => {
      expect(byName('Shortcut Only Traders')?.alias).toBe('42');
    });

    it('is undefined when the ledger has no extra name at all', () => {
      expect(byName('A2z')?.alias).toBeUndefined();
    });

    it('still prefers a flat ALIAS tag over LANGUAGENAME.LIST when Tally ever does emit one', () => {
      expect(byName('Flat Alias Traders')?.alias).toBe('9000000000');
    });
  });

  it('handles unicode ledger names', () => {
    const document = collectionParser.parseDocument(SAMPLE_UNICODE_LEDGER_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
    const ledger = mapLedger(collectionParser, nodes[0]!);
    expect(ledger?.name).toBe('ग्राहक खाता');
    expect(ledger?.id).toBeTruthy();
  });

  it('maps stock items with GUID and AlterID identity fields', () => {
    const document = collectionParser.parseDocument(SAMPLE_STOCK_ITEMS_RESPONSE);
    const nodes = collectionParser.parseNodes(document, { nodeName: 'STOCKITEM' });
    const item = mapStockItem(collectionParser, nodes[0]!);
    expect(item).toMatchObject({
      name: 'Widget A',
      guid: '6a2a5ccc-6394-4ccb-bb34-113991142c4f-0000040b',
      alterId: '2053',
      parentGroup: 'Finished Goods',
      baseUnit: 'Nos',
      hsnCode: '8471',
    });
    expect(item?.id).toBe('guid:6a2a5ccc-6394-4ccb-bb34-113991142c4f-0000040b');
  });

  /**
   * TD-035 permanent regression coverage. `PARENT` was excluded from the Ledgers TDL FETCH
   * request (2026-08-03, commit 8706a80) as a workaround for TD-001 -- a Tally export artifact
   * where a group/parent value can arrive as `&#4; <name>` (decimal numeric reference to the
   * illegal C0 control character 0x04, EOT), which used to hard-fail XML structural parsing.
   * TD-001 was properly fixed at the shared parsing layer (2026-08-16,
   * `TallyXmlResponseParser`'s `sanitizeXml10IllegalCharacters()`, unconditional for every
   * collection) -- these tests exist to prove, permanently, that restoring `PARENT` to the
   * Ledgers FETCH request (this same commit) does not reintroduce that failure, and that every
   * other adversarial character class a real Tally group name could plausibly contain survives
   * the full live pipeline (`TallyXmlResponseParser.parse` -> `mapLedger`) intact and correct.
   */
  describe('mapLedger PARENT adversarial coverage (TD-035)', () => {
    function ledgerWithParent(parentInnerXml: string): string {
      return [
        '<ENVELOPE><BODY><DATA><COLLECTION>',
        `<LEDGER NAME="Test Ledger"><GUID>guid-1</GUID><PARENT>${parentInnerXml}</PARENT></LEDGER>`,
        '</COLLECTION></DATA></BODY></ENVELOPE>',
      ].join('');
    }

    function mapFirstLedger(rawXml: string) {
      const document = collectionParser.parseDocument(rawXml);
      const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
      return mapLedger(collectionParser, nodes[0]!);
    }

    it('the exact TD-001/TD-035 failure class: decimal &#4; illegal reference is sanitized, not rejected', () => {
      const ledger = mapFirstLedger(ledgerWithParent('&#4; Sundry Debtors'));
      expect(ledger?.parentGroup).toBe('Sundry Debtors');
    });

    it('the hexadecimal &#x4; form of the same illegal reference is sanitized identically', () => {
      const ledger = mapFirstLedger(ledgerWithParent('&#x4; Sundry Creditors'));
      expect(ledger?.parentGroup).toBe('Sundry Creditors');
    });

    it('a literal 0x04 control byte (not a numeric reference) is also sanitized', () => {
      const ledger = mapFirstLedger(ledgerWithParent(' Sundry Debtors'));
      expect(ledger?.parentGroup).toBe('Sundry Debtors');
    });

    it('an ampersand, properly entity-encoded by Tally, decodes to a literal &', () => {
      const ledger = mapFirstLedger(ledgerWithParent('R &amp; D Distributors'));
      expect(ledger?.parentGroup).toBe('R & D Distributors');
    });

    it('angle brackets, properly entity-encoded, decode to literal < and > without being reparsed as markup', () => {
      const ledger = mapFirstLedger(ledgerWithParent('Sundry Debtors &lt;Retail&gt;'));
      expect(ledger?.parentGroup).toBe('Sundry Debtors <Retail>');
    });

    it('double and single quotes, properly entity-encoded, decode to literal quote characters', () => {
      const ledger = mapFirstLedger(ledgerWithParent('Sundry Debtors &quot;VIP&quot; &amp; Distributor&apos;s Group'));
      expect(ledger?.parentGroup).toBe(`Sundry Debtors "VIP" & Distributor's Group`);
    });

    it('legal XML whitespace -- newline and tab -- inside the value is preserved, not stripped', () => {
      const ledger = mapFirstLedger(ledgerWithParent('Sundry Debtors\n\tRetail Division'));
      expect(ledger?.parentGroup).toContain('\n');
      expect(ledger?.parentGroup).toContain('\t');
      expect(ledger?.parentGroup).toBe('Sundry Debtors\n\tRetail Division');
    });

    it('Unicode business-script text (Devanagari) round-trips exactly', () => {
      const ledger = mapFirstLedger(ledgerWithParent('विक्रेता समूह'));
      expect(ledger?.parentGroup).toBe('विक्रेता समूह');
    });

    it('unusual punctuation and symbols round-trip exactly', () => {
      const value = 'Sundry Debtors — (Retail)/Wholesale @2026! #1 100%';
      const ledger = mapFirstLedger(ledgerWithParent(escapeForXmlText(value)));
      expect(ledger?.parentGroup).toBe(value);
    });

    it('nested-tag-looking text (a fully escaped literal tag) decodes to a plain string, never reparsed as XML', () => {
      const ledger = mapFirstLedger(ledgerWithParent('&lt;PARENT&gt;Nested-looking text&lt;/PARENT&gt;'));
      expect(ledger?.parentGroup).toBe('<PARENT>Nested-looking text</PARENT>');
    });

    it('a very long group name (500+ characters) is not truncated', () => {
      const longName = 'Sundry Debtors ' + 'X'.repeat(500);
      const ledger = mapFirstLedger(ledgerWithParent(longName));
      expect(ledger?.parentGroup).toHaveLength(longName.length);
      expect(ledger?.parentGroup).toBe(longName);
    });

    it('a combination of illegal references, entities, Unicode, and long text all resolve correctly together', () => {
      const combined = '&#4;Sundry Debtors &amp; Co. &lt;विक्रेता&gt; ' + 'Y'.repeat(200) + '\n\t"end"';
      const ledger = mapFirstLedger(ledgerWithParent(combined));
      expect(ledger?.parentGroup).toBe('Sundry Debtors & Co. <विक्रेता> ' + 'Y'.repeat(200) + '\n\t"end"');
    });

    it('an empty PARENT element maps to undefined, not a crash or an empty string', () => {
      const ledger = mapFirstLedger(ledgerWithParent(''));
      expect(ledger?.parentGroup).toBeUndefined();
    });

    it('a wholly absent PARENT element (no tag at all) also maps to undefined', () => {
      const rawXml = [
        '<ENVELOPE><BODY><DATA><COLLECTION>',
        '<LEDGER NAME="Test Ledger"><GUID>guid-1</GUID></LEDGER>',
        '</COLLECTION></DATA></BODY></ENVELOPE>',
      ].join('');
      const document = collectionParser.parseDocument(rawXml);
      const nodes = collectionParser.parseNodes(document, { nodeName: 'LEDGER' });
      const ledger = mapLedger(collectionParser, nodes[0]!);
      expect(ledger?.parentGroup).toBeUndefined();
    });
  });
});

function escapeForXmlText(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}
