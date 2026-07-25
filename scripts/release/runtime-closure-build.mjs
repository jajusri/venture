#!/usr/bin/env node
/**
 * Pre-commit runtime-closure candidate build (does not require clean git tree).
 */
import { execSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { inspectPackageBoundary } from './package-boundary.mjs';
import { prepareConnectorPackaging } from './prepare-connector-packaging.mjs';
import { inspectPackagedConnectorDependencies } from './packaged-connector-dependency-inspection.mjs';
import { generateManifest, renderChecksumFile, verifyManifest } from './manifest.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const desktopRoot = path.join(repoRoot, 'apps/budcom_desktop');
const connectorRoot = path.join(repoRoot, 'connector/budcom_connector');
const version = JSON.parse(fs.readFileSync(path.join(desktopRoot, 'package.json'), 'utf8')).version;
const outputRoot = path.join(repoRoot, 'release/controlled-pilot', version, 'artifacts-runtime-closure');

function run(command, cwd, env = process.env) {
  console.log(`> ${command}`);
  execSync(command, { cwd, stdio: 'inherit', shell: true, env });
}

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

async function main() {
  const report = {
    classification: 'pre_commit_runtime_proof_only',
    startedAt: new Date().toISOString(),
    steps: [],
  };

  async function step(name, fn) {
    console.log(`\n=== ${name} ===`);
    const result = await fn();
    report.steps.push({ name, status: 'PASS', at: new Date().toISOString(), result: result ?? null });
    return result;
  }

  await step('connector-lint', () => run('npm run lint', connectorRoot));
  await step('connector-build', () => run('npm run build', connectorRoot));
  await step('connector-test', () => run('npm test', connectorRoot));
  await step('connector-architecture', () => run('npx vitest run test/architecture', connectorRoot));
  await step('connector-audit-prod', () => run('npm run audit:prod', connectorRoot));
  await step('desktop-build', () => run('npm run build', desktopRoot));
  await step('desktop-lint', () => run('npx tsc -p tsconfig.main.json --noEmit && npx tsc -p tsconfig.preload.json --noEmit', desktopRoot));
  await step('desktop-test', () => run('npm test', desktopRoot));
  await step('desktop-audit-prod', () => run('npm run audit:prod', desktopRoot));
  await step('contract-test', () => run('npm test', path.join(repoRoot, 'tests/contract')));

  const packaging = await step('connector-packaging-prepare', () => prepareConnectorPackaging());
  const nodeRuntime = await step('node-runtime-prepare', async () => {
    const { prepareNodeRuntime } = await import('./prepare-node-runtime.mjs');
    return prepareNodeRuntime();
  });

  fs.rmSync(outputRoot, { recursive: true, force: true });
  fs.mkdirSync(outputRoot, { recursive: true });

  await step('electron-builder', () => {
    run('npx electron-builder --win --config electron-builder.yml --config.directories.output=' + outputRoot, desktopRoot, {
      ...process.env,
      BUDCOM_RELEASE_MODE: 'controlled_pilot',
    });
  });

  const installerName = `BudcomDesktop-${version}-x64-setup.exe`;
  const installerPath = path.join(outputRoot, installerName);
  if (!fs.existsSync(installerPath)) {
    throw new Error(`Installer not found at ${installerPath}`);
  }

  const unpackedRoot = path.join(outputRoot, 'win-unpacked');
  await step('package-boundary', () => {
    const boundary = inspectPackageBoundary(unpackedRoot);
    if (!boundary.ok) {
      throw new Error(boundary.violations.map((v) => `${v.path}: ${v.reason}`).join('; '));
    }
    return { fileCount: boundary.files.length };
  });

  await step('packaged-runtime-contract', async () => {
    const { inspectPackagedRuntime } = await import('./packaged-runtime-inspection.mjs');
    await inspectPackagedRuntime(unpackedRoot);
    return { ok: true };
  });

  const dependencyInspection = await step('packaged-connector-dependencies', async () => {
    const { inspectPackagedConnectorDependencies } = await import('./packaged-connector-dependency-inspection.mjs');
    return inspectPackagedConnectorDependencies(unpackedRoot);
  });

  const manifest = await step('manifest', () => {
    const generated = generateManifest(outputRoot, [{
      filename: installerName,
      classification: 'nsis-installer',
      distributable: false,
    }]);
    fs.writeFileSync(path.join(outputRoot, 'artifacts.manifest.json'), JSON.stringify(generated, null, 2));
    fs.writeFileSync(path.join(outputRoot, 'SHA256SUMS.txt'), renderChecksumFile(generated));
    return generated;
  });

  await step('verify-manifest', () => {
    verifyManifest(manifest, outputRoot);
    return { ok: true };
  });

  report.installer = {
    filename: installerName,
    path: installerPath,
    sizeBytes: fs.statSync(installerPath).size,
    sha256: sha256File(installerPath),
    classification: 'pre_commit_runtime_proof_only',
  };
  report.packaging = packaging;
  report.nodeRuntime = nodeRuntime;
  report.dependencyInspection = dependencyInspection;
  report.finishedAt = new Date().toISOString();
  report.status = 'PASS';

  fs.writeFileSync(path.join(outputRoot, 'runtime-closure-build-report.json'), JSON.stringify(report, null, 2));
  console.log('\nRuntime-closure build PASS');
  console.log(JSON.stringify(report.installer, null, 2));
}

main().catch((error) => {
  console.error('Runtime-closure build FAIL:', error instanceof Error ? error.message : String(error));
  process.exit(1);
});
