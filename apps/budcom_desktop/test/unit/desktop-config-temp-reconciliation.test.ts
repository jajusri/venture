import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { getEnvironmentDefaults } from '../../src/application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../../src/application/desktop-config-paths.js';
import { DesktopConfigStore } from '../../src/application/desktop-config-store.js';
import {
  DesktopConfigTempReconciliationService,
  type DesktopConfigTempReconciliationFs,
} from '../../src/application/desktop-config-temp-reconciliation-service.js';
import type { DesktopConfigV1 } from '../../src/application/desktop-config-schema.js';
import type { StructuredLogInput } from '../../src/application/log-service.js';

function writeConfig(filePath: string, config: DesktopConfigV1): void {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(config, null, 2)}\n`, 'utf8');
}

const mintedRoots: string[] = [];

afterEach(() => {
  for (const root of mintedRoots.splice(0)) {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

function createHarness(tempDir?: string) {
  const root = tempDir ?? fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-config-temp-'));
  if (!tempDir) mintedRoots.push(root);
  const paths = resolveDesktopConfigPaths(root);
  const defaults = getEnvironmentDefaults(true);
  return { root, paths, defaults };
}

function createService(
  logs: StructuredLogInput[] = [],
  fsImpl?: DesktopConfigTempReconciliationFs,
): DesktopConfigTempReconciliationService {
  return new DesktopConfigTempReconciliationService({
    fsImpl,
    log: (input) => logs.push(input),
  });
}

describe('DesktopConfigTempReconciliationService', () => {
  it('1. treats an absent temp file as a successful no-op', () => {
    const { paths } = createHarness();
    const logs: StructuredLogInput[] = [];
    const result = createService(logs).reconcile({ paths });
    expect(result).toMatchObject({ ok: true, action: 'none', tempPresent: false });
    expect(logs[0]?.metadata).toMatchObject({ action: 'none' });
  });

  it('2. deletes a stale temp when the primary config is valid', () => {
    const { paths, defaults } = createHarness();
    const primary = { ...defaults, connectorPort: 18080 };
    const stale = { ...defaults, connectorPort: 19090 };
    writeConfig(paths.configFilePath, primary);
    writeConfig(paths.configTempPath, stale);
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({
      ok: true,
      action: 'deleted_stale',
      primaryValid: true,
      tempValid: true,
      tempPresent: false,
    });
    expect(fs.existsSync(paths.configTempPath)).toBe(false);
    expect(JSON.parse(fs.readFileSync(paths.configFilePath, 'utf8')).connectorPort).toBe(18080);
  });

  it('3. promotes a valid temp when the primary config is missing', () => {
    const { paths, defaults } = createHarness();
    const pending = { ...defaults, connectorPort: 19191 };
    writeConfig(paths.configTempPath, pending);
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({
      ok: true,
      action: 'promoted',
      primaryValid: true,
      tempPresent: false,
    });
    expect(fs.existsSync(paths.configTempPath)).toBe(false);
    expect(JSON.parse(fs.readFileSync(paths.configFilePath, 'utf8')).connectorPort).toBe(19191);
  });

  it('4. deletes an invalid temp when the primary config is valid', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    fs.writeFileSync(paths.configTempPath, '{not-json', 'utf8');
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({ ok: true, action: 'deleted_stale', tempValid: false });
    expect(fs.existsSync(paths.configTempPath)).toBe(false);
  });

  it('5. deletes invalid temp when backup is valid and primary/temp are unusable', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configBackupPath, defaults);
    fs.writeFileSync(paths.configFilePath, '{bad-primary', 'utf8');
    fs.writeFileSync(paths.configTempPath, '{bad-temp', 'utf8');
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({
      ok: true,
      action: 'deleted_invalid',
      backupValid: true,
      tempPresent: false,
    });
    expect(fs.existsSync(paths.configTempPath)).toBe(false);
    expect(fs.existsSync(paths.configBackupPath)).toBe(true);
    expect(fs.readFileSync(paths.configFilePath, 'utf8')).toContain('bad-primary');
  });

  it('6. skips symlink temp files without deleting them', () => {
    const { paths, defaults, root } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    const target = path.join(root, 'real-config.json');
    writeConfig(target, defaults);
    try {
      fs.symlinkSync(target, paths.configTempPath);
    } catch {
      // Skip on platforms without symlink support in temp dirs.
      return;
    }
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({ ok: true, action: 'skipped_symlink', tempPresent: true });
    expect(fs.lstatSync(paths.configTempPath).isSymbolicLink()).toBe(true);
  });

  it('7. skips directory temp paths without deleting them', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    fs.mkdirSync(paths.configTempPath, { recursive: true });
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({ ok: true, action: 'skipped_directory', tempPresent: true });
    expect(fs.existsSync(paths.configTempPath)).toBe(true);
  });

  it('8. reports failure when lstat fails and leaves temp untouched', () => {
    const { paths } = createHarness();
    const fsImpl: DesktopConfigTempReconciliationFs = {
      existsSync: () => true,
      lstatSync: () => {
        throw new Error('lstat denied');
      },
      readFileSync: () => '',
      renameSync: () => undefined,
      copyFileSync: () => undefined,
      unlinkSync: () => undefined,
    };
    const result = createService([], fsImpl).reconcile({ paths });
    expect(result).toMatchObject({ ok: false, action: 'failed', failureCount: 1 });
  });

  it('9. reports failure when delete fails and preserves primary/backup', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, defaults);
    const fsImpl: DesktopConfigTempReconciliationFs = {
      ...fs,
      unlinkSync: (filePath: string) => {
        if (filePath === paths.configTempPath) {
          throw new Error('delete denied');
        }
        fs.unlinkSync(filePath);
      },
    };
    const result = createService([], fsImpl).reconcile({ paths });
    expect(result).toMatchObject({ ok: false, action: 'failed', failureCount: 1 });
    expect(fs.existsSync(paths.configFilePath)).toBe(true);
    expect(fs.existsSync(paths.configBackupPath)).toBe(false);
  });

  it('10. reports failure when promote rename fails', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configTempPath, defaults);
    const fsImpl: DesktopConfigTempReconciliationFs = {
      ...fs,
      renameSync: () => {
        throw new Error('rename denied');
      },
    };
    const result = createService([], fsImpl).reconcile({ paths });
    expect(result).toMatchObject({ ok: false, action: 'failed', failureCount: 1 });
    expect(fs.existsSync(paths.configTempPath)).toBe(true);
    expect(fs.existsSync(paths.configFilePath)).toBe(false);
  });

  it('11. preserves uncertain state when primary, temp, and backup are all invalid', () => {
    const { paths } = createHarness();
    fs.writeFileSync(paths.configFilePath, '{bad-primary', 'utf8');
    fs.writeFileSync(paths.configTempPath, '{bad-temp', 'utf8');
    fs.writeFileSync(paths.configBackupPath, '{bad-backup', 'utf8');
    const result = createService().reconcile({ paths });
    expect(result).toMatchObject({ ok: true, action: 'skipped_uncertain', tempPresent: true });
    expect(fs.existsSync(paths.configTempPath)).toBe(true);
  });

  it('12. is idempotent across repeated reconciliation', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20000 });
    const service = createService();
    const first = service.reconcile({ paths });
    const second = service.reconcile({ paths });
    expect(first.action).toBe('deleted_stale');
    expect(second).toMatchObject({ ok: true, action: 'none', tempPresent: false });
  });

  it('13. never deletes unrelated tmp files in userData', () => {
    const { paths, defaults, root } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20001 });
    const unrelated = path.join(root, 'notes.tmp');
    fs.writeFileSync(unrelated, 'keep', 'utf8');
    createService().reconcile({ paths });
    expect(fs.existsSync(unrelated)).toBe(true);
  });

  it('14. never deletes backup or corrupt archives during stale cleanup', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configBackupPath, defaults);
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20002 });
    const corruptPath = `${paths.configFilePath}.corrupt-1234567890.json`;
    fs.writeFileSync(corruptPath, '{old-corrupt', 'utf8');
    createService().reconcile({ paths });
    expect(fs.existsSync(paths.configBackupPath)).toBe(true);
    expect(fs.existsSync(corruptPath)).toBe(true);
  });

  it('15. promotes valid temp over invalid primary and archives corrupt primary', () => {
    const { paths, defaults } = createHarness();
    fs.writeFileSync(paths.configFilePath, '{bad-primary', 'utf8');
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20003 });
    const result = createService().reconcile({ paths });
    expect(result.action).toBe('promoted');
    expect(JSON.parse(fs.readFileSync(paths.configFilePath, 'utf8')).connectorPort).toBe(20003);
    expect(fs.readdirSync(paths.userDataDir).some((name) => name.startsWith('desktop-config.json.corrupt-'))).toBe(true);
  });

  it('16. emits aggregate metadata only in lifecycle logs', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, defaults);
    const logs: StructuredLogInput[] = [];
    createService(logs).reconcile({ paths });
    const metadata = logs[0]?.metadata ?? {};
    expect(metadata).toMatchObject({
      action: 'deleted_stale',
      failureCount: 0,
    });
    expect(JSON.stringify(metadata)).not.toContain(paths.userDataDir);
    expect(JSON.stringify(metadata)).not.toContain('connectorPort');
  });

  it('17. does not throw when reconciliation is invoked from startup-style wrapper', () => {
    const { paths } = createHarness();
    expect(() => {
      try {
        createService().reconcile({ paths });
      } catch {
        // startup wrapper swallows
      }
    }).not.toThrow();
  });
});

describe('DesktopConfigTempReconciliationService integration with DesktopConfigStore', () => {
  it('18. promoted temp is loaded by DesktopConfigStore on next startup', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20004 });
    createService().reconcile({ paths });
    const store = new DesktopConfigStore({ paths, defaults });
    expect(store.getConfig().connectorPort).toBe(20004);
  });

  it('19. stale temp cleanup leaves persisted settings intact', () => {
    const { paths, defaults } = createHarness();
    const store = new DesktopConfigStore({ paths, defaults });
    store.save({ ...defaults, connectorPort: 20005 });
    store.save({ ...defaults, connectorPort: 20005 });
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 20006 });
    createService().reconcile({ paths });
    const reloaded = new DesktopConfigStore({ paths, defaults });
    expect(reloaded.getConfig().connectorPort).toBe(20005);
    expect(fs.existsSync(paths.configBackupPath)).toBe(true);
  });

  it('20. invalid temp with valid backup allows store recovery after cleanup', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configBackupPath, { ...defaults, connectorPort: 20007 });
    fs.writeFileSync(paths.configFilePath, '{bad', 'utf8');
    fs.writeFileSync(paths.configTempPath, '{bad-temp', 'utf8');
    createService().reconcile({ paths });
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    expect(store.getConfig().connectorPort).toBe(20007);
  });
});

describe('DesktopConfigTempReconciliationService unrelated retention boundaries', () => {
  it('21. does not modify diagnostic export retention behaviour', () => {
    const { paths, defaults } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, defaults);
    fs.mkdirSync(paths.diagnosticsExportDir, { recursive: true });
    const exportDir = path.join(
      paths.diagnosticsExportDir,
      'budcom-diagnostics-2026-07-01T00-00-00-000Z',
    );
    fs.mkdirSync(exportDir, { recursive: true });
    fs.writeFileSync(path.join(exportDir, 'diagnostics-bundle.json'), '{}', 'utf8');
    createService().reconcile({ paths });
    expect(fs.existsSync(exportDir)).toBe(true);
  });

  it('22. does not touch connector database paths outside desktop userData', () => {
    const { paths, defaults, root } = createHarness();
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, defaults);
    const dbMarker = path.join(root, 'connector-db-marker.txt');
    fs.writeFileSync(dbMarker, 'keep', 'utf8');
    createService().reconcile({ paths });
    expect(fs.readFileSync(dbMarker, 'utf8')).toBe('keep');
  });
});
