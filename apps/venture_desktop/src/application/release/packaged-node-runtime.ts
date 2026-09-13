import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as path from 'node:path';

import {
  isPathContainedInRoot,
  type PathContainmentFs,
  rejectPathWithNullBytes,
  toPlatformComparablePath,
} from '../path-containment.js';

export const PACKAGED_NODE_RUNTIME_MANIFEST_SCHEMA_VERSION = 1;
export const PACKAGED_NODE_RUNTIME_VERSION = '22.16.0';
export const PACKAGED_NODE_RUNTIME_ARCHITECTURE = 'x64';
export const PACKAGED_NODE_RUNTIME_PLATFORM = 'win32';
export const APPROVED_NODE_EXECUTABLE_RELATIVE_PATH = 'node/node.exe';

export type PackagedRuntimeIntegrityCategory =
  | 'missing_manifest'
  | 'malformed_manifest'
  | 'unsupported_schema'
  | 'unsupported_version'
  | 'unsupported_architecture'
  | 'unsupported_platform'
  | 'invalid_sha_format'
  | 'invalid_executable_path'
  | 'path_escape'
  | 'missing_executable'
  | 'unreadable_executable'
  | 'hash_mismatch'
  | 'sqlite_unsupported';

export class PackagedNodeRuntimeIntegrityError extends Error {
  readonly category: PackagedRuntimeIntegrityCategory;

  constructor(category: PackagedRuntimeIntegrityCategory, message: string) {
    super(message);
    this.name = 'PackagedNodeRuntimeIntegrityError';
    this.category = category;
  }
}

/** @deprecated Use PackagedNodeRuntimeIntegrityError */
export class PackagedNodeRuntimeError extends PackagedNodeRuntimeIntegrityError {
  constructor(message: string, category: PackagedRuntimeIntegrityCategory = 'malformed_manifest') {
    super(category, message);
    this.name = 'PackagedNodeRuntimeError';
  }
}

export interface PackagedNodeRuntimeManifest {
  readonly manifestSchemaVersion: number;
  readonly runtime: 'node';
  readonly version: string;
  readonly platform: string;
  readonly architecture: string;
  readonly license: string;
  readonly sourceUrl: string;
  readonly archiveSha256: string;
  readonly nodeExecutableSha256: string;
  readonly nodeExecutableRelativePath: string;
  readonly sqliteCapable: boolean;
  readonly preparedAt: string;
}

export interface PackagedNodeRuntimeVerificationResult {
  readonly nodeExecutable: string;
  readonly manifest: PackagedNodeRuntimeManifest;
  readonly nodeRoot: string;
}

export interface PackagedNodeRuntimeVerifyOptions {
  readonly validateSqlite?: boolean;
  readonly fsImpl?: Pick<typeof fs, 'existsSync' | 'readFileSync' | 'accessSync' | 'lstatSync' | 'realpathSync' | 'constants'>;
  readonly spawnSyncImpl?: typeof spawnSync;
  readonly forceReverify?: boolean;
}

type FsLike = NonNullable<PackagedNodeRuntimeVerifyOptions['fsImpl']>;

const defaultFs: FsLike = fs;

let cachedVerification: {
  readonly resourcesPath: string;
  readonly nodeExecutable: string;
  readonly statKey: string;
  readonly result: PackagedNodeRuntimeVerificationResult;
} | null = null;

function integrityError(
  category: PackagedRuntimeIntegrityCategory,
  message: string,
): PackagedNodeRuntimeIntegrityError {
  return new PackagedNodeRuntimeIntegrityError(category, message);
}

function normalizeRelativeManifestPath(value: string): string {
  return value.replace(/\\/g, '/').replace(/^\/+/, '');
}

function normalizeSha256(value: string): string {
  return value.trim().toLowerCase();
}

function isValidSha256(value: string): boolean {
  return /^[0-9a-f]{64}$/.test(normalizeSha256(value));
}

