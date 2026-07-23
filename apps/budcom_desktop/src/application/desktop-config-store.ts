import fs from 'node:fs';
import path from 'node:path';

import type { DesktopConfigV1 } from './desktop-config-schema.js';
import { validateDesktopConfig } from './desktop-config-schema.js';
import type { DesktopConfigPaths } from './desktop-config-paths.js';

export type ConfigLoadStatus = 'loaded' | 'defaults' | 'recovered' | 'corrupt';

export interface ConfigLoadResult {
  readonly status: ConfigLoadStatus;
  readonly config: DesktopConfigV1;
  readonly message: string | null;
}

export interface ConfigStoreOptions {
  readonly paths: DesktopConfigPaths;
  readonly defaults: DesktopConfigV1;
  readonly fsImpl?: Pick<typeof fs, 'readFileSync' | 'writeFileSync' | 'renameSync' | 'existsSync' | 'mkdirSync' | 'copyFileSync' | 'unlinkSync'>;
}

export class DesktopConfigStore {
  private readonly paths: DesktopConfigPaths;
  private readonly defaults: DesktopConfigV1;
  private readonly fsImpl: Pick<typeof fs, 'readFileSync' | 'writeFileSync' | 'renameSync' | 'existsSync' | 'mkdirSync' | 'copyFileSync' | 'unlinkSync'>;
  private current: DesktopConfigV1;

  constructor(options: ConfigStoreOptions) {
    this.paths = options.paths;
    this.defaults = options.defaults;
    this.fsImpl = options.fsImpl ?? fs;
    this.ensureDirectories();
    const loaded = this.loadFromDisk();
    this.current = loaded.config;
  }

  getConfig(): DesktopConfigV1 {
    return { ...this.current };
  }

  loadFromDisk(): ConfigLoadResult {
    this.ensureDirectories();
    if (!this.fsImpl.existsSync(this.paths.configFilePath)) {
      return {
        status: 'defaults',
        config: { ...this.defaults },
        message: 'No persisted configuration found. Using defaults.',
      };
    }

    try {
      const raw = this.fsImpl.readFileSync(this.paths.configFilePath, 'utf8');
      const parsed = JSON.parse(raw) as unknown;
      const validated = validateDesktopConfig(parsed);
      if (!validated.ok || !validated.config) {
        return this.recoverFromCorruption(validated.errors.map((error) => error.message).join('; '));
      }
      return {
        status: 'loaded',
        config: validated.config,
        message: null,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return this.recoverFromCorruption(message);
    }
  }

  save(config: DesktopConfigV1): { ok: true } | { ok: false; message: string } {
    const validated = validateDesktopConfig(config);
    if (!validated.ok || !validated.config) {
      return {
        ok: false,
        message: validated.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
      };
    }

    try {
      this.ensureDirectories();
      const tempPath = `${this.paths.configFilePath}.tmp`;
      const payload = `${JSON.stringify(validated.config, null, 2)}\n`;
      this.fsImpl.writeFileSync(tempPath, payload, 'utf8');
      if (this.fsImpl.existsSync(this.paths.configFilePath)) {
        this.fsImpl.copyFileSync(this.paths.configFilePath, this.paths.configBackupPath);
      }
      this.fsImpl.renameSync(tempPath, this.paths.configFilePath);
      this.current = validated.config;
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return { ok: false, message: `Failed to save configuration: ${message}` };
    }
  }

  restoreDefaults(): DesktopConfigV1 {
    this.current = { ...this.defaults };
    void this.save(this.current);
    return this.getConfig();
  }

  private recoverFromCorruption(reason: string): ConfigLoadResult {
    if (this.fsImpl.existsSync(this.paths.configBackupPath)) {
      try {
        const raw = this.fsImpl.readFileSync(this.paths.configBackupPath, 'utf8');
        const parsed = JSON.parse(raw) as unknown;
        const validated = validateDesktopConfig(parsed);
        if (validated.ok && validated.config) {
          this.fsImpl.writeFileSync(
            this.paths.configFilePath,
            `${JSON.stringify(validated.config, null, 2)}\n`,
            'utf8',
          );
          return {
            status: 'recovered',
            config: validated.config,
            message: `Recovered configuration from backup after corruption: ${reason}`,
          };
        }
      } catch {
        // Fall through to defaults
      }
    }

    const corruptPath = `${this.paths.configFilePath}.corrupt-${Date.now()}.json`;
    try {
      if (this.fsImpl.existsSync(this.paths.configFilePath)) {
        this.fsImpl.copyFileSync(this.paths.configFilePath, corruptPath);
      }
    } catch {
      // Best effort only
    }

    this.fsImpl.writeFileSync(
      this.paths.configFilePath,
      `${JSON.stringify(this.defaults, null, 2)}\n`,
      'utf8',
    );

    return {
      status: 'corrupt',
      config: { ...this.defaults },
      message: `Configuration was corrupt (${reason}). Defaults restored.`,
    };
  }

  private ensureDirectories(): void {
    this.fsImpl.mkdirSync(this.paths.userDataDir, { recursive: true });
    this.fsImpl.mkdirSync(this.paths.logsDir, { recursive: true });
    this.fsImpl.mkdirSync(path.dirname(this.paths.configFilePath), { recursive: true });
    this.fsImpl.mkdirSync(this.paths.diagnosticsExportDir, { recursive: true });
  }
}
