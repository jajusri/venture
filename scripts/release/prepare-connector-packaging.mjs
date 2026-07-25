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
const connectorRoot = path.join(repoRoot, 'connector/budcom_connector');
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

export function prepareConnectorPackaging() {
  if (!fs.existsSync(path.join(connectorRoot, 'package-lock.json'))) {
    throw new Error('connector/budcom_connector/package-lock.json is required for deterministic packaging');
  }
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