export function sha256File(
  filePath: string,
  fsImpl: Pick<typeof fs, 'readFileSync'> = fs,
): string {
  return createHash('sha256').update(fsImpl.readFileSync(filePath)).digest('hex');
}

export function resolvePackagedNodeRuntimeManifestPath(resourcesPath: string): string {
  return path.join(resourcesPath, 'node', 'node-runtime.manifest.json');
}

export function resolvePackagedNodeRuntimePath(resourcesPath: string): string {
  return path.join(resourcesPath, 'node', 'node.exe');
}

function readManifestFile(manifestPath: string, fsImpl: FsLike): unknown {
  if (!fsImpl.existsSync(manifestPath)) {
    throw integrityError('missing_manifest', 'Packaged Node runtime manifest is missing.');
  }
  try {
    return JSON.parse(fsImpl.readFileSync(manifestPath, 'utf8')) as unknown;
  } catch {
    throw integrityError('malformed_manifest', 'Packaged Node runtime manifest is malformed JSON.');
  }
}

export function parsePackagedNodeRuntimeManifest(raw: unknown): PackagedNodeRuntimeManifest {
  if (!raw || typeof raw !== 'object') {
    throw integrityError('malformed_manifest', 'Packaged Node runtime manifest must be an object.');
  }
  const record = raw as Record<string, unknown>;
  const schemaVersion = record.manifestSchemaVersion ?? record.schemaVersion;
  if (schemaVersion !== PACKAGED_NODE_RUNTIME_MANIFEST_SCHEMA_VERSION) {
    throw integrityError('unsupported_schema', 'Packaged Node runtime manifest schema is unsupported.');
  }
  if (record.runtime !== 'node') {
    throw integrityError('malformed_manifest', 'Packaged Node runtime manifest runtime must be node.');
  }
  for (const key of [
    'version',
    'platform',
    'architecture',
    'license',
    'sourceUrl',
    'archiveSha256',
    'nodeExecutableSha256',
    'nodeExecutableRelativePath',
    'preparedAt',
  ] as const) {
    if (typeof record[key] !== 'string' || record[key].trim().length === 0) {
      throw integrityError('malformed_manifest', `Packaged Node runtime manifest field "${key}" is invalid.`);
    }
  }
  if (typeof record.sqliteCapable !== 'boolean') {
    throw integrityError('malformed_manifest', 'Packaged Node runtime manifest sqliteCapable must be boolean.');
  }
  return record as unknown as PackagedNodeRuntimeManifest;
}

function validateManifestSemantics(manifest: PackagedNodeRuntimeManifest): void {
  if (manifest.version !== PACKAGED_NODE_RUNTIME_VERSION) {
    throw integrityError('unsupported_version', 'Packaged Node runtime version is unsupported.');
  }
  if (manifest.architecture !== PACKAGED_NODE_RUNTIME_ARCHITECTURE) {
    throw integrityError('unsupported_architecture', 'Packaged Node runtime architecture is unsupported.');
  }
  if (manifest.platform !== PACKAGED_NODE_RUNTIME_PLATFORM) {
    throw integrityError('unsupported_platform', 'Packaged Node runtime platform is unsupported.');
  }
  if (!isValidSha256(manifest.nodeExecutableSha256)) {
    throw integrityError('invalid_sha_format', 'Packaged Node runtime manifest SHA-256 is invalid.');
  }
  const relativePath = normalizeRelativeManifestPath(manifest.nodeExecutableRelativePath);
  if (relativePath !== APPROVED_NODE_EXECUTABLE_RELATIVE_PATH) {
    throw integrityError('invalid_executable_path', 'Packaged Node runtime executable path is not approved.');
  }
  if (path.isAbsolute(relativePath) || relativePath.includes('..')) {
    throw integrityError('invalid_executable_path', 'Packaged Node runtime executable path must stay relative.');
  }
}

