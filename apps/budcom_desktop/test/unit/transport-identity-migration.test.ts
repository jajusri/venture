import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { migrateLegacyTransportIdentity, resolvePackagedLegacyTransportIdentityDir } from '../../src/application/release/transport-identity-migration.js';

const tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
  }
});

function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-transport-identity-migration-test-'));
  tempDirs.push(dir);
  return dir;
}

function writeIdentity(dir: string, keyContent: string, certContent: string): void {
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'transport-key.pem'), keyContent, 'utf8');
  fs.writeFileSync(path.join(dir, 'transport-cert.pem'), certContent, 'utf8');
}

describe('migrateLegacyTransportIdentity', () => {
  it('derives the real packaged legacy layout beside dist/main.js', () => {
    expect(resolvePackagedLegacyTransportIdentityDir(
      'C:\\Program Files\\Budcom Desktop\\resources\\connector\\dist\\main.js',
    )).toBe(path.normalize('C:\\Program Files\\Budcom Desktop\\resources\\connector\\dist\\data\\transport'));
  });
  it('clean first install: neither persistent nor legacy identity exists — no-op, nothing created (scenario 6)', () => {
    const persistentDir = path.join(makeTempDir(), 'persistent');
    const legacyDir = path.join(makeTempDir(), 'legacy');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome).toEqual({ migrated: false, reason: 'no-legacy-identity-found' });
    expect(fs.existsSync(persistentDir)).toBe(false);
  });

  it('reuses an existing persistent identity untouched when one is already present (scenario 3)', () => {
    const persistentDir = makeTempDir();
    const legacyDir = makeTempDir();
    writeIdentity(persistentDir, 'PERSISTENT-KEY', 'PERSISTENT-CERT');
    writeIdentity(legacyDir, 'LEGACY-KEY', 'LEGACY-CERT');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome).toEqual({ migrated: false, reason: 'persistent-identity-already-present' });
    expect(fs.readFileSync(path.join(persistentDir, 'transport-key.pem'), 'utf8')).toBe('PERSISTENT-KEY');
    expect(fs.readFileSync(path.join(persistentDir, 'transport-cert.pem'), 'utf8')).toBe('PERSISTENT-CERT');
  });

  it('explicitly reports legitimate re-pair when a prior installation has no recoverable identity', () => {
    const outcome = migrateLegacyTransportIdentity({
      persistentDir: path.join(makeTempDir(), 'persistent'),
      legacyDir: path.join(makeTempDir(), 'legacy'),
      existingInstallation: true,
    });
    expect(outcome).toEqual({
      migrated: false,
      reason: 'no-legacy-identity-found',
      requiresRePair: true,
    });
  });

  it('migrates a legacy identity byte-for-byte, which is what preserves its exact fingerprint (scenario 4)', () => {
    const persistentDir = path.join(makeTempDir(), 'persistent');
    const legacyDir = makeTempDir();
    writeIdentity(legacyDir, 'LEGACY-KEY-MATERIAL', 'LEGACY-CERT-MATERIAL');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome).toEqual({ migrated: true });
    // The fingerprint is a pure function of the certificate's SPKI bytes (see
    // ConnectorTransportIdentityService / connector-transport-identity.test.ts, unmodified by
    // this change) — byte-identical copies are what guarantee an identical fingerprint.
    expect(fs.readFileSync(path.join(persistentDir, 'transport-key.pem'), 'utf8')).toBe('LEGACY-KEY-MATERIAL');
    expect(fs.readFileSync(path.join(persistentDir, 'transport-cert.pem'), 'utf8')).toBe('LEGACY-CERT-MATERIAL');
    // Legacy source is left intact (copy, not move) so an interrupted migration is retryable.
    expect(fs.readFileSync(path.join(legacyDir, 'transport-key.pem'), 'utf8')).toBe('LEGACY-KEY-MATERIAL');
    expect(fs.readFileSync(path.join(legacyDir, 'transport-cert.pem'), 'utf8')).toBe('LEGACY-CERT-MATERIAL');
  });

  it('never overwrites an existing persistent identity even when legacy content differs (scenario 5)', () => {
    const persistentDir = makeTempDir();
    const legacyDir = makeTempDir();
    writeIdentity(persistentDir, 'CURRENT-PERSISTENT-KEY', 'CURRENT-PERSISTENT-CERT');
    writeIdentity(legacyDir, 'DIFFERENT-LEGACY-KEY', 'DIFFERENT-LEGACY-CERT');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome.migrated).toBe(false);
    expect(fs.readFileSync(path.join(persistentDir, 'transport-key.pem'), 'utf8')).toBe('CURRENT-PERSISTENT-KEY');
    expect(fs.readFileSync(path.join(persistentDir, 'transport-cert.pem'), 'utf8')).toBe('CURRENT-PERSISTENT-CERT');
  });

  it('never guess-repairs a partial persistent identity from legacy material', () => {
    const persistentDir = makeTempDir();
    const legacyDir = makeTempDir();
    fs.writeFileSync(path.join(persistentDir, 'transport-key.pem'), 'ONLY-KEY-PRESENT', 'utf8');
    writeIdentity(legacyDir, 'LEGACY-KEY', 'LEGACY-CERT');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome).toEqual({ migrated: false, reason: 'persistent-identity-partial' });
    expect(fs.existsSync(path.join(persistentDir, 'transport-cert.pem'))).toBe(false);
  });

  it('does not migrate a partial legacy identity (only one of key/cert present)', () => {
    const persistentDir = path.join(makeTempDir(), 'persistent');
    const legacyDir = makeTempDir();
    fs.writeFileSync(path.join(legacyDir, 'transport-key.pem'), 'ONLY-LEGACY-KEY', 'utf8');

    const outcome = migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    expect(outcome).toEqual({ migrated: false, reason: 'no-legacy-identity-found' });
    expect(fs.existsSync(persistentDir)).toBe(false);
  });

  it('leaves no temporary ".migrating" artifacts behind after a successful migration', () => {
    const persistentDir = path.join(makeTempDir(), 'persistent');
    const legacyDir = makeTempDir();
    writeIdentity(legacyDir, 'LEGACY-KEY', 'LEGACY-CERT');

    migrateLegacyTransportIdentity({ persistentDir, legacyDir });

    const persistedFiles = fs.readdirSync(persistentDir);
    expect(persistedFiles.sort()).toEqual(['transport-cert.pem', 'transport-key.pem']);
  });

  it('never logs or surfaces private key material through the outcome or onOutcome callback', () => {
    const persistentDir = path.join(makeTempDir(), 'persistent');
    const legacyDir = makeTempDir();
    writeIdentity(legacyDir, 'SECRET-LEGACY-KEY-MATERIAL', 'LEGACY-CERT');

    let observed: unknown;
    const outcome = migrateLegacyTransportIdentity({
      persistentDir,
      legacyDir,
      onOutcome: (result) => {
        observed = result;
      },
    });

    expect(JSON.stringify(outcome)).not.toContain('SECRET-LEGACY-KEY-MATERIAL');
    expect(JSON.stringify(observed)).not.toContain('SECRET-LEGACY-KEY-MATERIAL');
  });
});
