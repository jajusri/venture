import { describe, expect, it } from 'vitest';

import { NodeProcessSpawner } from '../../src/application/node-process-spawner.js';

describe('NodeProcessSpawner startup diagnostics', () => {
  it('captures bounded sanitized stderr while leaving stdout discarded', async () => {
    const diagnostics: string[] = [];
    const spawner = new NodeProcessSpawner({
      maxDiagnosticBytes: 256,
      onDiagnostic: ({ text }) => diagnostics.push(text),
    });
    const child = spawner.spawn({
      command: process.execPath,
      args: ['-e', "console.log('stdout-not-captured'); console.error('Fatal bootstrap error Authorization: Bearer secret-token')"],
      cwd: process.cwd(),
      env: { PATH: process.env.PATH ?? '', SystemRoot: process.env.SystemRoot ?? '' },
    });
    await new Promise<void>((resolve) => child.onExit(() => resolve()));
    const captured = diagnostics.join(' ');
    expect(captured).toContain('Fatal bootstrap error');
    expect(captured).not.toContain('stdout-not-captured');
    expect(captured).not.toContain('secret-token');
    expect(Buffer.byteLength(captured, 'utf8')).toBeLessThanOrEqual(2_000);
  });
});
