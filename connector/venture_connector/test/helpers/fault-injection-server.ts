import http from 'node:http';
import type { AddressInfo } from 'node:net';

import {
  SAMPLE_LEDGERS_RESPONSE,
  SAMPLE_STOCK_ITEMS_RESPONSE,
} from './master-data-fixtures.js';
import { SAMPLE_LICENSE_INFO_RESPONSE, SAMPLE_TALLY_COMPANY_LIST_RESPONSE } from './mock-fetch.js';

export type FaultMode =
  | 'reset-before-headers'
  | 'reset-every-connection'
  | 'partial-body-destroy'
  | 'stall-after-headers'
  | 'truncated-xml'
  | 'complete-body'
  | 'chunked-premature-end'
  | 'content-length-mismatch';

export interface FaultInjectionServerOptions {
  readonly mode: FaultMode;
  readonly body?: string;
  readonly partialBody?: string;
  readonly stallMs?: number;
  readonly closeAfterResponse?: boolean;
}

export interface FaultInjectionServerStats {
  readonly requestCount: number;
  readonly ledgerRequestCount: number;
  readonly stockRequestCount: number;
}

/**
 * Loopback HTTP server that emulates Tally fault conditions deterministically.
 * Routes company/ping requests to safe fixtures; applies fault mode to collection exports.
 */
export class FaultInjectionTallyServer {
  private server: http.Server | null = null;
  private options: FaultInjectionServerOptions = { mode: 'complete-body' };
  private requestCount = 0;
  private ledgerRequestCount = 0;
  private stockRequestCount = 0;

  get port(): number {
    const address = this.server?.address();
    if (!address || typeof address === 'string') {
      return 0;
    }
    return (address as AddressInfo).port;
  }

  get stats(): FaultInjectionServerStats {
    return {
      requestCount: this.requestCount,
      ledgerRequestCount: this.ledgerRequestCount,
      stockRequestCount: this.stockRequestCount,
    };
  }

  configure(options: FaultInjectionServerOptions): void {
    this.options = options;
  }

  async start(options: FaultInjectionServerOptions = { mode: 'complete-body' }): Promise<void> {
    this.configure(options);
    this.requestCount = 0;
    this.ledgerRequestCount = 0;
    this.stockRequestCount = 0;

    if (this.server) {
      await this.close();
    }

    await new Promise<void>((resolve, reject) => {
      const server = http.createServer((req, res) => {
        void this.handleRequest(req, res);
      });

      server.once('error', reject);
      if (this.options.mode === 'reset-every-connection') {
        server.on('connection', (socket) => {
          socket.destroy();
        });
      }
      server.listen(0, '127.0.0.1', () => {
        this.server = server;
        resolve();
      });
    });
  }

  async close(): Promise<void> {
    if (!this.server) {
      return;
    }
    await new Promise<void>((resolve, reject) => {
      this.server!.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        this.server = null;
        resolve();
      });
    });
  }

  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
    this.requestCount += 1;
    const chunks: Buffer[] = [];
    for await (const chunk of req) {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    }
    const requestBody = Buffer.concat(chunks).toString('utf8');

    if (requestBody.includes('List of Companies')) {
      this.respondComplete(res, SAMPLE_TALLY_COMPANY_LIST_RESPONSE);
      return;
    }
    if (requestBody.includes('List of Ledgers')) {
      this.ledgerRequestCount += 1;
      if (this.options.mode === 'reset-before-headers') {
        req.socket?.destroy();
        return;
      }
      await this.applyFaultMode(res);
      return;
    }
    if (requestBody.includes('List of Stock Items')) {
      this.stockRequestCount += 1;
      if (this.options.mode === 'reset-before-headers') {
        req.socket?.destroy();
        return;
      }
      await this.applyFaultMode(res, this.options.body ?? SAMPLE_STOCK_ITEMS_RESPONSE);
      return;
    }

    this.respondComplete(res, SAMPLE_LICENSE_INFO_RESPONSE);
  }

  private async applyFaultMode(res: http.ServerResponse, body = this.options.body ?? SAMPLE_LEDGERS_RESPONSE): Promise<void> {
    const { mode, partialBody, stallMs, closeAfterResponse } = this.options;

    switch (mode) {
      case 'partial-body-destroy': {
        const payload = partialBody ?? body.slice(0, Math.max(32, Math.floor(body.length / 3)));
        res.writeHead(200, { 'Content-Type': 'text/xml' });
        res.write(payload);
        res.socket?.destroy();
        return;
      }
      case 'stall-after-headers': {
        res.writeHead(200, { 'Content-Type': 'text/xml' });
        if (stallMs && stallMs > 0) {
          await new Promise((resolve) => setTimeout(resolve, stallMs));
        }
        return;
      }
      case 'truncated-xml': {
        const truncated = partialBody ?? body.replace(/<\/ENVELOPE>\s*$/i, '');
        this.respondComplete(res, truncated);
        return;
      }
      case 'chunked-premature-end': {
        res.writeHead(200, { 'Content-Type': 'text/xml', 'Transfer-Encoding': 'chunked' });
          res.write(body.slice(0, Math.max(32, Math.floor(body.length / 2))));
        res.socket?.destroy();
        return;
      }
      case 'content-length-mismatch': {
        const declared = body.length + 4096;
        res.writeHead(200, {
          'Content-Type': 'text/xml',
          'Content-Length': String(declared),
        });
        res.end(body);
        if (closeAfterResponse) {
          await this.close();
        }
        return;
      }
      case 'complete-body':
      default: {
        this.respondComplete(res, body);
        if (closeAfterResponse) {
          await this.close();
        }
      }
    }
  }

  private respondComplete(res: http.ServerResponse, body: string): void {
    res.writeHead(200, { 'Content-Type': 'text/xml', 'Content-Length': String(Buffer.byteLength(body)) });
    res.end(body);
  }
}

export function buildLedgersResponse(recordCount: number): string {
  const ledgers = Array.from({ length: recordCount }, (_, index) => {
    const suffix = String(index).padStart(12, '0');
    return `<LEDGER NAME="Ledger ${index}">
          <NAME>Ledger ${index}</NAME>
          <PARENT>Cash-in-Hand</PARENT>
          <GUID TYPE="String">aaaaaaaa-bbbb-cccc-dddd-${suffix}</GUID>
        </LEDGER>`;
  }).join('\n        ');
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

export function buildStockItemsResponse(recordCount: number): string {
  const items = Array.from({ length: recordCount }, (_, index) => {
    const suffix = String(index).padStart(12, '0');
    return `<STOCKITEM NAME="Item ${index}">
          <NAME>Item ${index}</NAME>
          <GUID TYPE="String">bbbbbbbb-cccc-dddd-eeee-${suffix}</GUID>
          <PARENT>Finished Goods</PARENT>
          <BASEUNITS>Nos</BASEUNITS>
        </STOCKITEM>`;
  }).join('\n        ');
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
