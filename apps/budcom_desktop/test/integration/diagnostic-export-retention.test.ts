import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import {
  buildDiagnosticExportDirName,
  DIAGNOSTIC_EXPORT_BUNDLE_FILENAME,
} from '../../src/application/diagnostic-export-convention.js';
import { DiagnosticExportRetentionService } from '../../src/application/diagnostic-export-retention-service.js';
import { getEnvironmentDefaults } from '../../src/application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../../src/application/desktop-config-paths.js';
import { DesktopConfigStore } from '../../src/application/desktop-config-store.js';
import { DiagnosticsService } from '../../src/application/diagnostics-service.js';
import { LogService } from '../../src/application/log-service.js';

function createExportBundle(exportRoot: string, generatedAtIso: string): string {
  const dirName = buildDiagnosticExportDirName(generatedAtIso);
  const dirPath = path.join(exportRoot, dirName);
  fs.mkdirSync(dirPath, { recursive: true });
  fs.writeFileSync(path.join(dirPath, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME), '{}', 'utf8');
  return dirPath;
}

function createDiagnosticsHarness(tempDir: string, retentionDays = 7) {
  const paths = resolveDesktopConfigPaths(tempDir);
  const defaults = { ...getEnvironmentDefaults(true), diagnosticsRetentionDays: retentionDays };
  const store = new DesktopConfigStore({ paths, defaults });
  const logService = new LogService();
  const retentionService = new DiagnosticExportRetentionService({
    log: (input) => logService.appendStructured(input),
  });
  const resolvedConfig = {
    effective: store.getConfig(),
    persisted: store.getConfig(),
    sources: {},
    connectorBaseUrl: 'http://localhost:8080',
    lifecycleConfig: {} as never,
  };
  const diagnosticsService = new DiagnosticsService({
    desktopVersion: '0.4.3',
    electronVersion: 'test',
    configStore: store,
    resolvedConfig,
    configStatus: 'loaded',
    dashboardService: {
      getDashboardState: async () => ({
        connectorVersion: '0.3.1',
        connectorReachable: false,
        healthStatus: 'unknown',
        companyName: '—',
        sessionStatus: 'NO_COMPANY_SELECTED',
      }),
    } as never,
    lifecycleService: {
      getStatus: () => ({
        stateLabel: 'Disconnected',
        managedByDesktop: false,
        externalProcessDetected: false,
        lastSuccessfulHealthCheck: null,
        managedProcessPid: null,
        bundledConnectorVersion: '0.4.0',
      }),
    } as never,
    logService,
    exportDir: paths.diagnosticsExportDir,
    startedAt: Date.now(),
    retentionService,
  });
  return { paths, store, diagnosticsService, retentionService, logService };
}

