#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

import { assertPathTraversalSafe } from './package-boundary.mjs';

export const MANIFEST_VERSION = 1;
export const CHECKSUM_ALGORITHM = 'sha256';

export function sha256File(filePath) {
  const hash = crypto.createHash(CHECKSUM_ALGORITHM);
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

export function generateManifest(artifactsDir, entries) {
  const manifestEntries = [];
  const seen = new Set();
  for (const entry of entries) {
    const safeName = assertPathTraversalSafe(entry.filename);
    if (seen.has(safeName)) {
      throw new Error(`Duplicate manifest entry: ${safeName}`);
    }
    seen.add(safeName);
    const artifactPath = path.join(artifactsDir, safeName);
    if (!fs.existsSync(artifactPath)) {
      throw new Error(`Missing artifact: ${safeName}`);
    }
    const stat = fs.statSync(artifactPath);
    manifestEntries.push({
      filename: safeName,
      sizeBytes: stat.size,
      checksumAlgorithm: CHECKSUM_ALGORITHM,
      checksum: sha256File(artifactPath),
      classification: entry.classification,
      distributable: entry.distributable,
    });
  }
  return {
    manifestVersion: MANIFEST_VERSION,
    checksumAlgorithm: CHECKSUM_ALGORITHM,
    generatedAt: new Date().toISOString(),
    artifacts: manifestEntries,
  };
}

export function renderChecksumFile(manifest) {
  return manifest.artifacts
    .map((entry) => `${entry.checksum}  ${entry.filename}`)
    .join('\n')
    .concat('\n');
}

export function verifyManifest(manifest, artifactsDir) {
  if (manifest.manifestVersion !== MANIFEST_VERSION) {
    throw new Error(`Unsupported manifest version: ${manifest.manifestVersion}`);
  }
  if (manifest.checksumAlgorithm !== CHECKSUM_ALGORITHM) {
    throw new Error(`Unsupported checksum algorithm: ${manifest.checksumAlgorithm}`);
  }
  const seen = new Set();
  for (const entry of manifest.artifacts) {
    const safeName = assertPathTraversalSafe(entry.filename);
    if (seen.has(safeName)) {
      throw new Error(`Duplicate manifest entry: ${safeName}`);
    }
    seen.add(safeName);
    const artifactPath = path.join(artifactsDir, safeName);
    if (!fs.existsSync(artifactPath)) {
      throw new Error(`Missing artifact: ${safeName}`);
    }
    const actual = sha256File(artifactPath);
    if (actual !== entry.checksum) {
      throw new Error(`Checksum mismatch for ${safeName}`);
    }
    const stat = fs.statSync(artifactPath);
    if (stat.size !== entry.sizeBytes) {
      throw new Error(`Size mismatch for ${safeName}`);
    }
  }
  return true;
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, '/')}`) {
  const command = process.argv[2];
  const artifactsDir = process.argv[3];
  const manifestPath = process.argv[4];
  if (command === 'verify') {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    verifyManifest(manifest, artifactsDir);
    console.log('Manifest verification OK');
    process.exit(0);
  }
  console.error('Usage: node manifest.mjs verify <artifacts-dir> <manifest.json>');
  process.exit(2);
}
