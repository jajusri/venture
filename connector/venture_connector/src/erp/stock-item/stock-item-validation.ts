import type {
  StockItemDetails,
} from './stock-item-domain.js';

export type StockItemValidationCode =
  | 'DUPLICATE_NAME'
  | 'DUPLICATE_ID'
  | 'DUPLICATE_GUID'
  | 'DUPLICATE_ALTER_ID'
  | 'MISSING_REQUIRED'
  | 'INCOMPLETE_UNIT'
  | 'INVALID_BALANCE'
  | 'WHITESPACE_NORMALIZED'
  | 'MALFORMED_XML';

export interface StockItemValidationIssue {
  readonly code: StockItemValidationCode;
  readonly field: string;
  readonly message: string;
  readonly stockItemId?: string;
  readonly severity: 'error' | 'warning';
}

export interface StockItemValidationResult {
  readonly ok: boolean;
  readonly issues: readonly StockItemValidationIssue[];
}

function normalizeKey(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, ' ');
}

export function validateStockItemCollection(items: readonly StockItemDetails[]): StockItemValidationResult {
  const issues: StockItemValidationIssue[] = [];
  const names = new Map<string, string>();
  const ids = new Map<string, string>();
  const guids = new Map<string, string>();
  const alterIds = new Map<string, string>();

  for (const item of items) {
    const nameKey = normalizeKey(item.name);
    if (names.has(nameKey)) {
      issues.push({
        code: 'DUPLICATE_NAME',
        field: 'name',
        message: `Duplicate stock item name "${item.name}".`,
        stockItemId: item.id,
        severity: 'error',
      });
    } else {
      names.set(nameKey, item.id);
    }

    if (ids.has(item.id)) {
      issues.push({
        code: 'DUPLICATE_ID',
        field: 'id',
        message: `Duplicate stock item ID "${item.id}".`,
        stockItemId: item.id,
        severity: 'error',
      });
    } else {
      ids.set(item.id, item.id);
    }

    if (item.guid) {
      const guidKey = item.guid.trim().toLowerCase();
      if (guids.has(guidKey)) {
        issues.push({
          code: 'DUPLICATE_GUID',
          field: 'guid',
          message: `Duplicate GUID "${item.guid}".`,
          stockItemId: item.id,
          severity: 'error',
        });
      } else {
        guids.set(guidKey, item.id);
      }
    }

    if (item.alterId) {
      const alterKey = item.alterId.trim();
      if (alterIds.has(alterKey)) {
        issues.push({
          code: 'DUPLICATE_ALTER_ID',
          field: 'alterId',
          message: `Duplicate AlterID "${item.alterId}".`,
          stockItemId: item.id,
          severity: 'error',
        });
      } else {
        alterIds.set(alterKey, item.id);
      }
    }

    if (!item.name.trim()) {
      issues.push({
        code: 'MISSING_REQUIRED',
        field: 'name',
        message: 'Stock item name is required.',
        stockItemId: item.id,
        severity: 'error',
      });
    }

    if (item.name !== item.name.trim()) {
      issues.push({
        code: 'WHITESPACE_NORMALIZED',
        field: 'name',
        message: 'Stock item name contained leading or trailing whitespace.',
        stockItemId: item.id,
        severity: 'warning',
      });
    }

    if (item.dataQuality === 'incomplete') {
      issues.push({
        code: 'INCOMPLETE_UNIT',
        field: 'baseUnit',
        message: 'Base unit not present in ERP export; item marked incomplete.',
        stockItemId: item.id,
        severity: 'warning',
      });
    }

    if (
      item.openingBalance &&
      item.openingBalance.amount !== null &&
      Number.isNaN(item.openingBalance.amount)
    ) {
      issues.push({
        code: 'INVALID_BALANCE',
        field: 'openingBalance',
        message: 'Opening balance format is invalid.',
        stockItemId: item.id,
        severity: 'error',
      });
    }
  }

  return {
    ok: !issues.some((issue) => issue.severity === 'error'),
    issues,
  };
}
