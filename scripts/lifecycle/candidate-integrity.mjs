#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { verifyManifest } from '../release/manifest.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const releaseRoot = path.join(repoRoot, 'release', 'controlled-pilot', '0.4.3');

const EXPECTED = {
  gitCommit: 'a9595af857546de3c65f1457775f3f65eb78ae77',
  sha256: '911422191ea977558f73fc756b6eb32dd701e01b16fb6626fc030da6e6480bdd',
  sizeBytes: 81970565,
  installerFilename: 'BudcomDesktop-0.4.3-x64-setup.exe',
  releaseMode: 'controlled_pilot',
};

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

export function verifyControlledPilotCandidate(options = {}) {
  const root = options.releaseRoot ?? releaseRoot;
  const artifactsDir = path.join(root, 'artifacts');
  const installerPath = path.join(artifactsDir, EXPECTED.installerFilename);
  const manifestPath = path.join(root, 'manifest', 'artifacts.manifest.json');
  const checksumPath = path.join(root, 'checksums', 'SHA256SUMS.txt');
  const buildInfoPath = path.join(root, 'build-info.json');
  const reportPath = path.join(root, 'reports', 'release-report.json');

  if (!fs.existsSync(installerPath)) {
    throw new Error(`Installer missing: ${installerPath}`);
  }

  const actualSha256 = sha256File(installerPath);
  const actualSize = fs.statSync(installerPath).size;
  if (actualSha256 !== EXPECTED.sha256) {
    throw new Error(`SHA-256 mismatch: expected ${EXPECTED.sha256}, got ${actualSha256}`);
  }
  if (actualSize !== EXPECTED.sizeBytes) {
    throw new Error(`Size mismatch: expected ${EXPECTED.sizeBytes}, got ${actualSize}`);
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  verifyManifest(manifest, artifactsDir);

  const checksumLine = fs.readFileSync(checksumPath, 'utf8').trim();
  const expectedLine = `${EXPECTED.sha256}  ${EXPECTED.installerFilename}`;
  if (checksumLine !== expectedLine) {
    throw new Error('Checksum file mismatch');
  }

  const buildInfo = JSON.parse(fs.readFileSync(buildInfoPath, 'utf8'));
  const releaseReport = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  for (const source of [buildInfo, releaseReport.buildInfo ?? {}]) {
    if (source.gitCommit !== EXPECTED.gitCommit) {
      throw new Error(`Git commit mismatch: ${source.gitCommit}`);
    }
    if (source.releaseMode !== EXPECTED.releaseMode) {
      throw new Error(`Release mode mismatch: ${source.releaseMode}`);
    }
    if (source.dirtyTree !== false) {
      throw new Error('Candidate dirtyTree is not false');
    }
    if (source.sourceTreeCleanAtStart !== true) {
      throw new Error('Candidate sourceTreeCleanAtStart is not true');
    }
  }

  const entry = manifest.artifacts[0];
  if (entry.classification !== 'nsis_installer' || entry.distributable !== true) {
    throw new Error('Installer is not classified as distributable nsis_installer');
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
