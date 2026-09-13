import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { parse } from 'yaml';
import { describe, expect, it } from 'vitest';

const root = join(dirname(fileURLToPath(import.meta.url)), '../..');
const specPath = join(root, 'docs/openapi/connector-v1.yaml');
const spec = parse(readFileSync(specPath, 'utf8'));

describe('OpenAPI connector contract', () => {
  it('declares Venture connector title and v1 scope', () => {
    expect(spec.info.title).toBe('Venture Connector API');
    expect(spec.info.version).toBe('1.0.0');
  });

  it('defines read-only health endpoint', () => {
    expect(spec.paths['/health'].get).toBeDefined();
    expect(spec.components.schemas.HealthResponse.properties.readOnly.const).toBe(true);
  });

  it('includes all MVP 1 illustrative endpoints', () => {
    const requiredPaths = [
      '/health',
      '/device/pair',
      '/companies',
      '/companies/{companyId}/ledgers',
      '/companies/{companyId}/ledgers/{ledgerId}',
      '/companies/{companyId}/ledger-transactions',
      '/companies/{companyId}/vouchers',
      '/companies/{companyId}/vouchers/{voucherId}',
      '/sync/checkpoint',
    ];
    for (const path of requiredPaths) {
      expect(spec.paths[path], `missing path ${path}`).toBeDefined();
    }
  });

  it('uses explicit Dr/Cr money semantics', () => {
    expect(spec.components.schemas.Money.properties.side.enum).toEqual(['Dr', 'Cr']);
  });
});

describe('Architecture invariants', () => {
  it('does not define Tally write endpoints', () => {
    const paths = Object.keys(spec.paths);
    const writePaths = paths.filter((path) => {
      const methods = spec.paths[path];
      return methods.post || methods.put || methods.patch || methods.delete;
    });
    expect(writePaths).toEqual(['/device/pair']);
  });
});
