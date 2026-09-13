import type { Request } from 'express';

import { parseBearerCredential } from '../../api/middleware/pairing-credential-bearer.js';
import type { ConnectorIdentityRepository } from '../identity/connector-identity-repository.js';
import type { PairingDeviceCredentialRepository } from './pairing-device-credential-repository.js';

/**
 * Narrow internal principal produced only after a pairing credential has fully passed every
 * authentication check (well-formed bearer token, known, unrevoked, bound to this Connector's
 * own identity). Deliberately excludes anything this principal must never carry onward: the raw
 * token, the token hash, the pairing secret/short code that originally issued it, any
 * customer/company/accounting identifier, and any transport private-key material. See
 * pairing.ts's self-status route for the (further-sanitized) HTTP-facing shape derived from this.
 */
export interface PairingCredentialPrincipal {
  readonly kind: 'pairing-credential';
  readonly credentialId: string;
  readonly connectorId: string;
  readonly deviceId: string | null;
  readonly deviceLabel: string | null;
  readonly createdAt: string;
  /** The last-used timestamp produced by *this* successful authentication. */
  readonly lastUsedAt: string;
}

/**
 * Internal-only failure classification, used solely so tests (and any future metrics) can
 * distinguish these cases. Every one of them must map to the exact same generic external 401 —
 * see pairing.ts's self-status/self-revoke routes — so a caller can never learn which check
 * failed (missing vs malformed vs unknown vs revoked vs wrong-Connector).
 */
export type PairingCredentialAuthFailureReason =
  | 'missing_token'
  | 'malformed_token'
  | 'unknown_token'
  | 'revoked_token'
  | 'wrong_connector';

export type PairingCredentialAuthResult =
  | { readonly ok: true; readonly principal: PairingCredentialPrincipal }
  | { readonly ok: false; readonly reason: PairingCredentialAuthFailureReason };

/**
 * Authenticates an Android-facing request against a pairing device credential. Used only by the
 * self-status / self-revoke routes (pairing.ts) — never by the Desktop-control-token-gated
 * admin routes, and never by any business route (companies/ledgers/stock/vouchers/sync), which
 * this phase deliberately does not touch.
 *
 * Mutation ordering is deliberate: last_used_at is only ever updated via
 * PairingDeviceCredentialRepository.touchLastUsed(), and only after every check below — including
 * the Connector-identity match — has already passed. findByToken() (read-only, includes revoked
 * rows) is used instead of validateToken() (which mutates on hash-match alone) so that an unknown,
 * revoked, or wrong-Connector token never has a side effect.
 */
export class PairingCredentialAuthenticator {
  constructor(
    private readonly pairingCredentials: PairingDeviceCredentialRepository,
    private readonly connectorIdentity: ConnectorIdentityRepository,
  ) {}

  authenticate(req: Request): PairingCredentialAuthResult {
    const parsed = parseBearerCredential(req);
    if (!parsed.ok) {
      return { ok: false, reason: parsed.reason === 'missing' ? 'missing_token' : 'malformed_token' };
    }

    const record = this.pairingCredentials.findByToken(parsed.token);
    if (!record) {
      return { ok: false, reason: 'unknown_token' };
    }
    if (record.revokedAt) {
      return { ok: false, reason: 'revoked_token' };
    }

    const identity = this.connectorIdentity.getOrCreateIdentity();
    if (record.connectorId !== identity.connectorId) {
      return { ok: false, reason: 'wrong_connector' };
    }

    const lastUsedAt = this.pairingCredentials.touchLastUsed(record.credentialId);

    return {
      ok: true,
      principal: {
        kind: 'pairing-credential',
        credentialId: record.credentialId,
        connectorId: record.connectorId,
        deviceId: record.deviceId,
        deviceLabel: record.deviceLabel,
        createdAt: record.createdAt,
        lastUsedAt,
      },
    };
  }
}
