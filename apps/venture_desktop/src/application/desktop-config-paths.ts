import path from 'node:path';

export interface DesktopConfigPaths {
  readonly userDataDir: string;
  readonly configFilePath: string;
  readonly configTempPath: string;
  readonly configBackupPath: string;
  readonly logsDir: string;
  readonly diagnosticsExportDir: string;
}

export const DESKTOP_CONFIG_FILE_NAME = 'desktop-config.json';
export const DESKTOP_CONFIG_TEMP_FILE_NAME = 'desktop-config.json.tmp';
export const DESKTOP_CONFIG_BACKUP_FILE_NAME = 'desktop-config.backup.json';

export function resolveDesktopConfigPaths(userDataDir: string): DesktopConfigPaths {
  const configFilePath = path.join(userDataDir, DESKTOP_CONFIG_FILE_NAME);
  return {
    userDataDir,
    configFilePath,
    configTempPath: `${configFilePath}.tmp`,
    configBackupPath: path.join(userDataDir, DESKTOP_CONFIG_BACKUP_FILE_NAME),
    logsDir: path.join(userDataDir, 'logs'),
    diagnosticsExportDir: path.join(userDataDir, 'diagnostics-exports'),
  };
}

export function isAppOwnedDesktopConfigTempPath(tempPath: string, configFilePath: string): boolean {
  return tempPath === `${configFilePath}.tmp`
    && path.basename(tempPath) === DESKTOP_CONFIG_TEMP_FILE_NAME;
}
