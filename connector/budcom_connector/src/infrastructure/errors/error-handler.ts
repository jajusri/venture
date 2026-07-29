import type { NextFunction, Request, Response } from 'express';

import { sanitizeLogDetails, sanitizeLogMessage } from '../privacy/log-context-sanitizer.js';
import type { Logger } from '../logging/logger.js';
import { ErrorCodes, isAppError } from './app-error.js';

export function createErrorMiddleware(logger: Logger) {
  return (error: unknown, _req: Request, res: Response, _next: NextFunction): void => {
    if (
      typeof error === 'object'
      && error !== null
      && 'type' in error
      && (error as { type?: string }).type === 'entity.too.large'
    ) {
      res.status(413).json({
        code: 'PAYLOAD_TOO_LARGE',
        message: 'Request body exceeds the allowed size limit.',
      });
      return;
    }

    if (isAppError(error)) {
      if (error.statusCode === 400) {
        logger.warn('api.validation.failed', {
          event: 'api.validation.failed',
          code: error.code,
          statusCode: error.statusCode,
        });
      }
      if (error.statusCode >= 500 && error.code !== ErrorCodes.NOT_IMPLEMENTED) {
        const safeDetails = sanitizeLogDetails(error.details);
        logger.error(sanitizeLogMessage(error.message), {
          code: error.code,
          statusCode: error.statusCode,
          ...(safeDetails ?? {}),
        });
      }
      res.status(error.statusCode).json(error.toResponse());
      return;
    }

    logger.error('Unhandled error', {
      code: ErrorCodes.INTERNAL_ERROR,
    });

    res.status(500).json({
      code: ErrorCodes.INTERNAL_ERROR,
      message: 'An unexpected error occurred.',
    });
  };
}

export function asyncHandler(
  handler: (req: Request, res: Response, next: NextFunction) => Promise<void>,
) {
  return (req: Request, res: Response, next: NextFunction): void => {
    handler(req, res, next).catch(next);
  };
}