function assertNoSymlinksInExistingSegments(
  rootRealPath: string,
  targetPath: string,
  fsImpl: PathContainmentFs,
): void {
  const relative = path.relative(rootRealPath, targetPath);
  if (relative === '' || relative === '.') {
    return;
  }
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    return;
  }
  const segments = relative.split(path.sep).filter(Boolean);
  let current = rootRealPath;
  for (const segment of segments) {
    current = path.join(current, segment);
    if (!fsImpl.existsSync(current)) {
      break;
    }
    if (fsImpl.lstatSync(current).isSymbolicLink()) {
      throw integrityError('path_escape', 'Packaged Node runtime executable traverses a symbolic link or junction.');
    }
  }
}

function assertExecutablePathContained(
  resourcesPath: string,
  executablePath: string,
  fsImpl: FsLike,
): string {
  rejectPathWithNullBytes(resourcesPath);
  rejectPathWithNullBytes(executablePath);
  const nodeRoot = path.resolve(resourcesPath, 'node');
  const resolvedExecutable = path.resolve(executablePath);
  if (!isPathContainedInRoot(resolvedExecutable, nodeRoot)) {
    throw integrityError('path_escape', 'Packaged Node runtime executable resolves outside the node resource root.');
  }
  let nodeRootReal: string;
  try {
    nodeRootReal = fsImpl.realpathSync(nodeRoot);
  } catch {
    throw integrityError('path_escape', 'Packaged Node runtime root could not be verified safely.');
  }
  if (!isPathContainedInRoot(resolvedExecutable, nodeRootReal)) {
    throw integrityError('path_escape', 'Packaged Node runtime executable resolves outside the verified node root.');
  }
  assertNoSymlinksInExistingSegments(nodeRootReal, resolvedExecutable, fsImpl);
  try {
    const verified = fsImpl.existsSync(resolvedExecutable)
      ? fsImpl.realpathSync(resolvedExecutable)
      : resolvedExecutable;
    if (!isPathContainedInRoot(verified, nodeRootReal)) {
      throw integrityError('path_escape', 'Packaged Node runtime executable resolves outside the verified node root.');
    }
  } catch (error) {
    if (error instanceof PackagedNodeRuntimeIntegrityError) {
      throw error;
    }
    throw integrityError('path_escape', 'Packaged Node runtime executable could not be verified safely.');
  }
  const comparable = toPlatformComparablePath(resolvedExecutable);
  if (!comparable.endsWith(`${path.sep}node.exe`) && !comparable.endsWith('/node.exe')) {
    throw integrityError('invalid_executable_path', 'Packaged Node runtime executable name is not approved.');
  }
  return resolvedExecutable;
}

function assertExecutableReadable(executablePath: string, fsImpl: FsLike): void {
  if (!fsImpl.existsSync(executablePath)) {
    throw integrityError('missing_executable', 'Packaged Node runtime executable is missing.');
  }
  try {
    fsImpl.accessSync(executablePath, fsImpl.constants.R_OK);
  } catch {
    throw integrityError('unreadable_executable', 'Packaged Node runtime executable is not readable.');
  }
}

function buildStatKey(executablePath: string, fsImpl: FsLike): string {
  const stats = fsImpl.existsSync(executablePath) ? fsImpl.lstatSync(executablePath) : null;
  if (!stats) {
    return 'missing';
  }
  return `${stats.size}:${stats.mtimeMs}`;
}

