#!/usr/bin/env node
import { execSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ArtifactClassification,
  assertRealInstallerArtifact,
  classifyArtifactFilename,
  evaluateControlledPilotAcceptance,
  isControlledPilotInstallerFilename,
  isDistributableClassification,
} from './artifact-classification.mjs';
import { generateBuildInfo } from './generate-build-info.mjs';
import { generateManifest, renderChecksumFile, verifyManifest } from './manifest.mjs';
import { inspectPackageBoundary } from './package-boundary.mjs';
import {
  ALLOWLISTED_GENERATED_PATH_PREFIXES,
  assertReleaseStartClean,
  buildReleaseProvenanceMetadata,
  captureReleaseProvenanceSnapshot,
  createRepoGitRunner,
  validatePostBuildProvenance,
} from './release-provenance.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const desktopRoot = path.join(repoRoot, 'apps/budcom_desktop');
const connectorRoot = path.join(repoRoot, 'connector/budcom_connector');

function run(command, cwd, env = process.env) {
  console.log(`> ${command}`);
  execSync(command, { cwd, stdio: 'inherit', shell: true, env });
}

function cleanReleaseOutput(baseDir) {
  const releaseRoot = path.join(repoRoot, 'release');
  if (!path.resolve(baseDir).startsWith(path.resolve(releaseRoot))) {
    throw new Error(`Refusing to clean path outside release/: ${baseDir}`);
  }
  fs.rmSync(baseDir, { recursive: true, force: true });
}

function readPackageVersion(pkgRoot) {
  return JSON.parse(fs.readFileSync(path.join(pkgRoot, 'package.json'), 'utf8')).version;
}

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function findInstallerArtifact(artifactsDir, version) {
  const expected = `BudcomDesktop-${version}-x64-setup.exe`;
  const expectedPath = path.join(artifactsDir, expected);
  if (fs.existsSync(expectedPath)) {
    return expected;
  }
  const matches = fs.readdirSync(artifactsDir)
    .filter((name) => isControlledPilotInstallerFilename(name));
  if (matches.length === 1) {
    return matches[0];
  }
  throw new Error(`Expected exactly one NSIS installer artifact (${expected}) in ${artifactsDir}`);
}

