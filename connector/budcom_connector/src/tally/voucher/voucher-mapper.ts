import {
  VOUCHER_IDENTITY_VERSION_UNPROVEN,
  type VoucherAllocationType,
  type VoucherDetails,
  type VoucherInventoryEntry,
  type VoucherLedgerEntry,
  type VoucherMoney,
  type VoucherNestedAllocation,
  type VoucherStructuredValue,
} from '../../erp/voucher/voucher-domain.js';
import type {
  VoucherRuntimeValidationResult,
  VoucherValidationIssue,
} from '../../erp/voucher/voucher-validation.js';
import { normalizeAmount } from '../../extraction/normalization/amounts.js';
import type { ParsedXmlNode } from '../xml/response-parser.js';
import type { VoucherCollectionParser } from './voucher-parser.js';

const ALLOCATION_TYPES: Readonly<Record<string, VoucherAllocationType>> = {
  'ACCOUNTINGALLOCATIONS.LIST': 'accounting',
  'BATCHALLOCATIONS.LIST': 'batch',
  'BANKALLOCATIONS.LIST': 'bank',
  'BILLALLOCATIONS.LIST': 'bill',
  'COSTTRACKALLOCATIONS.LIST': 'cost-track',
  'INVENTORYALLOCATIONS.LIST': 'inventory',
};

export class VoucherXmlMapper {
  constructor(private readonly parser: VoucherCollectionParser) {}

  map(node: ParsedXmlNode): VoucherRuntimeValidationResult<VoucherDetails> {
    const issues: VoucherValidationIssue[] = [];
    const guid = textOrNull(this.parser.childText(node, 'GUID'));
    const masterId = textOrNull(this.parser.childText(node, 'MASTERID'));
    const alterId = textOrNull(this.parser.childText(node, 'ALTERID'));
    const voucherKey = textOrNull(this.parser.childText(node, 'VOUCHERKEY'));
    const voucherRetainKey = textOrNull(
      this.parser.childText(node, 'VOUCHERRETAINKEY'),
    );
    const voucherId = guid ?? voucherKey ?? masterId;
    if (!voucherId) {
      issues.push(issue(
        'MISSING_STABLE_IDENTITY',
        'identity',
        'rejected',
        'Voucher requires GUID, VoucherKey, or MasterID.',
      ));
    }

    const date = strictDate(this.parser.childText(node, 'DATE'));
    if (!date) {
      issues.push(issue('INVALID_DATE', 'date', 'rejected', 'Voucher date is missing or invalid.'));
    }
    const effectiveDateRaw = this.parser.childText(node, 'EFFECTIVEDATE');
    const effectiveDate = effectiveDateRaw ? strictDate(effectiveDateRaw) : undefined;
    if (effectiveDateRaw && !effectiveDate) {
      issues.push(issue(
        'INVALID_DATE',
        'effectiveDate',
        'incomplete',
        'Voucher effective date is invalid.',
      ));
    }

    const voucherType = this.parser.childText(node, 'VOUCHERTYPENAME');
    if (!voucherType) {
      issues.push(issue(
        'MISSING_TYPE_REQUIRED_FIELD',
        'voucherType',
        'rejected',
        'Voucher type is required.',
      ));
    }

    const narration = this.parser.childText(node, 'NARRATION');
    const ledgerEntries = this.mapLedgerEntries(node, issues);
    const inventoryEntries = this.mapInventoryEntries(node, issues);
    const amount = this.mapOptionalMoney(
      this.parser.childText(node, 'AMOUNT'),
      'amount',
      issues,
    );
    const rejected = issues.some((item) =>
      item.classification === 'rejected' || item.classification === 'contract-conflict'
    );
    if (rejected || !voucherId || !date || !voucherType) {
      return { accepted: false, value: null, issues };
    }

    const value: VoucherDetails = {
      voucherId,
      identityVersion: VOUCHER_IDENTITY_VERSION_UNPROVEN,
      guid,
      masterId,
      alterId,
      voucherKey,
      voucherRetainKey,
      date,
      voucherType,
      voucherNumber: textOrNull(this.parser.childText(node, 'VOUCHERNUMBER')),
      partyName: textOrNull(this.parser.childText(node, 'PARTYLEDGERNAME')),
      amount,
      amountComparable: false,
      status: isYes(this.parser.childText(node, 'ISCANCELLED')) ? 'cancelled' : 'active',
      dataQuality: issues.length === 0 ? 'complete' : 'incomplete',
      referenceNumber: this.parser.childText(node, 'REFERENCE'),
      narrationPreview: narration?.slice(0, 160),
      ...(effectiveDate ? { effectiveDate } : {}),
      ...(narration ? { narration } : {}),
      ledgerEntries,
      inventoryEntries,
      allocations: collectAllocations(node),
    };
    return { accepted: true, value, issues };
  }

