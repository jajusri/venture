import { describe, expect, it } from 'vitest';

import {
  ApprovedOperationId,
  findApprovedOperationByRequest,
  getApprovedOperation,
  listApprovedOperations,
  RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
  VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
  toPolicyOperation,
} from '../../../src/tally/registry/operation-registry.js';
import { LEDGER_CONTACT_FETCH_FIELDS, LEDGER_RICH_FETCH_FIELDS } from '../../../src/extraction/core/ledger-identity.js';
import { isForbiddenOperation } from '../../../src/tally/registry/forbidden-registry.js';
import { decidePolicy } from '../../../src/erp/policy/policy-engine.js';

describe('operation registry', () => {
  it('marks every production operation as EXPORT and read-only', () => {
    for (const op of listApprovedOperations()) {
      expect(op.tallyRequest).toBe('Export');
      expect(op.capability).toMatch(/^TALLY_(HEALTH|COMPANY|MASTER|REPORT)_READ$/);
    }
  });

  it('carries environment-scoped evidence per operation', () => {
    const ledgers = getApprovedOperation(ApprovedOperationId.Ledgers);
    expect(ledgers.adapterVersion).toBeTruthy();
    expect(ledgers.tallyEvidenceBuild).toContain('ESTIMATION');
    expect(ledgers.classification).toBe('VERIFIED_SAFE');
  });

  it('enriches stock items via TDL FETCH on the approved collection', () => {
    const spec = getApprovedOperation(ApprovedOperationId.StockItems).render({
      companyName: 'ESTIMATION',
    });
    expect(spec.collectionModifyFetch).toContain('GUID');
    expect(spec.collectionModifyFetch).toContain('ALTERID');
    expect(spec.id).toBe('List of Stock Items');
  });

  it('enriches ledgers via TDL FETCH on the approved collection', () => {
    const spec = getApprovedOperation(ApprovedOperationId.Ledgers).render({
      companyName: 'ESTIMATION',
    });
    expect(spec.collectionModifyFetch).toEqual([...LEDGER_RICH_FETCH_FIELDS]);
    expect(spec.id).toBe('List of Ledgers');
  });

  it('disables LEDGERS_CONTACT_DETAILS until its Fetch list is live-validated (render throws)', () => {
    const op = getApprovedOperation(ApprovedOperationId.LedgersContactDetails);
    expect(op.classification).toBe('EXPERIMENTAL_DISABLED');
    expect(op.rolloutStatus).toBe('disabled');
    expect(op.tallyId).toBe('List of Ledgers');
    expect(() => op.render({ companyName: 'ESTIMATION' })).toThrow(/LEDGERS_CONTACT_DETAILS is disabled/);
  });

  it(
    'documents that findApprovedOperationByRequest resolves the shared "List of Ledgers" tallyId ' +
      'to the pre-existing LEDGERS entry, not LEDGERS_CONTACT_DETAILS -- so that operation\'s own ' +
      'classification/rolloutStatus cannot gate this request shape at the transport chokepoint',
    () => {
      const resolved = findApprovedOperationByRequest('COLLECTION', 'List of Ledgers');
      expect(resolved?.operationId).toBe(ApprovedOperationId.Ledgers);
      expect(resolved?.operationId).not.toBe(ApprovedOperationId.LedgersContactDetails);
    },
  );

  it('would enrich ledger contact details via the same additive Fetch list once enabled', () => {
    // LEDGERS_CONTACT_DETAILS.render() throws until live-validated (see above), so this exercises
    // the underlying template directly -- confirms the intended Fetch list shape without sending
    // anything to Tally.
    expect([...LEDGER_CONTACT_FETCH_FIELDS]).toEqual(
      expect.arrayContaining(['MAILINGNAME', 'ADDRESS', 'EMAIL', 'MOBILENUMBER', 'PARTYGSTIN']),
    );
    expect(LEDGER_CONTACT_FETCH_FIELDS).not.toEqual(LEDGER_RICH_FETCH_FIELDS);
  });

  it('uses the shared rich master response cap for ledgers and stock items', () => {
    expect(getApprovedOperation(ApprovedOperationId.Ledgers).maxResponseBytes).toBe(
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    );
    expect(getApprovedOperation(ApprovedOperationId.StockItems).maxResponseBytes).toBe(
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    );
  });

  it('allows a higher response cap for voucher collections than rich masters', () => {
    expect(getApprovedOperation(ApprovedOperationId.Vouchers).maxResponseBytes).toBe(
      VOUCHER_COLLECTION_MAX_RESPONSE_BYTES,
    );
    expect(VOUCHER_COLLECTION_MAX_RESPONSE_BYTES).toBeGreaterThan(
      RICH_MASTER_COLLECTION_MAX_RESPONSE_BYTES,
    );
  });

  it('returns undefined for unregistered collections (UNKNOWN)', () => {
    expect(findApprovedOperationByRequest('COLLECTION', 'List of Zzz Nonexistent')).toBeUndefined();
  });

  it('permanently forbids List of Units and single Stock Item object export', () => {
    expect(isForbiddenOperation('COLLECTION', 'List of Units')).toBe(true);
    expect(isForbiddenOperation('OBJECT', 'Stock Item')).toBe(true);
  });

  it('maps Tally operations to ERP-neutral policy descriptors', () => {
    const health = toPolicyOperation(getApprovedOperation(ApprovedOperationId.HealthCheck));
    expect(health.isHealthProbe).toBe(true);
    expect(health.operationId).toBe('HEALTH_CHECK');
    expect(health).not.toHaveProperty('capability');
  });
});

describe('fail-closed ERP-neutral policy engine', () => {
  const base = {
    requestBytes: 500,
    circuitState: 'closed' as const,
    isHealthProbe: false,
  };

  it('treats UNKNOWN (unregistered) as DENY', () => {
    expect(decidePolicy({ ...base, operation: undefined }).decision).toBe('DENY');
  });

  it('DENYs a forbidden operation', () => {
    const result = decidePolicy({
      ...base,
      forbidden: { reason: 'DEADLOCK_EVIDENCE', operationId: 'List of Units' },
    });
    expect(result.decision).toBe('DENY');
  });

  it('ALLOWs a VERIFIED_SAFE operation with a closed circuit', () => {
    const op = toPolicyOperation(getApprovedOperation(ApprovedOperationId.Ledgers));
    expect(decidePolicy({ ...base, operation: op }).decision).toBe('ALLOW');
  });

  it('half-open circuit admits only the approved health probe', () => {
    const ledgers = toPolicyOperation(getApprovedOperation(ApprovedOperationId.Ledgers));
    const health = toPolicyOperation(getApprovedOperation(ApprovedOperationId.HealthCheck));
    expect(decidePolicy({ ...base, operation: ledgers, circuitState: 'half_open' }).decision).toBe(
      'REQUIRE_MANUAL_APPROVAL',
    );
    expect(
      decidePolicy({
        ...base,
        operation: health,
        circuitState: 'half_open',
        isHealthProbe: true,
      }).decision,
    ).toBe('ALLOW');
  });

  it('DENYs when request exceeds the operation payload cap', () => {
    const op = toPolicyOperation(getApprovedOperation(ApprovedOperationId.HealthCheck));
    expect(
      decidePolicy({ ...base, operation: op, requestBytes: op.maxRequestBytes + 1 }).decision,
    ).toBe('DENY');
  });
});
