import type { Request } from 'express';

import type { ConnectorIdentityRepository } from '../identity/connector-identity-repository.js';
import { parseBearerCredential } from '../../api/middleware/pairing-credential-bearer.js';
import { PairingCredentialAuthenticator, type PairingCredentialPrincipal } from '../pairing/pairing-credential-authenticator.js';
import type { PairingDeviceCredentialRepository } from '../pairing/pairing-device-credential-repository.js';
import type { TrustedDeviceRepository } from '../device/trusted-device-repository.js';

/**
 * Principal for the pre-existing, company-bound `trusted_devices` credential (see
 * require-trusted-device-auth.ts / trusted-device-repository.ts). Kept distinct from
 * PairingCredentialPrincipal on purpose: a pairing credential proves Connector/device trust
 * only, never company authorization — see the doc comment on DeviceCredentialAuthenticator below.
 */
export interface LegacyTrustedDevicePrincipal {
  readonly kind: 'trusted-device';
  readonly deviceRecordId: string;
  readonly companyId: string;
  readonly installationId: string;
  readonly friendlyName: string | null;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
}

export type DeviceAuthPrincipal = PairingCredentialPrincipal | LegacyTrustedDevicePrincipal;

export type DeviceCredentialAuthResult =
  | { readonly ok: true; readonly principal: DeviceAuthPrincipal }
  | { readonly ok: false };

/**
 * Reusable future-authentication adapter — NOT wired into any route or middleware by this phase.
 * It exists only so a later business-route cutover has a single place to classify an incoming
 * bearer credential as either family without duplicating either authenticator's logic. No current
 * route, middleware behavior, or feature-flag default changes as a result of this class existing.
 *
 * Tries the pairing-credential family first (self-status/self-revoke's own authenticator), then
 * falls back to the legacy trusted-device family via the same strict bearer parser
 * (pairing-credential-bearer.ts) and TrustedDeviceRepository.validateToken(). Both credential
 * kinds are generated as crypto.randomBytes(32).toString('base64url'), which the strict parser's
 * b64token charset accepts, so this is not expected to reject any token the legacy
 * require-trusted-device-auth.ts middleware would accept in practice — but the two parsers are
 * not byte-identical (the strict parser does not trim incidental whitespace the legacy one does),
 * and this adapter is not wired into any route, so that divergence has no live effect today.
 * Deliberately does NOT collapse the two principal kinds into one shape: a pairing
 * credential is Connector/device trust only, while a trusted-device credential is company-bound
 * authorization — merging them would silently grant company-scoped access to a credential that
 * never proved it, which this adapter must never do.
 */
export class DeviceCredentialAuthenticator {
  private readonly pairingAuthenticator: PairingCredentialAuthenticator;

  constructor(
    pairingCredentials: PairingDeviceCredentialRepository,
    connectorIdentity: ConnectorIdentityRepository,
    private readonly trustedDevices: TrustedDeviceRepository,
  ) {
    this.pairingAuthenticator = new PairingCredentialAuthenticator(pairingCredentials, connectorIdentity);
  }

  authenticate(req: Request): DeviceCredentialAuthResult {
    const pairingResult = this.pairingAuthenticator.authenticate(req);
    if (pairingResult.ok) {
      return { ok: true, principal: pairingResult.principal };
    }

    const parsed = parseBearerCredential(req);
    if (!parsed.ok) {
      return { ok: false };
    }

    const record = this.trustedDevices.validateToken(parsed.token);
    if (!record) {
      return { ok: false };
    }

    return {
      ok: true,
      principal: {
        kind: 'trusted-device',
        deviceRecordId: record.deviceRecordId,
        companyId: record.companyId,
        installationId: record.installationId,
        friendlyName: record.friendlyName,
        createdAt: record.createdAt,
        lastUsedAt: record.lastUsedAt,
      },
    };
  }
}
