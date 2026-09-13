#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateManifest, renderChecksumFile, verifyManifest } from '../release/manifest.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const out = path.join(repoRoot, process.argv[2] ?? '');
const installer = 'VentureDesktop-0.4.3-x64-setup.exe';

const installerPath = path.join(out, installer);
const sha256 = crypto.createHash('sha256').update(fs.readFileSync(installerPath)).digest('hex');
const manifest = generateManifest(out, [{
  filename: installer,
  classification: 'pre_commit_runtime_proof_only',
  distributable: false,
}]);
fs.writeFileSync(path.join(out, 'artifacts.manifest.json'), JSON.stringify(manifest, null, 2));
fs.writeFileSync(path.join(out, 'SHA256SUMS.txt'), renderChecksumFile(manifest));
verifyManifest(manifest, out);

const sourceBuildInfo = JSON.parse(fs.readFileSync(
  path.join(repoRoot, 'apps/venture_desktop/dist/main/build-info.json'),
  'utf8',
));
const buildInfo = {
  ...sourceBuildInfo,
  evidenceClassification: 'precommit_hardening_evidence_only',
  distributable: false,
  officialReleaseCandidate: false,
  dirtyTree: true,
};
fs.writeFileSync(path.join(out, 'build-info.json'), JSON.stringify(buildInfo, null, 2));
console.log(JSON.stringify({
  sha256,
  sizeBytes: fs.statSync(installerPath).size,
  dirtyTree: buildInfo.dirtyTree,
  officialCandidateSha256: '911422191ea977558f73fc756b6eb32dd701e01b16fb6626fc030da6e6480bdd',
  officialCandidateUnchanged: sha256 !== '911422191ea977558f73fc756b6eb32dd701e01b16fb6626fc030da6e6480bdd',
}, null, 2));
