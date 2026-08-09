#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { verifyManifest } from '../release/manifest.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const releaseRoot = path.join(repoRoot, 'release', 'controlled-pilot', '0.4.3');

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function readRequiredJson(filePath, label) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${label} missing: ${filePath}`);
  }
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    throw new Error(`${label} is malformed JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label} has malformed structure`);
  }
  return parsed;
}

function requireString(value, label, pattern) {
  if (typeof value !== 'string' || value.length === 0 || (pattern && !pattern.test(value))) {
    throw new Error(`${label} is missing or malformed`);
  }
  return value;
}

function requireInteger(value, label) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label} is missing or malformed`);
  }
  return value;
}

function assertBuildIdentity(source, label, expectedCommit) {
  const commit = requireString(source.gitCommit, `${label}.gitCommit`, /^[0-9a-f]{40}$/i);
  if (expectedCommit && commit !== expectedCommit) {
    throw new Error(`${label}.gitCommit disagrees with generated release evidence`);
  }
  if (source.dirtyTree !== false || source.sourceTreeCleanAtStart !== true) {
    throw new Error(`${label} does not describe a clean source tree`);
  }
  if (source.releaseMode !== 'controlled_pilot') {
    throw new Error(`${label}.releaseMode is not controlled_pilot`);
  }
  return commit;
}

export function verifyControlledPilotCandidate(options = {}) {
  const root = options.releaseRoot ?? releaseRoot;
  const artifactsDir = path.join(root, 'artifacts');
  const manifestPath = path.join(root, 'manifest', 'artifacts.manifest.json');
  const checksumPath = path.join(root, 'checksums', 'SHA256SUMS.txt');
  const buildInfoPath = path.join(root, 'build-info.json');
  const reportPath = path.join(root, 'reports', 'release-report.json');

  const manifest = readRequiredJson(manifestPath, 'Artifact manifest');
  const buildInfo = readRequiredJson(buildInfoPath, 'Build info');
  const releaseReport = readRequiredJson(reportPath, 'Release report');

  if (!Array.isArray(manifest.artifacts) || manifest.artifacts.length !== 1) {
    throw new Error('Artifact manifest must contain exactly one installer entry');
  }
  const entry = manifest.artifacts[0];
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
    throw new Error('Artifact manifest installer entry is malformed');
  }
  const manifestFilename = requireString(entry.filename, 'Manifest installer filename');
  if (path.basename(manifestFilename) !== manifestFilename) {
    throw new Error('Manifest installer filename is not a safe basename');
  }
  const manifestSha256 = requireString(entry.checksum, 'Manifest installer SHA-256', /^[0-9a-f]{64}$/i).toLowerCase();
  const manifestSize = requireInteger(entry.sizeBytes, 'Manifest installer size');
  if (entry.checksumAlgorithm !== 'sha256'
    || entry.classification !== 'nsis_installer'
    || entry.distributable !== true) {
    throw new Error('Artifact manifest does not describe a distributable SHA-256 NSIS installer');
  }

  if (releaseReport.status !== 'PASS' || releaseReport.verdict !== 'controlled_pilot_distributable') {
    throw new Error('Release report does not contain a passing controlled-pilot verdict');
  }
  if (!releaseReport.installer || typeof releaseReport.installer !== 'object'
    || !releaseReport.buildInfo || typeof releaseReport.buildInfo !== 'object'
    || !releaseReport.provenance || typeof releaseReport.provenance !== 'object') {
    throw new Error('Release report structure is malformed');
  }
  const reportFilename = requireString(releaseReport.installer.filename, 'Report installer filename');
  const reportSha256 = requireString(releaseReport.installer.sha256, 'Report installer SHA-256', /^[0-9a-f]{64}$/i).toLowerCase();
  const reportSize = requireInteger(releaseReport.installer.sizeBytes, 'Report installer size');
  if (releaseReport.installer.classification !== 'nsis_installer'
    || releaseReport.installer.distributable !== true) {
    throw new Error('Release report does not describe a distributable NSIS installer');
  }
  if (reportFilename !== manifestFilename || reportSha256 !== manifestSha256 || reportSize !== manifestSize) {
    throw new Error('Release report installer identity disagrees with artifact manifest');
  }

  const buildCommit = assertBuildIdentity(buildInfo, 'Build info');
  assertBuildIdentity(releaseReport.buildInfo, 'Release report buildInfo', buildCommit);
  const provenanceCommit = requireString(
    releaseReport.provenance.gitCommitAtStart,
    'Release report provenance commit',
    /^[0-9a-f]{40}$/i,
  );
  if (provenanceCommit !== buildCommit || releaseReport.provenance.sourceTreeCleanAtStart !== true) {
    throw new Error('Release provenance disagrees with clean build identity');
  }

  const installerPath = path.join(artifactsDir, manifestFilename);
  if (!fs.existsSync(installerPath) || !fs.statSync(installerPath).isFile()) {
    throw new Error(`Installer missing: ${installerPath}`);
  }

  const actualSha256 = sha256File(installerPath);
  const actualSize = fs.statSync(installerPath).size;
  if (actualSha256 !== manifestSha256) {
    throw new Error(`SHA-256 mismatch: expected ${manifestSha256}, got ${actualSha256}`);
  }
  if (actualSize !== manifestSize) {
    throw new Error(`Size mismatch: expected ${manifestSize}, got ${actualSize}`);
  }

  verifyManifest(manifest, artifactsDir);

  if (!fs.existsSync(checksumPath)) {
    throw new Error(`Checksum file missing: ${checksumPath}`);
  }
  const checksumLine = fs.readFileSync(checksumPath, 'utf8').trim();
  const expectedLine = `${manifestSha256}  ${manifestFilename}`;
  if (checksumLine !== expectedLine) {
    throw new Error('Checksum file mismatch');
  }

  return {
    installerPath,
    sha256: actualSha256,
    sizeBytes: actualSize,
    buildInfo,
    releaseReport,
  };
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (invokedDirectly) {
  try {
    const result = verifyControlledPilotCandidate();
    console.log('Controlled-pilot candidate integrity OK');
    console.log(JSON.stringify({
      sha256: result.sha256,
      sizeBytes: result.sizeBytes,
      gitCommit: result.buildInfo.gitCommit,
      releaseMode: result.buildInfo.releaseMode,
      dirtyTree: result.buildInfo.dirtyTree,
    }, null, 2));
  } catch (error) {
    console.error('Candidate integrity FAIL:', error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
