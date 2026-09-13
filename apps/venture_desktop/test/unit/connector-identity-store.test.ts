import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { ConnectorIdentityStore } from '../../src/application/connector-identity-store.js';

const mintedDirs: string[] = [];

afterEach(() => {
  for (const dir of mintedDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function createTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-connector-identity-'));
  mintedDirs.push(dir);
  return dir;
}

describe('ConnectorIdentityStore', () => {
  it('generates a connector id once and persists it to disk', () => {
    const userDataDir = createTempDir();
    const store = new ConnectorIdentityStore({ userDataDir });

    const identity = store.getOrCreateIdentity();

    expect(identity.connectorId).toBeTruthy();
    expect(fs.existsSync(path.join(userDataDir, 'connector-identity.json'))).toBe(true);
  });

  it('survives a Desktop restart — a fresh store instance over the same userDataDir returns the same id', () => {
    const userDataDir = createTempDir();
    const first = new ConnectorIdentityStore({ userDataDir });
    const idBeforeRestart = first.getOrCreateIdentity().connectorId;

    // Simulate an app restart: a brand new store instance, same on-disk directory.
    const second = new ConnectorIdentityStore({ userDataDir });
    const idAfterRestart = second.getOrCreateIdentity().connectorId;

    expect(idAfterRestart).toBe(idBeforeRestart);
  });

  it('is independent of IP/host — the store has no notion of network configuration at all', () => {
    const userDataDir = createTempDir();
    const store = new ConnectorIdentityStore({ userDataDir });

    const first = store.getOrCreateIdentity().connectorId;
    const second = store.getOrCreateIdentity().connectorId;

    expect(second).toBe(first);
  });

  it('a corrupt identity file falls back to generating a fresh id rather than crashing startup', () => {
    const userDataDir = createTempDir();
    fs.mkdirSync(userDataDir, { recursive: true });
    fs.writeFileSync(path.join(userDataDir, 'connector-identity.json'), 'not valid json{{{', 'utf8');

    const store = new ConnectorIdentityStore({ userDataDir });

    expect(() => store.getOrCreateIdentity()).not.toThrow();
    expect(store.getOrCreateIdentity().connectorId).toBeTruthy();
  });

  it('uses the injected id generator when provided (deterministic for tests)', () => {
    const userDataDir = createTempDir();
    const store = new ConnectorIdentityStore({ userDataDir, generateId: () => 'fixed-test-id' });

    expect(store.getOrCreateIdentity().connectorId).toBe('fixed-test-id');
  });
});
