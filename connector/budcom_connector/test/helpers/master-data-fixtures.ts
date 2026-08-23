export const SAMPLE_LEDGER_GROUPS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DESC><CMPINFO><GROUP>0</GROUP></CMPINFO></DESC>
    <DATA>
      <COLLECTION>
        <GROUP NAME="Sundry Debtors">
          <NAME>Sundry Debtors</NAME>
          <PARENT>Current Assets</PARENT>
          <ISREVENUE>No</ISREVENUE>
        </GROUP>
        <GROUP NAME="Sales Accounts">
          <NAME>Sales Accounts</NAME>
          <PARENT>Primary</PARENT>
          <ISREVENUE>Yes</ISREVENUE>
        </GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_LEDGERS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Cash">
          <NAME>Cash</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">aaaaaaaa-bbbb-cccc-dddd-000000000001</GUID>
          <ALTERID TYPE="Number">1001</ALTERID>
          <MASTERID TYPE="Number">2001</MASTERID>
          <OPENINGBALANCE>1,000.00 Dr</OPENINGBALANCE>
          <CLOSINGBALANCE>2,500.50 Dr</CLOSINGBALANCE>
          <ISBILLWISEON>No</ISBILLWISEON>
        </LEDGER>
        <LEDGER NAME="Acme Corp">
          <NAME>Acme Corp</NAME>
          <PARENT>Sundry Debtors</PARENT>
          <GUID TYPE="String">aaaaaaaa-bbbb-cccc-dddd-000000000002</GUID>
          <ALTERID TYPE="Number">1002</ALTERID>
          <MASTERID TYPE="Number">2002</MASTERID>
          <CLOSINGBALANCE>500.00 Cr</CLOSINGBALANCE>
          <ISBILLWISEON>Yes</ISBILLWISEON>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_SHALLOW_LEDGERS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="Cash">
          <NAME>Cash</NAME>
          <RESERVEDNAME></RESERVEDNAME>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_STOCK_ITEMS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <STOCKITEM NAME="Widget A">
          <NAME>Widget A</NAME>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-0000040b</GUID>
          <ALTERID TYPE="Number">2053</ALTERID>
          <PARENT>Finished Goods</PARENT>
          <BASEUNITS>Nos</BASEUNITS>
          <HSNCODE>8471</HSNCODE>
        </STOCKITEM>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_UNITS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <UNIT NAME="Nos">
          <NAME>Nos</NAME>
          <DECIMALPLACES>0</DECIMALPLACES>
        </UNIT>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_COMPANY_INFO_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COMPANY NAME="ESTIMATION">
        <NAME>ESTIMATION</NAME>
        <STARTINGFROM>20240401</STARTINGFROM>
        <BOOKSFROM>20240401</BOOKSFROM>
        <BASECURRENCY>INR</BASECURRENCY>
        <STATENAME>Maharashtra</STATENAME>
        <GSTREGISTRATIONNUMBER>27AAAAA0000A1Z5</GSTREGISTRATIONNUMBER>
      </COMPANY>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_EMPTY_COLLECTION_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION></COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

/**
 * Real Tally XML shape (2026-08-23 investigation, real ESTIMATION company): a ledger's Alias
 * value(s) -- entered as "Name (alias)" in the ledger master, one or several comma-separated --
 * never arrive as a flat `<ALIAS>` tag. Tally instead folds them into extra `<NAME>` siblings
 * inside `LANGUAGENAME.LIST/NAME.LIST`, alongside the primary name as the first entry. Proved by
 * sending the Connector's exact request directly to a live TallyPrime instance: zero `<ALIAS>`
 * tags across 949 real ledgers, including ones with a confirmed real Alias.
 */
export const SAMPLE_LEDGERS_WITH_LANGUAGENAME_ALIAS_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="4m Plywood &amp; Hw" RESERVEDNAME="">
          <PARENT TYPE="String">Sundry Debtors</PARENT>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-00000835</GUID>
          <MASTERID TYPE="Number"> 2101</MASTERID>
          <LANGUAGENAME.LIST>
            <NAME.LIST TYPE="String">
              <NAME>4m Plywood &amp; Hw</NAME>
              <NAME>8309814428</NAME>
            </NAME.LIST>
            <LANGUAGEID> 1033</LANGUAGEID>
          </LANGUAGENAME.LIST>
        </LEDGER>
        <LEDGER NAME="Balaji Kowkoor" RESERVEDNAME="">
          <PARENT TYPE="String">Sundry Debtors</PARENT>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-00000836</GUID>
          <MASTERID TYPE="Number"> 2102</MASTERID>
          <LANGUAGENAME.LIST>
            <NAME.LIST TYPE="String">
              <NAME>Balaji Kowkoor</NAME>
              <NAME>7877685616</NAME>
              <NAME>616</NAME>
            </NAME.LIST>
            <LANGUAGEID> 1033</LANGUAGEID>
          </LANGUAGENAME.LIST>
        </LEDGER>
        <LEDGER NAME="Shortcut Only Traders" RESERVEDNAME="">
          <PARENT TYPE="String">Sundry Debtors</PARENT>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-00000837</GUID>
          <MASTERID TYPE="Number"> 2103</MASTERID>
          <LANGUAGENAME.LIST>
            <NAME.LIST TYPE="String">
              <NAME>Shortcut Only Traders</NAME>
              <NAME>42</NAME>
            </NAME.LIST>
            <LANGUAGEID> 1033</LANGUAGEID>
          </LANGUAGENAME.LIST>
        </LEDGER>
        <LEDGER NAME="A2z" RESERVEDNAME="">
          <PARENT TYPE="String">Sundry Creditors</PARENT>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-00000a9e</GUID>
          <MASTERID TYPE="Number"> 2718</MASTERID>
          <LANGUAGENAME.LIST>
            <NAME.LIST TYPE="String">
              <NAME>A2z</NAME>
            </NAME.LIST>
            <LANGUAGEID> 1033</LANGUAGEID>
          </LANGUAGENAME.LIST>
        </LEDGER>
        <LEDGER NAME="Flat Alias Traders" RESERVEDNAME="">
          <PARENT TYPE="String">Sundry Debtors</PARENT>
          <GUID TYPE="String">6a2a5ccc-6394-4ccb-bb34-113991142c4f-00000838</GUID>
          <MASTERID TYPE="Number"> 2104</MASTERID>
          <ALIAS>9000000000</ALIAS>
          <LANGUAGENAME.LIST>
            <NAME.LIST TYPE="String">
              <NAME>Flat Alias Traders</NAME>
              <NAME>111</NAME>
            </NAME.LIST>
            <LANGUAGEID> 1033</LANGUAGEID>
          </LANGUAGENAME.LIST>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_UNICODE_LEDGER_RESPONSE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <LEDGER NAME="ग्राहक खाता">
          <NAME>ग्राहक खाता</NAME>
          <PARENT>Sundry Debtors</PARENT>
        </LEDGER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
