import crypto from 'node:crypto';
import type { NextFunction, Request, RequestHandler, Response } from 'express';

import type { ConnectorConfig } from '../../config/defaults.js';

export interface RequireDesktopControlTokenDeps {
  readonly config: Pick<ConnectorConfig, 'networkExposure' | 'desktopControlToken'>;
}

const CONTROL_TOKEN_HEADER = 'x-venture-desktop-control-token';

/**
 * Restricts pairing-session creation/cancellation to Desktop's own control boundary.
 *
 * Pass-through (no-op) when networkExposure !== 'lan' — a loopback-only Connector is already
 * only reachable from this machine, so there's no separate LAN-client threat to gate against.
 *
 * On LAN: requires header `X-Venture-Desktop-Control-Token` to exactly match
 * config.desktopControlToken (constant-time comparison). If no token is configured at all
 * (config.desktopControlToken is null — no Desktop supervisor has supplied one), this fails
 * CLOSED rather than open: there is no legitimate caller for a Connector whose Desktop hasn't
 * issued it a control token yet, so refusing every caller is the safe default, not a gap.
 *
 * Wiring Desktop to actually generate and pass VENTURE_DESKTOP_CONTROL_TOKEN (mirroring the
 * existing VENTURE_CONNECTOR_ID pattern) and to attach this header on its own calls is deferred
 * to the Desktop integration phase. Until then, these routes are correctly unreachable by
 * anyone, including Desktop, which is intentional: nothing calls them yet.
 */
export function createRequireDesktopControlTokenMiddleware(deps: RequireDesktopControlTokenDeps): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    if (deps.config.networkExposure !== 'lan') {
      next();
      return;
    }

    const configured = deps.config.desktopControlToken;
    if (!configured) {
      forbidden(res);
      return;
    }

    const presented = req.header(CONTROL_TOKEN_HEADER);
    if (!presented || !constantTimeStringsMatch(presented, configured)) {
      forbidden(res);
      return;
    }

    next();
  };
}

function forbidden(res: Response): void {
  res.status(403).json({
    code: 'FORBIDDEN',
    message: 'This action is restricted to the Desktop application that owns this Connector.',
  });
}

/** Constant-time comparison via SHA-256 digests so differing input lengths don't leak via `Buffer` length checks on the raw values. */
function constantTimeStringsMatch(a: string, b: string): boolean {
  const hashA = crypto.createHash('sha256').update(a, 'utf8').digest();
  const hashB = crypto.createHash('sha256').update(b, 'utf8').digest();
  return crypto.timingSafeEqual(hashA, hashB);
}
