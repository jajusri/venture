import type { LedgerDetails } from './ledger-domain.js';

export type LedgerValidationCode =
  | 'DUPLICATE_NAME'
  | 'DUPLICATE_GUID'
  | 'DUPLICATE_RESOLVED_ID'
  | 'DUPLICATE_ALTER_ID'
  | 'INVALID_PARENT'
  | 'CIRCULAR_REFERENCE'
  | 'RESERVED_CONFLICT'
  | 'MISSING_REQUIRED'
  | 'INVALID_BALANCE'
  | 'WHITESPACE_NORMALIZED'
  | 'ENCODING_ISSUE'
  | 'MALFORMED_XML';

export interface LedgerValidationIssue {
  readonly code: LedgerValidationCode;
  readonly field: string;
  readonly message: string;
  readonly ledgerId?: string;
  readonly severity: 'error' | 'warning';
}

export interface LedgerValidationResult {
  readonly ok: boolean;
  readonly issues: readonly LedgerValidationIssue[];
}

const RESERVED_LEDGER_NAMES = new Set([
  'primary',
  'cash',
  'profit & loss a/c',
  'profit and loss a/c',
]);

function normalizeKey(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, ' ');
}

export function validateLedgerCollection(ledgers: readonly LedgerDetails[]): LedgerValidationResult {
  const issues: LedgerValidationIssue[] = [];
  const names = new Map<string, string>();
  const guids = new Map<string, string>();
  const alterIds = new Map<string, string>();
  const resolvedIds = new Map<string, string>();
  const parentMap = new Map<string, string | undefined>();

  for (const ledger of ledgers) {
    parentMap.set(ledger.id, ledger.parentGroup);

    if (resolvedIds.has(ledger.id)) {
      issues.push({
        code: 'DUPLICATE_RESOLVED_ID',
        field: 'id',
        message: `Duplicate resolved ledger identity "${ledger.id}".`,
        ledgerId: ledger.id,
        severity: 'error',
      });
    } else {
      resolvedIds.set(ledger.id, ledger.name);
    }

    const nameKey = normalizeKey(ledger.name);
    if (names.has(nameKey)) {
      issues.push({
        code: 'DUPLICATE_NAME',
        field: 'name',
        message: `Duplicate ledger name "${ledger.name}".`,
        ledgerId: ledger.id,
        severity: 'error',
      });
    } else {
      names.set(nameKey, ledger.id);
    }

    if (ledger.guid) {
      const guidKey = ledger.guid.trim().toLowerCase();
      if (guids.has(guidKey)) {
        issues.push({
          code: 'DUPLICATE_GUID',
          field: 'guid',
          message: `Duplicate GUID "${ledger.guid}".`,
          ledgerId: ledger.id,
          severity: 'error',
        });
      } else {
        guids.set(guidKey, ledger.id);
      }
    }

    if (ledger.alterId) {
      const alterKey = ledger.alterId.trim();
      if (alterIds.has(alterKey)) {
        issues.push({
          code: 'DUPLICATE_ALTER_ID',
          field: 'alterId',
          message: `Duplicate AlterID "${ledger.alterId}".`,
          ledgerId: ledger.id,
          severity: 'error',
        });
      } else {
        alterIds.set(alterKey, ledger.id);
      }
    }

    if (!ledger.name.trim()) {
      issues.push({
        code: 'MISSING_REQUIRED',
        field: 'name',
        message: 'Ledger name is required.',
        ledgerId: ledger.id,
        severity: 'error',
      });
    }

    if (ledger.name !== ledger.name.trim()) {
      issues.push({
        code: 'WHITESPACE_NORMALIZED',
        field: 'name',
        message: 'Ledger name contained leading or trailing whitespace.',
        ledgerId: ledger.id,
        severity: 'warning',
      });
    }

    if (ledger.parentGroup && normalizeKey(ledger.parentGroup) === normalizeKey(ledger.name)) {
      issues.push({
        code: 'CIRCULAR_REFERENCE',
        field: 'parentGroup',
        message: 'Ledger parent cannot equal ledger name.',
        ledgerId: ledger.id,
        severity: 'error',
      });
    }

    if (RESERVED_LEDGER_NAMES.has(nameKey) && ledger.status !== 'reserved') {
      issues.push({
        code: 'RESERVED_CONFLICT',
        field: 'name',
        message: `Ledger name "${ledger.name}" conflicts with reserved Tally name.`,
        ledgerId: ledger.id,
        severity: 'warning',
      });
    }

    if (
      ledger.openingBalance &&
      ledger.openingBalance.amount !== null &&
      Number.isNaN(ledger.openingBalance.amount)
    ) {
      issues.push({
        code: 'INVALID_BALANCE',
        field: 'openingBalance',
        message: 'Opening balance format is invalid.',
        ledgerId: ledger.id,
        severity: 'error',
      });
    }
  }

  for (const ledger of ledgers) {
    if (!ledger.parentGroup) {
      continue;
    }
    const visited = new Set<string>([ledger.id]);
    let current: string | undefined = normalizeKey(ledger.parentGroup);
    while (current) {
      const parentLedger = [...ledgers].find((item) => normalizeKey(item.name) === current);
      if (!parentLedger) {
        issues.push({
          code: 'INVALID_PARENT',
          field: 'parentGroup',
          message: `Parent group "${ledger.parentGroup}" was not found in ledger collection.`,
          ledgerId: ledger.id,
          severity: 'warning',
        });
        break;
      }
      if (visited.has(parentLedger.id)) {
        issues.push({
          code: 'CIRCULAR_REFERENCE',
          field: 'parentGroup',
          message: 'Circular parent reference detected.',
          ledgerId: ledger.id,
          severity: 'error',
        });
        break;
      }
      visited.add(parentLedger.id);
      current = parentLedger.parentGroup ? normalizeKey(parentLedger.parentGroup) : undefined;
    }
  }

  return {
    ok: !issues.some((issue) => issue.severity === 'error'),
    issues,
  };
}

export function reportMalformedXml(detail: string): LedgerValidationIssue {
  return {
    code: 'MALFORMED_XML',
    field: 'xml',
    message: detail,
    severity: 'error',
  };
}
