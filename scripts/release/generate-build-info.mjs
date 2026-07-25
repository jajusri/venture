#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function gitValue(command) {
  try {
    return execSync(command, { cwd: repoRoot, encoding: 'utf8' }).trim();
  } catch {
    return 'unknown';
  }
}

function isDirtyTree() {
  try {
    const status = execSync('git status --porcelain', { cwd: repoRoot, encoding: 'utf8' }).trim();
    return status.length > 0;
  } catch {
    return true;
  }
}

export function generateBuildInfo(options = {}) {
  const desktopPkg = readJson(path.join(repoRoot, 'apps/budcom_desktop/package.json'));
  const connectorPkg = readJson(path.join(repoRoot, 'connector/budcom_connector/package.json'));
  const releaseMode = options.releaseMode ?? process.env.BUDCOM_RELEASE_MODE ?? 'controlled_pilot';
  const desktopVersion = desktopPkg.version;
  const connectorVersion = connectorPkg.version;
  return {
    applicationVersion: desktopVersion,
    desktopVersion,
    connectorVersion,
    storageSchemaVersion: options.storageSchemaVersion ?? 8,
    gitCommit: options.gitCommit ?? gitValue('git rev-parse HEAD'),
    buildTimestamp: options.buildTimestamp ?? new Date().toISOString(),
    releaseMode,
    packagingTarget: options.packagingTarget ?? 'windows-nsis-x64',
    architecture: options.architecture ?? 'x64',
    nodeVersion: process.version,
    electronVersion: options.electronVersion ?? desktopPkg.devDependencies?.electron ?? 'unknown',
    dirtyTree: options.dirtyTree ?? isDirtyTree(),
    buildChannel: options.buildChannel ?? 'controlled-pilot',
    checksumAlgorithm: 'sha256',
    artifactFilename: options.artifactFilename,
  };
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, '/')}`) {
  const outputPath = process.argv[2];
  if (!outputPath) {
    console.error('Usage: node generate-build-info.mjs <output-path>');
    process.exit(2);
  }
  const info = generateBuildInfo();
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(info, null, 2)}\n`, 'utf8');
  console.log(`Wrote ${outputPath}`);
}
