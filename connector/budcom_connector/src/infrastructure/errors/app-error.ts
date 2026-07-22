export const ErrorCodes = {
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  NOT_IMPLEMENTED: 'NOT_IMPLEMENTED',
  READ_ONLY_VIOLATION: 'READ_ONLY_VIOLATION',
  SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
  CONFIG_ERROR: 'CONFIG_ERROR',
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];

export interface ErrorResponse {
  readonly code: ErrorCode;
  readonly message: string;
  readonly details?: Record<string, unknown>;
}

export class AppError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
    readonly statusCode: number = 500,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'AppError';
  }

  toResponse(): ErrorResponse {
    return {
      code: this.code,
      message: this.message,
      ...(this.details ? { details: this.details } : {}),
    };
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}

export function notImplemented(feature: string): AppError {
  return new AppError(
    ErrorCodes.NOT_IMPLEMENTED,
    `${feature} is not implemented in Milestone 1 foundation.`,
    501,
  );
}
