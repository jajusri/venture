import { describe, expect, it } from 'vitest';

import { paginateItems, parsePaginationParams } from '../../../src/extraction/core/pagination.js';

describe('pagination', () => {
  it('parses query params with defaults', () => {
    expect(parsePaginationParams({})).toEqual({ page: 1, pageSize: 50 });
  });

  it('caps page size', () => {
    expect(parsePaginationParams({ pageSize: '1000' }).pageSize).toBe(500);
  });

  it('paginates items', () => {
    const items = Array.from({ length: 5 }, (_, i) => i + 1);
    const page = paginateItems(items, { page: 2, pageSize: 2 });
    expect(page.items).toEqual([3, 4]);
    expect(page.pagination).toEqual({
      page: 2,
      pageSize: 2,
      totalItems: 5,
      hasMore: true,
    });
  });
});
