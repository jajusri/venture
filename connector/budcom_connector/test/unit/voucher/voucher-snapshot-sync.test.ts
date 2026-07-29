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
import { APPROVED_VOUCHER_FIXTURE_XML } from '../../fixtures/vouchers/approved-voucher-fixture.js';

const PERIOD = { dateFrom: '2026-07-27', dateTo: '2026-07-27' };
const NEXT_PERIOD = { dateFrom: '2026-07-28', dateTo: '2026-07-28' };
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
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-voucher-sync-'));
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
    ...overrides,
  };
}

function service(
  repo: VoucherRepositoryPort,
  extract: VoucherReadPort['readVouchers'],
  batchSize?: number,
  resolveCompanyName?: (companyId: string) => Promise<string>,
): VoucherSnapshotSyncServiceImpl {
  return new VoucherSnapshotSyncServiceImpl(
    new VoucherExtractionService({ readVouchers: extract }),
    repo,
    batchSize,
    undefined,
    undefined,
    resolveCompanyName,
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

  it('returns already_current for a repeated company/period without extracting again', async () => {
    const repo = repository();
    const extract = vi.fn(async () => extractionResult([vouchers[0]!]));
    const sync = service(repo, extract);
    const first = await sync.synchronize({ companyId: 'company-a', ...PERIOD }, observer, notCancelled);
    const duplicate = await sync.synchronize(
      { companyId: 'company-a', ...PERIOD },
      observer,
      notCancelled,
    );

    expect(duplicate).toMatchObject({
      outcome: 'already_current',
      snapshotId: first.snapshotId,
      previousActiveSnapshotPreserved: true,
      promotionOccurred: false,
    });
    expect(extract).toHaveBeenCalledTimes(1);
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
        expect(companyId).toBe('budcom-test-01');
        return 'Budcom-Test-01';
      },
    ).synchronize({ companyId: 'budcom-test-01', ...PERIOD }, observer, notCancelled);

    expect(seenNames).toEqual(['Budcom-Test-01']);
    expect(result.outcome).toBe('completed');
    expect(await repo.getActiveSnapshotMetadata('budcom-test-01')).toMatchObject({ period: PERIOD });
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
