import { describe, expect, it } from 'vitest';

import {
  isLegacyLedgerId,
  normalizeLedgerGuid,
  resolveLedgerStableId,
} from '../../../src/extraction/core/ledger-identity.js';

describe('ledger identity', () => {
  it('resolves GUID identity to guid:{normalised-guid}', () => {
    const result = resolveLedgerStableId({
      guid: 'AAAA-BBBB-CCCC',
      name: 'Synthetic Ledger',
    });
    expect(result.id).toBe('guid:aaaa-bbbb-cccc');
    expect(result.identitySource).toBe('guid');
    expect(result.guid).toBe('aaaa-bbbb-cccc');
  });

  it('resolves missing GUID to name:{slug}', () => {
    const result = resolveLedgerStableId({ name: 'Fallback Ledger' });
    expect(result.id).toBe('name:fallback-ledger');
    expect(result.identitySource).toBe('name');
  });

  it('rejects empty names', () => {
    expect(() => resolveLedgerStableId({ name: '   ' })).toThrow(/required/i);
  });

  it('never uses AlterID or MasterID in resolved identity', () => {
    const result = resolveLedgerStableId({ guid: 'stable-guid-value', name: 'Any Name' });
    expect(result.id).not.toContain('alter');
    expect(result.id).not.toContain('master');
  });

  it('detects legacy slug-only persisted IDs', () => {
    expect(isLegacyLedgerId('cash')).toBe(true);
    expect(isLegacyLedgerId('guid:abc')).toBe(false);
    expect(isLegacyLedgerId('name:cash')).toBe(false);
  });

  it('normalises GUID deterministically', () => {
    expect(normalizeLedgerGuid(' AbC-123 ')).toBe('abc-123');
  });
});
