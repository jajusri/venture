export interface MockFetchResponse {
  readonly status?: number;
  readonly body: string;
  readonly headers?: Record<string, string>;
  readonly delayMs?: number;
}

export interface MockFetchCall {
  readonly url: string;
  readonly init?: RequestInit;
}

export function createMockFetch(
  handler: (call: MockFetchCall, index: number) => MockFetchResponse | Promise<MockFetchResponse>,
) {
  const calls: MockFetchCall[] = [];

  const fetchImpl = async (url: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const index = calls.length;
    calls.push({ url: String(url), init });
    const result = await handler(calls[index], index);
    if (result.delayMs) {
      await new Promise((resolve) => setTimeout(resolve, result.delayMs));
    }

    const headers = new Headers(result.headers ?? { 'content-type': 'text/xml' });
    return new Response(result.body, {
      status: result.status ?? 200,
      headers,
    });
  };

  return { fetchImpl, calls };
}

export const SAMPLE_LICENSE_INFO_RESPONSE = `<ENVELOPE>
  <HEADER>
    <VERSION>1</VERSION>
    <STATUS>1</STATUS>
  </HEADER>
  <BODY>
    <DATA>
      <COLLECTION>
        <LICENSEINFO>Connected</LICENSEINFO>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const SAMPLE_COMPANY_LIST_RESPONSE = `<ENVELOPE>
  <HEADER>
    <VERSION>1</VERSION>
    <STATUS>1</STATUS>
  </HEADER>
  <BODY>
    <DATA>
      <COLLECTION>
        <COMPANY>
          <NAME>Acme Traders Pvt Ltd</NAME>
          <STARTINGFROM>20240401</STARTINGFROM>
          <BOOKSFROM>20240401</BOOKSFROM>
        </COMPANY>
        <COMPANY>
          <NAME>Demo Company</NAME>
          <STARTINGFROM>20230401</STARTINGFROM>
        </COMPANY>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

/** Real Tally format includes CMPINFO count nodes and NAME attributes on COMPANY tags. */
export const SAMPLE_TALLY_COMPANY_LIST_RESPONSE = `<ENVELOPE>
  <BODY>
    <DESC>
      <CMPINFO>
        <COMPANY>0</COMPANY>
      </CMPINFO>
    </DESC>
    <DATA>
      <COLLECTION>
        <COMPANY NAME="ESTIMATION">
          <NAME>ESTIMATION</NAME>
        </COMPANY>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

import {
  SAMPLE_COMPANY_INFO_RESPONSE,
  SAMPLE_EMPTY_COLLECTION_RESPONSE,
  SAMPLE_LEDGER_GROUPS_RESPONSE,
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
  SAMPLE_UNITS_RESPONSE,
} from './master-data-fixtures.js';

export function createMasterDataMockFetch(options: {
  readonly pingOk?: boolean;
  readonly companiesXml?: string;
} = {}) {
  return createMockFetch(({ init }) => {
    const body = typeof init?.body === 'string' ? init.body : '';
    if (body.includes('List of Companies')) {
      return { body: options.companiesXml ?? SAMPLE_TALLY_COMPANY_LIST_RESPONSE };
    }
    if (body.includes('<ID>Company</ID>') || body.includes('Object')) {
      return { body: SAMPLE_COMPANY_INFO_RESPONSE };
    }
    if (body.includes('List of Groups')) return { body: SAMPLE_LEDGER_GROUPS_RESPONSE };
    if (body.includes('List of Ledgers')) return { body: SAMPLE_LEDGERS_RESPONSE };
    if (body.includes('List of Stock Items')) return { body: SAMPLE_STOCK_ITEMS_RESPONSE };
    if (body.includes('List of Units')) return { body: SAMPLE_UNITS_RESPONSE };
    if (body.includes('List of Stock Groups')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of Stock Categories')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of Godowns')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of Cost Categories')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of Cost Centres')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of Voucher Types')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (body.includes('List of GST Registrations')) return { body: SAMPLE_EMPTY_COLLECTION_RESPONSE };
    if (options.pingOk === false) {
      return { status: 503, body: 'Service Unavailable' };
    }
    return { body: SAMPLE_LICENSE_INFO_RESPONSE };
  });
}

export function createTallyMockFetch(options: {
  readonly pingOk?: boolean;
  readonly companiesXml?: string;
}) {
  return createMasterDataMockFetch(options);
}
