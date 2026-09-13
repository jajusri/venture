import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  inspectPackagedConnectorDependencies,
  PackagedConnectorDependencyError,
} from '../../src/application/release/packaged-connector-dependency.js';

const repoRoot = path.resolve(__dirname, '../../../..');

describe('packaged connector dependency inspection', () => {
  it('fails when express is missing from packaged connector root', () => {
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-packaged-connector-'));
    try {
      const connectorRoot = path.join(tempRoot, 'resources', 'connector');
      fs.mkdirSync(path.join(connectorRoot, 'dist'), { recursive: true });
      fs.writeFileSync(path.join(connectorRoot, 'package.json'), JSON.stringify({ name: '@venture/connector', type: 'module' }), 'utf8');
      fs.writeFileSync(path.join(connectorRoot, 'dist', 'main.js'), 'import express from "express";\n', 'utf8');
      fs.writeFileSync(path.join(connectorRoot, 'VERSION.txt'), '0.3.1\n', 'utf8');
      expect(() => inspectPackagedConnectorDependencies(tempRoot)).toThrow(PackagedConnectorDependencyError);
    } finally {
      fs.rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it('fails when a devDependency is present in packaged connector root', () => {
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-packaged-connector-'));
    try {
      const connectorRoot = path.join(tempRoot, 'resources', 'connector');
      fs.mkdirSync(path.join(connectorRoot, 'dist'), { recursive: true });
      fs.mkdirSync(path.join(connectorRoot, 'node_modules', 'vitest'), { recursive: true });
      fs.mkdirSync(path.join(connectorRoot, 'node_modules', 'express'), { recursive: true });
      fs.writeFileSync(path.join(connectorRoot, 'node_modules', 'express', 'package.json'), JSON.stringify({ name: 'express', type: 'commonjs' }), 'utf8');
      fs.writeFileSync(path.join(connectorRoot, 'package.json'), JSON.stringify({ name: '@venture/connector', type: 'module' }), 'utf8');
      fs.writeFileSync(path.join(connectorRoot, 'dist', 'main.js'), 'export {};\n', 'utf8');
      fs.writeFileSync(path.join(connectorRoot, 'VERSION.txt'), '0.3.1\n', 'utf8');
      expect(() => inspectPackagedConnectorDependencies(tempRoot)).toThrow(/DevDependency/i);
    } finally {
      fs.rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it('prepares production dependencies deterministically with express present', () => {
    const result = spawnSync('node', [path.join(repoRoot, 'scripts/release/prepare-connector-packaging.mjs')], {
      cwd: repoRoot,
      encoding: 'utf8',
      shell: true,
    });
    expect(result.status).toBe(0);
    const output = JSON.parse(result.stdout.trim());
    expect(fs.existsSync(path.join(output.outputModules, 'express'))).toBe(true);
    expect(output.audit.vulnerabilityCount).toBe(0);
    expect(output.productionDependencyCount).toBeGreaterThan(0);
    expect(output.connectorVersion).toMatch(/^\d+\.\d+\.\d+/);
    expect(fs.existsSync(output.voucherRoutes.vouchersRoute)).toBe(true);
    const versionLabel = fs.readFileSync(
      path.join(repoRoot, 'apps/venture_desktop/build/VERSION.txt'),
      'utf8',
    ).trim();
    expect(versionLabel).toBe(output.connectorVersion);
    const connectorPkg = JSON.parse(
      fs.readFileSync(path.join(repoRoot, 'connector/venture_connector/package.json'), 'utf8'),
    ) as { version: string };
    expect(versionLabel).toBe(connectorPkg.version);
  }, 120000);
});

/**
 * Runs `body` inside a fresh Node process that has dynamically imported
 * prepare-connector-packaging.mjs as `mod`, and prints `JSON.stringify(...)` of the result.
 * A real subprocess (rather than an in-process dynamic import) sidesteps Vitest's SSR module
 * transform, which does not handle this script's dynamic-import-at-module-scope shape.
 */
function runPackagingProbe(body: string): unknown {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-packaging-probe-'));
  const scriptPath = path.join(tempDir, 'probe.mjs');
  const scriptUrl = pathToFileURL(path.join(repoRoot, 'scripts/release/prepare-connector-packaging.mjs')).href;
  fs.writeFileSync(
    scriptPath,
    `const mod = await import(${JSON.stringify(scriptUrl)});\nconsole.log(JSON.stringify(${body}));\n`,
    'utf8',
  );
  try {
    const result = spawnSync('node', [scriptPath], { encoding: 'utf8', shell: true });
    if (result.status !== 0) {
      throw new Error(`packaging probe failed: ${result.stderr || result.stdout}`);
    }
    return JSON.parse(result.stdout.trim());
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

describe('connector runtime version drift guard (packaging integrity)', () => {
  it('passes for the real repository: package.json version matches defaults.ts CONNECTOR_VERSION', () => {
    const result = runPackagingProbe('mod.assertConnectorRuntimeVersionMatchesPackage()') as {
      packageVersion: string;
      runtimeVersion: string;
    };
    const connectorPkg = JSON.parse(
      fs.readFileSync(path.join(repoRoot, 'connector/venture_connector/package.json'), 'utf8'),
    ) as { version: string };
    expect(result.packageVersion).toBe(connectorPkg.version);
    expect(result.runtimeVersion).toBe(connectorPkg.version);
  });

  it('throws when the packaged installer would ship a Connector artifact whose runtime-reported version disagrees with package.json (scenario 12: stale/mismatched packaged artifact)', () => {
    expect(() => runPackagingProbe("mod.compareConnectorRuntimeVersion('0.4.0', '0.0.1-stale')"))
      .toThrow(/Connector runtime version drift/);
  });

  it('throws when either version source cannot be resolved', () => {
    expect(() => runPackagingProbe("mod.compareConnectorRuntimeVersion('', '0.4.0')"))
      .toThrow(/Unable to resolve connector version/);
    expect(() => runPackagingProbe("mod.compareConnectorRuntimeVersion('0.4.0', '')"))
      .toThrow(/Unable to resolve connector version/);
  });
});

describe('first launch validation helpers', () => {
  it('classifies privacy-safe loopback health responses', async () => {
    const mod = await import(pathToFileURL(path.join(repoRoot, 'scripts/lifecycle/lifecycle-runtime-evidence.mjs')).href);
    const result = mod.classifyHealthResponse({
      status: 'degraded',
      readOnly: true,
      bindHost: '127.0.0.1',
      bindPort: 8080,
      tallyReachable: false,
      schemaVersion: '8',
    });
    expect(result.privacySafe).toBe(true);
    expect(result.loopbackOnly).toBe(true);
    expect(result.readOnly).toBe(true);
  });

  it('fails closed when health is not ready', async () => {
    const mod = await import(pathToFileURL(path.join(repoRoot, 'scripts/lifecycle/lifecycle-runtime-evidence.mjs')).href);
    const verdict = mod.deriveFirstLaunchVerdict({
      desktopAlive: true,
      logs: { mainProcessException: null, logPath: 'x', empty: false },
      connectorProcesses: [{ ProcessId: 1 }],
      connectorAlive: false,
      health: { ready: false, status: null, body: null },
      healthClassification: { privacySafe: true, loopbackOnly: true },
      schemaBefore: 7,
      schemaAfter: 7,
      schemaAfterReopen: 7,
      orphanDesktop: 0,
      orphanConnector: 0,
      startupEvidence: { startupLogPresent: true },
      connectorDiagnostics: [{ classification: { usesRepositoryPath: false, usesPackagedConnectorScript: true } }],
      reopenHealth: { ready: false },
      reopenConnectorCount: 0,
    });
    expect(verdict.verdict).toBe('FAIL');
    expect(verdict.failures).toContain('health-not-ready');
    expect(verdict.failures).toContain('schema-not-migrated-to-8');
  });
});
