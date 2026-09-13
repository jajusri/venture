import { describe, expect, it } from 'vitest';

import type { LedgerDetails } from '../../../src/erp/ledger/ledger-domain.js';
import { validateLedgerCollection } from '../../../src/erp/ledger/ledger-validation.js';
import { mapNormalizedLedgerToDomain } from '../../../src/erp/ledger/ledger-mapper.js';
import {
  sampleNormalizedAmount,
  sampleNormalizedLedger,
} from '../../helpers/ledger-fixtures.js';

function sampleLedger(overrides: Partial<LedgerDetails> = {}): LedgerDetails {
  return {
    id: 'name:cash',
    name: 'Cash',
    normalizedName: 'cash',
    parentGroup: 'Cash-in-Hand',
    status: 'active',
    balanceNature: 'debit',
    identitySource: 'name',
    dataQuality: 'complete',
    isDeleted: false,
    syncedAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}

describe('validateLedgerCollection', () => {
  it('detects duplicate names', () => {
    const result = validateLedgerCollection([
      sampleLedger({ id: 'cash-1', name: 'Cash' }),
      sampleLedger({ id: 'cash-2', name: 'Cash' }),
    ]);
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.code === 'DUPLICATE_NAME')).toBe(true);
  });

  it('detects duplicate resolved IDs', () => {
    const result = validateLedgerCollection([
      sampleLedger({ id: 'guid:shared-id', guid: 'shared-id' }),
      sampleLedger({ id: 'guid:shared-id', name: 'Bank', guid: 'shared-id' }),
    ]);
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.code === 'DUPLICATE_RESOLVED_ID')).toBe(true);
  });

  it('detects duplicate GUID and AlterID', () => {
    const result = validateLedgerCollection([
      sampleLedger({ id: 'a', guid: 'guid-1', alterId: '10' }),
      sampleLedger({ id: 'b', name: 'Bank', guid: 'guid-1', alterId: '10' }),
    ]);
    expect(result.issues.some((issue) => issue.code === 'DUPLICATE_GUID')).toBe(true);
    expect(result.issues.some((issue) => issue.code === 'DUPLICATE_ALTER_ID')).toBe(true);
  });

  it('detects circular parent references', () => {
    const result = validateLedgerCollection([
      sampleLedger({ id: 'a', name: 'Alpha', parentGroup: 'Beta' }),
      sampleLedger({ id: 'b', name: 'Beta', parentGroup: 'Alpha' }),
    ]);
    expect(result.issues.some((issue) => issue.code === 'CIRCULAR_REFERENCE')).toBe(true);
  });
});

describe('mapNormalizedLedgerToDomain', () => {
  it('maps extraction model into ledger domain record', () => {
    const mapped = mapNormalizedLedgerToDomain(
      sampleNormalizedLedger({
        id: 'acme-corp',
        name: 'Acme Corp',
        normalizedName: 'acme corp',
        parentGroup: 'Sundry Debtors',
        closingBalance: sampleNormalizedAmount('500', 'Cr'),
        gstin: '27AAAAA0000A1Z5',
      }),
    );

    expect(mapped.id).toBe('acme-corp');
    expect(mapped.balanceNature).toBe('credit');
    expect(mapped.gst?.gstin).toBe('27AAAAA0000A1Z5');
  });
});
