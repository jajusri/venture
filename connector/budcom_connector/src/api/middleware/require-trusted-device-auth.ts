import type { NextFunction, Request, RequestHandler, Response } from 'express';

import type { ConnectorConfig } from '../../config/defaults.js';
import type { TrustedDeviceRepository } from '../../services/device/trusted-device-repository.js';

export interface RequireTrustedDeviceAuthDeps {
  readonly config: Pick<ConnectorConfig, 'networkExposure' | 'requireDeviceAuthForLan'>;
  readonly trustedDevices?: TrustedDeviceRepository;
}

const BEARER_PREFIX = 'Bearer ';

/**
 * Gated bearer-token enforcement for the LAN data routes (companies/session/master-data/
 * ledgers/stock-items/vouchers). Pairing (/device/*), /health, and /diagnostics stay open —
 * discovery must remain separate from authorization.
 *
 * Pass-through (no-op) unless BOTH:
 *   - config.networkExposure === 'lan' (loopback-only installs are unaffected), AND
 *   - config.requireDeviceAuthForLan === true (defaults to false; see config/defaults.ts)
 *
 * This keeps every existing installation's behavior byte-for-byte unchanged today. It exists so
 * the "smallest secure milestone" documented in the release-gate report is real, reviewable,
 * independently tested code rather than only a design note.
 */
export function createRequireTrustedDeviceAuthMiddleware(
  deps: RequireTrustedDeviceAuthDeps,
): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    if (deps.config.networkExposure !== 'lan' || !deps.config.requireDeviceAuthForLan) {
      next();
      return;
    }

    const header = req.header('authorization') ?? req.header('Authorization');
    if (!header || !header.startsWith(BEARER_PREFIX)) {
      unauthorized(res, 'A Bearer token is required to access this Connector over LAN.');
      return;
    }

    const token = header.slice(BEARER_PREFIX.length).trim();
    if (!token) {
      unauthorized(res, 'A Bearer token is required to access this Connector over LAN.');
      return;
    }

    if (!deps.trustedDevices) {
      unauthorized(res, 'Device pairing is not available in this deployment.');
      return;
    }

    const record = deps.trustedDevices.validateToken(token);
    if (!record) {
      unauthorized(res, 'Token is invalid or has been revoked.');
      return;
    }

    next();
  };
}

function unauthorized(res: Response, message: string): void {
  res.status(401).json({ code: 'UNAUTHORIZED', message });
}
