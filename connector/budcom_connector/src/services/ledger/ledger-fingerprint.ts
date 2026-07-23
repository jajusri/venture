import { createHash } from 'node:crypto';

import type { LedgerDetails } from '../../erp/ledger/ledger-domain.js';

export function computeLedgerFingerprint(ledger: LedgerDetails): string {
  const payload = [
    ledger.name,
    ledger.normalizedName,
    ledger.alias ?? '',
    ledger.parentGroup ?? '',
    ledger.status,
    ledger.balanceNature,
    ledger.guid ?? '',
    ledger.alterId ?? '',
    ledger.openingBalance?.amount ?? '',
    ledger.openingBalance?.side ?? '',
    ledger.closingBalance?.amount ?? '',
    ledger.closingBalance?.side ?? '',
    ledger.gst?.gstin ?? '',
    ledger.isDeleted ? '1' : '0',
  ].join('|');
  return createHash('sha256').update(payload).digest('hex');
}
