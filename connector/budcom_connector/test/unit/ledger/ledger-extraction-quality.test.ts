import { describe, expect, it } from 'vitest';

import { assessLedgerExtraction } from '../../../src/erp/ledger/ledger-extraction-quality.js';
import { sampleNameFallbackLedger, sampleNormalizedLedger } from '../../helpers/ledger-fixtures.js';

describe('ledger extraction quality', () => {
  it('marks rich GUID coverage as complete', () => {
    const assessment = assessLedgerExtraction([
      sampleNormalizedLedger(),
      sampleNormalizedLedger({
        guid: 'bbbb-cccc-dddd-eeee-000000000002',
        id: 'guid:bbbb-cccc-dddd-eeee-000000000002',
        name: 'Bank',
      }),
    ]);
    expect(assessment.quality).toBe('complete');
    expect(assessment.guidPresentCount).toBe(2);
  });

  it('marks shallow export as invalid', () => {
    const assessment = assessLedgerExtraction([
      sampleNormalizedLedger({
        guid: undefined,
        id: 'name:cash',
        parentGroup: undefined,
        identitySource: 'name',
      }),
    ]);
    expect(assessment.quality).toBe('invalid');
  });

  it('marks GUID-absent but parent-present collection as partial', () => {
    const assessment = assessLedgerExtraction([sampleNameFallbackLedger()]);
    expect(assessment.quality).toBe('partial');
    expect(assessment.parentPresentCount).toBe(1);
  });
});
