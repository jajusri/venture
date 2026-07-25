import crypto from 'node:crypto';
import fs from 'node:fs';

import { assertPathTraversalSafe } from './packaging-boundary.js';

export const MANIFEST_VERSION = 1;
export const CHECKSUM_ALGORITHM = 'sha256' as const;

export interface ArtifactManifestEntry {
  readonly filename: string;
  readonly sizeBytes: number;
  readonly checksumAlgorithm: typeof CHECKSUM_ALGORITHM;
  readonly checksum: string;
  readonly classification?: string;
  readonly distributable?: boolean;
}

export interface ArtifactManifest {
  readonly manifestVersion: number;
  readonly checksumAlgorithm: typeof CHECKSUM_ALGORITHM;
  readonly generatedAt: string;
  readonly artifacts: readonly ArtifactManifestEntry[];
}

export function sha256File(filePath: string, readFile: (path: string) => Buffer = (path) => fs.readFileSync(path)): string {
  const hash = crypto.createHash(CHECKSUM_ALGORITHM);
  hash.update(readFile(filePath));
  return hash.digest('hex');
}

export function generateManifest(
  artifactsDir: string,
  entries: readonly {
    readonly filename: string;
    readonly classification?: string;
    readonly distributable?: boolean;
  }[],
  fsImpl: Pick<typeof fs, 'existsSync' | 'statSync' | 'readFileSync'> = fs,
): ArtifactManifest {
  const manifestEntries: ArtifactManifestEntry[] = [];
  const seen = new Set<string>();
  for (const entry of entries) {
    const safeName = assertPathTraversalSafe(entry.filename);
    if (seen.has(safeName)) {
      throw new Error(`Duplicate manifest entry: ${safeName}`);
    }
    seen.add(safeName);
    const artifactPath = `${artifactsDir}/${safeName}`;
    if (!fsImpl.existsSync(artifactPath)) {
      throw new Error(`Missing artifact: ${safeName}`);
    }
    const stat = fsImpl.statSync(artifactPath);
    manifestEntries.push({
      filename: safeName,
      sizeBytes: stat.size,
      checksumAlgorithm: CHECKSUM_ALGORITHM,
      checksum: sha256File(artifactPath, (path) => fsImpl.readFileSync(path)),
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

export function renderChecksumFile(manifest: ArtifactManifest): string {
  return manifest.artifacts.map((entry) => `${entry.checksum}  ${entry.filename}`).join('\n').concat('\n');
}

export function verifyManifest(
  manifest: ArtifactManifest,
  artifactsDir: string,
  fsImpl: Pick<typeof fs, 'existsSync' | 'statSync' | 'readFileSync'> = fs,
): boolean {
  if (manifest.manifestVersion !== MANIFEST_VERSION) {
    throw new Error(`Unsupported manifest version: ${manifest.manifestVersion}`);
  }
  if (manifest.checksumAlgorithm !== CHECKSUM_ALGORITHM) {
    throw new Error(`Unsupported checksum algorithm: ${manifest.checksumAlgorithm}`);
  }
  const seen = new Set<string>();
  for (const entry of manifest.artifacts) {
    const safeName = assertPathTraversalSafe(entry.filename);
    if (seen.has(safeName)) {
      throw new Error(`Duplicate manifest entry: ${safeName}`);
    }
    seen.add(safeName);
    const artifactPath = `${artifactsDir}/${safeName}`;
    if (!fsImpl.existsSync(artifactPath)) {
      throw new Error(`Missing artifact: ${safeName}`);
    }
    const actual = sha256File(artifactPath, (path) => fsImpl.readFileSync(path));
    if (actual !== entry.checksum) {
      throw new Error(`Checksum mismatch for ${safeName}`);
    }
    if (fsImpl.statSync(artifactPath).size !== entry.sizeBytes) {
      throw new Error(`Size mismatch for ${safeName}`);
    }
  }
  return true;
}