  private mapLedgerEntries(
    node: ParsedXmlNode,
    issues: VoucherValidationIssue[],
  ): readonly VoucherLedgerEntry[] {
    return this.parser.directChildren(node, 'ALLLEDGERENTRIES.LIST').map((entry, index) => {
      const field = `ledgerEntries[${index}]`;
      const ledgerName = this.parser.childText(entry, 'LEDGERNAME');
      if (!ledgerName) {
        issues.push(issue(
          'MISSING_LEDGER_NAME',
          `${field}.ledgerName`,
          'incomplete',
          'Ledger entry is missing LedgerName.',
        ));
      }
      const amount = this.mapRequiredMoney(
        this.parser.childText(entry, 'AMOUNT'),
        `${field}.amount`,
        issues,
      );
      const deemedPositiveRaw = this.parser.childText(entry, 'ISDEEMEDPOSITIVE');
      const isDeemedPositive = yesNo(deemedPositiveRaw);
      if (deemedPositiveRaw && isDeemedPositive === null) {
        issues.push(issue(
          'INVALID_REQUIRED_CHILD_STRUCTURE',
          `${field}.isDeemedPositive`,
          'incomplete',
          'IsDeemedPositive must be Yes or No.',
        ));
      }
      return {
        lineNumber: index + 1,
        ledgerName: ledgerName ?? '',
        amount,
        isDeemedPositive,
        allocations: collectAllocations(entry),
      };
    });
  }

  private mapInventoryEntries(
    node: ParsedXmlNode,
    issues: VoucherValidationIssue[],
  ): readonly VoucherInventoryEntry[] {
    const entries = this.parser.directChildren(node, 'ALLINVENTORYENTRIES.LIST').filter(
      (entry) => !this.isEmptyInventoryPlaceholder(entry),
    );
    return entries.map((entry, index) => {
      const field = `inventoryEntries[${index}]`;
      const itemName = this.parser.childText(entry, 'STOCKITEMNAME');
      if (!itemName) {
        issues.push(issue(
          'INVALID_REQUIRED_CHILD_STRUCTURE',
          `${field}.itemName`,
          'incomplete',
          'Inventory entry is missing StockItemName.',
        ));
      }
      const actualQuantity = this.parser.childText(entry, 'ACTUALQTY');
      const billedQuantity = this.parser.childText(entry, 'BILLEDQTY');
      if (quantitiesConflict(actualQuantity, billedQuantity)) {
        issues.push(issue(
          'INCONSISTENT_QUANTITIES',
          `${field}.quantities`,
          'incomplete',
          'ActualQty and BilledQty use inconsistent units.',
        ));
      }
      const amount = this.mapOptionalMoney(
        this.parser.childText(entry, 'AMOUNT'),
        `${field}.amount`,
        issues,
      );
      if (!this.parser.childText(entry, 'AMOUNT')) {
        issues.push(issue(
          'INVALID_REQUIRED_CHILD_STRUCTURE',
          `${field}.amount`,
          'incomplete',
          'Inventory entry is missing Amount.',
        ));
      }
      return {
        lineNumber: index + 1,
        itemName: itemName ?? '',
        ...(billedQuantity || actualQuantity
          ? { quantity: billedQuantity ?? actualQuantity }
          : {}),
        ...(actualQuantity ? { actualQuantity } : {}),
        ...(billedQuantity ? { billedQuantity } : {}),
        ...(this.parser.childText(entry, 'RATE')
          ? { rate: this.parser.childText(entry, 'RATE') }
          : {}),
        ...(amount ? { amount } : {}),
        allocations: collectAllocations(entry),
      };
    });
  }

