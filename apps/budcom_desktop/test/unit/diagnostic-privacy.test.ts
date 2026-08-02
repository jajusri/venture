import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { DiagnosticsService } from '../../src/application/diagnostics-service.js';
import {
  APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS,
  APPROVED_DIAGNOSTIC_CONNECTOR_CONFIG_KEYS,
  APPROVED_DIAGNOSTIC_LIFECYCLE_CONFIG_KEYS,
  APPROVED_DIAGNOSTIC_LOGGING_CONFIG_KEYS,
  APPROVED_DIAGNOSTIC_SOURCE_SUMMARY_KEYS,
  APPROVED_DIAGNOSTIC_TALLY_CONFIG_KEYS,
  assertDiagnosticOutputExcludesSentinels,
  buildSafeDiagnosticBundle,
  buildSafeDiagnosticConfiguration,
  DIAGNOSTIC_LIMITS,
  DIAGNOSTIC_PRIVACY_SENTINELS,
  mapUnknownErrorToSafeDiagnostic,
  sanitizeDiagnosticLogEntries,
  sanitizeDiagnosticText,
  serializeSafeDiagnosticBundle,
} from '../../src/application/diagnostic-allowlist.js';
import type { DesktopConfigV1 } from '../../src/application/desktop-config-schema.js';
import type { ConfigSource } from '../../src/application/desktop-config-resolver.js';
import { getEnvironmentDefaults } from '../../src/application/desktop-config-defaults.js';
import { resolveDesktopConfigPaths } from '../../src/application/desktop-config-paths.js';
import { DesktopConfigStore } from '../../src/application/desktop-config-store.js';
import { LogService } from '../../src/application/log-service.js';
import type { LogEntry } from '../../src/application/types.js';

const S = DIAGNOSTIC_PRIVACY_SENTINELS;

function defaultConfigurationInput(status = 'loaded') {
  return {
    effective: getEnvironmentDefaults(true),
    sources: {},
    status,
  };
}

function buildMinimalBundleInput(
  overrides: Partial<Parameters<typeof buildSafeDiagnosticBundle>[0]> = {},
) {
  return {
    generatedAt: '2026-01-01T00:00:00.000Z',
    desktopVersion: '0.4.3',
    connectorVersion: '0.3.1',
    bundledConnectorVersion: '0.4.0',
    electronVersion: 'test-electron',
    nodeVersion: process.versions.node,
    platform: process.platform,
    osRelease: 'test',
    architecture: 'x64',
    uptimeSeconds: 10,
    connectorBaseUrl: 'http://127.0.0.1:8080',
    connectorBindHost: '127.0.0.1',
    connectorNetworkExposure: 'loopback' as const,
    connectorNetworkExposureWarning: null,
    connectorProcessState: 'Connected',
    connectorOwnership: 'desktop-managed' as const,
    connectorPid: 1,
    healthStatus: 'ok',
    healthReachable: true,
    lastSuccessfulHealthCheck: '2026-01-01T00:00:00.000Z',
    tallyReachable: true,
    sessionStatus: 'ACTIVE' as const,
    selectedCompanyPresent: true,
    configuration: defaultConfigurationInput(),
    environment: process.env,
    recentLifecycleEvents: [],
    recentErrors: [],
    logFilePath: 'budcom-desktop.log',
    fileLoggingAvailable: true,
    ...overrides,
  };
}

function maliciousConfigurationInput() {
  const defaults = getEnvironmentDefaults(true);
  return {
    effective: {
      ...defaults,
      connectorHost: '192.168.1.50',
      password: 'secret-password',
      token: S.secretToken,
      licenceId: S.licenceId,
      companyId: 'company-test-001',
      companyName: S.companyName,
      connectorExecutable: S.windowsPath,
      unixDataDir: '/home/user/secret-data',
    } as DesktopConfigV1 & Record<string, unknown>,
    sources: {
      connectorHost: 'environment',
      futureNested: 'environment',
      password: 'environment',
    } as Partial<Record<keyof DesktopConfigV1, ConfigSource>> & {
      futureNested: ConfigSource;
      password: ConfigSource;
    },
    status: `loaded ${S.companyName} ${'z'.repeat(DIAGNOSTIC_LIMITS.maxStringLength + 50)}`,
  };
}

