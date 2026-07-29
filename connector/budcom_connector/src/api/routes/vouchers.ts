import { Router } from 'express';

import type { VoucherSearchCriteria } from '../../erp/voucher/voucher-domain.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { VoucherApplicationService } from '../../services/voucher/voucher-application.interface.js';

const SCHEMA_VERSION = '1.0.0';
const MAX_PAGE_SIZE = 100;
const MAX_TEXT_LENGTH = 128;
const IDENTIFIER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const SORT_FIELDS = new Set<VoucherSearchCriteria['sortBy']>([
  'date',
  'voucherNumber',
  'amount',
]);

interface ValidationIssue {
  readonly field: string;
  readonly code: string;
  readonly message: string;
}

export function createVouchersRouter(application: VoucherApplicationService): Router {
  const router = Router();

  router.get('/api/v1/vouchers/search', listHandler(application));

  router.get(
    '/api/v1/vouchers/snapshots',
    asyncHandler(async (req, res) => {
      const company = requireCompany(req.query.company);
      const result = await application.listSnapshots(company);
      res.status(200).json({ schemaVersion: SCHEMA_VERSION, data: result });
    }),
  );

  router.get(
    '/api/v1/vouchers/snapshots/:snapshotId',
    asyncHandler(async (req, res) => {
      const company = requireCompany(req.query.company);
      const snapshotId = requireIdentifier(req.params.snapshotId, 'snapshotId');
      const result = await application.getSnapshot(company, snapshotId);
      if (!result.snapshot) {
        res.status(404).json({
          code: 'NOT_FOUND',
          message: 'Voucher snapshot was not found.',
        });
        return;
      }
      res.status(200).json({ schemaVersion: SCHEMA_VERSION, data: result });
    }),
  );

  router.get('/api/v1/vouchers', listHandler(application));

  router.get(
    '/api/v1/vouchers/:id',
    asyncHandler(async (req, res) => {
      const company = requireCompany(req.query.company);
      const voucherId = requireIdentifier(req.params.id, 'id');
      const result = await application.getDetails({ companyId: company, voucherId });
      if (!result.voucher) {
        res.status(404).json({
          code: 'NOT_FOUND',
          message: 'Voucher was not found.',
        });
        return;
      }
      res.status(200).json({ schemaVersion: SCHEMA_VERSION, data: result });
    }),
  );

  return router;
}

function listHandler(application: VoucherApplicationService) {
  return asyncHandler(async (req, res) => {
    const query = req.query as Record<string, unknown>;
    const company = requireCompany(query.company);
    const criteria = parseCriteria(query);
    const filterIssues: ValidationIssue[] = [];
    const voucherNumber = optionalText(query.voucherNumber, 'voucherNumber', filterIssues);
    const partyName = optionalText(query.partyName, 'partyName', filterIssues);
    throwIfInvalid(filterIssues);
    const result = await application.list({
      companyId: company,
      criteria,
      ...(voucherNumber ? { voucherNumber } : {}),
      ...(partyName ? { partyName } : {}),
    });
    res.status(200).json({ schemaVersion: SCHEMA_VERSION, data: result });
  });
}

function parseCriteria(query: Record<string, unknown>): VoucherSearchCriteria {
  const issues: ValidationIssue[] = [];
  const dateFrom = parseDate(query.from, 'from', issues);
  const dateTo = parseDate(query.to, 'to', issues);
  if (dateFrom && dateTo && dateFrom > dateTo) {
    issues.push({ field: 'to', code: 'INVALID_RANGE', message: 'to must not precede from.' });
  }
  const page = parsePositiveInteger(query.page, 'page', 1, Number.MAX_SAFE_INTEGER, issues);
  const pageSize = parsePositiveInteger(query.pageSize, 'pageSize', 50, MAX_PAGE_SIZE, issues);
  const { sortBy, sortDirection } = parseSort(query.sort, issues);
  const voucherType = optionalText(query.voucherType, 'voucherType', issues);
  const search = optionalText(query.q, 'q', issues);
  throwIfInvalid(issues);
  return {
    dateFrom: dateFrom!,
    dateTo: dateTo!,
    page,
    pageSize,
    sortBy,
    sortDirection,
    ...(voucherType ? { voucherType } : {}),
    ...(search ? { query: search } : {}),
  };
}

