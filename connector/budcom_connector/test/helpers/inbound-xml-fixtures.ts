/**
 * Synthetic inbound XML fixtures for Reliability Control #3 tests.
 * Shapes are illustrative unless noted as derived from committed operational fixtures.
 */

export const SYNTHETIC_HEADER_STATUS_ZERO_ONLY_RESPONSE = `<ENVELOPE>
  <HEADER>
    <STATUS>0</STATUS>
  </HEADER>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Status Only Ledger">
          <NAME>Status Only Ledger</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">status-only-guid-000000000001</GUID>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SYNTHETIC_TALLY_LINEERROR_RESPONSE = `<ENVELOPE>
  <HEADER>
    <STATUS>0</STATUS>
  </HEADER>
  <BODY>
    <DATA>
      <LINEERROR>Could not find collection</LINEERROR>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SYNTHETIC_MISSING_ENVELOPE_RESPONSE = '<HTML><BODY>Gateway error</BODY></HTML>';

export const SYNTHETIC_WRONG_ROOT_RESPONSE = `<RESPONSE><DATA><COLLECTION></COLLECTION></DATA></RESPONSE>`;

export const SYNTHETIC_PLACEHOLDER_ONLY_LEDGER_COLLECTION = `<ENVELOPE>
  <BODY>
    <DESC><CMPINFO><LEDGER>0</LEDGER></CMPINFO></DESC>
    <DATA>
      <COLLECTION></COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SYNTHETIC_UNRELATED_LEDGER_IN_SUBTREE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <OTHERDATA>
          <LEDGER NAME="Shadow Ledger">
            <NAME>Shadow Ledger</NAME>
            <PARENT>Cash-in-Hand</PARENT>
            <GUID TYPE="String">shadow-guid-000000000001</GUID>
          </LEDGER>
        </OTHERDATA>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export function buildRichLedgerEnvelope(records: ReadonlyArray<{
  readonly name: string;
  readonly guid: string;
  readonly parent?: string;
  readonly alterId?: string;
  readonly masterId?: string;
}>): string {
  const ledgers = records
    .map(
      (record, index) => `<LEDGER NAME="${record.name}">
          <NAME>${record.name}</NAME>
          <PARENT>${record.parent ?? 'Cash-in-Hand'}</PARENT>
          <GUID TYPE="String">${record.guid}</GUID>
          <ALTERID TYPE="Number">${record.alterId ?? String(1000 + index)}</ALTERID>
          <MASTERID TYPE="Number">${record.masterId ?? String(2000 + index)}</MASTERID>
        </LEDGER>`,
    )
    .join('\n        ');
  return `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        ${ledgers}
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

export function buildRichStockEnvelope(records: ReadonlyArray<{
  readonly name: string;
  readonly guid: string;
  readonly baseUnit?: string;
}>): string {
  const items = records
    .map(
      (record, index) => `<STOCKITEM NAME="${record.name}">
          <NAME>${record.name}</NAME>
          <GUID TYPE="String">${record.guid}</GUID>
          <ALTERID TYPE="Number">${2050 + index}</ALTERID>
          <PARENT>Finished Goods</PARENT>
          <BASEUNITS>${record.baseUnit ?? 'Nos'}</BASEUNITS>
        </STOCKITEM>`,
    )
    .join('\n        ');
  return `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        ${items}
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

export function buildMalformedEntityLedgerEnvelope(): string {
  return `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Valid Ledger">
          <NAME>Valid Ledger</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">valid-guid-000000000001</GUID>
        </LEDGER>
        <LEDGER>
          <PARENT>Sundry Debtors</PARENT>
          <GUID TYPE="String">unnamed-guid-000000000002</GUID>
        </LEDGER>
        <LEDGER>
          <NAME></NAME>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

export function buildDuplicateGuidLedgerEnvelope(): string {
  const guid = 'duplicate-guid-000000000099';
  return `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Ledger A">
          <NAME>Ledger A</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">${guid}</GUID>
        </LEDGER>
        <LEDGER NAME="Ledger B">
          <NAME>Ledger B</NAME>
          <PARENT>Sundry Debtors</PARENT>
          <GUID TYPE="String">${guid}</GUID>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

export const SYNTHETIC_UNRELATED_STOCKITEM_IN_SUBTREE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <OTHERDATA>
          <STOCKITEM NAME="Shadow Item">
            <NAME>Shadow Item</NAME>
            <GUID TYPE="String">shadow-stock-guid-000000000001</GUID>
            <BASEUNITS>Nos</BASEUNITS>
          </STOCKITEM>
        </OTHERDATA>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SYNTHETIC_MISSING_COLLECTION_RESPONSE = `<ENVELOPE><BODY><DATA></DATA></BODY></ENVELOPE>`;

export const SYNTHETIC_NON_XML_RESPONSE = 'Connection refused';

export function buildUnrelatedLedgerOutsideCollectionEnvelope(): string {
  return `<ENVELOPE>
  <BODY>
    <DESC>
      <UNRELATED>
        <LEDGER NAME="Outside Collection">
          <NAME>Outside Collection</NAME>
          <GUID TYPE="String">outside-guid-000000000001</GUID>
        </LEDGER>
      </UNRELATED>
    </DESC>
    <DATA>
      <COLLECTION></COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

export function buildDualCollectionLedgerEnvelope(): string {
  return `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="In First Collection">
          <NAME>In First Collection</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">first-collection-guid-001</GUID>
        </LEDGER>
      </COLLECTION>
      <COLLECTION>
        <OTHERDATA>
          <LEDGER NAME="Shadow Ledger">
            <NAME>Shadow Ledger</NAME>
            <GUID TYPE="String">shadow-guid-000000000002</GUID>
          </LEDGER>
        </OTHERDATA>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}
