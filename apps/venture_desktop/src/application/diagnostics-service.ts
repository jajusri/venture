import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import type { ConnectorLifecycleService } from './connector-lifecycle-service.js';
import type { DashboardService } from './dashboard-service.js';
import type { DesktopConfigStore } from './desktop-config-store.js';
import type { ResolvedDesktopConfig } from './desktop-config-resolver.js';
import {
  buildSafeDiagnosticBundle,
  buildSafeSessionDisplay,
  formatSafeDiagnosticSummary,
  mapUnknownErrorToSafeDiagnostic,
  sanitizeDiagnosticLogEntries,
  sanitizeDiagnosticLogFileRef,
  serializeSafeDiagnosticBundle,
} from './diagnostic-allowlist.js';
import {
  buildDiagnosticExportDirName,
  DIAGNOSTIC_EXPORT_BUNDLE_FILENAME,
} from './diagnostic-export-convention.js';
import { DiagnosticExportRetentionService } from './diagnostic-export-retention-service.js';
import {
  getConnectorNetworkExposure,
  getConnectorNetworkExposureWarning,
} from './connector-network-binding.js';
import type { LogService } from './log-service.js';
import type { DiagnosticsExportResult, DiagnosticsSnapshot, SessionDisplayStatus } from './types.js';

export interface DiagnosticsServiceOptions {
  readonly desktopVersion: string;
  readonly electronVersion: string;
  readonly configStore: DesktopConfigStore;
  readonly resolvedConfig: ResolvedDesktopConfig;
  readonly configStatus: string;
  readonly dashboardService: DashboardService;
  readonly lifecycleService: ConnectorLifecycleService;
  readonly logService: LogService;
  readonly exportDir: string;
  readonly startedAt: number;
  readonly retentionService?: DiagnosticExportRetentionService;
  readonly fsImpl?: Pick<typeof fs, 'mkdirSync' | 'writeFileSync' | 'readFileSync'>;
}

export class DiagnosticsService {
  private readonly desktopVersion: string;
  private readonly electronVersion: string;
  private readonly configStore: DesktopConfigStore;
  private resolvedConfig: ResolvedDesktopConfig;
  private readonly configStatus: string;
  private readonly dashboardService: DashboardService;
  private readonly lifecycleService: ConnectorLifecycleService;
  private readonly logService: LogService;
  private readonly exportDir: string;
  private readonly startedAt: number;
  private readonly retentionService: DiagnosticExportRetentionService | null;
  private readonly fsImpl: NonNullable<DiagnosticsServiceOptions['fsImpl']>;

  constructor(options: DiagnosticsServiceOptions) {
    this.desktopVersion = options.desktopVersion;
    this.electronVersion = options.electronVersion;
    this.configStore = options.configStore;
    this.resolvedConfig = options.resolvedConfig;
    this.configStatus = options.configStatus;
    this.dashboardService = options.dashboardService;
    this.lifecycleService = options.lifecycleService;
    this.logService = options.logService;
    this.exportDir = options.exportDir;
    this.startedAt = options.startedAt;
    this.retentionService = options.retentionService ?? null;
    this.fsImpl = options.fsImpl ?? fs;
  }

  updateResolvedConfig(resolvedConfig: ResolvedDesktopConfig): void {
    this.resolvedConfig = resolvedConfig;
  }

  async getSnapshot(): Promise<DiagnosticsSnapshot> {
    const lifecycle = this.lifecycleService.getStatus();
    const dashboard = await this.dashboardService.getDashboardState();
    const ownership = lifecycle.managedByDesktop
      ? 'desktop-managed'
      : lifecycle.externalProcessDetected
        ? 'external'
        : 'none';
    const selectedCompanyPresent =
      dashboard.sessionStatus !== 'NO_COMPANY_SELECTED' && dashboard.companyName !== '—';
    const session = buildSafeSessionDisplay(dashboard.sessionStatus, selectedCompanyPresent);
    const logFile = sanitizeDiagnosticLogFileRef(
      this.logService.getLogFilePath(),
      this.logService.isFileLoggingAvailable(),
    );

    return {
      generatedAt: new Date().toISOString(),
      desktopVersion: this.desktopVersion,
      connectorVersion: dashboard.connectorReachable ? dashboard.connectorVersion : null,
      bundledConnectorVersion: lifecycle.bundledConnectorVersion,
      electronVersion: this.electronVersion,
      nodeVersion: process.versions.node,
      platform: process.platform,
      osRelease: os.release(),
      architecture: process.arch,
      uptimeSeconds: Math.floor((Date.now() - this.startedAt) / 1000),
      connectorBaseUrl: this.resolvedConfig.connectorBaseUrl,
      connectorBindHost: this.resolvedConfig.effective.connectorHost,
      connectorNetworkExposure: getConnectorNetworkExposure(this.resolvedConfig.effective.connectorHost),
      connectorNetworkExposureWarning: getConnectorNetworkExposureWarning(
        this.resolvedConfig.effective.connectorHost,
      ),
      connectorProcessState: lifecycle.stateLabel,
      connectorOwnership: ownership,
      connectorPid: lifecycle.managedProcessPid,
      healthStatus: dashboard.healthStatus,
      healthReachable: dashboard.connectorReachable,
      lastSuccessfulHealthCheck: lifecycle.lastSuccessfulHealthCheck,
      tallyReachable: dashboard.connectorReachable ? dashboard.healthStatus.includes('ok') ? true : null : null,
      sessionStatus: session.status as SessionDisplayStatus,
      selectedCompanyPresent: session.selectedCompanyPresent,
      sessionDisplayLabel: session.displayLabel,
      configSource: Object.keys(this.resolvedConfig.sources).length > 0 ? 'mixed' : 'persisted/default',
      configStatus: this.configStatus,
      logFile,
      fileLoggingAvailable: logFile.available,
      recentLifecycleEvents: sanitizeDiagnosticLogEntries(this.logService.getLifecycleEvents()),
      recentErrors: sanitizeDiagnosticLogEntries(this.logService.getRecentErrors()),
    };
  }