function requireCompany(value: unknown): string {
  const issues: ValidationIssue[] = [];
  const company = optionalText(value, 'company', issues);
  if (!company) {
    issues.push({ field: 'company', code: 'REQUIRED', message: 'company is required.' });
  }
  throwIfInvalid(issues);
  return company!;
}

function requireIdentifier(value: unknown, field: string): string {
  const text = singleString(value);
  if (!text || !IDENTIFIER_PATTERN.test(text)) {
    throwValidation([{
      field,
      code: 'INVALID_IDENTIFIER',
      message: `${field} must be a valid identifier.`,
    }]);
  }
  return text;
}

function parseDate(
  value: unknown,
  field: string,
  issues: ValidationIssue[],
): string | undefined {
  const text = singleString(value);
  if (!text) {
    issues.push({ field, code: 'REQUIRED', message: `${field} is required.` });
    return undefined;
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    issues.push({ field, code: 'INVALID_DATE', message: `${field} must use YYYY-MM-DD.` });
    return undefined;
  }
  const date = new Date(`${text}T00:00:00Z`);
  if (Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== text) {
    issues.push({ field, code: 'INVALID_DATE', message: `${field} is not a valid date.` });
    return undefined;
  }
  return text;
}

function parsePositiveInteger(
  value: unknown,
  field: string,
  defaultValue: number,
  maximum: number,
  issues: ValidationIssue[],
): number {
  if (value === undefined) return defaultValue;
  const text = singleString(value);
  if (!text || !/^\d+$/.test(text)) {
    issues.push({ field, code: 'INVALID_INTEGER', message: `${field} must be an integer.` });
    return defaultValue;
  }
  const parsed = Number(text);
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > maximum) {
    issues.push({
      field,
      code: 'OUT_OF_RANGE',
      message: `${field} must be between 1 and ${maximum}.`,
    });
    return defaultValue;
  }
  return parsed;
}

function parseSort(
  value: unknown,
  issues: ValidationIssue[],
): Pick<VoucherSearchCriteria, 'sortBy' | 'sortDirection'> {
  if (value === undefined) return { sortBy: 'date', sortDirection: 'asc' };
  const text = singleString(value);
  if (!text) {
    issues.push({ field: 'sort', code: 'INVALID_SORT', message: 'sort is invalid.' });
    return { sortBy: 'date', sortDirection: 'asc' };
  }
  const descending = text.startsWith('-');
  const [field, explicitDirection, extra] = (descending ? text.slice(1) : text).split(':');
  if (
    extra !== undefined ||
    !SORT_FIELDS.has(field as VoucherSearchCriteria['sortBy']) ||
    (explicitDirection !== undefined && explicitDirection !== 'asc' && explicitDirection !== 'desc')
  ) {
    issues.push({
      field: 'sort',
      code: 'INVALID_SORT',
      message: 'sort must be date, voucherNumber, or amount with optional :asc/:desc.',
    });
    return { sortBy: 'date', sortDirection: 'asc' };
  }
  return {
    sortBy: field as VoucherSearchCriteria['sortBy'],
    sortDirection: descending || explicitDirection === 'desc' ? 'desc' : 'asc',
  };
}

function optionalText(
  value: unknown,
  field: string,
  issues: ValidationIssue[] = [],
): string | undefined {
  if (value === undefined) return undefined;
  const text = singleString(value);
  if (!text || text.length > MAX_TEXT_LENGTH) {
    issues.push({
      field,
      code: 'INVALID_TEXT',
      message: `${field} must contain 1 to ${MAX_TEXT_LENGTH} characters.`,
    });
    return undefined;
  }
  return text;
}

function singleString(value: unknown): string | undefined {
  return typeof value === 'string' ? value.trim() : undefined;
}

function throwIfInvalid(issues: readonly ValidationIssue[]): void {
  if (issues.length) throwValidation(issues);
}

function throwValidation(issues: readonly ValidationIssue[]): never {
  throw new AppError(
    ErrorCodes.VALIDATION_ERROR,
    'Voucher request validation failed.',
    400,
    { errors: issues },
  );
}