function resolveUnpackedInspectionRoot(artifactsDir) {
  const candidates = [
    path.join(artifactsDir, 'win-unpacked'),
    path.join(artifactsDir, 'win-unpacked', 'resources', 'app.asar.unpacked'),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  throw new Error('win-unpacked directory not found for package-boundary inspection');
}

function writeNonWindowsValidationReport(releaseRoot, reportsDir, report) {
  report.status = 'VALIDATION_ONLY';
  report.verdict = 'installer_not_built';
  report.platform = process.platform;
  report.message = 'Non-Windows runner completed validation steps only; NSIS installer not built.';
  report.finishedAt = new Date().toISOString();
  report.outputRoot = releaseRoot;
  fs.writeFileSync(path.join(reportsDir, 'release-report.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

async function main() {
  if (process.env.BUDCOM_RELEASE_MODE !== 'controlled_pilot') {
    console.error('BUDCOM_RELEASE_MODE must be controlled_pilot');
    process.exit(2);
  }

  const desktopVersion = readPackageVersion(desktopRoot);
  const releaseRoot = path.join(repoRoot, 'release', 'controlled-pilot', desktopVersion);
  const artifactsDir = path.join(releaseRoot, 'artifacts');
  const manifestDir = path.join(releaseRoot, 'manifest');
  const checksumsDir = path.join(releaseRoot, 'checksums');
  const reportsDir = path.join(releaseRoot, 'reports');

  cleanReleaseOutput(releaseRoot);
  for (const dir of [artifactsDir, manifestDir, checksumsDir, reportsDir]) {
    fs.mkdirSync(dir, { recursive: true });
  }

  const report = {
    steps: [],
    startedAt: new Date().toISOString(),
    releaseMode: 'controlled_pilot',
    platform: process.platform,
  };

  const gitRunner = createRepoGitRunner(repoRoot);
  const startProvenanceSnapshot = captureReleaseProvenanceSnapshot(gitRunner);
  assertReleaseStartClean(startProvenanceSnapshot);
  const releaseProvenanceEnv = buildReleaseProvenanceMetadata(startProvenanceSnapshot, []);
  process.env.BUDCOM_RELEASE_PROVENANCE = JSON.stringify(releaseProvenanceEnv);
  report.provenance = {
    sourceTreeCleanAtStart: startProvenanceSnapshot.sourceTreeCleanAtStart,
    gitCommitAtStart: startProvenanceSnapshot.headCommit,
    allowlistedGeneratedPaths: [...ALLOWLISTED_GENERATED_PATH_PREFIXES],
  };

  function step(name, fn) {
    console.log(`\n=== ${name} ===`);
    fn();
    report.steps.push({ name, status: 'PASS', at: new Date().toISOString() });
  }

  try {
    step('connector-lint', () => run('npm run lint', connectorRoot));
    step('connector-build', () => run('npm run build', connectorRoot));
    step('connector-test', () => run('npm test', connectorRoot));
    step('connector-architecture', () => run('npx vitest run test/architecture', connectorRoot));
    step('connector-audit', () => run('npm run audit:prod', connectorRoot));
    step('desktop-lint', () => run('npm run lint', desktopRoot));
    step('desktop-build', () => run('npm run build', desktopRoot));
    step('desktop-test', () => run('npm test', desktopRoot));
    step('desktop-audit', () => run('npm run audit:prod', desktopRoot));
    step('contract-test', () => run('npm test', path.join(repoRoot, 'tests/contract')));

    if (process.platform !== 'win32') {
      writeNonWindowsValidationReport(releaseRoot, reportsDir, report);
      console.error('\nControlled-pilot distributable release requires Windows for NSIS installer creation.');
      process.exit(1);
    }

    step('nsis-installer', () => {
      run('npm run dist:win', desktopRoot, {
        ...process.env,
        BUDCOM_RELEASE_MODE: 'controlled_pilot',
      });
    });

    const endProvenanceSnapshot = captureReleaseProvenanceSnapshot(gitRunner);
    const generatedChangesAfterBuild = validatePostBuildProvenance(
      startProvenanceSnapshot,
      endProvenanceSnapshot,
    );
    const releaseProvenance = buildReleaseProvenanceMetadata(
      startProvenanceSnapshot,
      generatedChangesAfterBuild,
    );
    process.env.BUDCOM_RELEASE_PROVENANCE = JSON.stringify(releaseProvenance);
    report.provenance.generatedChangesAfterBuild = [...generatedChangesAfterBuild];

    const buildInfoPath = path.join(releaseRoot, 'build-info.json');
    const buildInfo = generateBuildInfo({
      releaseMode: 'controlled_pilot',
      packagingTarget: 'windows-nsis-x64',
      provenance: releaseProvenance,
      gitCommit: releaseProvenance.gitCommit,
      dirtyTree: releaseProvenance.dirtyTree,
    });
    fs.writeFileSync(buildInfoPath, `${JSON.stringify(buildInfo, null, 2)}\n`, 'utf8');

    const installerFilename = findInstallerArtifact(artifactsDir, desktopVersion);
    const installerPath = path.join(artifactsDir, installerFilename);
    const installerStat = fs.statSync(installerPath);
    assertRealInstallerArtifact(installerFilename, installerStat.size);

    step('package-boundary', () => {
      const inspectionRoot = resolveUnpackedInspectionRoot(artifactsDir);
      const boundary = inspectPackageBoundary(inspectionRoot);
      if (!boundary.ok) {
        throw new Error(boundary.violations.map((v) => `${v.path}: ${v.reason}`).join('; '));
      }
    });

    const manifestEntries = [{
      filename: installerFilename,
      classification: ArtifactClassification.NsisInstaller,
      distributable: true,
    }];

    step('manifest', () => {
      const manifest = generateManifest(artifactsDir, manifestEntries);
      fs.writeFileSync(path.join(manifestDir, 'artifacts.manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
      fs.writeFileSync(path.join(checksumsDir, 'SHA256SUMS.txt'), renderChecksumFile(manifest), 'utf8');
    });

    step('verify-manifest', () => {
      const manifest = JSON.parse(fs.readFileSync(path.join(manifestDir, 'artifacts.manifest.json'), 'utf8'));
      verifyManifest(manifest, artifactsDir);
    });

    step('release-acceptance', () => {
      const acceptance = evaluateControlledPilotAcceptance({
        platform: process.platform,
        artifacts: manifestEntries.map((entry) => ({
          filename: entry.filename,
          classification: entry.classification,
          distributable: entry.distributable,
        })),
        releaseVerdict: 'PASS',
      });
      if (!acceptance.accepted) {
        throw new Error(acceptance.reasons.join('; '));
      }
    });

    report.status = 'PASS';
    report.verdict = 'controlled_pilot_distributable';
    report.installer = {
      filename: installerFilename,
      sizeBytes: installerStat.size,
      sha256: sha256File(installerPath),
      classification: ArtifactClassification.NsisInstaller,
      distributable: true,
    };
    report.buildInfo = buildInfo;
    report.finishedAt = new Date().toISOString();
    report.outputRoot = releaseRoot;
    fs.writeFileSync(path.join(reportsDir, 'release-report.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    console.log('\nControlled-pilot release build PASS');
    console.log(releaseRoot);
  } catch (error) {
    report.status = 'FAIL';
    report.verdict = 'failed';
    report.error = error instanceof Error ? error.message : String(error);
    report.finishedAt = new Date().toISOString();
    fs.mkdirSync(reportsDir, { recursive: true });
    fs.writeFileSync(path.join(reportsDir, 'release-report.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    console.error('\nControlled-pilot release build FAIL:', report.error);
    process.exit(1);
  }
}

main();
