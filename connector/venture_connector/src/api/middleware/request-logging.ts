import { randomUUID } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';

import type { Logger } from '../../infrastructure/logging/logger.js';

export function createRequestLoggingMiddleware(logger: Logger) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const correlationId = normalizeCorrelationId(req.header('x-correlation-id')) ?? randomUUID();
    const startedAt = Date.now();
    logger.info('api.request.started', {
      event: 'api.request.started',
      correlationId,
      httpMethod: req.method,
      httpPath: sanitizePath(req.path),
      remoteClientIp: sanitizeIp(req.ip),
    });
    res.setHeader('x-correlation-id', correlationId);
    res.once('finish', () => {
      logger.info('api.request.completed', {
        event: 'api.request.completed',
        correlationId,
        httpMethod: req.method,
        httpRoute: req.route?.path ?? sanitizePath(req.path),
        httpPath: sanitizePath(req.path),
        remoteClientIp: sanitizeIp(req.ip),
        statusCode: res.statusCode,
        durationMs: Math.max(0, Date.now() - startedAt),
      });
    });
    next();
  };
}

function sanitizePath(path: string): string {
  if (path === '/companies' || path === '/health' || path === '/ready') return path;
  return path.startsWith('/companies/') ? '/companies/:companyId/*' : 'other';
}

function sanitizeIp(value: string | undefined): string {
  if (!value) return 'unknown';
  return value.replace(/^::ffff:/, '');
}

function normalizeCorrelationId(value: string | undefined): string | null {
  const normalized = value?.trim();
  return normalized && /^[A-Za-z0-9._:-]{1,128}$/.test(normalized) ? normalized : null;
}