function createDiagnosticsService(options: {
  companyName: string;
  sessionStatus?: 'ACTIVE' | 'NO_COMPANY_SELECTED';
  logService?: LogService;
}) {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-diag-privacy-'));
  const paths = resolveDesktopConfigPaths(tempDir);
  const store = new DesktopConfigStore({ paths, defaults: getEnvironmentDefaults(true) });
  const logService = options.logService ?? new LogService();
  return new DiagnosticsService({
    desktopVersion: '0.4.3',
    electronVersion: 'test-electron',
    configStore: store,
    resolvedConfig: {
      effective: getEnvironmentDefaults(true),
      persisted: getEnvironmentDefaults(true),
      sources: {},
      connectorBaseUrl: 'http://127.0.0.1:8080',
      lifecycleConfig: {} as never,
    },
    configStatus: 'loaded',
    dashboardService: {
      getDashboardState: async () => ({
        connectorVersion: '0.3.1',
        connectorReachable: true,
        healthStatus: 'ok',
        companyName: options.companyName,
        companyId: 'company-test-001',
        sessionStatus: options.sessionStatus ?? 'ACTIVE',
      }),
    } as never,
    lifecycleService: {
      getStatus: () => ({
        stateLabel: 'Connected',
        managedByDesktop: true,
        externalProcessDetected: false,
        lastSuccessfulHealthCheck: '2026-01-01T00:00:00.000Z',
        managedProcessPid: 4242,
        bundledConnectorVersion: '0.4.0',
      }),
    } as never,
    logService,
    exportDir: paths.diagnosticsExportDir,
    startedAt: Date.now() - 60_000,
  });
}

function seedSentinelLogs(logService: LogService): void {
  logService.append('error', `Ledger sync failed for ${S.ledgerName} amount ${S.amount}`);
  logService.append('warning', `Session company: ${S.companyName}`);
  logService.append('error', `Tally response ${S.xmlSnippet} at ${S.windowsPath}`);
  logService.append('error', `Auth failed token ${S.secretToken} header Authorization: Bearer ${S.secretToken}`);
  logService.append('information', `[lifecycle:startup] contact ${S.email} gst ${S.gstin} voucher ${S.voucherNumber}`);
}

