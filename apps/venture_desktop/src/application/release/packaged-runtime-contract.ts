import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';

import { PRODUCTION_DEFAULTS } from '../desktop-config-defaults.js';

export class PackagedRuntimeContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PackagedRuntimeContractError';
  }
}

export interface PackagedRuntimeLayout {
  readonly desktopMainEntry: string;
  readonly desktopApplicationDir: string;
  readonly connectorEntryScript: string;
  readonly connectorPackageJson: string;
  readonly connectorResourceRoot: string;
}

const TOP_LEVEL_ESM_PATTERN = /(?:^|\n)\s*(?:import\s|export\s)/;

export function containsTopLevelEsmSyntax(source: string): boolean {
  return TOP_LEVEL_ESM_PATTERN.test(source);
}

export function assertCommonJsRuntimeSource(label: string, source: string): void {
  if (containsTopLevelEsmSyntax(source)) {
    throw new PackagedRuntimeContractError(`${label} contains top-level ESM import/export syntax`);
  }
  if (!/\brequire\s*\(/.test(source) && !/(?:^|\n)\s*(?:'use strict'|"use strict")/.test(source)) {
    throw new PackagedRuntimeContractError(`${label} is not recognizable CommonJS output`);
  }
}

export function assertEsmRuntimeSource(label: string, source: string): void {
  if (!containsTopLevelEsmSyntax(source)) {
    throw new PackagedRuntimeContractError(`${label} must contain top-level ESM import/export syntax`);
  }
}

export function assertPathWithinPackageBoundary(candidatePath: string, packageRoot: string): void {
  const relative = path.relative(path.resolve(packageRoot), path.resolve(candidatePath));
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new PackagedRuntimeContractError('Connector entry script is outside connector package boundary');
  }
}

export function readConnectorPackageType(packageJsonPath: string): string {
  if (!fs.existsSync(packageJsonPath)) {
    throw new PackagedRuntimeContractError('Packaged connector package.json is missing');
  }
  const parsed = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8')) as { type?: string };
  if (parsed.type !== 'module') {
    throw new PackagedRuntimeContractError('Packaged connector package.json must declare type=module');
  }
  return parsed.type;
}

export function resolveConnectorPackageRoot(entryScript: string): string {
  const parentDir = path.dirname(entryScript);
  if (path.basename(parentDir) === 'dist') {
    return path.dirname(parentDir);
  }
  return parentDir;
}

export function runNodeSyntaxCheck(filePath: string, inputType?: 'module' | 'commonjs'): void {
  if (inputType === 'module') {
    const packageRoot = path.resolve(resolveConnectorPackageRoot(filePath));
    const packageJsonPath = path.join(packageRoot, 'package.json');
    if (!fs.existsSync(packageJsonPath)) {
      throw new PackagedRuntimeContractError(`Connector package.json missing for ESM syntax check: ${packageJsonPath}`);
    }
    const resolvedFilePath = path.resolve(filePath);
    const result = spawnSync(process.execPath, ['--check', resolvedFilePath], {
      encoding: 'utf8',
      cwd: packageRoot,
      env: { ...process.env, NODE_OPTIONS: '' },
    });
    if (result.status !== 0) {
      throw new PackagedRuntimeContractError(
        `Node ESM syntax check failed for ${path.basename(filePath)}: ${result.stderr || result.stdout}`,
      );
    }
    return;
  }
  const args = ['--check', filePath];
  const result = spawnSync(process.execPath, args, { encoding: 'utf8' });
  if (result.status !== 0) {
    throw new PackagedRuntimeContractError(
      `Node syntax check failed for ${path.basename(filePath)}: ${result.stderr || result.stdout}`,
    );
  }
}

export function assertDiagnosticAllowlistCommonJsLoad(applicationDir: string): void {
  const modulePath = path.join(applicationDir, 'diagnostic-allowlist.js');
  if (!fs.existsSync(modulePath)) {
    throw new PackagedRuntimeContractError('diagnostic-allowlist.js is missing from packaged application modules');
  }
  const requireFn = createRequire(__filename);
  const loaded = requireFn(modulePath) as {
    buildSafeDiagnosticBundle: (input: Record<string, unknown>) => { correlationId: string };
    buildSafeDiagnosticConfiguration: (input: Record<string, unknown>) => Record<string, unknown>;
  };
  if (typeof loaded.buildSafeDiagnosticBundle !== 'function') {
    throw new PackagedRuntimeContractError('diagnostic-allowlist.js failed CommonJS load');
  }
  const bundle = loaded.buildSafeDiagnosticBundle({
    generatedAt: '2026-01-01T00:00:00.000Z',
    desktopVersion: '0.4.3',
    connectorVersion: '0.3.1',
    electronVersion: 'test-electron',
    nodeVersion: process.versions.node,
    platform: process.platform,
    osRelease: 'test',
    architecture: 'x64',
    uptimeSeconds: 10,
    connectorBaseUrl: 'http://127.0.0.1:8080',
    connectorBindHost: '127.0.0.1',
    connectorNetworkExposure: 'loopback',
    connectorNetworkExposureWarning: null,
    connectorProcessState: 'Connected',
    connectorOwnership: 'desktop-managed',
    connectorPid: 1,
    healthStatus: 'ok',
    healthReachable: true,
    lastSuccessfulHealthCheck: '2026-01-01T00:00:00.000Z',
    tallyReachable: false,
    sessionStatus: 'NO_COMPANY',
    selectedCompanyPresent: false,
    configuration: {
      effective: PRODUCTION_DEFAULTS,
      sources: {},
      status: 'loaded',
    },
    environment: {},
    recentLifecycleEvents: [],
    recentErrors: [],
    logFilePath: 'venture-desktop.log',
    fileLoggingAvailable: true,
  });
  if (!/^[0-9a-f-]{36}$/i.test(bundle.correlationId)) {
    throw new PackagedRuntimeContractError('diagnostic allowlist randomUUID path failed');
  }
}

