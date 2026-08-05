import crypto from 'node:crypto';

/**
 * Generates a fresh Desktop control token for one managed Connector child-process launch.
 *
 * 256 bits of CSPRNG entropy (matches the Connector's own bearer-token generation convention —
 * see connector/budcom_connector/src/services/device/trusted-device-repository.ts). Callers must
 * never persist, log, or return this value through IPC — it exists only in Desktop main-process
 * memory for the lifetime of the Connector process it was generated for, passed to that child
 * via the BUDCOM_DESKTOP_CONTROL_TOKEN environment variable and attached as a request header on
 * Desktop's own outgoing pairing-control requests (see mobile-pairing-service.ts).
 */
export function generateDesktopControlToken(): string {
  return crypto.randomBytes(32).toString('base64url');
}