export function verifyPackagedNodeRuntimeIntegrity(
  resourcesPath: string,
  options: PackagedNodeRuntimeVerifyOptions = {},
): PackagedNodeRuntimeVerificationResult {
  const fsImpl = options.fsImpl ?? defaultFs;
  const validateSqlite = options.validateSqlite ?? true;
  const resolvedResources = path.resolve(resourcesPath);
  const manifestPath = resolvePackagedNodeRuntimeManifestPath(resolvedResources);
  const manifest = parsePackagedNodeRuntimeManifest(readManifestFile(manifestPath, fsImpl));
  validateManifestSemantics(manifest);

  const nodeExecutable = assertExecutablePathContained(
    resolvedResources,
    resolvePackagedNodeRuntimePath(resolvedResources),
    fsImpl,
  );
  assertExecutableReadable(nodeExecutable, fsImpl);

  const statKey = buildStatKey(nodeExecutable, fsImpl);
  if (
    !options.forceReverify
    && cachedVerification
    && cachedVerification.resourcesPath === resolvedResources
    && cachedVerification.nodeExecutable === nodeExecutable
    && cachedVerification.statKey === statKey
  ) {
    return cachedVerification.result;
  }

  const actualHash = sha256File(nodeExecutable, fsImpl);
  if (normalizeSha256(actualHash) !== normalizeSha256(manifest.nodeExecutableSha256)) {
    throw integrityError('hash_mismatch', 'Packaged Node runtime executable hash does not match manifest.');
  }

  if (validateSqlite && !nodeSupportsBuiltinSqlite(nodeExecutable, options.spawnSyncImpl)) {
    throw integrityError('sqlite_unsupported', 'Packaged Node runtime does not support node:sqlite.');
  }

  const result: PackagedNodeRuntimeVerificationResult = {
    nodeExecutable,
    manifest,
    nodeRoot: path.join(resolvedResources, 'node'),
  };
  cachedVerification = {
    resourcesPath: resolvedResources,
    nodeExecutable,
    statKey,
    result,
  };
  return result;
}

export function clearPackagedNodeRuntimeVerificationCache(): void {
  cachedVerification = null;
}

export function nodeSupportsBuiltinSqlite(
  nodeExecutable: string,
  spawnSyncImpl: typeof spawnSync = spawnSync,
): boolean {
  const result = spawnSyncImpl(nodeExecutable, ['-e', "require('node:sqlite')"], {
    encoding: 'utf8',
    env: { ...process.env, NODE_OPTIONS: '' },
  });
  return result.status === 0;
}

export function resolvePackagedConnectorNodeRuntime(
  resourcesPath: string,
  validateSqlite = true,
): string {
  return verifyPackagedNodeRuntimeIntegrity(resourcesPath, { validateSqlite }).nodeExecutable;
}

export function tryResolvePackagedConnectorNodeRuntime(resourcesPath?: string): string | null {
  if (!resourcesPath) {
    return null;
  }
  try {
    return resolvePackagedConnectorNodeRuntime(resourcesPath, true);
  } catch {
    return null;
  }
}

export function tryResolvePackagedConnectorNodeRuntimeWithIntegrity(resourcesPath?: string): {
  readonly executable: string | null;
  readonly integrityCategory: PackagedRuntimeIntegrityCategory | null;
} {
  if (!resourcesPath) {
    return { executable: null, integrityCategory: 'missing_executable' };
  }
  try {
    return {
      executable: resolvePackagedConnectorNodeRuntime(resourcesPath, true),
      integrityCategory: null,
    };
  } catch (error) {
    if (error instanceof PackagedNodeRuntimeIntegrityError) {
      return { executable: null, integrityCategory: error.category };
    }
    return { executable: null, integrityCategory: 'malformed_manifest' };
  }
}

/** @deprecated Use verifyPackagedNodeRuntimeIntegrity */
export function assertPackagedNodeRuntimeLayout(resourcesPath: string): {
  nodeExecutable: string;
  manifest: PackagedNodeRuntimeManifest;
} {
  const verified = verifyPackagedNodeRuntimeIntegrity(resourcesPath, { validateSqlite: false });
  return { nodeExecutable: verified.nodeExecutable, manifest: verified.manifest };
}

/** @deprecated Use parsePackagedNodeRuntimeManifest */
export function readPackagedNodeRuntimeManifest(resourcesPath: string): PackagedNodeRuntimeManifest {
  const manifestPath = resolvePackagedNodeRuntimeManifestPath(resourcesPath);
  return parsePackagedNodeRuntimeManifest(readManifestFile(manifestPath, fs));
}
