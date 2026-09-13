import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { getEnvironmentDefaults } from '../../src/application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../../src/application/desktop-config-paths.js';
import { DesktopConfigStore } from '../../src/application/desktop-config-store.js';
import { DesktopConfigTempReconciliationService } from '../../src/application/desktop-config-temp-reconciliation-service.js';
import {
  buildDiagnosticExportDirName,
  DIAGNOSTIC_EXPORT_BUNDLE_FILENAME,
} from '../../src/application/diagnostic-export-convention.js';
import { DiagnosticExportRetentionService } from '../../src/application/diagnostic-export-retention-service.js';

function writeConfig(filePath: string, config: object): void {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(config, null, 2)}\n`, 'utf8');
}

const mintedDirs: string[] = [];

afterEach(() => {
  for (const dir of mintedDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function mkTempDir(prefix: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  mintedDirs.push(dir);
  return dir;
}

describe('desktop config temp reconciliation startup integration', () => {
  it('runs reconciliation before config load without blocking startup', () => {
    const tempDir = mkTempDir('venture-startup-temp-');
    const paths = resolveDesktopConfigPaths(tempDir);
    const defaults = getEnvironmentDefaults(true);
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 21000 });

    let startupFailed = false;
    try {
      new DesktopConfigTempReconciliationService().reconcile({ paths });
      const store = new DesktopConfigStore({ paths, defaults });
      expect(store.getConfig().connectorPort).toBe(21000);
    } catch {
      startupFailed = true;
    }

    expect(startupFailed).toBe(false);
    expect(fs.existsSync(paths.configTempPath)).toBe(false);
  });

  it('preserves diagnostic export retention and sync-run marker files in the same userData tree', () => {
    const tempDir = mkTempDir('venture-startup-boundary-');
    const paths = resolveDesktopConfigPaths(tempDir);
    const defaults = getEnvironmentDefaults(true);
    writeConfig(paths.configFilePath, defaults);
    writeConfig(paths.configTempPath, { ...defaults, connectorPort: 21001 });

    const exportDir = path.join(
      paths.diagnosticsExportDir,
      buildDiagnosticExportDirName('2026-05-01T00:00:00.000Z'),
    );
    fs.mkdirSync(exportDir, { recursive: true });
    fs.writeFileSync(path.join(exportDir, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), '{}', 'utf8');
    const syncRunsMarker = path.join(tempDir, 'sync-runs-marker.txt');
    fs.writeFileSync(syncRunsMarker, 'keep', 'utf8');

    new DesktopConfigTempReconciliationService().reconcile({ paths });

    new DiagnosticExportRetentionService().cleanup({
      exportDir: paths.diagnosticsExportDir,
      retentionDays: defaults.diagnosticsRetentionDays,
      nowMs: Date.parse('2026-07-25T12:00:00.000Z'),
    });

    expect(fs.existsSync(syncRunsMarker)).toBe(true);
    expect(fs.readdirSync(paths.diagnosticsExportDir).length).toBeGreaterThan(0);
  });
});
