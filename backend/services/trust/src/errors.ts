export interface ServiceErrorBody { readonly error: { readonly code: string; readonly message: string; readonly requestId: string } }
export class TrustServiceError extends Error {
  constructor(readonly code: string, message: string, readonly statusCode: number) { super(message); }
}
