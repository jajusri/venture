import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import type { ConnectorLifecycleService } from './connector-lifecycle-service.js';
import type { DashboardService } from './dashboard-service.js';
import type { DesktopConfigStore } from './desktop-config-store.js';
import type { ResolvedDesktopConfig } from './desktop-config-resolver.js';
import { sanitizeConfigForExport, stripEnvironmentVariables } from './log-redaction.js';
import {
  getConnectorNetworkExposure,
  getConnectorNetworkExposureWarning,
} from './connector-network-binding.js';
import type { LogService } from './log-service.js';
import type { DiagnosticsExportResult, DiagnosticsSnapshot } from './types.js';

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
  readonly fsImpl?: Pick<typeof fs, 'mkdirSync' | 'writeFileSync'>;
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

    return {
      generatedAt: new Date().toISOString(),
      desktopVersion: this.desktopVersion,
      connectorVersion: dashboard.connectorReachable ? dashboard.connectorVersion : null,
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
      sessionSummary: `${dashboard.sessionStatus} · ${dashboard.companyName}`,
      configSource: Object.keys(this.resolvedConfig.sources).length > 0 ? 'mixed' : 'persisted/default',
      configStatus: this.configStatus,
      logFilePath: this.logService.getLogFilePath(),
      fileLoggingAvailable: this.logService.isFileLoggingAvailable(),
      recentLifecycleEvents: this.logService.getLifecycleEvents(),
      recentErrors: this.logService.getRecentErrors(),
    };
  }

  formatSummary(snapshot: DiagnosticsSnapshot): string {
    return [
      'Budcom Desktop Diagnostics Summary',
      `Generated: ${snapshot.generatedAt}`,
      `Desktop: ${snapshot.desktopVersion}`,
      `Connector: ${snapshot.connectorVersion ?? 'unavailable'}`,
      `Electron: ${snapshot.electronVersion}`,
      `Node: ${snapshot.nodeVersion}`,
      `OS: ${snapshot.platform} ${snapshot.osRelease} (${snapshot.architecture})`,
      `Uptime: ${snapshot.uptimeSeconds}s`,
      `Connector URL: ${snapshot.connectorBaseUrl}`,
      `Connector bind host: ${snapshot.connectorBindHost} (${snapshot.connectorNetworkExposure})`,
      snapshot.connectorNetworkExposureWarning
        ? `Security warning: ${snapshot.connectorNetworkExposureWarning}`
        : 'Connector network exposure: loopback-only (secure default).',
      `Process State: ${snapshot.connectorProcessState}`,
      `Ownership: ${snapshot.connectorOwnership}`,
      `PID: ${snapshot.connectorPid ?? 'n/a'}`,
      `Health: ${snapshot.healthStatus} (reachable=${snapshot.healthReachable})`,
      `Session: ${snapshot.sessionSummary}`,
      `Config: ${snapshot.configStatus}`,
      `Log file: ${snapshot.logFilePath ?? 'unavailable'}`,
    ].join('\n');
  }

  async exportBundle(targetDir?: string): Promise<DiagnosticsExportResult> {
    try {
      const snapshot = await this.getSnapshot();
      const timestamp = snapshot.generatedAt.replace(/[:.]/g, '-');
      const bundleDir = targetDir ?? path.join(this.exportDir, `budcom-diagnostics-${timestamp}`);
      this.fsImpl.mkdirSync(bundleDir, { recursive: true });

      const bundle = {
        bundleVersion: 1,
        generatedAt: snapshot.generatedAt,
        versions: {
          desktop: snapshot.desktopVersion,
          connector: snapshot.connectorVersion,
          electron: snapshot.electronVersion,
          node: snapshot.nodeVersion,
        },
        runtime: {
          platform: snapshot.platform,
          osRelease: snapshot.osRelease,
          architecture: snapshot.architecture,
          uptimeSeconds: snapshot.uptimeSeconds,
        },
        connector: {
          baseUrl: snapshot.connectorBaseUrl,
          bindHost: snapshot.connectorBindHost,
          networkExposure: snapshot.connectorNetworkExposure,
          networkExposureWarning: snapshot.connectorNetworkExposureWarning,
          processState: snapshot.connectorProcessState,
          ownership: snapshot.connectorOwnership,
          pid: snapshot.connectorPid,
          healthStatus: snapshot.healthStatus,
          healthReachable: snapshot.healthReachable,
          lastSuccessfulHealthCheck: snapshot.lastSuccessfulHealthCheck,
        },
        session: {
          summary: snapshot.sessionSummary,
        },
        configuration: sanitizeConfigForExport({
          effective: this.resolvedConfig.effective,
          sources: this.resolvedConfig.sources,
          status: snapshot.configStatus,
        }),
        environment: stripEnvironmentVariables(process.env),
        logs: {
          recentLifecycleEvents: snapshot.recentLifecycleEvents,
          recentErrors: snapshot.recentErrors,
        },
        exclusions: [
          'passwords',
          'tokens',
          'license secrets',
          'full tally xml payloads',
          'customer ledger data',
          'voucher data',
          'personal or financial records',
          'arbitrary environment variables',
        ],
      };

      const bundlePath = path.join(bundleDir, 'diagnostics-bundle.json');
      this.fsImpl.writeFileSync(bundlePath, `${JSON.stringify(bundle, null, 2)}\n`, 'utf8');
      this.logService.appendStructured({
        level: 'information',
        message: `Diagnostics bundle exported to ${bundlePath}`,
        event: 'diagnostics_exported',
        component: 'diagnostics',
      });

      return {
        ok: true,
        message: 'Diagnostics bundle exported successfully.',
        bundlePath,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.logService.appendStructured({
        level: 'error',
        message: `Diagnostics export failed: ${message}`,
        event: 'diagnostics_export_failed',
        component: 'diagnostics',
      });
      return {
        ok: false,
        message,
        bundlePath: null,
      };
    }
  }
}
