import * as fs from 'node:fs';
import * as path from 'node:path';

import {
  nodeSupportsBuiltinSqlite,
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
  'BUDCOM_TALLY_REQUEST_AUDIT_PATH',
  'BUDCOM_TRANSPORT_IDENTITY_DIR',
  'BUDCOM_PRIVATE_STORAGE_VAULT_ID',
  'BUDCOM_PRIVATE_STORAGE_MARKER_PATH',
  'BUDCOM_CONNECTOR_PORT',
  'BUDCOM_CONNECTOR_HOST',
  'BUDCOM_CONNECTOR_BIND_MODE',
  'BUDCOM_CONNECTOR_LAN_MODE_ACKNOWLEDGED',
  'BUDCOM_CONNECTOR_ID',
  'BUDCOM_CONNECTOR_NAME',
  'BUDCOM_STARTUP_CORRELATION_ID',
  'BUDCOM_SECURE_PAIRING_ENABLED',
  'BUDCOM_SECURE_TRANSPORT_ENABLED',
  'BUDCOM_SECURE_TRANSPORT_PORT',
  'BUDCOM_DESKTOP_CONTROL_TOKEN',
  'BUDCOM_LOG_LEVEL',
  'NODE_ENV',
  'ELECTRON_RUN_AS_NODE',
] as const;

/**
 * Electron's embedded runtime, even under `ELECTRON_RUN_AS_NODE=1`, does not satisfy the
 * Connector's `node:sqlite` requirement — so this must never report true. It exists as an
 * explicit, named decision point (not a silent omission) for anyone tempted to re-introduce
 * Electron-as-Node spawning: see `resolveConnectorHostExecutable` for what development mode
 * uses instead (a real, PATH-discovered Node executable).
 */
export function shouldSpawnConnectorViaElectronNode(_connectorExecutable: string): boolean {
  return false;
}

/**
 * Searches PATH for a `node`/`node.exe` executable. Never a hardcoded, developer- or
 * machine-specific absolute path: PATH is the same mechanism the developer's own shell used
 * to resolve `node`/`npm` to run `npm start` in the first place, so if Desktop is running in
 * dev mode at all, a compatible entry should already be discoverable here.
 */
export function findNodeExecutableOnPath(
  pathEnv: string | undefined,
  fsImpl: Pick<typeof fs, 'existsSync'> = fs,
): string | null {
  if (!pathEnv) {
    return null;
  }
  const candidateNames = process.platform === 'win32' ? ['node.exe'] : ['node'];
  for (const dir of pathEnv.split(path.delimiter)) {
    if (!dir) {
      continue;
    }
    for (const name of candidateNames) {
      const candidate = path.join(dir, name);
      if (fsImpl.existsSync(candidate)) {
        return candidate;
      }
    }
  }
  return null;
}

export interface ResolveConnectorHostExecutableInput {
  readonly isPackaged: boolean;
  readonly resourcesPath?: string;
  readonly overrideExecutable?: string;
  readonly validateSqlite?: boolean;
  /** Injectable for tests; defaults to process.execPath (real value in production). */
  readonly currentExecPath?: string;
  /** Injectable for tests; defaults to process.env (real value in production). */
  readonly env?: NodeJS.ProcessEnv;
  readonly fsImpl?: Pick<typeof fs, 'existsSync'>;
  /** Injectable for tests; defaults to the real `node:sqlite` spawn check. */
  readonly sqliteChecker?: (nodeExecutable: string) => boolean;
}

/**
 * Resolves the executable used to host the Connector child process.
 *
 * Packaged: the integrity-verified bundled Node runtime (unchanged).
 * Development: never Electron itself — `process.execPath` inside Electron's main process is
 * `electron.exe`, which (a) cannot run the Connector's `node:sqlite`-dependent code under
 * `ELECTRON_RUN_AS_NODE`, and (b) launched bare would silently start a second Electron GUI
 * instance instead of running the script. Development mode instead discovers a real Node
 * executable via PATH and validates it supports `node:sqlite` before use, failing with a
 * clear, actionable error otherwise — never silently falling back to Electron.
 */
export function resolveConnectorHostExecutable(input: ResolveConnectorHostExecutableInput): string {
  if (input.overrideExecutable?.trim()) {
    return input.overrideExecutable.trim();
  }

  if (input.isPackaged) {
    if (!input.resourcesPath) {
      throw new Error('resourcesPath is required for packaged connector host runtime resolution');
    }
    return resolvePackagedConnectorNodeRuntime(input.resourcesPath, input.validateSqlite ?? true);
  }

  const execPath = input.currentExecPath ?? process.execPath;
  const currentBase = path.basename(execPath).toLowerCase();
  if (currentBase === 'node.exe' || currentBase === 'node') {
    return execPath;
  }

  const fsImpl = input.fsImpl ?? fs;
  const env = input.env ?? process.env;
  const discovered = findNodeExecutableOnPath(env.PATH, fsImpl);
  if (!discovered) {
    throw new Error(
      'No compatible development Node runtime was found on PATH. Electron cannot host the Connector directly. ' +
        'Install Node.js 22+ so "node" resolves on PATH, or set BUDCOM_CONNECTOR_EXECUTABLE to a compatible ' +
        'node executable.',
    );
  }

  const shouldValidateSqlite = input.validateSqlite ?? true;
  const sqliteChecker = input.sqliteChecker ?? nodeSupportsBuiltinSqlite;
  if (shouldValidateSqlite && !sqliteChecker(discovered)) {
    throw new Error(
      `Development Node runtime at "${discovered}" does not support node:sqlite, which the Connector requires. ` +
        'Install Node.js 22+ and ensure it resolves first on PATH, or set BUDCOM_CONNECTOR_EXECUTABLE to a ' +
        'compatible node executable.',
    );
  }
  return discovered;
}

export interface DevelopmentConnectorRuntimeResolution {
  readonly executable: string | null;
  readonly error: string | null;
}

/**
 * Exception-safe wrapper around {@link resolveConnectorHostExecutable} for development mode,
 * mirroring {@link tryResolvePackagedConnectorNodeRuntimeWithIntegrity}'s shape so config
 * resolution never throws mid-startup — failures surface as a clear user-facing message via
 * `validateConnectorExecutable` instead of an uncaught exception in the Electron main process.
 */
export function tryResolveDevelopmentConnectorNodeExecutable(
  input: Omit<ResolveConnectorHostExecutableInput, 'isPackaged' | 'resourcesPath'> = {},
): DevelopmentConnectorRuntimeResolution {
  try {
    return { executable: resolveConnectorHostExecutable({ ...input, isPackaged: false }), error: null };
  } catch (error) {
    return {
      executable: null,
      error: error instanceof Error ? error.message : 'Unable to resolve a development connector runtime.',
    };
  }
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
