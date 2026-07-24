import { createHash } from 'node:crypto';

import type { NormalizedLedger } from '../../src/extraction/core/types.js';
import { resolveLedgerStableId } from '../../src/extraction/core/ledger-identity.js';
import type { LedgerDetails } from '../../src/erp/ledger/ledger-domain.js';
import { mapNormalizedLedgerToDomain } from '../../src/erp/ledger/ledger-mapper.js';
import type { AmountSide, NormalizedAmount } from '../../src/extraction/normalization/amounts.js';

function fixtureGuidForName(name: string): string {
  const suffix = createHash('sha256').update(name).digest('hex').slice(0, 12);
  return `aaaaaaaa-bbbb-cccc-${suffix}`;
}

function hasOverride<T extends object, K extends keyof T>(
  overrides: T,
  key: K,
): overrides is T & Record<K, T[K]> {
  return Object.prototype.hasOwnProperty.call(overrides, key);
}

export function sampleNormalizedAmount(
  amount: string,
  side: AmountSide = 'Dr',
  currencyCode = 'INR',
): NormalizedAmount {
  return { amount, currencyCode, side };
}

export function sampleNormalizedLedger(overrides: Partial<NormalizedLedger> = {}): NormalizedLedger {
  const name = overrides.name ?? 'Cash';
  const defaultGuid = fixtureGuidForName(name);
  const guidForIdentity = hasOverride(overrides, 'guid') ? overrides.guid : defaultGuid;
  const identity = resolveLedgerStableId({ guid: guidForIdentity, name });

  return {
    name,
    normalizedName: overrides.normalizedName ?? name.toLowerCase(),
    parentGroup: hasOverride(overrides, 'parentGroup') ? overrides.parentGroup : 'Cash-in-Hand',
    alterId: overrides.alterId ?? '1001',
    masterId: overrides.masterId ?? '2001',
    dataQuality: overrides.dataQuality ?? 'complete',
    ...overrides,
    id: hasOverride(overrides, 'id') ? overrides.id! : identity.id,
    guid: hasOverride(overrides, 'guid') ? overrides.guid : identity.guid ?? defaultGuid,
    identitySource: overrides.identitySource ?? identity.identitySource,
  };
}

export function sampleNameFallbackLedger(overrides: Partial<NormalizedLedger> = {}): NormalizedLedger {
  const name = overrides.name ?? 'Fallback Ledger';
  const identity = resolveLedgerStableId({ name });
  return sampleNormalizedLedger({
    guid: undefined,
    parentGroup: overrides.parentGroup ?? 'Sundry Debtors',
    identitySource: 'name',
    dataQuality: 'partial',
    ...overrides,
    name,
    id: identity.id,
  });
}

export function sampleLedgerDetails(overrides: Partial<LedgerDetails> = {}): LedgerDetails {
  const {
    name,
    id,
    guid,
    parentGroup,
    alterId,
    masterId,
    identitySource,
    dataQuality,
    syncedAt,
    ...rest
  } = overrides;

  const normalizedInput: {
    name?: string;
    id?: string;
    guid?: string;
    parentGroup?: string;
    alterId?: string;
    masterId?: string;
    identitySource?: NormalizedLedger['identitySource'];
    dataQuality?: NormalizedLedger['dataQuality'];
  } = {};
  if (name !== undefined) normalizedInput.name = name;
  if (id !== undefined) normalizedInput.id = id;
  if (guid !== undefined) normalizedInput.guid = guid;
  if (parentGroup !== undefined) normalizedInput.parentGroup = parentGroup;
  if (alterId !== undefined) normalizedInput.alterId = alterId;
  if (masterId !== undefined) normalizedInput.masterId = masterId;
  if (identitySource !== undefined) normalizedInput.identitySource = identitySource;
  if (dataQuality !== undefined) normalizedInput.dataQuality = dataQuality;

  const mapped = mapNormalizedLedgerToDomain(
    sampleNormalizedLedger(normalizedInput),
    syncedAt ?? '2026-01-01T00:00:00.000Z',
    dataQuality ?? 'complete',
  );
  return { ...mapped, ...rest, ...(name !== undefined ? { name } : {}) };
}
