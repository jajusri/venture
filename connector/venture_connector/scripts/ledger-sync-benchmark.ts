import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { performance } from 'node:perf_hooks';

import { SqliteDatabase } from '../src/storage/sqlite/sqlite-database.js';
import { SqliteLedgerRepository } from '../src/storage/sqlite/sqlite-ledger-repository.js';
import type { LedgerDetails } from '../src/erp/ledger/ledger-domain.js';

const DATASET_SIZES = [1_000, 10_000, 50_000] as const;

function createLedger(index: number): LedgerDetails {
  const name = `Ledger ${index}`;
  return {
    id: `ledger-${index}`,
    name,
    normalizedName: name.toLowerCase(),
    parentGroup: index % 2 === 0 ? 'Sundry Debtors' : 'Sundry Creditors',
    status: 'active',
    balanceNature: 'debit',
    isDeleted: false,
    syncedAt: new Date().toISOString(),
  };
}

async function benchmarkSize(size: number, basePath: string) {
  const dbPath = path.join(basePath, `bench-${size}.db`);
  const database = new SqliteDatabase({ databasePath: dbPath });
  const repository = new SqliteLedgerRepository(database);
  const companyId = 'bench-company';
  const ledgers = Array.from({ length: size }, (_, index) => createLedger(index + 1));

  const parseStart = performance.now();
  const validated = ledgers.map((ledger) => ({ ...ledger }));
  const parseMs = performance.now() - parseStart;

  const insertStart = performance.now();
  await repository.upsertMany(companyId, validated);
  const insertMs = performance.now() - insertStart;

  const searchStart = performance.now();
  await repository.search(companyId, { page: 1, pageSize: 25, query: 'Ledger 1' });
  const searchMs = performance.now() - searchStart;

  const pageStart = performance.now();
  await repository.search(companyId, { page: 2, pageSize: 50 });
  const paginationMs = performance.now() - pageStart;

  const statsStart = performance.now();
  await repository.getStatistics(companyId);
  const statsMs = performance.now() - statsStart;

  const dbSizeBytes = fs.existsSync(dbPath) ? fs.statSync(dbPath).size : 0;
  database.close();

  return {
    size,
    parseMs: Math.round(parseMs),
    insertMs: Math.round(insertMs),
    searchMs: Math.round(searchMs),
    paginationMs: Math.round(paginationMs),
    statsMs: Math.round(statsMs),
    dbSizeBytes,
    constraints: size === 50_000 ? 'May be skipped on low-memory hosts' : null,
  };
}

async function main(): Promise<void> {
  const basePath = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-ledger-bench-'));
  const results = [];
  for (const size of DATASET_SIZES) {
    try {
      results.push(await benchmarkSize(size, basePath));
    } catch (error) {
      results.push({
        size,
        error: error instanceof Error ? error.message : String(error),
        blocked: true,
      });
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    nodeVersion: process.version,
    platform: process.platform,
    thresholds: {
      insertMsPer1k: 2000,
      searchMs: 250,
      paginationMs: 150,
    },
    results,
  };

  const outDir = path.resolve('docs/diagnostics');
  fs.mkdirSync(outDir, { recursive: true });
  const jsonPath = path.join(outDir, 'm5ap-ledger-sync-benchmark.json');
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  fs.rmSync(basePath, { recursive: true, force: true });
}

void main();
