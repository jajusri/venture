import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

import {
  nodeSupportsBuiltinSqlite,
  resolvePackagedConnectorNodeRuntime,
} from './packaged-node-runtime.js';

export class PackagedConnectorDependencyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PackagedConnectorDependencyError';
  }
}

const DEV_DEPENDENCY_NAMES = new Set([
  '@eslint/js',
  '@types/express',
  '@types/node',
  '@types/supertest',
  'eslint',
  'eslint-config-prettier',
  'prettier',
  'supertest',
  'tsx',
  'typescript',
  'typescript-eslint',
  'vitest',
]);

const FORBIDDEN_RELATIVE_PATTERNS = [
  /\.\.\/\.\.\/\.\./,
  /connector\/budcom_connector\/src\//i,
  /Projects[\\/]+Budcom/i,
  /\.env/i,
  /\.(db|sqlite|sqlite3|log)$/i,
  /(^|\/)test\//i,
  /(^|\/)fixtures?\//i,
];

export function resolvePackagedConnectorRoot(unpackedRoot: string): string {
  return path.resolve(unpackedRoot, 'resources', 'connector');
}

export function countPackagedProductionDependencies(connectorRoot: string): {
  topLevelCount: number;
  names: string[];
} {
  const modulesDir = path.join(connectorRoot, 'node_modules');
  if (!fs.existsSync(modulesDir)) {
    throw new PackagedConnectorDependencyError('Packaged connector node_modules is missing');
  }
  const names = fs.readdirSync(modulesDir, { withFileTypes: true })
    .flatMap((entry) => {
      if (!entry.isDirectory() || entry.name === '.bin') {
        return [];
      }
      if (entry.name.startsWith('@')) {
        const scopeDir = path.join(modulesDir, entry.name);
        return fs.readdirSync(scopeDir, { withFileTypes: true })
          .filter((child) => child.isDirectory())
          .map((child) => `${entry.name}/${child.name}`);
      }
      return [entry.name];
    });
  return { topLevelCount: names.length, names };
}

export function assertNoDevDependenciesPresent(connectorRoot: string): void {
  const modulesDir = path.join(connectorRoot, 'node_modules');
  for (const devName of DEV_DEPENDENCY_NAMES) {
    const candidate = path.join(modulesDir, ...devName.split('/'));
    if (fs.existsSync(candidate)) {
      throw new PackagedConnectorDependencyError(`DevDependency present in packaged tree: ${devName}`);
    }
  }
}

export function assertPackagedConnectorLayout(connectorRoot: string): void {
  const required = [
    path.join(connectorRoot, 'package.json'),
    path.join(connectorRoot, 'dist', 'main.js'),
    path.join(connectorRoot, 'node_modules', 'express'),
    path.join(connectorRoot, 'VERSION.txt'),
  ];
  for (const filePath of required) {
    if (!fs.existsSync(filePath)) {
      throw new PackagedConnectorDependencyError(`Missing packaged connector artifact: ${filePath}`);
    }
  }
  const packageJson = JSON.parse(fs.readFileSync(path.join(connectorRoot, 'package.json'), 'utf8')) as { type?: string };
  if (packageJson.type !== 'module') {
    throw new PackagedConnectorDependencyError('Packaged connector package.json must declare type=module');
  }
}

export function assertNoForbiddenPackagedPaths(connectorRoot: string): void {
  const files: string[] = [];
  function walk(current: string, relative = ''): void {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const rel = relative ? `${relative}/${entry.name}` : entry.name;
      const abs = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(abs, rel);
        continue;
      }
      files.push(rel.replace(/\\/g, '/'));
    }
  }
  walk(connectorRoot);
  for (const rel of files) {
    const inNodeModules = /^node_modules\//i.test(rel);
    for (const pattern of FORBIDDEN_RELATIVE_PATTERNS) {
      if (inNodeModules && (pattern.source.includes('test') || pattern.source.includes('fixtures'))) {
        continue;
      }
      if (pattern.test(rel)) {
        throw new PackagedConnectorDependencyError(`Forbidden packaged connector path: ${rel}`);
      }
    }
    if (!inNodeModules && /\.ts$/i.test(rel) && !rel.endsWith('.d.ts')) {
      throw new PackagedConnectorDependencyError(`TypeScript source must not ship in packaged connector: ${rel}`);
    }
  }
}

export function assertExpressResolves(connectorRoot: string, nodeExecutable = process.execPath): void {
  const resolvedRoot = path.resolve(connectorRoot);
  const result = spawnSync(nodeExecutable, [
    '--input-type=module',
    '-e',
    "import express from 'express'; if (typeof express !== 'function') process.exit(2);",
  ], {
    cwd: resolvedRoot,
    encoding: 'utf8',
    env: { ...process.env, NODE_OPTIONS: '' },
  });
  if (result.status !== 0) {
    throw new PackagedConnectorDependencyError(
      `express failed to resolve in packaged connector root: ${result.stderr || result.stdout}`,
    );
  }
}

export function assertConnectorEntryImportsResolve(connectorRoot: string, nodeExecutable = process.execPath): void {
  const resolvedRoot = path.resolve(connectorRoot);
  const entryScript = path.join(resolvedRoot, 'dist', 'main.js');
  const result = spawnSync(nodeExecutable, ['--check', entryScript], {
    cwd: resolvedRoot,
    encoding: 'utf8',
    env: { ...process.env, NODE_OPTIONS: '' },
  });
  if (result.status !== 0) {
    throw new PackagedConnectorDependencyError(
      `Packaged connector entry syntax/import check failed: ${result.stderr || result.stdout || `exit ${result.status}`}`,
    );
  }
}

export function inspectPackagedConnectorDependencies(unpackedRoot: string): {
  connectorRoot: string;
  productionDependencyCount: number;
  dependencyNamesSample: string[];
  expressResolved: boolean;
  devDependenciesExcluded: boolean;
  typeModule: boolean;
  packagedNodeRuntimeVersion: string;
  packagedNodeExecutableSha256: string;
} {
  const connectorRoot = resolvePackagedConnectorRoot(unpackedRoot);
  assertPackagedConnectorLayout(connectorRoot);
  assertNoDevDependenciesPresent(connectorRoot);
  assertNoForbiddenPackagedPaths(connectorRoot);
  assertExpressResolves(connectorRoot);
  assertConnectorEntryImportsResolve(connectorRoot);
  const counts = countPackagedProductionDependencies(connectorRoot);
  const resourcesPath = path.join(path.resolve(unpackedRoot), 'resources');
  const hostExecutable = resolvePackagedConnectorNodeRuntime(resourcesPath, true);
  const manifest = JSON.parse(
    fs.readFileSync(path.join(resourcesPath, 'node', 'node-runtime.manifest.json'), 'utf8'),
  ) as { version: string; nodeExecutableSha256: string };
  if (!nodeSupportsBuiltinSqlite(hostExecutable)) {
    throw new PackagedConnectorDependencyError('Resolved connector host runtime does not support node:sqlite');
  }
  return {
    connectorRoot,
    productionDependencyCount: counts.topLevelCount,
    dependencyNamesSample: counts.names.slice(0, 20),
    expressResolved: true,
    devDependenciesExcluded: true,
    typeModule: true,
    packagedNodeRuntimeVersion: manifest.version,
    packagedNodeExecutableSha256: manifest.nodeExecutableSha256,
  };
}
