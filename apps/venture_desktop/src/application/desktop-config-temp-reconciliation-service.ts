import fs from 'node:fs';
import path from 'node:path';

import type { DesktopConfigPaths } from './desktop-config-paths.js';
import { isAppOwnedDesktopConfigTempPath } from './desktop-config-paths.js';
import type { DesktopConfigV1 } from './desktop-config-schema.js';
import { validateDesktopConfig } from './desktop-config-schema.js';
import type { StructuredLogInput } from './log-service.js';

export type DesktopConfigTempReconciliationAction =
  | 'none'
  | 'promoted'
  | 'deleted_stale'
  | 'deleted_invalid'
  | 'skipped_symlink'
  | 'skipped_directory'
  | 'skipped_uncertain'
  | 'failed';

export interface DesktopConfigTempReconciliationResult {
  readonly ok: boolean;
  readonly action: DesktopConfigTempReconciliationAction;
  readonly tempPresent: boolean;
  readonly primaryPresent: boolean;
  readonly primaryValid: boolean;
  readonly tempValid: boolean;
  readonly backupPresent: boolean;
  readonly backupValid: boolean;
  readonly failureCount: number;
}

export interface DesktopConfigTempReconciliationOptions {
  readonly paths: DesktopConfigPaths;
}

export interface DesktopConfigTempReconciliationFs {
  existsSync(filePath: string): boolean;
  lstatSync(filePath: string): {
    isFile(): boolean;
    isDirectory(): boolean;
    isSymbolicLink(): boolean;
  };
  readFileSync(filePath: string, encoding: 'utf8'): string;
  renameSync(oldPath: string, newPath: string): void;
  copyFileSync(source: string, destination: string): void;
  unlinkSync(filePath: string): void;
}

export interface DesktopConfigTempReconciliationServiceOptions {
  readonly fsImpl?: DesktopConfigTempReconciliationFs;
  readonly log?: (input: StructuredLogInput) => void;
}

interface ParsedConfigState {
  readonly present: boolean;
  readonly valid: boolean;
  readonly config: DesktopConfigV1 | null;
}

function emptyResult(
  partial: Partial<DesktopConfigTempReconciliationResult> = {},
): DesktopConfigTempReconciliationResult {
  return {
    ok: true,
    action: 'none',
    tempPresent: false,
    primaryPresent: false,
    primaryValid: false,
    tempValid: false,
    backupPresent: false,
    backupValid: false,
    failureCount: 0,
    ...partial,
  };
}

function readConfigState(
  fsImpl: DesktopConfigTempReconciliationFs,
  filePath: string,
): ParsedConfigState {
  if (!fsImpl.existsSync(filePath)) {
    return { present: false, valid: false, config: null };
  }

  try {
    const raw = fsImpl.readFileSync(filePath, 'utf8');
    const parsed = JSON.parse(raw) as unknown;
    const validated = validateDesktopConfig(parsed);
    return {
      present: true,
      valid: validated.ok && validated.config !== null,
      config: validated.config,
    };
  } catch {
    return { present: true, valid: false, config: null };
  }
}

export class DesktopConfigTempReconciliationService {
  private readonly fsImpl: DesktopConfigTempReconciliationFs;
  private readonly log?: (input: StructuredLogInput) => void;

  constructor(options: DesktopConfigTempReconciliationServiceOptions = {}) {
    this.fsImpl = options.fsImpl ?? fs;
    this.log = options.log;
  }

