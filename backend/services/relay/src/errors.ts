export interface RelayErrorBody { readonly error: { readonly code: string; readonly message: string; readonly requestId: string } }
export class RelayServiceError extends Error {
  constructor(readonly code: string, message: string, readonly statusCode: number, readonly retryAfterMs?: number) { super(message); }
}
