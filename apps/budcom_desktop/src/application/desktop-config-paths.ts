import path from 'node:path';

export interface DesktopConfigPaths {
  readonly userDataDir: string;
  readonly configFilePath: string;
  readonly configBackupPath: string;
  readonly logsDir: string;
  readonly diagnosticsExportDir: string;
}

export function resolveDesktopConfigPaths(userDataDir: string): DesktopConfigPaths {
  return {
    userDataDir,
    configFilePath: path.join(userDataDir, 'desktop-config.json'),
    configBackupPath: path.join(userDataDir, 'desktop-config.backup.json'),
    logsDir: path.join(userDataDir, 'logs'),
    diagnosticsExportDir: path.join(userDataDir, 'diagnostics-exports'),
  };
}