  /**
   * Tally emits a self-closing ALLINVENTORYENTRIES.LIST placeholder on
   * accounting-only Vouchers. It is collection shape, not an inventory row.
   */
  private isEmptyInventoryPlaceholder(entry: ParsedXmlNode): boolean {
    return ![
      'STOCKITEMNAME',
      'ACTUALQTY',
      'BILLEDQTY',
      'RATE',
      'AMOUNT',
    ].some((field) => this.parser.childText(entry, field)) &&
      collectAllocations(entry).length === 0;
  }

  private mapRequiredMoney(
    raw: string | undefined,
    field: string,
    issues: VoucherValidationIssue[],
  ): VoucherMoney {
    if (!raw) {
      issues.push(issue(
        'INVALID_REQUIRED_CHILD_STRUCTURE',
        field,
        'incomplete',
        'Ledger entry is missing Amount.',
      ));
    }
    const value = this.mapOptionalMoney(raw, field, issues);
    if (value) return value;
    return { amount: '0', side: null };
  }

  private mapOptionalMoney(
    raw: string | undefined,
    field: string,
    issues: VoucherValidationIssue[],
  ): VoucherMoney | null {
    if (!raw) return null;
    const normalized = normalizeAmount(raw);
    if (!normalized) {
      issues.push(issue('INVALID_AMOUNT', field, 'incomplete', 'Amount is malformed.'));
      return null;
    }
    return {
      amount: normalized.amount,
      side: normalized.side === 'Dr' ? 'debit' : 'credit',
    };
  }
}

function collectAllocations(container: ParsedXmlNode): readonly VoucherNestedAllocation[] {
  const allocations: VoucherNestedAllocation[] = [];
  for (const child of container.children) {
    const type = ALLOCATION_TYPES[child.name];
    if (type) {
      allocations.push({
        type,
        sourceName: child.name,
        values: child.children.map(toStructuredValue),
      });
      continue;
    }
    allocations.push(...collectAllocations(child));
  }
  return allocations;
}

function toStructuredValue(node: ParsedXmlNode): VoucherStructuredValue {
  return {
    name: node.name,
    ...(node.text ? { value: node.text } : {}),
    attributes: node.attributes,
    children: node.children.map(toStructuredValue),
  };
}

function strictDate(value: string | undefined): string | undefined {
  if (!value) return undefined;
  let year: number;
  let month: number;
  let day: number;
  if (/^\d{8}$/.test(value)) {
    year = Number(value.slice(0, 4));
    month = Number(value.slice(4, 6));
    day = Number(value.slice(6, 8));
  } else {
    const iso = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    const local = value.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
    if (iso) {
      year = Number(iso[1]);
      month = Number(iso[2]);
      day = Number(iso[3]);
    } else if (local) {
      day = Number(local[1]);
      month = Number(local[2]);
      year = Number(local[3]);
    } else {
      return undefined;
    }
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return undefined;
  }
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function quantitiesConflict(
  actual: string | undefined,
  billed: string | undefined,
): boolean {
  if (!actual || !billed) return false;
  const actualUnit = quantityUnit(actual);
  const billedUnit = quantityUnit(billed);
  return Boolean(actualUnit && billedUnit && actualUnit !== billedUnit);
}

function quantityUnit(value: string): string | undefined {
  return value.trim().match(/^-?[\d,.]+\s+(.+)$/)?.[1]?.trim().toUpperCase();
}

function issue(
  code: VoucherValidationIssue['code'],
  field: string,
  classification: VoucherValidationIssue['classification'],
  message: string,
): VoucherValidationIssue {
  return { code, field, classification, message };
}

function textOrNull(value: string | undefined): string | null {
  return value ?? null;
}

function yesNo(value: string | undefined): boolean | null {
  if (!value) return null;
  const normalized = value.trim().toUpperCase();
  if (normalized === 'YES') return true;
  if (normalized === 'NO') return false;
  return null;
}

function isYes(value: string | undefined): boolean {
  return yesNo(value) === true;
}
