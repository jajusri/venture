import type { DesktopConfigV1 } from './desktop-config-schema.js';
import {
  requiresRestart,
  validateDesktopConfig,
  validateSettingsPatch,
} from './desktop-config-schema.js';
import type { DesktopConfigStore } from './desktop-config-store.js';
import { mergeSettingsPatch, resolveDesktopConfig, type ResolvedDesktopConfig } from './desktop-config-resolver.js';
import { getEnvironmentDefaults } from './desktop-config-defaults.js';
import type { LogService } from './log-service.js';
import type { SettingsSaveResult, SettingsState, SettingsValidationResult } from './types.js';
import { DESKTOP_VERSION } from './dashboard-service.js';

export interface SettingsServiceOptions {
  readonly configStore: DesktopConfigStore;
  readonly logService: LogService;
  readonly isDevelopment?: boolean;
  readonly connectorExecutable: string;
}

export class SettingsService {
  private readonly configStore: DesktopConfigStore;
  private readonly logService: LogService;
  private readonly isDevelopment: boolean;
  private readonly connectorExecutable: string;
  private resolved: ResolvedDesktopConfig;
  private configStatus: string;
  private pendingDraft: Partial<DesktopConfigV1> | null = null;

  constructor(options: SettingsServiceOptions) {
    this.configStore = options.configStore;
    this.logService = options.logService;
    this.isDevelopment = options.isDevelopment ?? process.env.NODE_ENV !== 'production';
    this.connectorExecutable = options.connectorExecutable;
    const loadResult = this.configStore.loadFromDisk();
    this.configStatus = loadResult.status;
    this.resolved = resolveDesktopConfig(loadResult.config, getEnvironmentDefaults(this.isDevelopment));
  }

  getResolvedConfig(): ResolvedDesktopConfig {
    return this.resolved;
  }

  getConfigStatus(): string {
    return this.configStatus;
  }

  setConfigStatus(status: string): void {
    this.configStatus = status;
  }

  reload(): ResolvedDesktopConfig {
    const loadResult = this.configStore.loadFromDisk();
    this.configStatus = loadResult.status;
    this.resolved = resolveDesktopConfig(loadResult.config, getEnvironmentDefaults(this.isDevelopment));
    this.pendingDraft = null;
    return this.resolved;
  }

  applyPersistedConfig(config: DesktopConfigV1): ResolvedDesktopConfig {
    this.resolved = resolveDesktopConfig(config, getEnvironmentDefaults(this.isDevelopment));
    return this.resolved;
  }

  validateInput(input: unknown): SettingsValidationResult {
    const result = validateSettingsPatch(input, this.resolved.persisted);
    return {
      ok: result.ok,
      errors: result.errors,
    };
  }

  getSettingsState(): SettingsState {
    const effective = this.resolved.effective;
    return {
      connectorUrl: this.resolved.connectorBaseUrl,
      connectorHost: effective.connectorHost,
      apiVersion: '1.0.0',
      desktopVersion: DESKTOP_VERSION,
      erpType: 'tally',
      pollIntervalSeconds: Math.round(effective.healthPollIntervalMs / 1000),
      connectorExecutable: this.connectorExecutable,
      connectorPort: effective.connectorPort,
      autoStartConnector: effective.autoStartConnector,
      healthPollIntervalMs: effective.healthPollIntervalMs,
      startupTimeoutMs: effective.startupTimeoutMs,
      shutdownGraceMs: effective.shutdownGraceMs,
      maxRestartAttempts: effective.maxRestartAttempts,
      reconnectBaseDelayMs: effective.reconnectBaseDelayMs,
      logLevel: effective.logLevel,
      diagnosticsRetentionDays: effective.diagnosticsRetentionDays,
      tallyHost: effective.tallyHost,
      tallyPort: effective.tallyPort,
      configSource: Object.keys(this.resolved.sources).length > 0 ? 'mixed (env + persisted)' : 'persisted/default',
      configStatus: this.configStatus,
      restartRequired: false,
      hasUnsavedChanges: this.pendingDraft !== null,
    };
  }

  previewPatch(input: unknown): SettingsState {
    const validation = validateSettingsPatch(input, this.resolved.persisted);
    if (!validation.ok || !validation.config) {
      return this.getSettingsState();
    }
    this.pendingDraft = validation.config;
    const preview = validation.config;
    const previewResolved = resolveDesktopConfig(preview, getEnvironmentDefaults(this.isDevelopment));
    return {
      ...this.getSettingsState(),
      connectorUrl: previewResolved.connectorBaseUrl,
      connectorHost: preview.connectorHost,
      connectorPort: preview.connectorPort,
      autoStartConnector: preview.autoStartConnector,
      healthPollIntervalMs: preview.healthPollIntervalMs,
      startupTimeoutMs: preview.startupTimeoutMs,
      shutdownGraceMs: preview.shutdownGraceMs,
      maxRestartAttempts: preview.maxRestartAttempts,
      reconnectBaseDelayMs: preview.reconnectBaseDelayMs,
      logLevel: preview.logLevel,
      diagnosticsRetentionDays: preview.diagnosticsRetentionDays,
      tallyHost: preview.tallyHost,
      tallyPort: preview.tallyPort,
      restartRequired: requiresRestart(this.resolved.effective, preview),
      hasUnsavedChanges: true,
    };
  }

  saveSettings(input: unknown): SettingsSaveResult {
    const validation = validateSettingsPatch(input, this.resolved.persisted);
    if (!validation.ok || !validation.config) {
      return {
        ok: false,
        message: validation.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
        restartRequired: false,
        settings: null,
      };
    }

    const merged = validation.config;
    const validated = validateDesktopConfig(merged);
    if (!validated.ok || !validated.config) {
      return {
        ok: false,
        message: validated.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
        restartRequired: false,
        settings: null,
      };
    }

    const restartRequired = requiresRestart(this.resolved.effective, validated.config);
    const saveResult = this.configStore.save(validated.config);
    if (!saveResult.ok) {
      return {
        ok: false,
        message: saveResult.message,
        restartRequired: false,
        settings: null,
      };
    }

    this.configStatus = 'loaded';
    this.resolved = resolveDesktopConfig(validated.config, getEnvironmentDefaults(this.isDevelopment));
    this.pendingDraft = null;
    this.logService.appendStructured({
      level: 'information',
      message: 'Desktop settings saved.',
      event: 'settings_saved',
      component: 'settings',
      metadata: { restartRequired },
    });

    return {
      ok: true,
      message: restartRequired
        ? 'Settings saved. Restart the desktop app or reconnect the connector for all changes to take effect.'
        : 'Settings saved successfully.',
      restartRequired,
      settings: this.getSettingsState(),
    };
  }

  restoreDefaults(): SettingsSaveResult {
    const defaults = this.configStore.restoreDefaults();
    this.configStatus = 'defaults';
    this.resolved = resolveDesktopConfig(defaults, getEnvironmentDefaults(this.isDevelopment));
    this.pendingDraft = null;
    this.logService.appendStructured({
      level: 'information',
      message: 'Desktop settings restored to defaults.',
      event: 'settings_restored',
      component: 'settings',
    });
    return {
      ok: true,
      message: 'Default settings restored.',
      restartRequired: true,
      settings: this.getSettingsState(),
    };
  }
}
