import { app as electronApp, type App } from 'electron';
import os from 'node:os';
import path from 'node:path';

import { isPathContainedInRoot, rejectPathWithNullBytes } from '../path-containment.js';

export const INSTALLED_PROBE_MODE_ENV = 'BUDCOM_INSTALLED_PROBE_MODE';
export const USER_DATA_OVERRIDE_ENV = 'BUDCOM_USER_DATA_DIR';

export type UserDataOverrideRejectionCategory =
  | 'probe_mode_required'
  | 'relative_path_rejected'
  | 'blocked_system_path'
  | 'outside_probe_temp_root'
  | 'install_path_rejected'
  | 'resources_path_rejected';

export class UserDataOverrideRejectedError extends Error {
  readonly category: UserDataOverrideRejectionCategory;

  constructor(category: UserDataOverrideRejectionCategory, message: string) {
    super(message);
    this.name = 'UserDataOverrideRejectedError';
    this.category = category;
  }
}

export interface UserDataOverrideContext {
  readonly installRoot?: string | null;
  readonly resourcesPath?: string | null;
}

/**
 * Electron must never inherit ELECTRON_RUN_AS_NODE in the desktop shell process.
 * When set, the main process runs as plain Node and exits without creating a window.
 */
export function sanitizeDesktopProcessEnvironment(env: NodeJS.ProcessEnv = process.env): void {
  delete env.ELECTRON_RUN_AS_NODE;
}

export function isInstalledProbeMode(env: NodeJS.ProcessEnv = process.env): boolean {
  const marker = env[INSTALLED_PROBE_MODE_ENV]?.trim();
  return marker === '1' || marker?.toLowerCase() === 'true';
}

function blockedSystemRoots(): readonly string[] {
  const candidates = [
    process.env.SystemRoot,
    process.env.windir,
    process.env.ProgramFiles,
    process.env['ProgramFiles(x86)'],
    process.env.ProgramData,
    path.parse(process.cwd()).root,
  ];
  return [...new Set(candidates.filter((value): value is string => Boolean(value?.trim())).map((value) => path.resolve(value)))];
}

export function validateInstalledProbeUserDataDir(
  candidatePath: string,
  context: UserDataOverrideContext = {},
): string {
  rejectPathWithNullBytes(candidatePath);
  const resolved = path.resolve(candidatePath);
  if (!path.isAbsolute(resolved)) {
    throw new UserDataOverrideRejectedError('relative_path_rejected', 'Probe user-data override must be an absolute path.');
  }

  for (const blocked of blockedSystemRoots()) {
    if (isPathContainedInRoot(resolved, blocked) || isPathContainedInRoot(blocked, resolved)) {
      throw new UserDataOverrideRejectedError('blocked_system_path', 'Probe user-data override targets a blocked system path.');
    }
  }

  if (context.installRoot) {
    const installRoot = path.resolve(context.installRoot);
    if (isPathContainedInRoot(resolved, installRoot) || isPathContainedInRoot(installRoot, resolved)) {
      throw new UserDataOverrideRejectedError('install_path_rejected', 'Probe user-data override must not target install paths.');
    }
  }

  if (context.resourcesPath) {
    const resourcesPath = path.resolve(context.resourcesPath);
    if (isPathContainedInRoot(resolved, resourcesPath) || isPathContainedInRoot(resourcesPath, resolved)) {
      throw new UserDataOverrideRejectedError('resources_path_rejected', 'Probe user-data override must not target resource paths.');
    }
  }

  const tempRoot = path.resolve(os.tmpdir());
  if (!isPathContainedInRoot(resolved, tempRoot)) {
    throw new UserDataOverrideRejectedError('outside_probe_temp_root', 'Probe user-data override must remain under the OS temp directory.');
  }

  return resolved;
}

export function readUserDataDirOverride(
  env: NodeJS.ProcessEnv = process.env,
  argv: readonly string[] = process.argv,
  context: UserDataOverrideContext = {},
): string | null {
  const fromEnv = env[USER_DATA_OVERRIDE_ENV]?.trim();
  if (fromEnv) {
    if (!isInstalledProbeMode(env)) {
      return null;
    }
    return validateInstalledProbeUserDataDir(fromEnv, context);
  }

  if (!isInstalledProbeMode(env)) {
    return null;
  }

  for (const arg of argv) {
    if (arg.startsWith('--user-data-dir=')) {
      return validateInstalledProbeUserDataDir(arg.slice('--user-data-dir='.length), context);
    }
  }
  return null;
}

export function applyUserDataDirOverride(
  env: NodeJS.ProcessEnv = process.env,
  app: Pick<App, 'setPath'> = electronApp,
  argv: readonly string[] = process.argv,
  context: UserDataOverrideContext = {},
): string | null {
  try {
    const override = readUserDataDirOverride(env, argv, context);
    if (override) {
      app.setPath('userData', override);
    }
    return override;
  } catch (error) {
    if (error instanceof UserDataOverrideRejectedError) {
      return null;
    }
    throw error;
  }
}
