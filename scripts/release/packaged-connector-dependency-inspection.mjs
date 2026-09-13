#!/usr/bin/env node
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const desktopDist = path.join(__dirname, '../../apps/venture_desktop/dist/application/release/packaged-connector-dependency.js');

export async function inspectPackagedConnectorDependencies(unpackedRoot) {
  const moduleUrl = pathToFileURL(desktopDist).href;
  const { inspectPackagedConnectorDependencies: inspect } = await import(moduleUrl);
  return inspect(unpackedRoot);
}

if (import.meta.url === pathToFileURL(path.resolve(process.argv[1] ?? '')).href) {
  inspectPackagedConnectorDependencies(process.argv[2])
    .then((result) => {
      console.log(JSON.stringify(result, null, 2));
    })
    .catch((error) => {
      console.error('Packaged connector dependency inspection FAIL:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    });
}
