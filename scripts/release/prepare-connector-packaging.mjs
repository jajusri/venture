#!/usr/bin/env node
/**
 * Prepare production-only connector node_modules for desktop packaging.
 */
import { execSync, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const connectorRoot = path.join(repoRoot, 'connector/venture_connector');
const outputRoot = path.join(connectorRoot, '.packaged');
const outputModules = path.join(outputRoot, 'node_modules');

const DEV_DEPENDENCY_NAMES = [
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
];

function run(command, cwd, options = {}) {
  execSync(command, {
    cwd,
    stdio: options.quiet ? ['ignore', 'pipe', 'inherit'] : 'inherit',
    shell: true,
  });
}

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function assertLockfileMatchesPackageJson() {
  const sourceLock = path.join(connectorRoot, 'package-lock.json');
  const stagedLock = path.join(outputRoot, 'package-lock.json');
  if (sha256File(sourceLock) !== sha256File(stagedLock)) {
    throw new Error('Prepared connector package-lock.json does not match source lockfile');
  }
}

function assertNoDevDependenciesInstalled() {
  for (const name of DEV_DEPENDENCY_NAMES) {
    const candidate = path.join(outputModules, ...name.split('/'));
    if (fs.existsSync(candidate)) {
      throw new Error(`DevDependency leaked into prepared production tree: ${name}`);
    }
  }
}

function runProductionAudit() {
  const result = spawnSync('npm', ['audit', '--omit=dev', '--json'], {
    cwd: outputRoot,
    encoding: 'utf8',
    shell: true,
  });
  let parsed;
  try {
    parsed = JSON.parse(result.stdout || '{}');
  } catch {
    throw new Error(`Unable to parse npm audit output: ${result.stderr || result.stdout}`);
  }
  const vulnerabilityCount = parsed.metadata?.vulnerabilities?.total ?? 0;
  if (vulnerabilityCount > 0) {
    throw new Error(`Prepared connector production audit reported ${vulnerabilityCount} vulnerabilities`);
  }
  return { vulnerabilityCount };
}

/**
 * Pure comparison for the connector-version drift guard, kept separate from file I/O so it can be
 * unit-tested without mutating real repository files.
 */
export function compareConnectorRuntimeVersion(packageVersion, runtimeVersion) {
  const normalizedPackageVersion = typeof packageVersion === 'string' ? packageVersion.trim() : '';
  const normalizedRuntimeVersion = typeof runtimeVersion === 'string' ? runtimeVersion.trim() : '';
  if (!normalizedPackageVersion || !normalizedRuntimeVersion) {
    throw new Error(
      `Unable to resolve connector version for drift check (package.json: "${normalizedPackageVersion || '(empty)'}", `
      + `defaults.ts CONNECTOR_VERSION: "${normalizedRuntimeVersion || '(empty)'}")`,
    );
  }
  if (normalizedPackageVersion !== normalizedRuntimeVersion) {
    throw new Error(
      `Connector runtime version drift: package.json declares "${normalizedPackageVersion}" but `
      + `src/config/defaults.ts CONNECTOR_VERSION is "${normalizedRuntimeVersion}". The running Connector's `
      + '/health response would report a version that does not match the package being packaged. '
      + 'Update CONNECTOR_VERSION in src/config/defaults.ts to match package.json.',
    );
  }
  return { packageVersion: normalizedPackageVersion, runtimeVersion: normalizedRuntimeVersion };
}

/**
 * Guard against the Connector's own runtime-reported version (CONNECTOR_VERSION, a hand-maintained
 * literal in src/config/defaults.ts) drifting from package.json. This literal is what the running
 * Connector actually reports via /health -> connectorVersion, which Desktop's runtime-integrity
 * check compares against its bundled-version fingerprint. If the two sources disagree, that
 * fingerprint check would be comparing against a value that no longer reflects the real build.
 */
export function assertConnectorRuntimeVersionMatchesPackage() {
  const packageJsonPath = path.join(connectorRoot, 'package.json');
  const defaultsPath = path.join(connectorRoot, 'src/config/defaults.ts');
  const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
  const defaultsSource = fs.readFileSync(defaultsPath, 'utf8');
  const match = defaultsSource.match(/export const CONNECTOR_VERSION\s*=\s*'([^']+)'/);
  return compareConnectorRuntimeVersion(pkg.version, match?.[1] ?? '');
}

