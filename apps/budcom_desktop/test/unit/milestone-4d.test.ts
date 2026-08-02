import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { getEnvironmentDefaults } from '../../src/application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../../src/application/desktop-config-paths.js';
import { applyEnvironmentOverrides } from '../../src/application/desktop-config-resolver.js';
import { DesktopConfigStore } from '../../src/application/desktop-config-store.js';
import {
  buildConnectorBaseUrl,
  requiresRestart,
  validateDesktopConfig,
  validateSettingsPatch,
} from '../../src/application/desktop-config-schema.js';
import { DiagnosticsService } from '../../src/application/diagnostics-service.js';
import { FileLogWriter } from '../../src/application/file-log-writer.js';
import { assertAllowedIpcChannel, validateCompanyId } from '../../src/application/ipc-allowlist.js';
import { redactString, sanitizeConfigForExport } from '../../src/application/log-redaction.js';
import { LogService } from '../../src/application/log-service.js';
import { RecoveryService } from '../../src/application/recovery-service.js';
import { SettingsService } from '../../src/application/settings-service.js';

describe('desktop config schema', () => {
  it('validates defaults', () => {
    const result = validateDesktopConfig(getEnvironmentDefaults(false));
    expect(result.ok).toBe(true);
    expect(result.config?.connectorPort).toBe(8080);
  });

  it('rejects invalid port', () => {
    const result = validateDesktopConfig({
      ...getEnvironmentDefaults(false),
      connectorPort: 99999,
    });
    expect(result.ok).toBe(false);
  });

  it('detects restart-required changes', () => {
    const current = getEnvironmentDefaults(false);
    const next = { ...current, connectorPort: 18080 };
    expect(requiresRestart(current, next)).toBe(true);
  });

  it('builds connector base url', () => {
    expect(buildConnectorBaseUrl('localhost', 8080)).toBe('http://localhost:8080');
  });
});

describe('desktop config store', () => {
  it('persists and reloads settings', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-config-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const defaults = getEnvironmentDefaults(true);
    const store = new DesktopConfigStore({ paths, defaults });
    const next = { ...defaults, connectorPort: 18080 };
    expect(store.save(next).ok).toBe(true);
    const reloaded = new DesktopConfigStore({ paths, defaults });
    expect(reloaded.getConfig().connectorPort).toBe(18080);
  });

  it('recovers corrupt configuration from defaults', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-config-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    fs.mkdirSync(tempDir, { recursive: true });
    fs.writeFileSync(paths.configFilePath, '{not-json', 'utf8');
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    expect(store.getConfig().connectorPort).toBe(8080);
    expect(fs.readFileSync(paths.configFilePath, 'utf8')).toContain('"schemaVersion"');
  });
});

describe('environment overrides', () => {
  it('applies connector port override', () => {
    const previous = process.env.BUDCOM_CONNECTOR_PORT;
    process.env.BUDCOM_CONNECTOR_PORT = '9090';
    try {
      const result = applyEnvironmentOverrides(getEnvironmentDefaults(false));
      expect(result.config.connectorPort).toBe(9090);
      expect(result.sources.connectorPort).toBe('environment');
    } finally {
      if (previous === undefined) {
        delete process.env.BUDCOM_CONNECTOR_PORT;
      } else {
        process.env.BUDCOM_CONNECTOR_PORT = previous;
      }
    }
  });
});

