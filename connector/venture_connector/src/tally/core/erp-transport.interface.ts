import type { ErpTransportRequest, ErpTransportResponse } from './types.js';

/**
 * Abstract ERP transport — implement for Tally today, other ERP systems later.
 * Keeps HTTP/XML details out of domain services.
 */
export interface ErpTransport {
  send(request: ErpTransportRequest): Promise<ErpTransportResponse>;
  close(): Promise<void>;
}
