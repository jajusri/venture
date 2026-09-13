import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import type { VoucherExtractionResult, VoucherReadPort } from '../../../src/erp/ports/vouchers.js';
import type { VoucherDetails } from '../../../src/erp/voucher/voucher-domain.js';
import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { VoucherExtractionService } from '../../../src/services/voucher/voucher-extraction.service.js';
import type { VoucherRepositoryPort } from '../../../src/services/voucher/voucher-repository.interface.js';
import {
  VoucherSnapshotSyncServiceImpl,
  VoucherSynchronizationService,
} from '../../../src/services/voucher/voucher-snapshot-sync.service.js';
import type {
  VoucherSyncCancellation,
  VoucherSyncProgressObserver,
} from '../../../src/services/voucher/voucher-sync-progress.js';
import { SqliteDatabase } from '../../../src/storage/sqlite/sqlite-database.js';
import {
  SqliteVoucherRepository,
  VoucherRepositoryError,
} from '../../../src/storage/sqlite/sqlite-voucher-repository.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { VoucherXmlMapper } from '../../../src/tally/voucher/voucher-mapper.js';
import { VoucherCollectionParser } from '../../../src/tally/voucher/voucher-parser.js';
import type { VoucherSyncFailureAuditor } from '../../../src/services/voucher/voucher-sync-failure-audit.js';
import { createTestVoucherSyncFailureAuditor } from '../../helpers/voucher-sync-failure-audit-test-helpers.js';
import { APPROVED_VOUCHER_FIXTURE_XML } from '../../fixtures/vouchers/approved-voucher-fixture.js';

const PERIOD = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };
const NEXT_PERIOD = { dateFrom: '2026-07-28', dateTo: '2026-07-28' };
const OLDER_PERIOD = { dateFrom: '2026-07-01', dateTo: '2026-07-01' };
const tempDirs: string[] = [];
const databases: SqliteDatabase[] = [];
let vouchers: readonly VoucherDetails[];

beforeAll(() => {
  const parser = new VoucherCollectionParser(new TallyXmlResponseParser());
  const parsed = parser.parse(APPROVED_VOUCHER_FIXTURE_XML);
  if (parsed.status !== 'records') throw new Error('Fixture parse failed.');
  const mapper = new VoucherXmlMapper(parser);
  vouchers = parsed.records.map((node) => {
    const mapped = mapper.map(node);
    if (!mapped.value) throw new Error('Fixture map failed.');
    return mapped.value;
  });
});

