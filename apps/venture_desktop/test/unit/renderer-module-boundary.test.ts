import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('packaged renderer module boundary', () => {
  it('does not import runtime values from CommonJS application output', () => {
    const rendererPath = path.resolve(__dirname, '../../src/renderer/scripts/app.ts');
    const source = fs.readFileSync(rendererPath, 'utf8');
    const runtimeApplicationImports = [...source.matchAll(/^import\s+(?!type\b).*?from\s+['"]\.\.\/\.\.\/application\/.*?['"];?$/gm)];

    expect(runtimeApplicationImports.map((match) => match[0])).toEqual([]);
  });
});
