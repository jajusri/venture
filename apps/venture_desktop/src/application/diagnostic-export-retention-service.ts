import fs from 'node:fs';
import path from 'node:path';

import {
  isAppOwnedDiagnosticExportDirName,
  parseDiagnosticExportDirTimestampMs,
  retentionPeriodMs,
} from './diagnostic-export-convention.js';
import type { StructuredLogInput } from './log-service.js';

export interface DiagnosticExportCleanupResult {
  readonly ok: boolean;
  readonly scannedCount: number;
  readonly eligibleCount: number;
  readonly deletedCount: number;
  readonly preservedNewestCount: number;
  readonly skippedCount: number;
  readonly failureCount: number;
  readonly invalidTimestampCount: number;
}

export interface DiagnosticExportRetentionCleanupOptions {
  readonly exportDir: string;
  readonly retentionDays: number;
  readonly nowMs?: number;
}

export interface DiagnosticExportRetentionFs {
  existsSync(filePath: string): boolean;
  readdirSync(dirPath: string): string[];
  lstatSync(filePath: string): {
    isDirectory(): boolean;
    isSymbolicLink(): boolean;
    mtimeMs: number;
  };
  rmSync(filePath: string, options: { recursive: boolean; force: boolean }): void;
}

export interface DiagnosticExportRetentionServiceOptions {
  readonly fsImpl?: DiagnosticExportRetentionFs;
  readonly now?: () => number;
  readonly log?: (input: StructuredLogInput) => void;
}

interface EligibleExport {
  readonly name: string;
  readonly dirPath: string;
  readonly exportTimeMs: number;
  readonly timestampSource: 'dirname' | 'mtime';
  readonly invalidTimestamp: boolean;
}

function emptyResult(partial: Partial<DiagnosticExportCleanupResult> = {}): DiagnosticExportCleanupResult {
  return {
    ok: true,
    scannedCount: 0,
    eligibleCount: 0,
    deletedCount: 0,
    preservedNewestCount: 0,
    skippedCount: 0,
    failureCount: 0,
    invalidTimestampCount: 0,
    ...partial,
  };
}

function compareEligibleExports(a: EligibleExport, b: EligibleExport): number {
  if (a.exportTimeMs !== b.exportTimeMs) {
    return b.exportTimeMs - a.exportTimeMs;
  }
  return b.name.localeCompare(a.name);
}

export class DiagnosticExportRetentionService {
  private readonly fsImpl: DiagnosticExportRetentionFs;
  private readonly now: () => number;
  private readonly log?: (input: StructuredLogInput) => void;

  constructor(options: DiagnosticExportRetentionServiceOptions = {}) {
    this.fsImpl = options.fsImpl ?? fs;
    this.now = options.now ?? (() => Date.now());
    this.log = options.log;
  }

  cleanup(options: DiagnosticExportRetentionCleanupOptions): DiagnosticExportCleanupResult {
    const nowMs = options.nowMs ?? this.now();
    const retentionMs = retentionPeriodMs(options.retentionDays);

    if (!this.fsImpl.existsSync(options.exportDir)) {
      return emptyResult();
    }

    let entries: string[];
    try {
      entries = this.fsImpl.readdirSync(options.exportDir);
    } catch {
      const result = emptyResult({ ok: false, failureCount: 1 });
      this.emitCleanupLog('warning', result);
      return result;
    }

    const eligible: EligibleExport[] = [];
    let skippedCount = 0;
    let invalidTimestampCount = 0;

    for (const name of entries) {
      const entryPath = path.join(options.exportDir, name);
      let entryStat: ReturnType<DiagnosticExportRetentionFs['lstatSync']>;
      try {
        entryStat = this.fsImpl.lstatSync(entryPath);
      } catch {
        skippedCount += 1;
        continue;
      }

      if (entryStat.isSymbolicLink()) {
        skippedCount += 1;
        continue;
      }

      if (!entryStat.isDirectory()) {
        skippedCount += 1;
        continue;
      }

      if (!isAppOwnedDiagnosticExportDirName(name)) {
        skippedCount += 1;
        continue;
      }

      const parsedTimestamp = parseDiagnosticExportDirTimestampMs(name);
      if (parsedTimestamp !== null) {
        eligible.push({
          name,
          dirPath: entryPath,
          exportTimeMs: parsedTimestamp,
          timestampSource: 'dirname',
          invalidTimestamp: false,
        });
        continue;
      }

      const mtimeMs = entryStat.mtimeMs;
      if (!Number.isFinite(mtimeMs)) {
        invalidTimestampCount += 1;
        eligible.push({
          name,
          dirPath: entryPath,
          exportTimeMs: 0,
          timestampSource: 'mtime',
          invalidTimestamp: true,
        });
        continue;
      }

      eligible.push({
        name,
        dirPath: entryPath,
        exportTimeMs: mtimeMs,
        timestampSource: 'mtime',
        invalidTimestamp: false,
      });
    }

    eligible.sort(compareEligibleExports);

    const newest = eligible[0];
    let deletedCount = 0;
    let failureCount = 0;
    let preservedNewestCount = 0;

    for (const entry of eligible) {
      if (newest && entry.name === newest.name) {
        preservedNewestCount = 1;
        continue;
      }

      if (entry.invalidTimestamp) {
        skippedCount += 1;
        continue;
      }

      if (nowMs - entry.exportTimeMs <= retentionMs) {
        continue;
      }

      try {
        this.fsImpl.rmSync(entry.dirPath, { recursive: true, force: true });
        deletedCount += 1;
      } catch {
        failureCount += 1;
      }
    }

    const result: DiagnosticExportCleanupResult = {
      ok: failureCount === 0,
      scannedCount: entries.length,
      eligibleCount: eligible.length,
      deletedCount,
      preservedNewestCount,
      skippedCount,
      failureCount,
      invalidTimestampCount,
    };
    this.emitCleanupLog(failureCount > 0 ? 'warning' : 'information', result);
    return result;
  }

  private emitCleanupLog(level: StructuredLogInput['level'], result: DiagnosticExportCleanupResult): void {
    if (!this.log) {
      return;
    }
    this.log({
      level,
      message: 'Diagnostic export retention cleanup completed',
      event: 'diagnostics_export_retention_cleanup',
      component: 'diagnostics',
      metadata: {
        scannedCount: result.scannedCount,
        eligibleCount: result.eligibleCount,
        deletedCount: result.deletedCount,
        preservedNewestCount: result.preservedNewestCount,
        skippedCount: result.skippedCount,
        failureCount: result.failureCount,
        invalidTimestampCount: result.invalidTimestampCount,
      },
    });
  }
}
