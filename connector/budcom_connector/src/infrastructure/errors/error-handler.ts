import type { NextFunction, Request, Response } from 'express';

import type { Logger } from '../logging/logger.js';
import { ErrorCodes, isAppError } from './app-error.js';

export function createErrorMiddleware(logger: Logger) {
  return (error: unknown, _req: Request, res: Response, _next: NextFunction): void => {
    if (isAppError(error)) {
      if (error.statusCode >= 500 && error.code !== ErrorCodes.NOT_IMPLEMENTED) {
        logger.error(error.message, { code: error.code, details: error.details });
      }
      res.status(error.statusCode).json(error.toResponse());
      return;
    }

    logger.error('Unhandled error', {
      code: ErrorCodes.INTERNAL_ERROR,
      error: error instanceof Error ? error.message : String(error),
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
