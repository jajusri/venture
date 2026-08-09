import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import {
  generateManifest,
  renderChecksumFile,
  verifyManifest,
} from '../../src/application/release/artifact-manifest.js';
import {
  assertPathTraversalSafe,
  inspectPackageBoundary,
} from '../../src/application/release/packaging-boundary.js';

describe('packaging boundary inspection', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  function makeRoot(files: Record<string, string>) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-pack-'));
    tempDirs.push(dir);
    for (const [rel, content] of Object.entries(files)) {
      const target = path.join(dir, rel);
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, content, 'utf8');
    }
    return dir;
  }

  it('accepts allowed runtime content', () => {
    const root = makeRoot({ 'desktop/dist/main/main.js': 'console.log("ok");' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(true);
  });

  it('rejects .env files', () => {
    const root = makeRoot({ '.env': 'SECRET=1' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(false);
  });

  it('rejects database files', () => {
    const root = makeRoot({ 'data/budcom-ledger.db': 'sqlite' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(false);
  });

  it('rejects log files', () => {
    const root = makeRoot({ 'logs/app.log': 'line' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(false);
  });

  it('rejects fixture directories', () => {
    const root = makeRoot({ 'test/fixtures/sample.xml': '<x/>' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(false);
  });

  it('allows third-party dependency test directories inside node_modules', () => {
    const root = makeRoot({ 'resources/connector/node_modules/express/test/index.js': 'ok();' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(true);
  });

  it('rejects private key material', () => {
    const root = makeRoot({ 'certs/code-sign.key': 'private' });
    expect(inspectPackageBoundary(root, fs).ok).toBe(false);
  });

  it('rejects path traversal manifest entries', () => {
    expect(() => assertPathTraversalSafe('../escape.exe')).toThrow(/Path traversal/);
  });
});

describe('controlled-pilot release ordering', () => {
  it('captures and validates clean source provenance before deleting generated release output', () => {
    const script = fs.readFileSync(
      path.resolve(process.cwd(), '..', '..', 'scripts', 'release', 'controlled-pilot-release.mjs'),
      'utf8',
    );
    expect(script.indexOf('assertReleaseStartClean(startProvenanceSnapshot)'))
      .toBeLessThan(script.indexOf('cleanReleaseOutput(releaseRoot)'));
  });

  it('excludes generated Connector state and dependency source maps from the package', () => {
    const config = fs.readFileSync(
      path.resolve(process.cwd(), 'electron-builder.yml'),
      'utf8',
    );
    expect(config).toContain('- "!data/**"');
    expect(config.match(/- "!\*\*\/\*\.map"/g)).toHaveLength(3);
  });
});

describe('manifest and checksum verification', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  it('verifies valid artifact checksums', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-manifest-'));
    tempDirs.push(dir);
    fs.writeFileSync(path.join(dir, 'artifact.zip'), 'payload', 'utf8');
    const manifest = generateManifest(dir, [{ filename: 'artifact.zip' }]);
    expect(verifyManifest(manifest, dir)).toBe(true);
    expect(renderChecksumFile(manifest)).toContain('artifact.zip');
  });

  it('rejects tampered artifact checksums', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-manifest-bad-'));
    tempDirs.push(dir);
    fs.writeFileSync(path.join(dir, 'artifact.zip'), 'payload', 'utf8');
    const manifest = generateManifest(dir, [{ filename: 'artifact.zip' }]);
    fs.writeFileSync(path.join(dir, 'artifact.zip'), 'tampered', 'utf8');
    expect(() => verifyManifest(manifest, dir)).toThrow(/Checksum mismatch/);
  });

  it('rejects malformed manifest version', () => {
    expect(() => verifyManifest({ manifestVersion: 99, checksumAlgorithm: 'sha256', generatedAt: '', artifacts: [] }, '.'))
      .toThrow(/Unsupported manifest version/);
  });

  it('rejects missing artifacts', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-manifest-missing-'));
    tempDirs.push(dir);
    expect(() => generateManifest(dir, [{ filename: 'missing.zip' }])).toThrow(/Missing artifact/);
  });

  it('rejects duplicate manifest entries', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-manifest-dup-'));
    tempDirs.push(dir);
    fs.writeFileSync(path.join(dir, 'artifact.zip'), 'payload', 'utf8');
    expect(() => generateManifest(dir, [{ filename: 'artifact.zip' }, { filename: 'artifact.zip' }]))
      .toThrow(/Duplicate manifest entry/);
  });

  it('fixes checksum algorithm to sha256', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-manifest-algo-'));
    tempDirs.push(dir);
    fs.writeFileSync(path.join(dir, 'artifact.zip'), 'payload', 'utf8');
    const manifest = generateManifest(dir, [{ filename: 'artifact.zip' }]);
    expect(manifest.checksumAlgorithm).toBe('sha256');
    expect(manifest.artifacts[0].checksum).toMatch(/^[a-f0-9]{64}$/);
  });
});
