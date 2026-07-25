import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it, beforeEach, vi } from 'vitest';

import {
  APPROVED_NODE_EXECUTABLE_RELATIVE_PATH,
  clearPackagedNodeRuntimeVerificationCache,
  PackagedNodeRuntimeIntegrityError,
  tryResolvePackagedConnectorNodeRuntimeWithIntegrity,
  verifyPackagedNodeRuntimeIntegrity,
} from '../../src/application/release/packaged-node-runtime.js';

const BASE_MANIFEST = {
  manifestSchemaVersion: 1,
  runtime: 'node',
  version: '22.16.0',
  platform: 'win32',
  architecture: 'x64',
  license: 'MIT',
  sourceUrl: 'https://nodejs.org/dist/v22.16.0/node-v22.16.0-win-x64.zip',
  archiveSha256: 'a'.repeat(64),
  nodeExecutableRelativePath: APPROVED_NODE_EXECUTABLE_RELATIVE_PATH,
  sqliteCapable: true,
  preparedAt: '2026-01-01T00:00:00.000Z',
};

function writeSyntheticRuntime(
  root: string,
  exeContent: string,
  manifestOverrides: Record<string, unknown> = {},
): { readonly exePath: string; readonly hash: string } {
  const nodeDir = path.join(root, 'node');
  fs.mkdirSync(nodeDir, { recursive: true });
  const exePath = path.join(nodeDir, 'node.exe');
  fs.writeFileSync(exePath, exeContent, 'utf8');
  const hash = createHash('sha256').update(exeContent).digest('hex');
  fs.writeFileSync(
    path.join(nodeDir, 'node-runtime.manifest.json'),
    JSON.stringify({
      ...BASE_MANIFEST,
      nodeExecutableSha256: hash,
      ...manifestOverrides,
    }),
    'utf8',
  );
  return { exePath, hash };
}

describe('packaged node runtime integrity', () => {
  beforeEach(() => {
    clearPackagedNodeRuntimeVerificationCache();
  });

  it('accepts a valid manifest and matching executable hash', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      const { exePath } = writeSyntheticRuntime(root, 'valid-node-runtime');
      const verified = verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false });
      expect(verified.nodeExecutable).toBe(exePath);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects missing manifest', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      fs.mkdirSync(path.join(root, 'node'), { recursive: true });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(PackagedNodeRuntimeIntegrityError);
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/missing/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects malformed JSON manifest', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      const nodeDir = path.join(root, 'node');
      fs.mkdirSync(nodeDir, { recursive: true });
      fs.writeFileSync(path.join(nodeDir, 'node-runtime.manifest.json'), '{not-json', 'utf8');
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/malformed/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects unsupported manifest schema', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { manifestSchemaVersion: 99 });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/schema is unsupported/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects missing executable', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      const nodeDir = path.join(root, 'node');
      fs.mkdirSync(nodeDir, { recursive: true });
      fs.writeFileSync(
        path.join(nodeDir, 'node-runtime.manifest.json'),
        JSON.stringify({ ...BASE_MANIFEST, nodeExecutableSha256: 'b'.repeat(64) }),
        'utf8',
      );
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/executable is missing/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects unsupported runtime version', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { version: '20.18.0' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/version is unsupported/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects unsupported architecture', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { architecture: 'arm64' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/architecture is unsupported/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects invalid SHA format', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableSha256: 'not-a-sha' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/SHA-256 is invalid/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects hash mismatch', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableSha256: 'c'.repeat(64) });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/hash does not match/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects executable path traversal in manifest', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableRelativePath: '../node.exe' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/not approved|invalid executable path/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects absolute manifest executable path', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableRelativePath: 'C:/node/node.exe' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/not approved|invalid executable path/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects alternate executable filename', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableRelativePath: 'node/node.cmd' });
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false }))
        .toThrow(/not approved|invalid executable path/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('rejects unreadable executable', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      const { exePath } = writeSyntheticRuntime(root, 'node');
      const fsImpl = {
        ...fs,
        accessSync: (target: string, mode?: number) => {
          if (target === exePath) {
            throw new Error('permission denied');
          }
          return fs.accessSync(target, mode as never);
        },
      };
      expect(() => verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false, fsImpl }))
        .toThrow(/not readable/i);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('does not rehash on repeated verification for unchanged executable metadata', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'cached-node-runtime');
      const readSpy = vi.spyOn(fs, 'readFileSync');
      verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false });
      const readsAfterFirst = readSpy.mock.calls.length;
      verifyPackagedNodeRuntimeIntegrity(root, { validateSqlite: false });
      expect(readSpy.mock.calls.length).toBe(readsAfterFirst);
      readSpy.mockRestore();
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  it('returns integrity category without falling back to system Node', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-node-runtime-'));
    try {
      writeSyntheticRuntime(root, 'node', { nodeExecutableSha256: 'd'.repeat(64) });
      const resolved = tryResolvePackagedConnectorNodeRuntimeWithIntegrity(root);
      expect(resolved.executable).toBeNull();
      expect(resolved.integrityCategory).toBe('hash_mismatch');
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
});
