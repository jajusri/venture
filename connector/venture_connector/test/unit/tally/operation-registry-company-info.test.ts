import { describe, expect, it } from 'vitest';

import { ApprovedOperationId, getApprovedOperation } from '../../../src/tally/registry/operation-registry.js';

/**
 * TD-040 regression: COMPANY_INFO's `render()` used to call `buildObjectTemplate('Company', ...)`,
 * which emits a structurally incomplete Tally Object-type export (`<TYPE>Object</TYPE>
 * <ID>Company</ID>`, no `<SUBTYPE>`, no `<ID TYPE="Name">`). Sending this exact shape to the real,
 * live TallyPrime instance (2026-08-23, during an unrelated investigation) produced no response and
 * put Tally's own UI into a fault state requiring a restart. `render()` must never again construct
 * or return XML for this operation until a live-validated shape exists -- it must fail closed,
 * before any network call, so `MasterDataService.getCompanyInfo()`'s existing discovery-metadata
 * fallback is what actually runs, not an unverified request to Tally.
 */
describe('COMPANY_INFO operation (TD-040 disabled pending a live-validated request shape)', () => {
  it('render() throws before constructing any Tally XML, never returning a request spec', () => {
    const operation = getApprovedOperation(ApprovedOperationId.CompanyInfo);
    expect(() => operation.render({ companyName: 'ESTIMATION' })).toThrow(/COMPANY_INFO is disabled/);
  });

  it('render() throws even with no company context supplied', () => {
    const operation = getApprovedOperation(ApprovedOperationId.CompanyInfo);
    expect(() => operation.render({})).toThrow(/COMPANY_INFO is disabled/);
  });
});
