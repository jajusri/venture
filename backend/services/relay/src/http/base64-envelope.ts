import { RelayServiceError } from '../errors.js';

/**
 * Shared canonical-base64 decode for authenticated envelope/request bytes carried in an HTTP body.
 * Extracted from `map-submission.ts`'s own pre-existing `decodeEnvelope` (byte-identical validation:
 * strict base64 alphabet, canonical re-encoding round-trip check) so Mailbox Fetch and
 * Acknowledgement's own authenticated-request field decode exactly the same way Submit's always
 * has, rather than a second slightly-different implementation.
 */
export function decodeBase64Envelope(value: unknown, errorCode: string, fieldName: string): Uint8Array {
  if (typeof value !== 'string' || !value.trim()) throw new RelayServiceError(errorCode, `${fieldName} is required`, 400);
  try {
    if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) throw new Error('invalid base64');
    const decoded = Buffer.from(value, 'base64');
    if (decoded.toString('base64') !== value) throw new Error('non-canonical base64');
    return Uint8Array.from(decoded);
  } catch {
    throw new RelayServiceError(errorCode, `${fieldName} must be base64`, 400);
  }
}
