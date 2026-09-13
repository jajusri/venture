import type { ConnectorErrorBody, CompanySelectionStatus } from './types.js';

export class ConnectorRequestError extends Error {
  readonly statusCode: number;
  readonly code: string;
  readonly userMessage: string;
  readonly details?: Record<string, unknown>;

  constructor(
    statusCode: number,
    code: string,
    userMessage: string,
    details?: Record<string, unknown>,
  ) {
    super(userMessage);
    this.name = 'ConnectorRequestError';
    this.statusCode = statusCode;
    this.code = code;
    this.userMessage = userMessage;
    this.details = details;
  }
}

export function toUserMessage(error: unknown): string {
  if (error instanceof ConnectorRequestError) {
    return error.userMessage;
  }
  if (error instanceof Error) {
    if (error.name === 'AbortError') {
      return 'The connector did not respond in time. Please check that it is running.';
    }
    if (error.message.includes('fetch failed') || error.message.includes('ECONNREFUSED')) {
      return 'Cannot reach the connector service. Ensure it is running on the configured URL.';
    }
    return 'An unexpected error occurred while contacting the connector.';
  }
  return 'An unexpected error occurred.';
}

export function mapConnectorErrorBody(statusCode: number, body: ConnectorErrorBody): ConnectorRequestError {
  const code = body.code ?? 'REQUEST_FAILED';
  const message = body.message ?? `Connector request failed with HTTP ${statusCode}`;
  return new ConnectorRequestError(statusCode, code, message, body.details);
}

const SELECTION_MESSAGES: Record<CompanySelectionStatus | 'CONNECTOR_UNAVAILABLE', string> = {
  SUCCESS: 'Company selected successfully.',
  DUPLICATE_SELECTION: 'This company is already selected.',
  EMPTY_SELECTION: 'Please choose a company before continuing.',
  INVALID_COMPANY: 'The connector cannot select a company right now. Check Tally connection.',
  COMPANY_NOT_FOUND: 'That company was not found. Refresh the company list and try again.',
  CONNECTOR_UNAVAILABLE: 'Cannot reach the connector service.',
};

export function mapSelectionUserMessage(
  status: CompanySelectionStatus | 'CONNECTOR_UNAVAILABLE',
  reason?: string,
): string {
  if (reason && status !== 'SUCCESS' && status !== 'DUPLICATE_SELECTION') {
    return reason;
  }
  return SELECTION_MESSAGES[status];
}

export function mapDiscoveryUserMessage(status: string, reason?: string): string {
  if (reason) {
    return reason;
  }
  switch (status) {
    case 'EMPTY':
      return 'No companies were found in Tally.';
    case 'UNAVAILABLE':
    case 'TIMEOUT':
      return 'Tally is unavailable for company discovery.';
    case 'DENIED':
      return 'Company discovery is not permitted by connector policy.';
    case 'MALFORMED':
      return 'Company discovery returned unexpected data.';
    default:
      return 'Unable to load companies from the connector.';
  }
}
