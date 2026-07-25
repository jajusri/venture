import fs from 'node:fs';
import path from 'node:path';

import type { ReleaseMode } from './release-mode.js';

export const BUILD_INFO_FILENAME = 'build-info.json';
export const CHECKSUM_ALGORITHM = 'sha256' as const;

export interface BudcomBuildInfo {
  readonly applicationVersion: string;
  readonly desktopVersion: string;
  readonly connectorVersion: string;
  readonly storageSchemaVersion: number;
  readonly gitCommit: string;
  readonly buildTimestamp: string;
  readonly releaseMode: ReleaseMode;
  readonly packagingTarget: string;
  readonly architecture: string;
  readonly nodeVersion: string;
  readonly electronVersion: string;
  readonly dirtyTree: boolean;
  readonly buildChannel: string;
  readonly artifactFilename?: string;
  readonly checksumAlgorithm: typeof CHECKSUM_ALGORITHM;
}

export const DEV_BUILD_INFO_FALLBACK: BudcomBuildInfo = {
  applicationVersion: '0.0.0-dev',
  desktopVersion: '0.0.0-dev',
  connectorVersion: '0.0.0-dev',
  storageSchemaVersion: 0,
  gitCommit: 'unknown',
  buildTimestamp: 'unknown',
  releaseMode: 'development',
  packagingTarget: 'development',
  architecture: process.arch,
  nodeVersion: process.version,
  electronVersion: 'unknown',
  dirtyTree: true,
  buildChannel: 'local-dev',
  checksumAlgorithm: CHECKSUM_ALGORITHM,
};

export interface LoadBuildInfoOptions {
  readonly searchPaths?: readonly string[];
  readonly fsImpl?: Pick<typeof fs, 'existsSync' | 'readFileSync'>;
  readonly fallback?: BudcomBuildInfo;
}

export function loadBuildInfo(options: LoadBuildInfoOptions = {}): BudcomBuildInfo {
  const fsImpl = options.fsImpl ?? fs;
  const fallback = options.fallback ?? DEV_BUILD_INFO_FALLBACK;
  const candidates = options.searchPaths ?? defaultBuildInfoSearchPaths();
  for (const candidate of candidates) {
    if (!fsImpl.existsSync(candidate)) {
      continue;
    }
    const parsed = parseBuildInfoJson(fsImpl.readFileSync(candidate, 'utf8'));
    if (parsed) {
      return parsed;
    }
  }
  return fallback;
}

export function defaultBuildInfoSearchPaths(baseDir = __dirname): string[] {
  return [
    path.join(baseDir, BUILD_INFO_FILENAME),
    path.join(baseDir, '..', BUILD_INFO_FILENAME),
    path.join(baseDir, '..', '..', BUILD_INFO_FILENAME),
    path.join(process.cwd(), BUILD_INFO_FILENAME),
  ];
}

export function parseBuildInfoJson(raw: string): BudcomBuildInfo | null {
  try {
    const value = JSON.parse(raw) as Partial<BudcomBuildInfo>;
    if (
      typeof value.desktopVersion !== 'string'
      || typeof value.connectorVersion !== 'string'
      || typeof value.releaseMode !== 'string'
    ) {
      return null;
    }
    return {
      applicationVersion: String(value.applicationVersion ?? value.desktopVersion),
      desktopVersion: value.desktopVersion,
      connectorVersion: value.connectorVersion,
      storageSchemaVersion: Number(value.storageSchemaVersion ?? 0),
      gitCommit: String(value.gitCommit ?? 'unknown'),
      buildTimestamp: String(value.buildTimestamp ?? 'unknown'),
      releaseMode: value.releaseMode as ReleaseMode,
      packagingTarget: String(value.packagingTarget ?? 'unknown'),
      architecture: String(value.architecture ?? process.arch),
      nodeVersion: String(value.nodeVersion ?? process.version),
      electronVersion: String(value.electronVersion ?? 'unknown'),
      dirtyTree: Boolean(value.dirtyTree),
      buildChannel: String(value.buildChannel ?? 'unknown'),
      artifactFilename: value.artifactFilename ? String(value.artifactFilename) : undefined,
      checksumAlgorithm: CHECKSUM_ALGORITHM,
    };
  } catch {
    return null;
  }
}

export function detectVersionMismatch(info: BudcomBuildInfo): string | null {
  if (info.desktopVersion !== info.applicationVersion && info.applicationVersion !== '0.0.0-dev') {
    return `applicationVersion (${info.applicationVersion}) differs from desktopVersion (${info.desktopVersion})`;
  }
  return null;
}

export function formatBuildInfoForDiagnostics(info: BudcomBuildInfo): Record<string, string | number | boolean> {
  return {
    applicationVersion: info.applicationVersion,
    desktopVersion: info.desktopVersion,
    connectorVersion: info.connectorVersion,
    storageSchemaVersion: info.storageSchemaVersion,
    gitCommit: info.gitCommit === 'unknown' ? 'unknown-local' : info.gitCommit.slice(0, 12),
    buildTimestamp: info.buildTimestamp,
    releaseMode: info.releaseMode,
    packagingTarget: info.packagingTarget,
    architecture: info.architecture,
    buildChannel: info.buildChannel,
    dirtyTree: info.dirtyTree,
    checksumAlgorithm: info.checksumAlgorithm,
  };
}