describe('settings service', () => {
  const connectorEnvKeys = ['BUDCOM_CONNECTOR_PORT', 'BUDCOM_CONNECTOR_URL', 'BUDCOM_CONNECTOR_HOST'] as const;
  const savedConnectorEnv: Partial<Record<(typeof connectorEnvKeys)[number], string | undefined>> = {};

  beforeEach(() => {
    for (const key of connectorEnvKeys) {
      savedConnectorEnv[key] = process.env[key];
      delete process.env[key];
    }
  });

  afterEach(() => {
    for (const key of connectorEnvKeys) {
      if (savedConnectorEnv[key] === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = savedConnectorEnv[key];
      }
    }
  });

  it('validates invalid settings patch', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-settings-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    const service = new SettingsService({ configStore: store, logService: new LogService(), connectorExecutable: process.execPath });
    const result = service.validateInput({ connectorPort: 99999 });
    expect(result.ok).toBe(false);
  });

  it('saves valid settings', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-settings-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    const service = new SettingsService({ configStore: store, logService: new LogService(), connectorExecutable: process.execPath });
    const result = service.saveSettings({ connectorPort: 18080 });
    expect(result.ok).toBe(true);
    expect(result.settings?.connectorPort).toBe(18080);
  });
});

describe('log redaction and file logging', () => {
  it('redacts bearer tokens and secrets', () => {
    const redacted = redactString('Authorization: Bearer abc.def password=secret');
    expect(redacted).not.toContain('abc.def');
    expect(redacted).not.toContain('secret');
  });

  it('rotates log files when size exceeded', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-log-'));
    const writer = new FileLogWriter({ logsDir: tempDir, maxFileBytes: 20, maxFiles: 2 });
    writer.appendLine('012345678901234567890');
    writer.appendLine('next-line');
    expect(fs.existsSync(writer.getLogFilePath())).toBe(true);
  });

  it('retains only error logs when clearing nonessential logs', () => {
    const logService = new LogService();
    logService.append('information', 'info');
    logService.append('error', 'error');
    logService.clearNonessential();
    expect(logService.getEntries()).toHaveLength(1);
    expect(logService.getEntries()[0]?.level).toBe('error');
  });
});

describe('diagnostics service', () => {
  it('exports sanitized bundle', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-diag-'));
    const paths = resolveDesktopConfigPaths(tempDir);
    const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
    const service = new DiagnosticsService({
      desktopVersion: '0.4.3',
      electronVersion: 'test',
      configStore: store,
      resolvedConfig: {
        effective: getEnvironmentDefaults(true),
        persisted: getEnvironmentDefaults(true),
        sources: {},
        connectorBaseUrl: 'http://localhost:8080',
        lifecycleConfig: {} as never,
      },
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
      logService: new LogService(),
      exportDir: paths.diagnosticsExportDir,
      startedAt: Date.now(),
    });
    const result = await service.exportBundle();
    expect(result.ok).toBe(true);
    const bundle = JSON.parse(fs.readFileSync(result.bundlePath!, 'utf8')) as {
      privacyPolicy: { allowlistVersion: number };
      session: { displayLabel: string; selectedCompanyPresent: boolean };
    };
    expect(bundle.privacyPolicy.allowlistVersion).toBe(1);
    expect(bundle.session.displayLabel).toContain('no company selected');
    expect(bundle.session.selectedCompanyPresent).toBe(false);
    expect(sanitizeConfigForExport({ token: 'secret' }).token).toBe('[REDACTED]');
  });
});

describe('ipc allowlist and recovery', () => {
  it('allows known ipc channels', () => {
    expect(() => assertAllowedIpcChannel('desktop:get-dashboard')).not.toThrow();
    expect(() => assertAllowedIpcChannel('desktop:evil-channel')).toThrow();
  });

  it('validates company id', () => {
    expect(validateCompanyId('estimation')).toBe('estimation');
    expect(() => validateCompanyId('../etc/passwd')).toThrow();
  });

  it('describes recovery for corrupt config', () => {
    const recovery = new RecoveryService(new LogService());
    const action = recovery.handleConfigLoad({
      status: 'corrupt',
      config: getEnvironmentDefaults(true),
      message: 'bad json',
    });
    expect(action.code).toBe('CONFIG_CORRUPT');
  });
});

describe('settings patch validation', () => {
  it('merges patch with persisted base', () => {
    const base = getEnvironmentDefaults(false);
    const result = validateSettingsPatch({ connectorPort: 18080 }, base);
    expect(result.ok).toBe(true);
    expect(result.config?.connectorPort).toBe(18080);
  });
});