describe('diagnostic privacy allowlist (desktop)', () => {
  it('removes company name from snapshot session display label', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName });
    const snapshot = await service.getSnapshot();
    expect(snapshot.sessionDisplayLabel).not.toContain(S.companyName);
    expect(snapshot.sessionDisplayLabel).toContain('company selected');
    expect(snapshot.selectedCompanyPresent).toBe(true);
  });

  it('removes company name from exported bundle JSON', async () => {
    const logService = new LogService();
    seedSentinelLogs(logService);
    const service = createDiagnosticsService({ companyName: S.companyName, logService });
    const exported = await service.exportBundle();
    expect(exported.ok).toBe(true);
    const serialized = fs.readFileSync(exported.bundlePath!, 'utf8');
    assertDiagnosticOutputExcludesSentinels(serialized);
    const bundle = JSON.parse(serialized) as { session: { displayLabel: string } };
    expect(bundle.session.displayLabel).not.toContain(S.companyName);
  });

  it('removes ledger, tax, voucher, financial, xml, secret, path, and nested cause sentinels from bundle', async () => {
    const logService = new LogService();
    seedSentinelLogs(logService);
    logService.append('error', `Nested cause ${S.nestedCauseBody}`);
    const service = createDiagnosticsService({ companyName: S.companyName, logService });
    const exported = await service.exportBundle();
    const serialized = fs.readFileSync(exported.bundlePath!, 'utf8');
    assertDiagnosticOutputExcludesSentinels(serialized, [S.phone]);
  });

  it('maps unknown errors to generic safe output without leaking cause payloads', () => {
    const error = new Error(`Failed for ${S.companyName}`);
    (error as Error & { cause?: unknown }).cause = {
      responseBody: S.xmlSnippet,
      headers: { Authorization: `Bearer ${S.secretToken}` },
    };
    const safe = mapUnknownErrorToSafeDiagnostic(error, 'export');
    const serialized = JSON.stringify(safe);
    assertDiagnosticOutputExcludesSentinels(serialized);
    expect(safe.message).toBe('An unexpected error occurred.');
  });

  it('preserves allowed operational metadata in bundle', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName });
    const exported = await service.exportBundle();
    const bundle = JSON.parse(fs.readFileSync(exported.bundlePath!, 'utf8')) as {
      versions: { desktop: string };
      connector: { healthReachable: boolean; bindHost: string };
      runtime: { platform: string };
      privacyPolicy: { allowlistVersion: number };
    };
    expect(bundle.versions.desktop).toBe('0.4.3');
    expect(bundle.connector.healthReachable).toBe(true);
    expect(bundle.connector.bindHost).toBe('127.0.0.1');
    expect(bundle.runtime.platform).toBe(process.platform);
    expect(bundle.privacyPolicy.allowlistVersion).toBe(1);
  });

  it('applies size truncation limits without preserving removed content', () => {
    const hugeMessage = `${'x'.repeat(DIAGNOSTIC_LIMITS.maxStringLength + 200)} ${S.companyName}`;
    const entries: LogEntry[] = Array.from({ length: 40 }, (_, index) => ({
      id: `log-${index}`,
      timestamp: '2026-01-01T00:00:00.000Z',
      level: 'error',
      message: `${hugeMessage}-${index}`,
      event: 'test',
      component: 'test',
      metadata: null,
    }));
    const sanitized = sanitizeDiagnosticLogEntries(entries);
    expect(sanitized.length).toBeLessThanOrEqual(DIAGNOSTIC_LIMITS.maxLogEntries);
    const serialized = JSON.stringify(sanitized);
    expect(serialized).not.toContain(S.companyName);
    expect(sanitized[0]?.message.includes('[truncated]')).toBe(true);
  });

  it('produces deterministic JSON export structure', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName, sessionStatus: 'NO_COMPANY_SELECTED' });
    const snapshot = await service.getSnapshot();
    const bundle = buildSafeDiagnosticBundle({
      generatedAt: snapshot.generatedAt,
      desktopVersion: snapshot.desktopVersion,
      connectorVersion: snapshot.connectorVersion,
      electronVersion: snapshot.electronVersion,
      nodeVersion: snapshot.nodeVersion,
      platform: snapshot.platform,
      osRelease: snapshot.osRelease,
      architecture: snapshot.architecture,
      uptimeSeconds: snapshot.uptimeSeconds,
      connectorBaseUrl: snapshot.connectorBaseUrl,
      connectorBindHost: snapshot.connectorBindHost,
      connectorNetworkExposure: snapshot.connectorNetworkExposure,
      connectorNetworkExposureWarning: snapshot.connectorNetworkExposureWarning,
      connectorProcessState: snapshot.connectorProcessState,
      connectorOwnership: snapshot.connectorOwnership,
      connectorPid: snapshot.connectorPid,
      healthStatus: snapshot.healthStatus,
      healthReachable: snapshot.healthReachable,
      lastSuccessfulHealthCheck: snapshot.lastSuccessfulHealthCheck,
      tallyReachable: snapshot.tallyReachable,
      sessionStatus: snapshot.sessionStatus,
      selectedCompanyPresent: snapshot.selectedCompanyPresent,
      configuration: defaultConfigurationInput(snapshot.configStatus),
      environment: process.env,
      recentLifecycleEvents: snapshot.recentLifecycleEvents,
      recentErrors: snapshot.recentErrors,
      logFilePath: snapshot.logFile.basename,
      fileLoggingAvailable: snapshot.fileLoggingAvailable,
    });
    const first = serializeSafeDiagnosticBundle({ ...bundle, correlationId: 'fixed-id' });
    const second = serializeSafeDiagnosticBundle({ ...bundle, correlationId: 'fixed-id' });
    expect(first.json).toBe(second.json);
    expect(() => JSON.parse(first.json)).not.toThrow();
  });

  it('clipboard summary excludes company name', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName });
    const snapshot = await service.getSnapshot();
    const summary = service.formatSummary(snapshot);
    assertDiagnosticOutputExcludesSentinels(summary);
    expect(summary).toContain('company selected');
  });

  it('sanitizes path-like and phone sentinels in free text', () => {
    const sanitized = sanitizeDiagnosticText(
      `path ${S.windowsPath} phone ${S.phone} gst ${S.gstin}`,
    );
    assertDiagnosticOutputExcludesSentinels(sanitized, [S.phone, S.gstin]);
    expect(sanitized).not.toContain('ContosoUser');
  });

  it('does not mutate source log service entries when building snapshot', async () => {
    const logService = new LogService();
    logService.append('error', `Session company: ${S.companyName}`);
    const before = logService.getRecentErrors()[0]?.message;
    const service = createDiagnosticsService({ companyName: S.companyName, logService });
    await service.getSnapshot();
    expect(logService.getRecentErrors()[0]?.message).toBe(before);
    expect(before).toContain(S.companyName);
  });

  it('excludes stock-item, address, licence, guid, alterid, and masterid sentinels from serialized bundle', async () => {
    const logService = new LogService();
    logService.append(
      'error',
      `stock ${S.stockItemName} at ${S.postalAddress} licence ${S.licenceId} guid ${S.rawGuid} ${S.rawAlterId} ${S.rawMasterId}`,
    );
    const service = createDiagnosticsService({ companyName: S.companyName, logService });
    const exported = await service.exportBundle();
    const serialized = fs.readFileSync(exported.bundlePath!, 'utf8');
    assertDiagnosticOutputExcludesSentinels(serialized);
  });

  it('drops log entry metadata and unexpected array member fields from serialized export', async () => {
    const entries: LogEntry[] = [
      {
        id: 'log-1',
        timestamp: '2026-01-01T00:00:00.000Z',
        level: 'error',
        message: `ledger ${S.ledgerName}`,
        event: 'sync_failed',
        component: 'ledger-sync',
        metadata: {
          companyName: S.companyName,
          rawGuid: S.rawGuid,
          nestedSecret: S.secretToken,
        },
      },
    ];
    const sanitized = sanitizeDiagnosticLogEntries(entries);
    const serialized = JSON.stringify(sanitized);
    assertDiagnosticOutputExcludesSentinels(serialized);
    expect(serialized).not.toContain('metadata');
    expect(serialized).not.toContain('nestedSecret');
  });

  it('constructs bundle top-level fields from explicit allowlist only', () => {
    const bundle = buildSafeDiagnosticBundle(buildMinimalBundleInput());
    const parsed = JSON.parse(serializeSafeDiagnosticBundle(bundle).json) as Record<string, unknown>;
    expect(Object.keys(parsed).sort()).toEqual(
      [
        'bundleVersion',
        'generatedAt',
        'correlationId',
        'truncated',
        'versions',
        'runtime',
        'connector',
        'session',
        'configuration',
        'environment',
        'logs',
        'privacyPolicy',
      ].sort(),
    );
    expect(parsed.privacyPolicy).toEqual({
      allowlistVersion: 1,
      exportSurface: 'desktop-diagnostics-bundle-v1',
    });
  });

  it('maps overlong raw errors to generic safe output without leaking content', () => {
    const longMessage = `${'E'.repeat(DIAGNOSTIC_LIMITS.maxStringLength + 300)} ${S.companyName}`;
    const safe = mapUnknownErrorToSafeDiagnostic(new Error(longMessage), 'export');
    const serialized = JSON.stringify(safe);
    assertDiagnosticOutputExcludesSentinels(serialized);
    expect(safe.message).toBe('An unexpected error occurred.');
    expect(safe.message.length).toBeLessThanOrEqual(DIAGNOSTIC_LIMITS.maxStringLength);
  });

  it('retains approved operational fields including correlation id and privacy policy version', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName });
    const exported = await service.exportBundle();
    const bundle = JSON.parse(fs.readFileSync(exported.bundlePath!, 'utf8')) as {
      correlationId: string;
      versions: { desktop: string; connector: string | null };
      connector: { healthReachable: boolean; bindHost: string; healthStatus: string };
      runtime: { platform: string; osRelease: string };
      privacyPolicy: { allowlistVersion: number; exportSurface: string };
    };
    expect(bundle.correlationId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
    expect(bundle.versions.desktop).toBe('0.4.3');
    expect(bundle.versions.connector).toBe('0.3.1');
    expect(bundle.versions.bundledConnector).toBe('0.4.0');
    expect(bundle.connector.healthReachable).toBe(true);
    expect(bundle.connector.bindHost).toBe('127.0.0.1');
    expect(bundle.connector.healthStatus).toBe('ok');
    expect(bundle.runtime.platform).toBe(process.platform);
    expect(bundle.privacyPolicy.allowlistVersion).toBe(1);
    expect(bundle.privacyPolicy.exportSurface).toBe('desktop-diagnostics-bundle-v1');
  });
});

