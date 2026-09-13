#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { resolveDirtyTreeFromProvenance } from './release-provenance.mjs';

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

function loadProvenanceFromEnv() {
  const raw = process.env.VENTURE_RELEASE_PROVENANCE;
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function generateBuildInfo(options = {}) {
  const desktopPkg = readJson(path.join(repoRoot, 'apps/venture_desktop/package.json'));
  const connectorPkg = readJson(path.join(repoRoot, 'connector/venture_connector/package.json'));
  const schemaModulePath = path.join(repoRoot, 'connector/venture_connector/src/storage/sqlite/schema.ts');
  const schemaSource = fs.readFileSync(schemaModulePath, 'utf8');
  const schemaMatch = schemaSource.match(/export const STORAGE_SCHEMA_VERSION\s*=\s*(\d+)\s*;/);
  const storageSchemaVersionFromSource = schemaMatch ? Number.parseInt(schemaMatch[1], 10) : undefined;
  const provenance = options.provenance ?? loadProvenanceFromEnv();
  const releaseMode = options.releaseMode ?? process.env.VENTURE_RELEASE_MODE ?? 'controlled_pilot';
  const desktopVersion = desktopPkg.version;
  const connectorVersion = connectorPkg.version;
  const gitCommit = options.gitCommit
    ?? provenance?.gitCommit
    ?? gitValue('git rev-parse HEAD');
  const dirtyTree = options.dirtyTree
    ?? resolveDirtyTreeFromProvenance(provenance, isDirtyTree());

  return {
    applicationVersion: desktopVersion,
    desktopVersion,
    connectorVersion,
    storageSchemaVersion: options.storageSchemaVersion ?? storageSchemaVersionFromSource ?? 11,
    gitCommit,
    buildTimestamp: options.buildTimestamp ?? new Date().toISOString(),
    releaseMode,
    packagingTarget: options.packagingTarget ?? 'windows-nsis-x64',
    architecture: options.architecture ?? 'x64',
    nodeVersion: process.version,
    electronVersion: options.electronVersion ?? desktopPkg.devDependencies?.electron ?? 'unknown',
    dirtyTree,
    buildChannel: options.buildChannel ?? 'controlled-pilot',
    checksumAlgorithm: 'sha256',
    artifactFilename: options.artifactFilename,
    sourceTreeCleanAtStart: provenance?.sourceTreeCleanAtStart,
    allowlistedGeneratedPaths: provenance?.allowlistedGeneratedPaths,
    generatedChangesAfterBuild: provenance?.generatedChangesAfterBuild,
  };
}

if (process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
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
