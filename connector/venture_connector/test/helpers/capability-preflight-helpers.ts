import { expect } from 'vitest';

import type { MockFetchCall } from './mock-fetch.js';

export function classifyTallyRequestBody(body: string): string {
  if (body.includes('License Info')) return 'health';
  if (body.includes('List of Companies')) return 'company-discovery';
  if (body.includes('List of Ledgers')) return 'ledgers';
  if (body.includes('List of Stock Items')) return 'stock-items';
  if (body.includes('List of Groups')) return 'ledger-groups';
  return 'other';
}

export function summarizeTallyCalls(calls: readonly MockFetchCall[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const call of calls) {
    const body = typeof call.init?.body === 'string' ? call.init.body : '';
    const kind = classifyTallyRequestBody(body);
    counts[kind] = (counts[kind] ?? 0) + 1;
  }
  return counts;
}

export function assertNoPrivatePreflightDetails(details: unknown): void {
  const serialized = JSON.stringify(details ?? {});
  expect(serialized).not.toMatch(/ESTIMATION|Demo Company|Cash|GSTIN|@/i);
  expect(serialized).not.toMatch(/<ENVELOPE|<LEDGER|<STOCKITEM/i);
}
