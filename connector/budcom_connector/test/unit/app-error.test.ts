import { describe, expect, it } from 'vitest';

import {
  AppError,
  ErrorCodes,
  isAppError,
  notImplemented,
} from '../../src/infrastructure/errors/app-error.js';

describe('AppError', () => {
  it('serializes to response payload', () => {
    const error = new AppError(ErrorCodes.VALIDATION_ERROR, 'Invalid input', 400, {
      field: 'deviceId',
    });
    expect(error.toResponse()).toEqual({
      code: ErrorCodes.VALIDATION_ERROR,
      message: 'Invalid input',
      details: { field: 'deviceId' },
    });
  });

  it('identifies AppError instances', () => {
    expect(isAppError(notImplemented('Feature'))).toBe(true);
    expect(isAppError(new Error('nope'))).toBe(false);
  });
});
