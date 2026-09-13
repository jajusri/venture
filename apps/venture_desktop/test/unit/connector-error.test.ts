import { describe, expect, it } from 'vitest';

import { mapSelectionUserMessage, toUserMessage, ConnectorRequestError } from '../../src/application/connector-error.js';

describe('connector-error', () => {
  it('maps connector request errors to user messages', () => {
    const error = new ConnectorRequestError(503, 'SERVICE_UNAVAILABLE', 'Tally is unavailable');
    expect(toUserMessage(error)).toBe('Tally is unavailable');
  });

  it('maps abort errors to timeout guidance', () => {
    const error = new Error('Aborted');
    error.name = 'AbortError';
    expect(toUserMessage(error)).toContain('did not respond');
  });

  it('maps selection statuses to friendly messages', () => {
    expect(mapSelectionUserMessage('COMPANY_NOT_FOUND')).toContain('not found');
    expect(mapSelectionUserMessage('SUCCESS')).toContain('successfully');
  });
});
