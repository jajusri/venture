import { Router } from 'express';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { asyncHandler } from '../../infrastructure/errors/error-handler.js';

export function createDeviceRouter(): Router {
  const router = Router();

  router.post(
    '/device/pair',
    asyncHandler(async (req, _res, _next) => {
      const { deviceId, pairingCode } = req.body ?? {};
      if (!deviceId || !pairingCode) {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          'deviceId and pairingCode are required.',
          400,
        );
      }

      throw new AppError(
        ErrorCodes.NOT_IMPLEMENTED,
        'Device pairing is not implemented in Milestone 1 foundation.',
        501,
      );
    }),
  );

  return router;
}
