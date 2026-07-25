import * as fs from 'node:fs';
import * as path from 'node:path';

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
  'BUDCOM_LOG_LEVEL',
  'NODE_ENV',
] as const;

export function buildConnectorChildEnvironment(
  baseEnv: NodeJS.ProcessEnv,
  overrides: Record<string, string> = {},
): Readonly<Record<string, string | undefined>> {
  const env: NodeJS.ProcessEnv = {};
  for (const key of CONNECTOR_CHILD_ENV_ALLOWLIST) {
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