afterEach(async () => {
  for (const database of databases.splice(0)) database.close();
  await new Promise((resolve) => setTimeout(resolve, 20));
  for (const dir of tempDirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

function repository(): SqliteVoucherRepository {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-voucher-sync-'));
  tempDirs.push(dir);
  const database = new SqliteDatabase({ databasePath: path.join(dir, 'sync.db') });
  database.open();
  databases.push(database);
  return new SqliteVoucherRepository(database);
}

function extractionResult(
  items: readonly VoucherDetails[],
  overrides: Partial<VoucherExtractionResult> = {},
): VoucherExtractionResult {
  return {
    items,
    candidateRecordCount: items.length,
    droppedRecordCount: 0,
    validationIssues: [],
    responseStatus: items.length ? 'records' : 'empty',
    durationMs: 1,
    rawByteLength: 1,
    illegalCharactersSanitized: 0,
    amountSignConflictCount: 0,
    ...overrides,
  };
}

function service(
  repo: VoucherRepositoryPort,
  extract: VoucherReadPort['readVouchers'],
  batchSize?: number,
  resolveCompanyName?: (companyId: string) => Promise<string>,
  failureAuditor?: VoucherSyncFailureAuditor,
): VoucherSnapshotSyncServiceImpl {
  return new VoucherSnapshotSyncServiceImpl(
    new VoucherExtractionService({ readVouchers: extract }),
    repo,
    batchSize,
    undefined,
    undefined,
    resolveCompanyName,
    failureAuditor,
  );
}

const observer: VoucherSyncProgressObserver = { onProgress: () => undefined };
const notCancelled: VoucherSyncCancellation = { requested: false };

describe('VoucherSnapshotSyncServiceImpl', () => {
  it('promotes a successful empty period', async () => {
    const repo = repository();
    const result = await service(repo, async () => extractionResult([]))
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({
      outcome: 'completed',
      responseStatus: 'empty',
      candidateVoucherCount: 0,
      acceptedVoucherCount: 0,
      promotionOccurred: true,
      previousActiveSnapshotPreserved: false,
      vouchersExtracted: 0,
      vouchersPersisted: 0,
      droppedVouchers: 0,
      rollbackStatus: 'not_required',
      promoted: true,
    });
    expect(result.startedAt).toMatch(/Z$/);
    expect(result.finishedAt).toMatch(/Z$/);
    expect(result.durationMs).toBeGreaterThanOrEqual(0);
    expect(service(repo, async () => extractionResult([])))
      .toBeInstanceOf(VoucherSynchronizationService);
    expect(await repo.getActiveSnapshotMetadata('company-a')).toMatchObject({ period: PERIOD });
  });

  it('promotes multiple Vouchers with exact nested child metrics', async () => {
    const repo = repository();
    const result = await service(repo, async () => extractionResult(vouchers))
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    const stored = await repo.getSnapshotMetrics('company-a', result.snapshotId!);

    expect(result.outcome).toBe('completed');
    expect(stored).toEqual({
      voucherCount: result.acceptedVoucherCount,
      ledgerEntryCount: result.ledgerEntryCount,
      inventoryEntryCount: result.inventoryEntryCount,
      allocationCount: result.allocationCount,
    });
    expect(await repo.querySnapshot('company-a')).toHaveLength(vouchers.length);
  });

  it('archives the previous snapshot when a replacement is promoted', async () => {
    const repo = repository();
    const sync = service(repo, async (_company, period) =>
      extractionResult([
        period.dateFrom === PERIOD.dateFrom ? vouchers[0]! : vouchers[1]!,
      ])
    );
    const first = await sync.synchronize(
      { companyId: 'company-a', ...PERIOD },
      observer,
      notCancelled,
    );
    const second = await sync.synchronize(
      { companyId: 'company-a', ...NEXT_PERIOD },
      observer,
      notCancelled,
    );

    expect(second).toMatchObject({ outcome: 'completed', promoted: true });
    expect((await repo.getSnapshot('company-a', first.snapshotId!))?.status).toBe('Archived');
    expect((await repo.getSnapshot('company-a', second.snapshotId!))?.status).toBe('Promoted');
  });

  it.each([
    ['partial response', { responseStatus: 'partial' as const }, 'partial_response'],
    ['rejected record', { candidateRecordCount: 2, droppedRecordCount: 1 }, 'rejected_records'],
    ['unaccounted candidate', { candidateRecordCount: 2 }, 'candidate_count_mismatch'],
  ])('does not promote a %s', async (_name, overrides, reason) => {
    const repo = repository();
    const result = await service(repo, async () => extractionResult([vouchers[0]!], overrides))
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({
      outcome: 'failed',
      failureReason: reason,
      promotionOccurred: false,
      previousActiveSnapshotPreserved: true,
    });
    expect(await repo.getActiveSnapshotMetadata('company-a')).toBeNull();
  });

  it('classifies extraction/parser/source failures and never retries', async () => {
    const repo = repository();
    const extract = vi.fn<VoucherReadPort['readVouchers']>().mockRejectedValue(
      Object.assign(new Error('source rejected envelope'), { name: 'VoucherParserError' }),
    );
    const result = await service(repo, extract)
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({
      outcome: 'failed',
      failureReason: 'parser_failure',
      promotionOccurred: false,
    });
    expect(extract).toHaveBeenCalledTimes(1);
  });

  // Diagnostic hardening (2026-08-16, TD-001 follow-up): classifyExtractionFailure()
  // collapses seven distinct parser causes into the single external 'parser_failure'
  // bucket for compatibility -- this proves the granular, privacy-safe reasonCode
  // (failureDetail) survives alongside it instead of being discarded, and that it is
  // exactly the fixed enum string from AppError.details.reasonCode, never raw error
  // text or business content.
  it.each([
    ['malformed-xml', 'Voucher response is not well-formed XML.'],
    ['missing-envelope', 'Voucher response root must be ENVELOPE.'],
    ['missing-header', 'Voucher response is missing HEADER.'],
    ['missing-body', 'Voucher response is missing BODY.'],
    ['missing-data', 'Voucher response is missing BODY/DATA.'],
    ['missing-collection', 'Voucher response is missing BODY/DATA/COLLECTION.'],
    ['voucher-discovery-expansion', 'Voucher discovery response contains forbidden monetary or compound fields.'],
    ['voucher-ledger-validation', 'Voucher ledger response failed closed validation.'],
    ['voucher-inventory-validation', 'Voucher inventory response failed closed validation.'],
  ])('preserves the granular reasonCode %s as failureDetail without leaking the raw message', async (reasonCode, message) => {
    const repo = repository();
    const extract = vi.fn<VoucherReadPort['readVouchers']>().mockRejectedValue(
      new AppError(ErrorCodes.VALIDATION_ERROR, message, 422, { reasonCode }),
    );
    const result = await service(repo, extract)
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({
      outcome: 'failed',
      failureReason: 'parser_failure',
      failureDetail: reasonCode,
    });
    expect(JSON.stringify(result)).not.toContain(message);
  });

  it('preserves failureDetail as tally_source_error-adjacent null shape when the reasonCode indicates a Tally source error', async () => {
    const repo = repository();
    const extract = vi.fn<VoucherReadPort['readVouchers']>().mockRejectedValue(
      new AppError(ErrorCodes.VALIDATION_ERROR, 'Tally returned a source error for the Voucher export.', 422, {
        reasonCode: 'tally-source-error',
      }),
    );
    const result = await service(repo, extract)
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({
      outcome: 'failed',
      failureReason: 'tally_source_error',
      failureDetail: 'tally-source-error',
    });
  });

  it('records a structural-only entry via the file-based failureAuditor when extraction fails', async () => {
    const repo = repository();
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-voucher-failure-audit-'));
    tempDirs.push(dir);
    const auditPath = path.join(dir, 'audit.jsonl');
    const auditor = createTestVoucherSyncFailureAuditor(auditPath);
    const extract = vi.fn<VoucherReadPort['readVouchers']>().mockRejectedValue(
      new AppError(ErrorCodes.VALIDATION_ERROR, 'Voucher inventory response failed closed validation.', 422, {
        reasonCode: 'voucher-inventory-validation',
        reconciliationReason: 'orphan-inventory-entry',
        operation: 'VoucherInventoryEntries',
      }),
    );

    const result = await service(repo, extract, undefined, undefined, auditor)
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({ outcome: 'failed', failureReason: 'parser_failure' });
    const contents = fs.readFileSync(auditPath, 'utf8');
    const entry = JSON.parse(contents.trim()) as Record<string, unknown>;
    expect(entry).toMatchObject({
      companyId: 'company-a',
      failureReason: 'parser_failure',
      details: {
        reasonCode: 'voucher-inventory-validation',
        reconciliationReason: 'orphan-inventory-entry',
        operation: 'VoucherInventoryEntries',
      },
    });
    expect(contents).not.toContain('failed closed validation');
  });

  it('reports illegalCharactersSanitized on a successful sync that required sanitization', async () => {
    const repo = repository();
    const result = await service(
      repo,
      async () => extractionResult([vouchers[0]!], { illegalCharactersSanitized: 2 }),
    ).synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({ outcome: 'completed', illegalCharactersSanitized: 2 });
  });

  // TD-001 round 4 (2026-08-16, Option C): a tolerated ledger-entry amount-sign
  // conflict must not abort the sync -- it completes normally and the count is
  // surfaced through the full VoucherSynchronizationResult, same as
  // illegalCharactersSanitized above.
  it('reports amountSignConflictCount on a successful sync that tolerated a conflict', async () => {
    const repo = repository();
    const result = await service(
      repo,
      async () => extractionResult([vouchers[0]!], { amountSignConflictCount: 1 }),
    ).synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result).toMatchObject({ outcome: 'completed', amountSignConflictCount: 1 });
  });

  it.each(['writeVoucherBatch', 'finalizeSnapshot', 'promoteSnapshot'] as const)(
    'preserves the previous ACTIVE snapshot when %s fails',
    async (method) => {
      const repo = repository();
      const first = await service(repo, async () => extractionResult([vouchers[0]!]))
        .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
      expect(first.outcome).toBe('completed');

      const failing = proxyRepository(repo, method, async () => {
        throw new VoucherRepositoryError('SNAPSHOT_INCOMPLETE', `${method} failed`);
      });
      const failed = await service(failing, async () => extractionResult([vouchers[1]!]))
        .synchronize({ companyId: 'company-a', ...NEXT_PERIOD }, observer, notCancelled);

      expect(failed).toMatchObject({
        outcome: 'failed',
        repositoryFailureCode: 'SNAPSHOT_INCOMPLETE',
        previousActiveSnapshotPreserved: true,
        promotionOccurred: false,
        rollbackStatus: 'completed',
      });
      expect(await repo.getSnapshot('company-a', failed.snapshotId!)).toBeNull();
      expect((await repo.getActiveSnapshotMetadata('company-a'))?.snapshotId).toBe(first.snapshotId);
      // A failed windowed refresh must leave the previous snapshot's actual content intact and
      // fully readable, not just its metadata pointer.
      const active = await repo.querySnapshot('company-a');
      expect(active.map((voucher) => voucher.voucherId)).toEqual([vouchers[0]!.voucherId]);
    },
  );

  it('supports cancellation before extraction, after extraction, before completion, and before promotion', async () => {
    for (const point of ['extracting', 'after-extraction', 'validating_snapshot', 'promoting'] as const) {
      const repo = repository();
      const cancellation = { requested: false };
      const phaseObserver: VoucherSyncProgressObserver = {
        onProgress(progress) {
          if (progress.phase === point) cancellation.requested = true;
        },
      };
      const extract = async (): Promise<VoucherExtractionResult> => {
        const value = extractionResult([vouchers[0]!]);
        if (point === 'after-extraction') cancellation.requested = true;
        return value;
      };
      const result = await service(repo, extract)
        .synchronize({ companyId: `company-${point}`, ...PERIOD }, phaseObserver, cancellation);
      expect(result.outcome).toBe('cancelled');
      expect(result.promotionOccurred).toBe(false);
    }
  });

  it('cancels safely between staging batches', async () => {
    const repo = repository();
    const cancellation = { requested: false };
    let calls = 0;
    const wrapped = proxyRepository(repo, 'writeVoucherBatch', async (...args) => {
      calls += 1;
      await repo.writeVoucherBatch(...args);
      cancellation.requested = true;
    });
    const result = await service(wrapped, async () => extractionResult(vouchers.slice(0, 2)), 1)
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, cancellation);

    expect(calls).toBe(1);
    expect(result.outcome).toBe('cancelled');
    expect(result.rollbackStatus).toBe('completed');
    expect(result.vouchersPersisted).toBe(1);
    expect(await repo.getActiveSnapshotMetadata('company-a')).toBeNull();
  });

  it('rejects same-company overlap while allowing a different company', async () => {
    const repo = repository();
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const extract = vi.fn(async () => {
      await gate;
      return extractionResult([]);
    });
    const sync = service(repo, extract);
    const first = sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    await vi.waitFor(() => expect(extract).toHaveBeenCalledTimes(1));
    const conflict = await sync.synchronize(
      { companyId: 'company-a', ...NEXT_PERIOD },
      observer,
      notCancelled,
    );
    const other = sync.synchronize({ companyId: 'company-b', ...PERIOD }, observer, notCancelled);
    await vi.waitFor(() => expect(extract).toHaveBeenCalledTimes(2));
    release();

    expect(conflict).toMatchObject({ outcome: 'conflict', failureReason: 'already_running' });
    expect((await first).outcome).toBe('completed');
    expect((await other).outcome).toBe('completed');
  });

  it('performs a real live extraction on a same-day second explicit sync with an identical period, never short-circuiting', async () => {
    // Regression test for the already_current bug: matching date strings must never be treated
    // as proof Tally content is unchanged. A repeated explicit sync for the same window must
    // always re-read Tally, never returning 'already_current' or skipping extraction.
    const repo = repository();
    const extract = vi.fn(async () => extractionResult([vouchers[0]!]));
    const sync = service(repo, extract);
    const first = await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    const second = await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(extract).toHaveBeenCalledTimes(2);
    expect(second.outcome).toBe('completed');
    expect(second.promotionOccurred).toBe(true);
    expect(second.snapshotId).not.toBe(first.snapshotId);
  });

  it('creates no duplicate logical voucher when an identical re-extraction changes nothing', async () => {
    const repo = repository();
    const sync = service(repo, async () => extractionResult([vouchers[0]!]));
    await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    const active = await repo.querySnapshot('company-a');
    expect(active).toHaveLength(1);
    expect(active[0]!.voucherId).toBe(vouchers[0]!.voucherId);
  });

  it('carries forward vouchers outside the refreshed window instead of discarding them', async () => {
    // The core windowed-refresh invariant: a sync of one window must not erase coverage of a
    // different, previously-synced window — the resulting snapshot is a complete union, not a
    // partial replacement.
    const repo = repository();
    const sync = service(repo, async (_company, period) =>
      extractionResult([period.dateFrom === PERIOD.dateFrom ? vouchers[0]! : vouchers[1]!]),
    );
    await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    const second = await sync.synchronize(
      { companyId: 'company-a', ...NEXT_PERIOD },
      observer,
      notCancelled,
    );

    const active = await repo.querySnapshot('company-a');
    const activeIds = active.map((voucher) => voucher.voucherId).sort();
    expect(activeIds).toEqual([vouchers[0]!.voucherId, vouchers[1]!.voucherId].sort());
    expect(second.vouchersPersisted).toBe(2);
    expect((await repo.getActiveSnapshotMetadata('company-a'))?.period).toEqual({
      dateFrom: PERIOD.dateFrom,
      dateTo: NEXT_PERIOD.dateTo,
    });
  });

  it('makes a back-dated voucher visible once its window is reconciled, without disturbing the already-refreshed recent window', async () => {
    const repo = repository();
    const sync = service(repo, async (_company, period) =>
      extractionResult([period.dateFrom === NEXT_PERIOD.dateFrom ? vouchers[1]! : vouchers[0]!]),
    );
    // Fast recent-window refresh happens first, matching the Phase A / Phase B ordering.
    await sync.synchronize({ companyId: 'company-a', ...NEXT_PERIOD }, observer, notCancelled);
    // Background historical reconciliation later discovers the back-dated voucher.
    await sync.synchronize({ companyId: 'company-a', ...OLDER_PERIOD }, observer, notCancelled);

    const active = await repo.querySnapshot('company-a');
    const activeIds = active.map((voucher) => voucher.voucherId).sort();
    expect(activeIds).toEqual([vouchers[0]!.voucherId, vouchers[1]!.voucherId].sort());
  });

  it('replaces the old value of an edited voucher (party/amount/etc.) after its window is resynced, with no duplicate row', async () => {
    const repo = repository();
    const original = vouchers[0]!;
    const edited: VoucherDetails = {
      ...original,
      partyName: 'Changed Party Pvt Ltd',
      narration: 'Corrected narration',
      amount: original.amount ? { ...original.amount, amount: '999999' } : original.amount,
    };
    const sync = service(repo, async () => extractionResult([original]));
    await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    const resync = service(repo, async () => extractionResult([edited]));
    await resync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    const active = await repo.querySnapshot('company-a');
    expect(active).toHaveLength(1);
    expect(active[0]!.partyName).toBe('Changed Party Pvt Ltd');
    expect(active[0]!.narration).toBe('Corrected narration');
  });

  it('removes a cancelled/deleted voucher from the active view once its window is resynced without it', async () => {
    const repo = repository();
    const firstSync = service(repo, async () => extractionResult([vouchers[0]!, vouchers[1]!]));
    await firstSync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    // Tally no longer returns vouchers[1] for this window — it was cancelled/deleted.
    const resync = service(repo, async () => extractionResult([vouchers[0]!]));
    await resync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    const active = await repo.querySnapshot('company-a');
    expect(active.map((voucher) => voucher.voucherId)).toEqual([vouchers[0]!.voucherId]);
  });

  // TD-026: proves the carry-forward mechanism never "bakes in" permanence for an older voucher —
  // even after it has already been carried forward at least once by an unrelated intervening sync,
  // resyncing its own specific window still tombstones it the moment Tally stops returning it
  // there. Distinct from the "removes a deleted voucher" test above, which only ever resyncs the
  // SAME window twice in a row and never exercises an intervening carry-forward in between.
  // Carry-forward eligibility is keyed on each voucher's own embedded `date` field, not on which
  // period a stub happened to return it under — so this uses two synthetic clones explicitly dated
  // inside OLDER_PERIOD, distinct from the shared `vouchers` fixture (which is dated to match
  // PERIOD, matched by every other test in this file that syncs it under PERIOD).
  it('tombstones an older voucher once its own window is reconciled, even after an intervening carry-forward left it untouched', async () => {
    const repo = repository();
    const olderVoucherA: VoucherDetails = { ...vouchers[0]!, voucherId: 'older-voucher-a', date: OLDER_PERIOD.dateFrom };
    const olderVoucherB: VoucherDetails = { ...vouchers[1]!, voucherId: 'older-voucher-b', date: OLDER_PERIOD.dateFrom };

    const sync = service(repo, async (_company, period) =>
      period.dateFrom === OLDER_PERIOD.dateFrom
        ? extractionResult([olderVoucherA, olderVoucherB])
        : extractionResult([]),
    );
    // 1. The older window is synced fresh: both vouchers present, correctly dated within it.
    await sync.synchronize({ companyId: 'company-a', ...OLDER_PERIOD }, observer, notCancelled);
    // 2. An unrelated, more recent window is synced (nothing new there) — this carries the older
    //    window's two vouchers forward untouched, exactly the intervening step this test targets.
    await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    let active = await repo.querySnapshot('company-a');
    expect(active.map((voucher) => voucher.voucherId).sort()).toEqual(
      ['older-voucher-a', 'older-voucher-b'].sort(),
    );

    // 3. The older window is reconciled again — Tally no longer returns olderVoucherB there (it
    //    was cancelled/deleted in Tally). Carry-forward for everything OUTSIDE the older window
    //    still applies, but nothing else exists outside it in this scenario.
    const resync = service(repo, async () => extractionResult([olderVoucherA]));
    await resync.synchronize({ companyId: 'company-a', ...OLDER_PERIOD }, observer, notCancelled);

    active = await repo.querySnapshot('company-a');
    expect(active.map((voucher) => voucher.voucherId)).toEqual(['older-voucher-a']);
  });

  it('never carries forward or otherwise touches another company\'s vouchers', async () => {
    const repo = repository();
    await service(repo, async () => extractionResult([vouchers[0]!]))
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    await service(repo, async () => extractionResult([vouchers[1]!]))
      .synchronize({ companyId: 'company-b', ...PERIOD }, observer, notCancelled);

    await service(repo, async () => extractionResult([vouchers[1]!]))
      .synchronize({ companyId: 'company-a', ...NEXT_PERIOD }, observer, notCancelled);

    const companyB = await repo.querySnapshot('company-b');
    expect(companyB.map((voucher) => voucher.voucherId)).toEqual([vouchers[1]!.voucherId]);
  });

  it('rolls back interrupted snapshots on restart and preserves company isolation', async () => {
    const repo = repository();
    await repo.beginSnapshot('company-a', 'stale-a', NEXT_PERIOD);
    await repo.beginSnapshot('company-b', 'staging-b', NEXT_PERIOD);

    const result = await service(repo, async () => extractionResult([]))
      .synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);

    expect(result.outcome).toBe('completed');
    expect(await repo.getSnapshotMetrics('company-b', 'staging-b')).toEqual({
      voucherCount: 0,
      ledgerEntryCount: 0,
      inventoryEntryCount: 0,
      allocationCount: 0,
    });
    expect(await repo.getSnapshot('company-a', 'stale-a')).toBeNull();
    expect((await repo.getSnapshot('company-b', 'staging-b'))?.status).toBe('Pending');
  });

  it('propagates AbortSignal cancellation into extraction', async () => {
    const repo = repository();
    const controller = new AbortController();
    const extract = vi.fn<VoucherReadPort['readVouchers']>(
      async (_company, _period, options) => {
        expect(options?.signal).toBe(controller.signal);
        controller.abort();
        throw new AppError(ErrorCodes.SYNC_CANCELLED, 'cancelled', 499);
      },
    );
    const result = await service(repo, extract).synchronize(
      { companyId: 'company-abort', ...PERIOD },
      observer,
      { requested: false, signal: controller.signal },
    );
    expect(result).toMatchObject({
      outcome: 'cancelled',
      rollbackStatus: 'completed',
      promoted: false,
    });
  });

  it.each(['validating', 'completed'] as const)(
    'isolates an observer failure during %s',
    async (phase) => {
      const repo = repository();
      const result = await service(repo, async () => extractionResult([])).synchronize(
        { companyId: `company-observer-${phase}`, ...PERIOD },
        {
          onProgress(progress) {
            if (progress.phase === phase) throw new Error('observer failed');
          },
        },
        notCancelled,
      );

      expect(result).toMatchObject({
        outcome: 'completed',
        promotionOccurred: true,
        notificationFailureCount: 1,
      });
      expect(Object.isFrozen(result)).toBe(true);
    },
  );

  it('isolates synchronous and asynchronous failures across multiple observers', async () => {
    const repo = repository();
    const successful = vi.fn();
    const result = await service(repo, async () => extractionResult([])).synchronize(
      { companyId: 'company-observers', ...PERIOD },
      [
        { onProgress: successful },
        { onProgress: () => { throw new Error('sync observer failure'); } },
        { onProgress: async () => { throw new Error('async observer failure'); } },
      ],
      notCancelled,
    );

    expect(result.outcome).toBe('completed');
    expect(result.notificationFailureCount).toBeGreaterThanOrEqual(2);
    expect(successful).toHaveBeenCalled();
  });

  it('releases the database reservation when synchronization rolls back', async () => {
    const repo = repository();
    const failed = await service(repo, async () => {
      throw Object.assign(new Error('parser failed'), { name: 'VoucherParserError' });
    }).synchronize({ companyId: 'company-release', ...PERIOD }, observer, notCancelled);
    expect(failed.outcome).toBe('failed');

    const retry = await service(repo, async () => extractionResult([]))
      .synchronize({ companyId: 'company-release', ...PERIOD }, observer, notCancelled);
    expect(retry.outcome).toBe('completed');
  });

  it('extracts with resolved Tally company name while storing under companyId', async () => {
    const repo = repository();
    const seenNames: string[] = [];
    const result = await service(
      repo,
      async (companyName) => {
        seenNames.push(companyName);
        return extractionResult([]);
      },
      undefined,
      async (companyId) => {
        expect(companyId).toBe('venture-test-01');
        return 'Venture-Test-01';
      },
    ).synchronize({ companyId: 'venture-test-01', ...PERIOD }, observer, notCancelled);

    expect(seenNames).toEqual(['Venture-Test-01']);
    expect(result.outcome).toBe('completed');
    expect(await repo.getActiveSnapshotMetadata('venture-test-01')).toMatchObject({ period: PERIOD });
  });
});

function proxyRepository<K extends keyof VoucherRepositoryPort>(
  repo: VoucherRepositoryPort,
  method: K,
  replacement: VoucherRepositoryPort[K],
): VoucherRepositoryPort {
  return new Proxy(repo, {
    get(target, property) {
      if (property === method) return replacement;
      const value = Reflect.get(target, property);
      return typeof value === 'function' ? value.bind(target) : value;
    },
  });
}