describe('diagnostic configuration allowlist (desktop)', () => {
  it('retains approved configuration fields with exact top-level keys', () => {
    const bundle = buildSafeDiagnosticBundle(buildMinimalBundleInput());
    const configuration = JSON.parse(serializeSafeDiagnosticBundle(bundle).json).configuration as Record<
      string,
      unknown
    >;
    expect(Object.keys(configuration).sort()).toEqual([...APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS].sort());
    expect(Object.keys(configuration.connector as Record<string, unknown>).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_CONNECTOR_CONFIG_KEYS].sort(),
    );
    expect(Object.keys(configuration.tally as Record<string, unknown>).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_TALLY_CONFIG_KEYS].sort(),
    );
    expect(Object.keys(configuration.lifecycle as Record<string, unknown>).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_LIFECYCLE_CONFIG_KEYS].sort(),
    );
    expect(Object.keys(configuration.logging as Record<string, unknown>).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_LOGGING_CONFIG_KEYS].sort(),
    );
    expect(Object.keys(configuration.sourceSummary as Record<string, unknown>).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_SOURCE_SUMMARY_KEYS].sort(),
    );
    expect(configuration.connector).toMatchObject({
      hostScope: 'loopback',
      port: 8080,
      autoStart: true,
    });
    expect(configuration.logging).toMatchObject({
      level: 'debug',
      diagnosticsRetentionDays: 7,
    });
  });

  it('excludes password, token, licence, company, and path sentinels from serialized configuration', () => {
    const bundle = buildSafeDiagnosticBundle(
      buildMinimalBundleInput({ configuration: maliciousConfigurationInput() }),
    );
    const serialized = serializeSafeDiagnosticBundle(bundle).json;
    expect(serialized.toLowerCase()).not.toContain('secret-password');
    expect(serialized.toLowerCase()).not.toContain(S.secretToken.toLowerCase());
    expect(serialized.toLowerCase()).not.toContain(S.licenceId.toLowerCase());
    expect(serialized.toLowerCase()).not.toContain(S.companyName.toLowerCase());
    expect(serialized.toLowerCase()).not.toContain('company-test-001');
    expect(serialized.toLowerCase()).not.toContain('contosouser');
    expect(serialized.toLowerCase()).not.toContain('/home/user/secret-data');
    expect(serialized.toLowerCase()).not.toContain('192.168.1.50');
  });

  it('drops arbitrary future top-level and nested configuration properties', () => {
    const safe = buildSafeDiagnosticConfiguration(maliciousConfigurationInput());
    const serialized = JSON.stringify(safe);
    expect(serialized).not.toContain('password');
    expect(serialized).not.toContain('futureNested');
    expect(serialized).not.toContain('connectorExecutable');
    expect(serialized).not.toContain('unixDataDir');
    expect(serialized).not.toContain('companyName');
    expect(Object.keys(safe).sort()).toEqual([...APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS].sort());
  });

  it('bounds overlong configuration status text in serialized output', () => {
    const safe = buildSafeDiagnosticConfiguration(maliciousConfigurationInput());
    expect(safe.status.length).toBeLessThanOrEqual(120);
    expect(safe.status).toContain('[truncated]');
    expect(safe.status.toLowerCase()).not.toContain(S.companyName.toLowerCase());
  });

  it('does not include raw environment-variable object in configuration section', async () => {
    const service = createDiagnosticsService({ companyName: S.companyName });
    const exported = await service.exportBundle();
    const bundle = JSON.parse(fs.readFileSync(exported.bundlePath!, 'utf8')) as {
      configuration: Record<string, unknown>;
      environment: Record<string, unknown>;
    };
    expect(Object.keys(bundle.configuration).sort()).toEqual(
      [...APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS].sort(),
    );
    expect(bundle.configuration).not.toHaveProperty('environment');
    expect(Object.keys(bundle.environment).length).toBeGreaterThan(0);
  });

  it('prevents object-spread injection from entering serialized configuration keys', () => {
    const injected = buildSafeDiagnosticConfiguration({
      effective: {
        ...getEnvironmentDefaults(true),
        ...({
          injectedTopLevel: 'must-not-export',
          connector: { injectedNested: 'must-not-export' },
        } as Record<string, unknown>),
      } as DesktopConfigV1,
      sources: {},
      status: 'loaded',
    });
    const serialized = JSON.stringify(injected);
    expect(serialized).not.toContain('injectedTopLevel');
    expect(serialized).not.toContain('must-not-export');
    expect(Object.keys(injected).sort()).toEqual([...APPROVED_DIAGNOSTIC_CONFIGURATION_TOP_LEVEL_KEYS].sort());
    expect(injected.connector.hostScope).toBe('loopback');
  });
});