  formatSummary(snapshot: DiagnosticsSnapshot): string {
    const bundle = buildSafeDiagnosticBundle({
      generatedAt: snapshot.generatedAt,
      desktopVersion: snapshot.desktopVersion,
      connectorVersion: snapshot.connectorVersion,
      bundledConnectorVersion: snapshot.bundledConnectorVersion,
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
      configuration: {
        effective: this.resolvedConfig.effective,
        sources: this.resolvedConfig.sources,
        status: snapshot.configStatus,
      },
      environment: process.env,
      recentLifecycleEvents: snapshot.recentLifecycleEvents,
      recentErrors: snapshot.recentErrors,
      logFilePath: snapshot.logFile.basename,
      fileLoggingAvailable: snapshot.fileLoggingAvailable,
    });
    return formatSafeDiagnosticSummary(bundle, snapshot.logFile);
  }

  async exportBundle(targetDir?: string): Promise<DiagnosticsExportResult> {
    try {
      const snapshot = await this.getSnapshot();
      const bundle = buildSafeDiagnosticBundle({
        generatedAt: snapshot.generatedAt,
        desktopVersion: snapshot.desktopVersion,
        connectorVersion: snapshot.connectorVersion,
        bundledConnectorVersion: snapshot.bundledConnectorVersion,
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
        configuration: {
          effective: this.resolvedConfig.effective,
          sources: this.resolvedConfig.sources,
          status: snapshot.configStatus,
        },
        environment: process.env,
        recentLifecycleEvents: snapshot.recentLifecycleEvents,
        recentErrors: snapshot.recentErrors,
        logFilePath: snapshot.logFile.basename,
        fileLoggingAvailable: snapshot.fileLoggingAvailable,
      });
      const serialized = serializeSafeDiagnosticBundle(bundle);
      const bundleDir = targetDir ?? path.join(this.exportDir, buildDiagnosticExportDirName(snapshot.generatedAt));
      this.fsImpl.mkdirSync(bundleDir, { recursive: true });
      const bundlePath = path.join(bundleDir, DIAGNOSTIC_EXPORT_BUNDLE_FILENAME);
      this.fsImpl.writeFileSync(bundlePath, serialized.json, 'utf8');
      this.confirmExportedBundle(bundlePath);
      this.runRetentionCleanup();
      this.logService.appendStructured({
        level: 'information',
        message: `Diagnostics bundle exported (${serialized.truncated ? 'truncated' : 'complete'})`,
        event: 'diagnostics_exported',
        component: 'diagnostics',
      });

      return {
        ok: true,
        message: 'Diagnostics bundle exported successfully.',
        bundlePath,
      };
    } catch (error) {
      const safe = mapUnknownErrorToSafeDiagnostic(error, 'diagnostics_export');
      this.logService.appendStructured({
        level: 'error',
        message: `Diagnostics export failed: ${safe.message}`,
        event: 'diagnostics_export_failed',
        component: 'diagnostics',
        metadata: { errorCode: safe.code },
      });
      return {
        ok: false,
        message: safe.message,
        bundlePath: null,
      };
    }
  }

  runRetentionCleanup(): void {
    if (!this.retentionService) {
      return;
    }
    try {
      this.retentionService.cleanup({
        exportDir: this.exportDir,
        retentionDays: this.resolvedConfig.effective.diagnosticsRetentionDays,
      });
    } catch {
      // Cleanup failure must not block export or startup callers.
    }
  }

  private confirmExportedBundle(bundlePath: string): void {
    const readFileSync = this.fsImpl.readFileSync ?? fs.readFileSync.bind(fs);
    const onDisk = readFileSync(bundlePath, 'utf8');
    JSON.parse(onDisk);
  }
}
