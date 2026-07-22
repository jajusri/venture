import { readdirSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { ServiceTokens } from '../../src/core/tokens.js';
import { registerServices } from '../../src/bootstrap/register-services.js';

/**
 * Architecture / dependency-boundary tests.
 *
 * TypeScript `private` and `readonly` are NOT security boundaries. These tests
 * fail the build if any module crosses an enforced boundary.
 */

const SRC_ROOT = fileURLToPath(new URL('../../src', import.meta.url));

interface SourceFile {
  readonly relPath: string;
  readonly imports: string[];
  readonly content: string;
}

function collectTsFiles(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      collectTsFiles(full, out);
    } else if (entry.endsWith('.ts')) {
      out.push(full);
    }
  }
}

function loadSources(): SourceFile[] {
  const files: string[] = [];
  collectTsFiles(SRC_ROOT, files);
  const importPattern = /from\s+['"]([^'"]+)['"]/g;
  return files.map((full) => {
    const content = readFileSync(full, 'utf8');
    const imports: string[] = [];
    let match: RegExpExecArray | null;
    while ((match = importPattern.exec(content)) !== null) {
      imports.push(match[1]!);
    }
    const relPath = full.slice(SRC_ROOT.length + 1).split('\\').join('/');
    return { relPath, imports, content };
  });
}

const SOURCES = loadSources();

function inAnyScope(relPath: string, scopes: string[]): boolean {
  return scopes.some((scope) => relPath.startsWith(scope));
}

function importsAny(file: SourceFile, needles: string[]): string[] {
  return file.imports.filter((spec) => needles.some((needle) => spec.includes(needle)));
}

/** Application/business scopes — not connector parsing infrastructure (extraction/). */
const BUSINESS_SCOPES = [
  'api/',
  'services/extraction/',
  'services/tally/',
  'ingestion/',
];

/** Tally-specific imports forbidden in business scopes. */
const TALLY_INTERNALS = [
  'tally/gateway',
  'tally/registry',
  'tally/transport',
  'tally/connection',
  'tally/xml/response-parser',
  'tally/xml/request-builder',
  'tally/discovery',
];

describe('module boundaries', () => {
  const RAW_TRANSPORT = ['tally/transport', 'tally-http-transport'];
  const CONNECTION = ['tally/connection', 'tally-connection-manager'];
  const NETWORK = ['node:net', 'node:http', 'node:https', 'node:tls', 'node-fetch', 'undici'];

  it('application code never imports the raw HTTP transport', () => {
    const violations = SOURCES.filter(
      (f) => inAnyScope(f.relPath, BUSINESS_SCOPES) && importsAny(f, RAW_TRANSPORT).length > 0,
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('application code never imports the connection manager directly', () => {
    // Lifecycle/diagnostics services (services/tally/) may use connectionManager
    // for ping and diagnostics — they have no exchange() bypass.
    const scopes = ['api/', 'services/extraction/'];
    const violations = SOURCES.filter(
      (f) => inAnyScope(f.relPath, scopes) && importsAny(f, CONNECTION).length > 0,
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('business read services never import Tally adapter internals (gateway, registry, parsers)', () => {
    const scopes = ['api/', 'services/extraction/'];
    const violations = SOURCES.filter(
      (f) => inAnyScope(f.relPath, scopes) && importsAny(f, TALLY_INTERNALS).length > 0,
    ).map((f) => `${f.relPath} -> ${importsAny(f, TALLY_INTERNALS).join(', ')}`);
    expect(violations).toEqual([]);
  });

  it('business read services never reference rawXml or ParsedXmlNode', () => {
    const scopes = ['api/', 'services/extraction/', 'services/tally/'];
    const violations = SOURCES.filter(
      (f) =>
        inAnyScope(f.relPath, scopes) &&
        (/\brawXml\b/.test(f.content) || /\bParsedXmlNode\b/.test(f.content)),
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('extraction and ingestion code never import raw network modules', () => {
    const violations = SOURCES.filter(
      (f) =>
        inAnyScope(f.relPath, ['extraction/', 'ingestion/', 'services/extraction/']) &&
        importsAny(f, NETWORK).length > 0,
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('offline XML ingestion cannot import live Tally communication', () => {
    const violations = SOURCES.filter(
      (f) =>
        f.relPath.startsWith('ingestion/') &&
        importsAny(f, ['tally/transport', 'tally/connection', 'tally/gateway']).length > 0,
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('the Tally XML request builder class is only used inside the tally adapter', () => {
    const violations = SOURCES.filter(
      (f) =>
        !f.relPath.startsWith('tally/') &&
        f.relPath !== 'bootstrap/register-services.ts' &&
        /\bTallyXmlRequestBuilder\b/.test(f.content),
    ).map((f) => f.relPath);
    expect(violations).toEqual([]);
  });

  it('ERP-neutral policy engine has no Tally module imports', () => {
    const erpPolicyFiles = SOURCES.filter((f) => f.relPath.startsWith('erp/policy/'));
    expect(erpPolicyFiles.length).toBeGreaterThan(0);
    for (const file of erpPolicyFiles) {
      expect(importsAny(file, ['tally/', '../tally', '../../tally']).length).toBe(0);
      expect(importsAny(file, ['ApprovedOperationId', 'TallyCapability']).length).toBe(0);
    }
  });

  it('business services depend on ErpReadPort, not TallyReadGateway', () => {
    const gatewayInBusiness = SOURCES.filter(
      (f) =>
        inAnyScope(f.relPath, ['services/extraction/', 'services/tally/']) &&
        importsAny(f, ['tally/gateway']).length > 0,
    ).map((f) => f.relPath);
    expect(gatewayInBusiness).toEqual([]);

    const portUsers = SOURCES.filter(
      (f) =>
        inAnyScope(f.relPath, ['services/extraction/', 'services/tally/']) &&
        importsAny(f, ['erp/ports']).length > 0,
    );
    expect(portUsers.length).toBeGreaterThan(0);
  });
});

describe('DI boundary enforcement', () => {
  it('registers ErpReadPort but not TallyModule', () => {
    const tokenValues = Object.values(ServiceTokens);
    expect(tokenValues).toContain('ErpReadPort');
    expect(tokenValues).not.toContain('TallyModule');
  });

  it('composition root resolves ErpReadPort for business services', () => {
    const ctx = registerServices({ env: 'test' });
    expect(ctx.container.has(ServiceTokens.ErpReadPort)).toBe(true);
    expect(ctx.container.has('TallyModule' as typeof ServiceTokens.ErpReadPort)).toBe(false);
  });

  it('TallyConnectionService interface has no exchange method', async () => {
    const { TallyConnectionServiceImpl } = await import(
      '../../src/services/tally/tally-connection.service.js'
    );
    const proto = TallyConnectionServiceImpl.prototype as unknown as Record<string, unknown>;
    expect(typeof proto.exchange).toBe('undefined');
    expect(typeof proto.ping).toBe('function');
  });
});