export function resolveDefaultPackagedRuntimeLayout(repoRoot: string): PackagedRuntimeLayout {
  const desktopDist = path.join(repoRoot, 'apps/venture_desktop/dist');
  const connectorDist = path.join(repoRoot, 'connector/venture_connector/dist');
  return {
    desktopMainEntry: path.join(desktopDist, 'main/main.js'),
    desktopApplicationDir: path.join(desktopDist, 'application'),
    connectorEntryScript: path.join(connectorDist, 'main.js'),
    connectorPackageJson: path.join(repoRoot, 'connector/venture_connector/package.json'),
    connectorResourceRoot: path.join(repoRoot, 'connector/venture_connector'),
  };
}

export function assertPackagedRuntimeContract(layout: PackagedRuntimeLayout): void {
  const desktopMainSource = fs.readFileSync(layout.desktopMainEntry, 'utf8');
  assertCommonJsRuntimeSource('desktop main entry', desktopMainSource);
  runNodeSyntaxCheck(layout.desktopMainEntry);

  for (const fileName of fs.readdirSync(layout.desktopApplicationDir)) {
    if (!fileName.endsWith('.js') || fileName.endsWith('.test.js')) {
      continue;
    }
    const source = fs.readFileSync(path.join(layout.desktopApplicationDir, fileName), 'utf8');
    assertCommonJsRuntimeSource(`desktop application module ${fileName}`, source);
  }

  const connectorSource = fs.readFileSync(layout.connectorEntryScript, 'utf8');
  assertEsmRuntimeSource('connector dist/main.js', connectorSource);
  readConnectorPackageType(layout.connectorPackageJson);
  assertPathWithinPackageBoundary(layout.connectorEntryScript, layout.connectorResourceRoot);
  runNodeSyntaxCheck(layout.connectorEntryScript, 'module');
  assertDiagnosticAllowlistCommonJsLoad(layout.desktopApplicationDir);
}

export function assertPackagedRuntimeContractFailsWithoutConnectorPackage(layout: PackagedRuntimeLayout): void {
  const tempRoot = fs.mkdtempSync(path.join(path.dirname(layout.connectorResourceRoot), 'venture-runtime-'));
  const tempConnectorRoot = path.join(tempRoot, 'connector');
  fs.mkdirSync(path.join(tempConnectorRoot, 'dist'), { recursive: true });
  fs.copyFileSync(layout.connectorEntryScript, path.join(tempConnectorRoot, 'dist', 'main.js'));
  try {
    assertPackagedRuntimeContract({
      ...layout,
      connectorEntryScript: path.join(tempConnectorRoot, 'dist', 'main.js'),
      connectorPackageJson: path.join(tempConnectorRoot, 'package.json'),
      connectorResourceRoot: tempConnectorRoot,
    });
    throw new PackagedRuntimeContractError('Expected missing connector package.json to fail validation');
  } catch (error) {
    if (error instanceof PackagedRuntimeContractError && error.message.includes('package.json is missing')) {
      return;
    }
    throw error;
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
}

export function assertPackagedRuntimeContractFailsForEsmOutsidePackage(layout: PackagedRuntimeLayout): void {
  const tempRoot = fs.mkdtempSync(path.join(path.dirname(layout.connectorResourceRoot), 'venture-runtime-'));
  const orphanScript = path.join(tempRoot, 'orphan-main.js');
  fs.writeFileSync(orphanScript, fs.readFileSync(layout.connectorEntryScript, 'utf8'), 'utf8');
  try {
    assertPathWithinPackageBoundary(orphanScript, layout.connectorResourceRoot);
    throw new PackagedRuntimeContractError('Expected orphan ESM script outside package boundary to fail');
  } catch (error) {
    if (error instanceof PackagedRuntimeContractError && error.message.includes('outside connector package boundary')) {
      return;
    }
    throw error;
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
}
