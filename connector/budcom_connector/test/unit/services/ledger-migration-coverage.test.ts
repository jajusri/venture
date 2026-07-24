import { describe, expect, it } from 'vitest';

import { mapNormalizedLedgerToDomain } from '../../../src/erp/ledger/ledger-mapper.js';
import {
  assessLegacyMigrationCoverage,
  resolveLedgerMigrationKey,
} from '../../../src/services/ledger/ledger-migration-coverage.js';
import { sampleNormalizedLedger } from '../../helpers/ledger-fixtures.js';

describe('legacy migration coverage', () => {
  it('uses normalizeName(name) as the canonical migration key', () => {
    expect(resolveLedgerMigrationKey('Legacy Cash')).toBe('legacy cash');
    expect(resolveLedgerMigrationKey('  LEGACY CASH  ')).toBe('legacy cash');
  });

  it('allows replacement when every legacy row has exactly one incoming match and extras exist', () => {
    const legacy = [
      { id: 'legacy-cash', name: 'Legacy Cash', normalizedName: 'legacy cash' },
      { id: 'legacy-bank', name: 'Legacy Bank', normalizedName: 'legacy bank' },
    ];
    const incoming = [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Legacy Cash', guid: 'guid-1', alterId: '1' })),
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Legacy Bank', guid: 'guid-2', alterId: '2' })),
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Extra Ledger', guid: 'guid-3', alterId: '3' })),
    ];

    const result = assessLegacyMigrationCoverage(legacy, incoming);
    expect(result.safeToReplace).toBe(true);
    expect(result.matchedLegacyCount).toBe(2);
    expect(result.incomingCount).toBe(3);
  });

  it('blocks replacement when legacy rows are unmatched', () => {
    const legacy = [
      { id: 'legacy-cash', name: 'Legacy Cash', normalizedName: 'legacy cash' },
      { id: 'legacy-bank', name: 'Legacy Bank', normalizedName: 'legacy bank' },
    ];
    const incoming = [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Legacy Cash', guid: 'guid-1', alterId: '1' })),
    ];

    const result = assessLegacyMigrationCoverage(legacy, incoming);
    expect(result.safeToReplace).toBe(false);
    expect(result.reason).toBe('legacy_coverage_incomplete');
    expect(result.unmatchedLegacyCount).toBe(1);
  });

  it('blocks replacement when incoming rows collide on the canonical key', () => {
    const legacy = [{ id: 'legacy-cash', name: 'Legacy Cash', normalizedName: 'legacy cash' }];
    const incoming = [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Legacy Cash', guid: 'guid-1', alterId: '1' })),
      mapNormalizedLedgerToDomain(
        sampleNormalizedLedger({
          name: 'Legacy Cash',
          guid: 'guid-2',
          id: 'guid:guid-2',
          alterId: '2',
        }),
      ),
    ];

    const result = assessLegacyMigrationCoverage(legacy, incoming);
    expect(result.safeToReplace).toBe(false);
    expect(result.reason).toBe('legacy_coverage_ambiguous');
  });

  it('blocks replacement when legacy rows collide on the canonical key', () => {
    const legacy = [
      { id: 'legacy-a', name: 'Legacy Cash', normalizedName: 'legacy cash' },
      { id: 'legacy-b', name: 'LEGACY CASH', normalizedName: 'legacy cash' },
    ];
    const incoming = [
      mapNormalizedLedgerToDomain(sampleNormalizedLedger({ name: 'Legacy Cash', guid: 'guid-1', alterId: '1' })),
    ];

    const result = assessLegacyMigrationCoverage(legacy, incoming);
    expect(result.safeToReplace).toBe(false);
    expect(result.reason).toBe('legacy_coverage_ambiguous');
  });
});
