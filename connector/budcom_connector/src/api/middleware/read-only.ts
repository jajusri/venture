import type { NextFunction, Request, Response } from 'express';

const ALLOWED_WRITE_ROUTES = new Set([
  'POST /device/pair',
  'POST /session/company',
  'DELETE /session/company',
  'POST /session/validate',
  'POST /sync/ledgers',
  'POST /sync/ledgers/clear-cache',
]);

/**
 * Rejects any mutating HTTP method except explicit pairing.
 * MVP 1 connector must remain read-only toward Tally.
 */
export function readOnlyMiddleware(req: Request, res: Response, next: NextFunction): void {
  const method = req.method.toUpperCase();
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    next();
    return;
  }

  const routeKey = `${method} ${req.path}`;
  if (ALLOWED_WRITE_ROUTES.has(routeKey)) {
    next();
    return;
  }

  res.status(405).json({
    code: 'READ_ONLY_VIOLATION',
    message: 'This connector accepts read-only requests in MVP 1.',
  });
}
