import * as fs from 'node:fs';
import * as path from 'node:path';

import {
  resolvePackagedConnectorNodeRuntime,
  tryResolvePackagedConnectorNodeRuntime,
} from './packaged-node-runtime.js';

export interface PackagedConnectorPaths {
  readonly connectorEntryScript: string;
  readonly connectorResourceRoot: string;
  readonly connectorVersionFile: string;
}

export interface ResolvePackagedConnectorPathsInput {
  readonly isPackaged: boolean;
  readonly resourcesPath?: string;
  readonly repoRoot?: string;
  readonly fsImpl?: Pick<typeof fs, 'existsSync'>;
}

export function resolvePackagedConnectorPaths(
  input: ResolvePackagedConnectorPathsInput,
): PackagedConnectorPaths {
  const fsImpl = input.fsImpl ?? fs;
  if (input.isPackaged) {
    if (!input.resourcesPath) {
      throw new Error('resourcesPath is required for packaged connector resolution.');
    }
    const resourceRoot = path.join(input.resourcesPath, 'connector');
    const connectorEntryScript = path.join(resourceRoot, 'dist', 'main.js');
    return {
      connectorEntryScript,
      connectorResourceRoot: resourceRoot,
      connectorVersionFile: path.join(resourceRoot, 'VERSION.txt'),
    };
  }
  const repoRoot = input.repoRoot ?? path.resolve(process.cwd(), '../..');
  const devScript = path.join(repoRoot, 'connector', 'budcom_connector', 'dist', 'main.js');
  const fromDistMain = path.resolve(__dirname, '../../../../connector/budcom_connector/dist/main.js');
  const connectorEntryScript = fsImpl.existsSync(fromDistMain) ? fromDistMain : devScript;
  return {
    connectorEntryScript,
    connectorResourceRoot: path.dirname(path.dirname(connectorEntryScript)),
    connectorVersionFile: path.join(path.dirname(path.dirname(connectorEntryScript)), 'VERSION.txt'),
  };
}

export const CONNECTOR_CHILD_ENV_ALLOWLIST = [
  'PATH',
  'SystemRoot',
  'TEMP',
  'TMP',
  'USERPROFILE',
  'APPDATA',
  'LOCALAPPDATA',
  'BUDCOM_RELEASE_MODE',
  'BUDCOM_DATABASE_PATH',
  'BUDCOM_CONNECTOR_PORT',
  'BUDCOM_CONNECTOR_HOST',
  'BUDCOM_STARTUP_CORRELATION_ID',
  'BUDCOM_LOG_LEVEL',
  'NODE_ENV',
  'ELECTRON_RUN_AS_NODE',
] as const;

export function shouldSpawnConnectorViaElectronNode(connectorExecutable: string): boolean {
  const base = path.basename(connectorExecutable).toLowerCase();
  if (base === 'node.exe' || base === 'node') {
    return false;
  }
  // Electron-as-Node embeds an older Node runtime without built-in node:sqlite.
  return false;
}

export interface ResolveConnectorHostExecutableInput {
  readonly isPackaged: boolean;
  readonly resourcesPath?: string;
  readonly overrideExecutable?: string;
  readonly validateSqlite?: boolean;
}

export function resolveConnectorHostExecutable(input: ResolveConnectorHostExecutableInput): string {
  if (input.overrideExecutable?.trim()) {
    return input.overrideExecutable;
  }

  if (input.isPackaged) {
    if (!input.resourcesPath) {
      throw new Error('resourcesPath is required for packaged connector host runtime resolution');
    }
    return resolvePackagedConnectorNodeRuntime(input.resourcesPath, input.validateSqlite ?? true);
  }

  const currentBase = path.basename(process.execPath).toLowerCase();
  if (currentBase === 'node.exe' || currentBase === 'node') {
    return process.execPath;
  }

  throw new Error('Development connector host runtime must be launched via Node or BUDCOM_CONNECTOR_EXECUTABLE');
}

export { nodeSupportsBuiltinSqlite, resolvePackagedConnectorNodeRuntime, tryResolvePackagedConnectorNodeRuntime } from './packaged-node-runtime.js';

export function buildConnectorChildEnvironment(
  baseEnv: NodeJS.ProcessEnv,
  overrides: Record<string, string> = {},
): Readonly<Record<string, string | undefined>> {
  const env: NodeJS.ProcessEnv = {};
  for (const key of CONNECTOR_CHILD_ENV_ALLOWLIST) {
    if (key === 'ELECTRON_RUN_AS_NODE' && !(key in overrides)) {
      continue;
    }
    const value = overrides[key] ?? baseEnv[key];
    if (value !== undefined) {
      env[key] = value;
    }
  }
  for (const [key, value] of Object.entries(overrides)) {
    if (!(CONNECTOR_CHILD_ENV_ALLOWLIST as readonly string[]).includes(key)) {
      continue;
    }
    env[key] = value;
  }
  return env;
}
