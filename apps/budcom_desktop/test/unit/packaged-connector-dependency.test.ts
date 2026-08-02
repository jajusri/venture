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
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-packaged-connector-'));
    const connectorRoot = path.join(tempRoot, 'resources', 'connector');
    fs.mkdirSync(path.join(connectorRoot, 'dist'), { recursive: true });
    fs.writeFileSync(path.join(connectorRoot, 'package.json'), JSON.stringify({ name: '@budcom/connector', type: 'module' }), 'utf8');
    fs.writeFileSync(path.join(connectorRoot, 'dist', 'main.js'), 'import express from "express";\n', 'utf8');
    fs.writeFileSync(path.join(connectorRoot, 'VERSION.txt'), '0.3.1\n', 'utf8');
    expect(() => inspectPackagedConnectorDependencies(tempRoot)).toThrow(PackagedConnectorDependencyError);
    fs.rmSync(tempRoot, { recursive: true, force: true });
  });

  it('fails when a devDependency is present in packaged connector root', () => {
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-packaged-connector-'));
    const connectorRoot = path.join(tempRoot, 'resources', 'connector');
    fs.mkdirSync(path.join(connectorRoot, 'dist'), { recursive: true });
    fs.mkdirSync(path.join(connectorRoot, 'node_modules', 'vitest'), { recursive: true });
    fs.mkdirSync(path.join(connectorRoot, 'node_modules', 'express'), { recursive: true });
    fs.writeFileSync(path.join(connectorRoot, 'node_modules', 'express', 'package.json'), JSON.stringify({ name: 'express', type: 'commonjs' }), 'utf8');
    fs.writeFileSync(path.join(connectorRoot, 'package.json'), JSON.stringify({ name: '@budcom/connector', type: 'module' }), 'utf8');
    fs.writeFileSync(path.join(connectorRoot, 'dist', 'main.js'), 'export {};\n', 'utf8');
    fs.writeFileSync(path.join(connectorRoot, 'VERSION.txt'), '0.3.1\n', 'utf8');
    expect(() => inspectPackagedConnectorDependencies(tempRoot)).toThrow(/DevDependency/i);
    fs.rmSync(tempRoot, { recursive: true, force: true });
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
      path.join(repoRoot, 'apps/budcom_desktop/build/VERSION.txt'),
      'utf8',
    ).trim();
    expect(versionLabel).toBe(output.connectorVersion);
    const connectorPkg = JSON.parse(
      fs.readFileSync(path.join(repoRoot, 'connector/budcom_connector/package.json'), 'utf8'),
    ) as { version: string };
    expect(versionLabel).toBe(connectorPkg.version);
  }, 120000);
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
