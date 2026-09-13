#!/usr/bin/env node
/**
 * Packaged A/B/C verification for pre-commit hardening evidence gate.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

export function verifyPackagedAbcControls(unpackedRoot) {
  const resourcesPath = path.join(unpackedRoot, 'resources');
  const manifestPath = path.join(resourcesPath, 'node', 'node-runtime.manifest.json');
  const nodeExePath = path.join(resourcesPath, 'node', 'node.exe');
  const buildInfoCandidates = [
    path.join(unpackedRoot, 'resources', 'app.asar.unpacked', 'dist', 'main', 'build-info.json'),
    path.join(repoRoot, 'apps/venture_desktop/dist/main/build-info.json'),
  ];

  const results = {
    controlA: { pass: false, details: {} },
    controlB: { pass: false, details: {} },
    controlC: { pass: false, details: {} },
  };

  if (!fs.existsSync(manifestPath)) {
    results.controlA.details.error = 'missing_manifest';
    return results;
  }
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const actualHash = sha256File(nodeExePath);
  const hashMatches = actualHash.toLowerCase() === String(manifest.nodeExecutableSha256).toLowerCase();
  results.controlA = {
    pass: manifest.manifestSchemaVersion === 1
      && manifest.version === '22.16.0'
      && manifest.architecture === 'x64'
      && manifest.platform === 'win32'
      && manifest.nodeExecutableRelativePath === 'node/node.exe'
      && hashMatches
      && fs.existsSync(nodeExePath),
    details: {
      manifestSchemaVersion: manifest.manifestSchemaVersion,
      version: manifest.version,
      architecture: manifest.architecture,
      platform: manifest.platform,
      nodeExecutableRelativePath: manifest.nodeExecutableRelativePath,
      expectedSha256: manifest.nodeExecutableSha256,
      actualSha256: actualHash,
      hashMatches,
    },
  };

  const desktopDist = path.join(repoRoot, 'apps/venture_desktop/dist/application/release/startup-environment.js');
  const startupEnvSource = fs.readFileSync(
    fs.existsSync(desktopDist)
      ? desktopDist
      : path.join(repoRoot, 'apps/venture_desktop/src/application/release/startup-environment.ts'),
    'utf8',
  );
  results.controlB = {
    pass: startupEnvSource.includes('VENTURE_INSTALLED_PROBE_MODE')
      && startupEnvSource.includes('USER_DATA_OVERRIDE_ENV')
      && startupEnvSource.includes('outside_probe_temp_root'),
    details: {
      probeModeEnvPresent: startupEnvSource.includes('VENTURE_INSTALLED_PROBE_MODE'),
      userDataGated: startupEnvSource.includes('isInstalledProbeMode'),
    },
  };

  const networkDist = path.join(repoRoot, 'apps/venture_desktop/dist/application/release/packaged-connector-network.js');
  const lifecycleDist = path.join(repoRoot, 'apps/venture_desktop/dist/application/connector-lifecycle-config.js');
  const networkSource = fs.readFileSync(
    fs.existsSync(networkDist)
      ? networkDist
      : path.join(repoRoot, 'apps/venture_desktop/src/application/release/packaged-connector-network.ts'),
    'utf8',
  );
  const lifecycleSource = fs.readFileSync(
    fs.existsSync(lifecycleDist)
      ? lifecycleDist
      : path.join(repoRoot, 'apps/venture_desktop/src/application/connector-lifecycle-config.ts'),
    'utf8',
  );
  results.controlC = {
    pass: networkSource.includes('127.0.0.1')
      && networkSource.includes('resolvePackagedConnectorLoopbackHost')
      && lifecycleSource.includes('VENTURE_CONNECTOR_HOST')
      && lifecycleSource.includes('resolvePackagedConnectorLoopbackHost'),
    details: {
      loopbackHostConstant: networkSource.includes('127.0.0.1'),
      lifecycleForcesLoopback: lifecycleSource.includes('resolvePackagedConnectorLoopbackHost'),
      childEnvHostPropagation: lifecycleSource.includes('VENTURE_CONNECTOR_HOST'),
    },
  };

  let buildInfo = null;
  for (const candidate of buildInfoCandidates) {
    if (fs.existsSync(candidate)) {
      buildInfo = JSON.parse(fs.readFileSync(candidate, 'utf8'));
      break;
    }
  }

  return {
    unpackedRoot,
    buildInfo,
    controls: results,
    pass: results.controlA.pass && results.controlB.pass && results.controlC.pass,
  };
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (invokedDirectly) {
  const unpackedRoot = process.argv[2];
  if (!unpackedRoot) {
    console.error('Usage: node precommit-hardening-packaged-verify.mjs <win-unpacked-root>');
    process.exit(2);
  }
  const report = verifyPackagedAbcControls(path.resolve(unpackedRoot));
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.pass ? 0 : 1);
}