/**
 * Keep Desktop packaging VERSION.txt aligned with connector package.json.
 * Stale VERSION.txt previously caused packaged installs to advertise 0.3.1
 * while shipping newer connector JS (or the reverse).
 */
export function syncDesktopConnectorVersionLabel() {
  const packageJsonPath = path.join(connectorRoot, 'package.json');
  const versionFilePath = path.join(repoRoot, 'apps/venture_desktop/build/VERSION.txt');
  const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
  const version = typeof pkg.version === 'string' ? pkg.version.trim() : '';
  if (!/^\d+\.\d+\.\d+/.test(version)) {
    throw new Error(`Invalid connector package version: ${version || '(empty)'}`);
  }
  fs.mkdirSync(path.dirname(versionFilePath), { recursive: true });
  fs.writeFileSync(versionFilePath, `${version}\n`, 'utf8');
  return { version, versionFilePath };
}

/**
 * Fail packaging early when connector dist lacks voucher API routes.
 * Physical Android validation requires /api/v1/vouchers in the packaged binary.
 */
export function assertPackagedConnectorDistIncludesVouchers() {
  const vouchersRoute = path.join(connectorRoot, 'dist/api/routes/vouchers.js');
  const serverEntry = path.join(connectorRoot, 'dist/api/server.js');
  const mainEntry = path.join(connectorRoot, 'dist/main.js');
  if (!fs.existsSync(mainEntry)) {
    throw new Error(
      'connector/venture_connector/dist/main.js is missing. Run `npm run build` in the connector before packaging.',
    );
  }
  if (!fs.existsSync(vouchersRoute)) {
    throw new Error(
      'Packaged connector dist is missing dist/api/routes/vouchers.js. Rebuild the connector (0.4.x+) before packaging.',
    );
  }
  if (!fs.existsSync(serverEntry)) {
    throw new Error('Packaged connector dist is missing dist/api/server.js');
  }
  const serverSource = fs.readFileSync(serverEntry, 'utf8');
  const vouchersSource = fs.readFileSync(vouchersRoute, 'utf8');
  if (!serverSource.includes('createVouchersRouter')) {
    throw new Error('Packaged connector server.js does not register createVouchersRouter');
  }
  if (!vouchersSource.includes('/api/v1/vouchers')) {
    throw new Error('Packaged connector vouchers.js does not declare /api/v1/vouchers');
  }
  return { vouchersRoute, mainEntry };
}

export function prepareConnectorPackaging() {
  if (!fs.existsSync(path.join(connectorRoot, 'package-lock.json'))) {
    throw new Error('connector/venture_connector/package-lock.json is required for deterministic packaging');
  }
  assertConnectorRuntimeVersionMatchesPackage();
  const versionLabel = syncDesktopConnectorVersionLabel();
  const voucherRoutes = assertPackagedConnectorDistIncludesVouchers();
  fs.rmSync(outputRoot, { recursive: true, force: true });
  fs.mkdirSync(outputRoot, { recursive: true });
  for (const fileName of ['package.json', 'package-lock.json']) {
    fs.copyFileSync(path.join(connectorRoot, fileName), path.join(outputRoot, fileName));
  }
  run('npm ci --omit=dev', outputRoot, { quiet: true });
  assertLockfileMatchesPackageJson();
  if (!fs.existsSync(path.join(outputModules, 'express'))) {
    throw new Error('Prepared connector packaging is missing express dependency');
  }
  assertNoDevDependenciesInstalled();
  const audit = runProductionAudit();
  const productionDependencyCount = fs.readdirSync(outputModules, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name !== '.bin')
    .length;
  return {
    outputRoot,
    outputModules,
    productionDependencyCount,
    connectorVersion: versionLabel.version,
    voucherRoutes,
    audit,
  };
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (invokedDirectly) {
  try {
    const prepared = prepareConnectorPackaging();
    console.log(JSON.stringify(prepared, null, 2));
  } catch (error) {
    console.error('Connector packaging preparation FAIL:', error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
