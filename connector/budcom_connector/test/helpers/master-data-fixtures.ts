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
