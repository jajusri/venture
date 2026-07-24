import type { NormalizedLedger } from '../../extraction/core/types.js';
import type { BalanceNature, LedgerDetails, LedgerIdentitySource, LedgerStatus } from './ledger-domain.js';
import { LEDGER_DOMAIN_CONTRACT_VERSION } from './ledger-domain.js';
import type { LedgerDataQuality } from './ledger-extraction-quality.js';

function resolveBalanceNature(ledger: NormalizedLedger): BalanceNature {
  if (ledger.balanceNature) {
    return ledger.balanceNature;
  }
  const side = ledger.closingBalance?.side ?? ledger.openingBalance?.side;
  if (side === 'Dr') return 'debit';
  if (side === 'Cr') return 'credit';
  return 'unknown';
}

function resolveStatus(ledger: NormalizedLedger): LedgerStatus {
  if (ledger.status) {
    return ledger.status;
  }
  if (ledger.reservedName) {
    return 'reserved';
  }
  return 'active';
}

/** Maps ERP-neutral extraction model into the persisted ledger domain record. */
export function mapNormalizedLedgerToDomain(
  ledger: NormalizedLedger,
  syncedAt = new Date().toISOString(),
  dataQuality: LedgerDataQuality = ledger.dataQuality ?? 'complete',
): LedgerDetails {
  const identitySource: LedgerIdentitySource = ledger.identitySource ?? (ledger.guid ? 'guid' : 'name');
  return {
    id: ledger.id,
    name: ledger.name,
    normalizedName: ledger.normalizedName,
    alias: ledger.alias,
    parentGroup: ledger.parentGroup,
    status: resolveStatus(ledger),
    openingBalance: ledger.openingBalance,
    closingBalance: ledger.closingBalance,
    balanceNature: resolveBalanceNature(ledger),
    guid: ledger.guid,
    alterId: ledger.alterId,
    masterId: ledger.masterId,
    identitySource,
    dataQuality,
    isBillWiseOn: ledger.isBillWiseOn,
    reservedName: ledger.reservedName,
    isDeleted: false,
    syncedAt,
    mailing: ledger.mailingName || ledger.address || ledger.state || ledger.country || ledger.pincode
      ? {
          mailingName: ledger.mailingName,
          address: ledger.address,
          state: ledger.state,
          country: ledger.country,
          pincode: ledger.pincode,
        }
      : undefined,
    contact: ledger.email || ledger.phone || ledger.mobile
      ? {
          email: ledger.email,
          phone: ledger.phone,
          mobile: ledger.mobile,
        }
      : undefined,
    gst: ledger.gstin || ledger.gstRegistrationType
      ? {
          gstin: ledger.gstin,
          registrationType: ledger.gstRegistrationType,
          applicableFrom: ledger.gstApplicableFrom,
        }
      : undefined,
    metadata: {
      contractVersion: LEDGER_DOMAIN_CONTRACT_VERSION,
      identitySource,
      dataQuality,
    },
  };
}