describe('diagnostic export retention integration', () => {
  it('16. startup cleanup does not throw when cleanup fails', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-startup-retention-'));
    const { retentionService, paths } = createDiagnosticsHarness(tempDir);
    const failingFs = {
      existsSync: () => true,
      readdirSync: () => {
        throw new Error('permission denied');
      },
      lstatSync: () => {
        throw new Error('unused');
      },
      rmSync: () => {},
    };
    const startupService = new DiagnosticExportRetentionService({ fsImpl: failingFs });
    expect(() =>
      startupService.cleanup({
        exportDir: paths.diagnosticsExportDir,
        retentionDays: 7,
      }),
    ).not.toThrow();
    expect(retentionService.cleanup({ exportDir: paths.diagnosticsExportDir, retentionDays: 7 }).ok).toBe(true);
  });

  it('17. successful export remains successful when post-export cleanup fails', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-export-retention-fail-'));
    const { diagnosticsService, paths, retentionService } = createDiagnosticsHarness(tempDir);
    const cleanupSpy = vi.spyOn(retentionService, 'cleanup').mockImplementation(() => {
      throw new Error('cleanup failed');
    });
    const result = await diagnosticsService.exportBundle();
    expect(result.ok).toBe(true);
    expect(result.bundlePath).toBeTruthy();
    expect(fs.existsSync(result.bundlePath!)).toBe(true);
    cleanupSpy.mockRestore();
    expect(fs.existsSync(paths.diagnosticsExportDir)).toBe(true);
  });

  it('18. post-export cleanup protects the newly generated export', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-export-protect-newest-'));
    const { diagnosticsService, paths } = createDiagnosticsHarness(tempDir, 7);
    createExportBundle(paths.diagnosticsExportDir, '2026-05-01T00:00:00.000Z');
    createExportBundle(paths.diagnosticsExportDir, '2026-04-01T00:00:00.000Z');
    const result = await diagnosticsService.exportBundle();
    expect(result.ok).toBe(true);
    const remaining = fs.readdirSync(paths.diagnosticsExportDir).filter((name) =>
      name.startsWith('budcom-diagnostics-'),
    );
    expect(remaining.length).toBeGreaterThanOrEqual(1);
    expect(remaining.some((name) => result.bundlePath!.includes(name))).toBe(true);
  });

  it('preserves unrelated files in the real export directory during cleanup', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-export-unrelated-'));
    const { diagnosticsService, paths, retentionService } = createDiagnosticsHarness(tempDir, 7);
    fs.mkdirSync(paths.diagnosticsExportDir, { recursive: true });
    fs.writeFileSync(path.join(paths.diagnosticsExportDir, 'support-notes.txt'), 'keep', 'utf8');
    fs.writeFileSync(path.join(paths.diagnosticsExportDir, 'desktop-config.backup.json'), '{}', 'utf8');
    createExportBundle(paths.diagnosticsExportDir, '2026-05-01T00:00:00.000Z');
    await diagnosticsService.exportBundle();
    retentionService.cleanup({
      exportDir: paths.diagnosticsExportDir,
      retentionDays: 7,
      nowMs: Date.parse('2026-07-25T12:00:00.000Z'),
    });
    expect(fs.existsSync(path.join(paths.diagnosticsExportDir, 'support-notes.txt'))).toBe(true);
    expect(fs.existsSync(path.join(paths.diagnosticsExportDir, 'desktop-config.backup.json'))).toBe(true);
  });

  it('retention setting persists across restart and cleanup honors reloaded value', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-retention-restart-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const defaults = getEnvironmentDefaults(true);
    const store = new DesktopConfigStore({ paths, defaults });
    store.save({ ...defaults, diagnosticsRetentionDays: 30 });
    const reloaded = new DesktopConfigStore({ paths, defaults });
    expect(reloaded.getConfig().diagnosticsRetentionDays).toBe(30);
    fs.mkdirSync(paths.diagnosticsExportDir, { recursive: true });
    createExportBundle(paths.diagnosticsExportDir, '2026-06-01T00:00:00.000Z');
    const recent = createExportBundle(paths.diagnosticsExportDir, '2026-07-20T00:00:00.000Z');
    const service = new DiagnosticExportRetentionService();
    service.cleanup({
      exportDir: paths.diagnosticsExportDir,
      retentionDays: reloaded.getConfig().diagnosticsRetentionDays,
      nowMs: Date.parse('2026-07-25T12:00:00.000Z'),
    });
    expect(fs.existsSync(recent)).toBe(true);
    expect(fs.readdirSync(paths.diagnosticsExportDir).filter((name) => name.startsWith('budcom-diagnostics-'))).toHaveLength(1);
  });

  it('19. does not touch connector sync_runs storage', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-sync-runs-untouched-'));
    const dbDir = path.join(tempDir, 'data');
    fs.mkdirSync(dbDir, { recursive: true });
    const syncRunsMarker = path.join(dbDir, 'sync-runs-marker.txt');
    fs.writeFileSync(syncRunsMarker, 'sync-run-record', 'utf8');
    const { paths, retentionService } = createDiagnosticsHarness(tempDir, 1);
    createExportBundle(paths.diagnosticsExportDir, '2026-01-01T00:00:00.000Z');
    retentionService.cleanup({
      exportDir: paths.diagnosticsExportDir,
      retentionDays: 1,
      nowMs: Date.parse('2026-07-25T12:00:00.000Z'),
    });
    expect(fs.readFileSync(syncRunsMarker, 'utf8')).toBe('sync-run-record');
  });

  it('20. does not change backup lifecycle outside export cleanup scope', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-backup-lifecycle-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    fs.mkdirSync(path.dirname(paths.configBackupPath), { recursive: true });
    fs.writeFileSync(paths.configBackupPath, '{"schemaVersion":1}', 'utf8');
    const service = new DiagnosticExportRetentionService();
    service.cleanup({
      exportDir: paths.diagnosticsExportDir,
      retentionDays: 7,
      nowMs: Date.parse('2026-07-25T12:00:00.000Z'),
    });
    expect(fs.existsSync(paths.configBackupPath)).toBe(true);
  });
});
