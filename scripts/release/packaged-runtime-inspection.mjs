#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const desktopDist = path.join(repoRoot, 'apps/venture_desktop/dist');

export function resolveInspectionLayout(unpackedRoot = null) {
  const resolvedUnpacked = unpackedRoot ? path.resolve(unpackedRoot) : null;
  if (resolvedUnpacked) {
    const resources = path.join(resolvedUnpacked, 'resources');
    return {
      desktopMainEntry: path.join(desktopDist, 'main/main.js'),
      desktopApplicationDir: path.join(desktopDist, 'application'),
      connectorEntryScript: path.join(resources, 'connector/dist/main.js'),
      connectorPackageJson: path.join(resources, 'connector/package.json'),
      connectorResourceRoot: path.join(resources, 'connector'),
    };
  }
  return {
    desktopMainEntry: path.join(desktopDist, 'main/main.js'),
    desktopApplicationDir: path.join(desktopDist, 'application'),
    connectorEntryScript: path.join(repoRoot, 'connector/venture_connector/dist/main.js'),
    connectorPackageJson: path.join(repoRoot, 'connector/venture_connector/package.json'),
    connectorResourceRoot: path.join(repoRoot, 'connector/venture_connector'),
  };
}

export async function inspectPackagedRuntime(unpackedRoot = null) {
  const contractPath = path.join(desktopDist, 'application/release/packaged-runtime-contract.js');
  if (!fs.existsSync(contractPath)) {
    throw new Error(`Packaged runtime contract module missing: ${contractPath}. Run desktop build first.`);
  }
  const moduleUrl = pathToFileURL(contractPath).href;
  const {
    assertPackagedRuntimeContract,
  } = await import(moduleUrl);
  const layout = resolveInspectionLayout(unpackedRoot);
  assertPackagedRuntimeContract(layout);
  return layout;
}

if (import.meta.url === pathToFileURL(path.resolve(process.argv[1] ?? '')).href) {
  inspectPackagedRuntime(process.argv[2] ?? null)
    .then(() => {
      console.log('Packaged runtime contract inspection OK');
    })
    .catch((error) => {
      console.error('Packaged runtime contract inspection FAIL:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    });
}
