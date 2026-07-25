#!/usr/bin/env node
/**
 * Prepare pinned Node.js 22+ win-x64 runtime for packaged desktop connector host.
 */
import { execSync, spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const desktopRoot = path.join(repoRoot, 'apps/budcom_desktop');
const outputRoot = path.join(desktopRoot, '.packaged-node');

export const NODE_RUNTIME_VERSION = '22.16.0';
export const NODE_RUNTIME_ARCH = 'x64';
export const NODE_RUNTIME_PLATFORM = 'win32';
export const NODE_RUNTIME_LICENSE = 'MIT';
const NODE_DIST_BASENAME = `node-v${NODE_RUNTIME_VERSION}-win-${NODE_RUNTIME_ARCH}`;
const NODE_DIST_URL = `https://nodejs.org/dist/v${NODE_RUNTIME_VERSION}/${NODE_DIST_BASENAME}.zip`;

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function sha256Buffer(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

function downloadNodeArchive(targetZip) {
  const response = spawnSync(
    'curl',
    ['-fsSL', NODE_DIST_URL, '-o', targetZip],
    { encoding: 'utf8', shell: true },
  );
  if (response.status !== 0) {
    throw new Error(`Failed to download Node runtime: ${response.stderr || response.stdout}`);
  }
}

function extractNodeArchive(zipPath, extractRoot) {
  fs.rmSync(extractRoot, { recursive: true, force: true });
  fs.mkdirSync(extractRoot, { recursive: true });
  execSync(`tar -xf "${zipPath}" -C "${extractRoot}"`, { stdio: 'inherit', shell: true });
  const extractedDir = path.join(extractRoot, NODE_DIST_BASENAME);
  if (!fs.existsSync(extractedDir)) {
    throw new Error(`Expected extracted Node directory missing: ${extractedDir}`);
  }
  return extractedDir;
}

function assertNodeRuntimeSupportsSqlite(nodeExecutable) {
  const result = spawnSync(nodeExecutable, ['-e', "require('node:sqlite')"], {
    encoding: 'utf8',
    env: { ...process.env, NODE_OPTIONS: '' },
  });
  if (result.status !== 0) {
    throw new Error(`Packaged Node runtime failed node:sqlite probe: ${result.stderr || result.stdout}`);
  }
}

function assertNodeRuntimeArchitecture(nodeExecutable) {
  const result = spawnSync(nodeExecutable, ['-p', 'process.arch'], { encoding: 'utf8' });
  if (result.status !== 0 || String(result.stdout).trim() !== NODE_RUNTIME_ARCH) {
    throw new Error(`Packaged Node runtime architecture mismatch: expected ${NODE_RUNTIME_ARCH}`);
  }
}

export function prepareNodeRuntime() {
  const cacheDir = path.join(outputRoot, '.cache');
  const extractRoot = path.join(outputRoot, '.extract');
  const zipPath = path.join(cacheDir, `${NODE_DIST_BASENAME}.zip`);
  fs.mkdirSync(cacheDir, { recursive: true });

  if (!fs.existsSync(zipPath)) {
    downloadNodeArchive(zipPath);
  }

  const zipSha256 = sha256File(zipPath);
  const extractedDir = extractNodeArchive(zipPath, extractRoot);
  const nodeExe = path.join(extractedDir, 'node.exe');
  const licenseFile = path.join(extractedDir, 'LICENSE');
  if (!fs.existsSync(nodeExe)) {
    throw new Error(`node.exe missing in extracted runtime: ${nodeExe}`);
  }

  const stagedRoot = path.join(outputRoot, '.stage');
  fs.rmSync(stagedRoot, { recursive: true, force: true });
  fs.mkdirSync(stagedRoot, { recursive: true });
  fs.copyFileSync(nodeExe, path.join(stagedRoot, 'node.exe'));
  if (fs.existsSync(licenseFile)) {
    fs.copyFileSync(licenseFile, path.join(stagedRoot, 'LICENSE'));
  }

  const stagedNodeExe = path.join(stagedRoot, 'node.exe');
  assertNodeRuntimeArchitecture(stagedNodeExe);
  assertNodeRuntimeSupportsSqlite(stagedNodeExe);

  const manifest = {
    manifestSchemaVersion: 1,
    runtime: 'node',
    version: NODE_RUNTIME_VERSION,
    platform: NODE_RUNTIME_PLATFORM,
    architecture: NODE_RUNTIME_ARCH,
    license: NODE_RUNTIME_LICENSE,
    sourceUrl: NODE_DIST_URL,
    archiveSha256: zipSha256,
    nodeExecutableSha256: sha256File(stagedNodeExe),
    nodeExecutableRelativePath: 'node/node.exe',
    sqliteCapable: true,
    preparedAt: new Date().toISOString(),
  };
  fs.writeFileSync(path.join(stagedRoot, 'node-runtime.manifest.json'), JSON.stringify(manifest, null, 2));

  for (const entry of fs.readdirSync(stagedRoot)) {
    const from = path.join(stagedRoot, entry);
    const to = path.join(outputRoot, entry);
    fs.mkdirSync(outputRoot, { recursive: true });
    fs.copyFileSync(from, to);
  }
  fs.rmSync(stagedRoot, { recursive: true, force: true });

  return manifest;
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (invokedDirectly) {
  try {
    const manifest = prepareNodeRuntime();
    console.log(JSON.stringify(manifest, null, 2));
  } catch (error) {
    console.error('Node runtime preparation FAIL:', error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
