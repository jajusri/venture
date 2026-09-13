import type { PaginationMeta, PaginationParams } from '../core/types.js';

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 50;
export const MAX_PAGE_SIZE = 500;

export function parsePaginationParams(query: {
  page?: string;
  pageSize?: string;
}): PaginationParams {
  const page = parsePositiveInt(query.page, DEFAULT_PAGE);
  const pageSize = Math.min(parsePositiveInt(query.pageSize, DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);
  return { page, pageSize };
}

function parsePositiveInt(value: string | undefined, fallback: number): number {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) return fallback;
  return parsed;
}

export function paginateItems<T>(
  items: readonly T[],
  params: PaginationParams,
): { items: T[]; pagination: PaginationMeta } {
  const totalItems = items.length;
  const start = (params.page - 1) * params.pageSize;
  const end = start + params.pageSize;
  const pageItems = items.slice(start, end);
  return {
    items: pageItems,
    pagination: {
      page: params.page,
      pageSize: params.pageSize,
      totalItems,
      hasMore: end < totalItems,
    },
  };
}

export function dedupeById<T extends { id: string }>(items: readonly T[]): T[] {
  const seen = new Set<string>();
  const result: T[] = [];
  for (const item of items) {
    if (seen.has(item.id)) continue;
    seen.add(item.id);
    result.push(item);
  }
  return result;
}
