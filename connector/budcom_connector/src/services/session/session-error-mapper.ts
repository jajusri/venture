import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { SessionValidationStatus } from '../../erp/session/session-constants.js';
import type { SessionValidationResult } from '../../erp/session/session-results.js';

export function assertSessionValidation(
  validation: SessionValidationResult,
): asserts validation is SessionValidationResult & { status: 'SUCCESS' } {
  if (validation.status === 'SUCCESS') {
    return;
  }

  throw new AppError(
    ErrorCodes.VALIDATION_ERROR,
    validation.reason ?? sessionValidationMessage(validation.status),
    mapSessionValidationToHttpStatus(validation.status),
    {
      sessionStatus: validation.status,
      companyId: validation.companyId,
      companyName: validation.companyName,
    },
  );
}

function mapSessionValidationToHttpStatus(status: SessionValidationStatus): number {
  switch (status) {
    case 'NO_COMPANY_SELECTED':
    case 'SESSION_INVALID':
      return 400;
    case 'COMPANY_NOT_FOUND':
      return 404;
    case 'COMPANY_NOT_ACCESSIBLE':
      return 403;
    case 'SESSION_EXPIRED':
      return 410;
    case 'SUCCESS':
      return 200;
  }
}

function sessionValidationMessage(status: SessionValidationStatus): string {
  switch (status) {
    case 'NO_COMPANY_SELECTED':
      return 'No company is selected for this connector session';
    case 'COMPANY_NOT_FOUND':
      return 'Selected company was not found';
    case 'COMPANY_NOT_ACCESSIBLE':
      return 'Selected company is not accessible';
    case 'SESSION_INVALID':
      return 'Connector session is invalid';
    case 'SESSION_EXPIRED':
      return 'Connector session has expired';
    case 'SUCCESS':
      return 'Session validation succeeded';
  }
}
