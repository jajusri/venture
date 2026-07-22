import { describe, expect, it } from 'vitest';

import { assessDerivedUnits, assessEnvelope } from '../../../src/tally/contracts/response-contract.js';

describe('response-contract safety', () => {
  it('flags INCOMPLETE when stock items exist but no unit could be derived', () => {
    const result = assessDerivedUnits(0, 1502);
    expect(result.status).toBe('INCOMPLETE');
    expect(result.reason).toContain('units cannot be derived');
  });

  it('reports EMPTY when there are no source stock items', () => {
    expect(assessDerivedUnits(0, 0).status).toBe('EMPTY');
  });

  it('reports COMPLETE when units were derived', () => {
    expect(assessDerivedUnits(5, 100).status).toBe('COMPLETE');
  });

  it('detects schema drift when the response is not an ENVELOPE', () => {
    expect(assessEnvelope('<HTML>error</HTML>')?.status).toBe('DRIFT');
    expect(assessEnvelope('<ENVELOPE><BODY/></ENVELOPE>')).toBeUndefined();
  });
});