  reconcile(options: DesktopConfigTempReconciliationOptions): DesktopConfigTempReconciliationResult {
    const { paths } = options;
    const tempPath = paths.configTempPath;

    if (!isAppOwnedDesktopConfigTempPath(tempPath, paths.configFilePath)) {
      const result = emptyResult({ ok: false, action: 'failed', failureCount: 1 });
      this.emitLog('warning', result);
      return result;
    }

    if (!this.fsImpl.existsSync(tempPath)) {
      const result = emptyResult();
      this.emitLog('information', result);
      return result;
    }

    let tempStat: ReturnType<DesktopConfigTempReconciliationFs['lstatSync']>;
    try {
      tempStat = this.fsImpl.lstatSync(tempPath);
    } catch {
      const result = emptyResult({
        ok: false,
        action: 'failed',
        tempPresent: true,
        failureCount: 1,
      });
      this.emitLog('warning', result);
      return result;
    }

    if (tempStat.isSymbolicLink()) {
      const result = emptyResult({
        action: 'skipped_symlink',
        tempPresent: true,
      });
      this.emitLog('information', result);
      return result;
    }

    if (tempStat.isDirectory()) {
      const result = emptyResult({
        action: 'skipped_directory',
        tempPresent: true,
      });
      this.emitLog('information', result);
      return result;
    }

    if (!tempStat.isFile()) {
      const result = emptyResult({
        action: 'skipped_uncertain',
        tempPresent: true,
      });
      this.emitLog('information', result);
      return result;
    }

    const primary = readConfigState(this.fsImpl, paths.configFilePath);
    const temp = readConfigState(this.fsImpl, tempPath);
    const backup = readConfigState(this.fsImpl, paths.configBackupPath);

    const base = {
      tempPresent: true,
      primaryPresent: primary.present,
      primaryValid: primary.valid,
      tempValid: temp.valid,
      backupPresent: backup.present,
      backupValid: backup.valid,
    };

    if (primary.valid) {
      return this.deleteTemp(tempPath, 'deleted_stale', base);
    }

    if (temp.valid) {
      return this.promoteTemp(paths, tempPath, primary.present, base);
    }

    if (backup.valid) {
      return this.deleteTemp(tempPath, 'deleted_invalid', base);
    }

    const result = emptyResult({
      ...base,
      action: 'skipped_uncertain',
    });
    this.emitLog('information', result);
    return result;
  }

  private promoteTemp(
    paths: DesktopConfigPaths,
    tempPath: string,
    primaryPresent: boolean,
    base: Omit<DesktopConfigTempReconciliationResult, 'ok' | 'action' | 'failureCount'>,
  ): DesktopConfigTempReconciliationResult {
    try {
      if (primaryPresent) {
        const corruptPath = `${paths.configFilePath}.corrupt-${Date.now()}.json`;
        try {
          this.fsImpl.copyFileSync(paths.configFilePath, corruptPath);
        } catch {
          // Best effort only; promotion still attempted when temp is valid.
        }
        try {
          this.fsImpl.unlinkSync(paths.configFilePath);
        } catch {
          const result = emptyResult({
            ...base,
            ok: false,
            action: 'failed',
            failureCount: 1,
          });
          this.emitLog('warning', result);
          return result;
        }
      }

      this.fsImpl.renameSync(tempPath, paths.configFilePath);
      const result = emptyResult({
        ...base,
        action: 'promoted',
        primaryPresent: true,
        primaryValid: true,
        tempPresent: false,
      });
      this.emitLog('information', result);
      return result;
    } catch {
      const result = emptyResult({
        ...base,
        ok: false,
        action: 'failed',
        failureCount: 1,
      });
      this.emitLog('warning', result);
      return result;
    }
  }

  private deleteTemp(
    tempPath: string,
    action: 'deleted_stale' | 'deleted_invalid',
    base: Omit<DesktopConfigTempReconciliationResult, 'ok' | 'action' | 'failureCount'>,
  ): DesktopConfigTempReconciliationResult {
    try {
      this.fsImpl.unlinkSync(tempPath);
      const result = emptyResult({
        ...base,
        action,
        tempPresent: false,
      });
      this.emitLog('information', result);
      return result;
    } catch {
      const result = emptyResult({
        ...base,
        ok: false,
        action: 'failed',
        failureCount: 1,
      });
      this.emitLog('warning', result);
      return result;
    }
  }

  private emitLog(level: StructuredLogInput['level'], result: DesktopConfigTempReconciliationResult): void {
    if (!this.log) {
      return;
    }
    this.log({
      level,
      message: 'Desktop config temp reconciliation completed',
      event: 'desktop_config_temp_reconciliation',
      component: 'configuration',
      metadata: {
        ok: result.ok,
        action: result.action,
        tempPresent: result.tempPresent,
        primaryPresent: result.primaryPresent,
        primaryValid: result.primaryValid,
        tempValid: result.tempValid,
        backupPresent: result.backupPresent,
        backupValid: result.backupValid,
        failureCount: result.failureCount,
      },
    });
  }
}
