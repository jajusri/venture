import type { CollectionEntityParser } from '../../extraction/parsers/entity-mappers.js';
import type { ParsedXmlNode } from '../xml/response-parser.js';
import { resolveLedgerStableId } from '../../extraction/core/ledger-identity.js';
import { normalizeAmount } from '../../extraction/normalization/amounts.js';
import { normalizeName, normalizeText } from '../../extraction/normalization/strings.js';
import type {
  BalanceNature,
  LedgerDetails,
  LedgerStatus,
} from '../../erp/ledger/ledger-domain.js';
import { LEDGER_DOMAIN_CONTRACT_VERSION } from '../../erp/ledger/ledger-domain.js';

function resolveBalanceNature(amountText: string | undefined, side?: string): BalanceNature {
  if (side === 'debit' || side === 'Dr') return 'debit';
  if (side === 'credit' || side === 'Cr') return 'credit';
  const normalized = amountText?.toLowerCase() ?? '';
  if (normalized.includes(' dr')) return 'debit';
  if (normalized.includes(' cr')) return 'credit';
  return 'unknown';
}

function resolveStatus(parser: CollectionEntityParser, node: ParsedXmlNode): LedgerStatus {
  const reserved = parser.getLogical(node, 'ISDELETED') === false && parser.getChildText(node, 'RESERVEDNAME');
  if (reserved) return 'reserved';
  if (parser.getLogical(node, 'ISDELETED') === true) return 'inactive';
  const active = parser.getLogical(node, 'ISACTIVE');
  if (active === false) return 'inactive';
  return 'active';
}

export function mapTallyLedgerToDomain(
  parser: CollectionEntityParser,
  node: ParsedXmlNode,
  syncedAt = new Date().toISOString(),
): LedgerDetails | undefined {
  const name = parser.resolveName(node);
  if (!name) {
    return undefined;
  }

  const openingText = parser.getChildText(node, 'OPENINGBALANCE');
  const closingText = parser.getChildText(node, 'CLOSINGBALANCE');
  const openingBalance = normalizeAmount(openingText);
  const closingBalance = normalizeAmount(closingText);
  const guid = parser.getChildText(node, 'GUID');
  const identity = resolveLedgerStableId({ guid, name });

  return {
    id: identity.id,
    name,
    normalizedName: normalizeName(name),
    alias: parser.getChildText(node, 'ALIAS'),
    parentGroup: normalizeText(parser.getChildText(node, 'PARENT')),
    status: resolveStatus(parser, node),
    openingBalance,
    closingBalance,
    balanceNature: resolveBalanceNature(closingText ?? openingText, closingBalance?.side ?? openingBalance?.side),
    guid: identity.guid ?? guid?.trim(),
    alterId: parser.getChildText(node, 'ALTERID'),
    masterId: parser.getChildText(node, 'MASTERID')?.trim() || undefined,
    identitySource: identity.identitySource,
    dataQuality: identity.guid ? 'complete' : 'partial',
    reservedName: parser.getChildText(node, 'RESERVEDNAME'),
    isDeleted: false,
    syncedAt,
    mailing: {
      mailingName: parser.getChildText(node, 'MAILINGNAME'),
      address: parser.getChildText(node, 'ADDRESS'),
      state: parser.getChildText(node, 'STATENAME'),
      country: parser.getChildText(node, 'COUNTRYNAME'),
      pincode: parser.getChildText(node, 'PINCODE'),
    },
    contact: {
      email: parser.getChildText(node, 'EMAIL'),
      phone: parser.getChildText(node, 'PHONENUMBER'),
      mobile: parser.getChildText(node, 'MOBILENUMBER'),
    },
    gst: {
      gstin: parser.getChildText(node, 'PARTYGSTIN') ?? parser.getChildText(node, 'GSTIN'),
      registrationType: parser.getChildText(node, 'GSTREGISTRATIONTYPE'),
      applicableFrom: parser.getChildText(node, 'APPLICABLEFROM'),
    },
    metadata: {
      contractVersion: LEDGER_DOMAIN_CONTRACT_VERSION,
    },
  };
}
