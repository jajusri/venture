import type { NextFunction, Request, RequestHandler, Response } from 'express';

import type { ConnectorConfig } from '../../config/defaults.js';
import type { ConnectorIdentityRepository } from '../../services/identity/connector-identity-repository.js';
import {
  DeviceCredentialAuthenticator,
  type DeviceAuthPrincipal,
} from '../../services/auth/device-credential-authenticator.js';
import type { PairingDeviceCredentialRepository } from '../../services/pairing/pairing-device-credential-repository.js';
import type { TrustedDeviceRepository } from '../../services/device/trusted-device-repository.js';
import { createRequireTrustedDeviceAuthMiddleware } from './require-trusted-device-auth.js';

declare module 'express-serve-static-core' {
  interface Request {
    /**
     * Populated only by createRequireBusinessRouteAuthMiddleware, only once secureLanRouteProtectionEnabled
     * is true and the request authenticated on LAN. Lets a specific downstream route (e.g. GET
     * /device/trusted-companies in device.ts) restrict which principal *kind* it accepts without
     * re-validating the already-authenticated credential a second time. Never set on loopback,
     * never set while the flag is off, never set for the Desktop-control-only admin routes this
     * middleware deliberately does not authenticate (see DESKTOP_CONTROL_ONLY_ROUTES below).
     */
    deviceAuthPrincipal?: DeviceAuthPrincipal;
  }
}

export interface RequireBusinessRouteAuthDeps {
  readonly config: Pick<
    ConnectorConfig,
    'networkExposure' | 'requireDeviceAuthForLan' | 'secureLanRouteProtectionEnabled'
  >;
  readonly trustedDevices?: TrustedDeviceRepository;
  readonly pairingCredentials?: PairingDeviceCredentialRepository;
  readonly connectorIdentity: ConnectorIdentityRepository;
}

/**
 * Routes that must never be gated by this middleware's own device-credential check, even while
 * secureLanRouteProtectionEnabled is active: each is an administrative action already
 * independently and unconditionally restricted to Desktop's own control-token boundary (see
 * createDeviceManagementRouter's conditional wiring and createPairingCredentialManagementRouter
 * in pairing.ts, both mounted after this middleware). Requiring both a device credential here
 * AND a control token there would mean either (a) a valid Android device principal could
 * enumerate/revoke other devices' trust, which this phase's fixed decisions forbid outright, or
 * (b) Desktop itself — which never holds a device credential — would be newly locked out of
 * pairing-credential administration the moment this flag is turned on, a regression this phase
 * must not introduce. Matched by method + path, mirroring the exact path patterns registered in
 * device.ts / pairing.ts.
 */
const DESKTOP_CONTROL_ONLY_ROUTES: ReadonlyArray<{ readonly method: string; readonly pattern: RegExp }> = [
  { method: 'GET', pattern: /^\/device\/list$/ },
  { method: 'DELETE', pattern: /^\/device\/[^/]+$/ },
  { method: 'POST', pattern: /^\/device\/pairing-credential\/revoke$/ },
  { method: 'GET', pattern: /^\/device\/pairing-credentials$/ },
];

function isDesktopControlOnlyRoute(req: Request): boolean {
  const method = req.method.toUpperCase();
  return DESKTOP_CONTROL_ONLY_ROUTES.some((route) => route.method === method && route.pattern.test(req.path));
}

function rejectInsecureTransport(res: Response): void {
  res.status(400).json({
    ok: false,
    code: 'INSECURE_TRANSPORT',
    message: 'This request requires a secure (HTTPS) connection.',
  });
}

function unauthorized(res: Response): void {
  res.status(401).json({
    code: 'UNAUTHORIZED',
    message: 'A valid device credential is required to access this Connector over LAN.',
  });
}

/**
 * Dormant (default-off) secure LAN business-route policy. Composes with, rather than duplicates
 * or stacks on top of, the pre-existing legacy gate (require-trusted-device-auth.ts):
 *
 *  - secureLanRouteProtectionEnabled === false (the default): delegates the entire decision to
 *    the untouched legacy requireTrustedDeviceAuth middleware, so every existing installation's
 *    behavior remains byte-for-byte unchanged regardless of requireDeviceAuthForLan's value.
 *  - secureLanRouteProtectionEnabled === true: the legacy middleware is never invoked for this
 *    request at all — this branch is the sole authority, which structurally rules out double
 *    authentication rather than merely avoiding it by careful reimplementation.
 *  - Loopback traffic always passes through unconditionally, regardless of the flag — Desktop is
 *    never required to hold a device credential.
 *
 * When active on LAN: HTTPS is required before anything else is inspected — a plaintext request
 * is rejected before the Authorization header is even read, before DeviceCredentialAuthenticator
 * is constructed, and before any downstream handler or Tally access can occur. Every
 * authentication failure (missing header, malformed bearer, unknown/revoked/wrong-Connector
 * token, or the required repositories not being wired up in this deployment) produces the exact
 * same generic 401, so a caller can never learn which check failed or whether a presented
 * credential was merely unrecognized versus actually revoked.
 *
 * The two legacy device-administration routes (GET /device/list, DELETE /device/:deviceRecordId)
 * are deliberately exempted from the credential check below — see DESKTOP_CONTROL_ONLY_ROUTES —
 * and instead rely solely on device.ts's own conditional requireDesktopControlToken wiring.
 */
export function createRequireBusinessRouteAuthMiddleware(deps: RequireBusinessRouteAuthDeps): RequestHandler {
  const legacyMiddleware = createRequireTrustedDeviceAuthMiddleware({
    config: deps.config,
    trustedDevices: deps.trustedDevices,
  });

  return (req: Request, res: Response, next: NextFunction) => {
    if (!deps.config.secureLanRouteProtectionEnabled) {
      legacyMiddleware(req, res, next);
      return;
    }

    if (deps.config.networkExposure !== 'lan') {
      next();
      return;
    }

    if (!req.secure) {
      rejectInsecureTransport(res);
      return;
    }

    if (isDesktopControlOnlyRoute(req)) {
      next();
      return;
    }

    if (!deps.pairingCredentials || !deps.trustedDevices) {
      unauthorized(res);
      return;
    }

    const authenticator = new DeviceCredentialAuthenticator(
      deps.pairingCredentials,
      deps.connectorIdentity,
      deps.trustedDevices,
    );
    const result = authenticator.authenticate(req);
    if (!result.ok) {
      unauthorized(res);
      return;
    }

    req.deviceAuthPrincipal = result.principal;
    next();
  };
}
