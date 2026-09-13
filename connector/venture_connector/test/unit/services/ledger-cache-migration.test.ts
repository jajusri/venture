import { describe, expect, it } from 'vitest';

import { assessLedgerExtraction } from '../../../src/erp/ledger/ledger-extraction-quality.js';
import { validateLedgerCollection } from '../../../src/erp/ledger/ledger-validation.js';
import { mapNormalizedLedgerToDomain } from '../../../src/erp/ledger/ledger-mapper.js';
import { assertLedgerRebuildPrecheck } from '../../../src/services/ledger/ledger-cache-migration.js';
import { sampleNameFallbackLedger, sampleNormalizedLedger } from '../../helpers/ledger-fixtures.js';

describe('ledger migration rebuild precheck', () => {
  it('rejects empty extraction for destructive replacement', () => {
    const assessment = assessLedgerExtraction([]);
    const validation = validateLedgerCollection([]);
    expect(() => assertLedgerRebuildPrecheck({ assessment, validation, ledgers: [] })).toThrow(/empty extraction/i);
  });

  it('rejects partial extraction for destructive replacement', () => {
    const ledgers = [mapNormalizedLedgerToDomain(sampleNameFallbackLedger())];
    const assessment = assessLedgerExtraction([sampleNameFallbackLedger()]);
    const validation = validateLedgerCollection(ledgers);
    expect(() => assertLedgerRebuildPrecheck({ assessment, validation, ledgers })).toThrow(/partial|guid|fallback|missing/i);
  });

  it('accepts complete extraction with valid collection', () => {
    const normalized = [
      sampleNormalizedLedger({ alterId: '9001' }),
      sampleNormalizedLedger({
        name: 'Bank',
        guid: 'bbbb-cccc-dddd-eeee-000000000002',
        id: 'guid:bbbb-cccc-dddd-eeee-000000000002',
        alterId: '9002',
      }),
    ];
    const ledgers = normalized.map((item) => mapNormalizedLedgerToDomain(item));
    const assessment = assessLedgerExtraction(normalized);
    const validation = validateLedgerCollection(ledgers);
    expect(() => assertLedgerRebuildPrecheck({ assessment, validation, ledgers })).not.toThrow();
  });
});
