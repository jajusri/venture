import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

import { describe, expect, it } from 'vitest';

import {
  assertCommonJsRuntimeSource,
  assertEsmRuntimeSource,
  assertPackagedRuntimeContract,
  assertPackagedRuntimeContractFailsForEsmOutsidePackage,
  assertPackagedRuntimeContractFailsWithoutConnectorPackage,
  assertPathWithinPackageBoundary,
  containsTopLevelEsmSyntax,
  PackagedRuntimeContractError,
  readConnectorPackageType,
  resolveDefaultPackagedRuntimeLayout,
  runNodeSyntaxCheck,
} from '../../src/application/release/packaged-runtime-contract.js';
import { PRODUCTION_DEFAULTS } from '../../src/application/desktop-config-defaults.js';
import { shouldSpawnConnectorViaElectronNode } from '../../src/application/release/connector-packaged-paths.js';

const repoRoot = path.resolve(__dirname, '../../../..');
const requireFromHere = createRequire(__filename);

describe('packaged runtime module-format contract', () => {
  const layout = resolveDefaultPackagedRuntimeLayout(repoRoot);

  it('detects top-level ESM syntax', () => {
    expect(containsTopLevelEsmSyntax('import fs from "node:fs";')).toBe(true);
    expect(containsTopLevelEsmSyntax('"use strict";\nconst fs = require("node:fs");')).toBe(false);
  });

  it('accepts packaged desktop main entry as CommonJS', () => {
    const source = fs.readFileSync(layout.desktopMainEntry, 'utf8');
    assertCommonJsRuntimeSource('desktop main entry', source);
    runNodeSyntaxCheck(layout.desktopMainEntry);
  });

  it('rejects incompatible top-level ESM in desktop application modules', () => {
    expect(() => assertCommonJsRuntimeSource('desktop module', 'import fs from "node:fs";')).toThrow(/ESM/);
  });

  it('accepts connector dist/main.js as ESM', () => {
    const source = fs.readFileSync(layout.connectorEntryScript, 'utf8');
    assertEsmRuntimeSource('connector dist/main.js', source);
    runNodeSyntaxCheck(layout.connectorEntryScript, 'module');
  });

  it('requires packaged connector package.json with type=module', () => {
    expect(readConnectorPackageType(layout.connectorPackageJson)).toBe('module');
  });

  it('requires connector script inside connector package boundary', () => {
    expect(() => assertPathWithinPackageBoundary(layout.connectorEntryScript, layout.connectorResourceRoot)).not.toThrow();
  });

  it('loads diagnostic-allowlist.js via CommonJS require and exercises randomUUID path', () => {
    const modulePath = path.join(layout.desktopApplicationDir, 'diagnostic-allowlist.js');
    const loaded = requireFromHere(modulePath) as typeof import('../../src/application/diagnostic-allowlist.js');
    const bundle = loaded.buildSafeDiagnosticBundle({
      generatedAt: '2026-01-01T00:00:00.000Z',
      desktopVersion: '0.4.3',
      connectorVersion: '0.3.1',
      electronVersion: 'test-electron',
      nodeVersion: process.versions.node,
      platform: process.platform,
      osRelease: 'test',
      architecture: 'x64',
      uptimeSeconds: 10,
      connectorBaseUrl: 'http://127.0.0.1:8080',
      connectorBindHost: '127.0.0.1',
      connectorNetworkExposure: 'loopback',
      connectorNetworkExposureWarning: null,
      connectorProcessState: 'Connected',
      connectorOwnership: 'desktop-managed',
      connectorPid: 1,
      healthStatus: 'ok',
      healthReachable: true,
      lastSuccessfulHealthCheck: '2026-01-01T00:00:00.000Z',
      tallyReachable: false,
      sessionStatus: 'NO_COMPANY',
      selectedCompanyPresent: false,
      configuration: {
        effective: PRODUCTION_DEFAULTS,
        sources: {},
        status: 'loaded',
      },
      environment: {},
      recentLifecycleEvents: [],
      recentErrors: [],
      logFilePath: 'venture-desktop.log',
      fileLoggingAvailable: true,
    });
    expect(bundle.correlationId).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it('fails when connector package.json is missing', () => {
    expect(() => assertPackagedRuntimeContractFailsWithoutConnectorPackage(layout)).not.toThrow();
  });

  it('fails when connector ESM is outside package scope', () => {
    expect(() => assertPackagedRuntimeContractFailsForEsmOutsidePackage(layout)).not.toThrow();
  });

  it('passes full packaged runtime contract inspection', () => {
    expect(() => assertPackagedRuntimeContract(layout)).not.toThrow();
  });

  it('requires a Node host runtime with node:sqlite instead of Electron-as-Node', () => {
    expect(shouldSpawnConnectorViaElectronNode('C:/Apps/Venture Desktop/Venture Desktop.exe')).toBe(false);
    expect(shouldSpawnConnectorViaElectronNode('C:/Program Files/nodejs/node.exe')).toBe(false);
  });

  it('fails CommonJS/ESM mismatch before acceptance when desktop entry is ESM', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-runtime-bad-'));
    try {
      const badMain = path.join(tempDir, 'main.js');
      fs.writeFileSync(badMain, 'import fs from "node:fs";\n', 'utf8');
      expect(() => assertCommonJsRuntimeSource('bad desktop entry', fs.readFileSync(badMain, 'utf8')))
        .toThrow(PackagedRuntimeContractError);
    } finally {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
  });
});
