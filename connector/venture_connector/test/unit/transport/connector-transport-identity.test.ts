import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';
import selfsigned from 'selfsigned';

import {
  ConnectorTransportIdentityService,
} from '../../../src/services/transport/connector-transport-identity.js';
import { isAppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';
import type { Logger, LogContext } from '../../../src/infrastructure/logging/logger.js';

const tempDirs: string[] = [];

afterEach(async () => {
  await cleanupTestSqliteStorage();
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // Windows may keep file handles briefly after close.
    }
  }
});

function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-transport-identity-test-'));
  tempDirs.push(dir);
  return dir;
}

interface CapturedLogEntry {
  readonly message: string;
  readonly context: LogContext | undefined;
}

function createCapturingLogger(): { logger: Logger; entries: CapturedLogEntry[] } {
  const entries: CapturedLogEntry[] = [];
  const capture = (message: string, context?: LogContext) => {
    entries.push({ message, context });
  };
  const logger: Logger = {
    debug: capture,
    info: capture,
    warn: capture,
    error: capture,
    child: () => logger,
  };
  return { logger, entries };
}

describe('ConnectorTransportIdentityService', () => {
  it('generates a transport keypair only once — a second call returns the identical cached material', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();
    const service = new ConnectorTransportIdentityService(dir, logger);

    const first = await service.getServerCredentials();
    const second = await service.getServerCredentials();

    expect(second.key).toBe(first.key);
    expect(second.cert).toBe(first.cert);
  });

  it('reloads the same keypair after a restart (new service instance, same directory)', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();

    const before = await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();
    const after = await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();

    expect(after.key).toBe(before.key);
    expect(after.cert).toBe(before.cert);
  });

  it('two independent Connector data directories produce different keys and fingerprints', async () => {
    const dirA = makeTempDir();
    const dirB = makeTempDir();
    const { logger } = createCapturingLogger();

    const identityA = await new ConnectorTransportIdentityService(dirA, logger).getIdentity();
    const identityB = await new ConnectorTransportIdentityService(dirB, logger).getIdentity();

    expect(identityA.fingerprint).not.toBe(identityB.fingerprint);
  });

  it('private key is never stored in SQLite', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const dbPath = path.join(basePath, 'venture-ledger.db');
    const { logger } = createCapturingLogger();
    const transportDir = path.join(basePath, 'transport');

    const credentials = await new ConnectorTransportIdentityService(transportDir, logger).getServerCredentials();
    // Force the sqlite file to exist on disk before scanning it.
    storage.getBundle();

    const dbBytes = fs.readFileSync(dbPath, 'utf8');
    expect(dbBytes).not.toContain('BEGIN PRIVATE KEY');
    expect(dbBytes).not.toContain(credentials.key.trim());
  });

  it('private key is never logged, including during generation and renewal', async () => {
    const dir = makeTempDir();
    const { logger, entries } = createCapturingLogger();
    const service = new ConnectorTransportIdentityService(dir, logger);

    const credentials = await service.getServerCredentials();

    // Force a renewal too, by seeding an already-expired cert with the same key, then resolving
    // again with a fresh service instance sharing the capturing logger.
    const certPath = path.join(dir, 'transport-cert.pem');
    const expiredCert = await selfSignExpired(credentials.key);
    fs.writeFileSync(certPath, expiredCert, 'utf8');

    await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();

    const serialized = JSON.stringify(entries);
    expect(serialized).not.toContain('BEGIN PRIVATE KEY');
    expect(serialized).not.toContain(credentials.key.trim());
  });

  it('corrupt private-key material fails explicitly instead of silently regenerating', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();

    // Seed a valid-looking pair, then corrupt only the key file.
    await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();
    fs.writeFileSync(path.join(dir, 'transport-key.pem'), 'not a real key', 'utf8');

    const broken = new ConnectorTransportIdentityService(dir, logger);
    await expect(broken.getServerCredentials()).rejects.toSatisfy(
      (error: unknown) => isAppError(error) && error.code === ErrorCodes.TRANSPORT_IDENTITY_ERROR,
    );
  });

  it('mismatched key and certificate fail explicitly instead of silently replacing either file', async () => {
    const dirA = makeTempDir();
    const dirB = makeTempDir();
    const { logger } = createCapturingLogger();

    await new ConnectorTransportIdentityService(dirA, logger).getServerCredentials();
    await new ConnectorTransportIdentityService(dirB, logger).getServerCredentials();

    const mismatchedDir = makeTempDir();
    fs.copyFileSync(path.join(dirA, 'transport-key.pem'), path.join(mismatchedDir, 'transport-key.pem'));
    fs.copyFileSync(path.join(dirB, 'transport-cert.pem'), path.join(mismatchedDir, 'transport-cert.pem'));

    const broken = new ConnectorTransportIdentityService(mismatchedDir, logger);
    await expect(broken.getServerCredentials()).rejects.toSatisfy(
      (error: unknown) => isAppError(error) && error.code === ErrorCodes.TRANSPORT_IDENTITY_ERROR,
    );
  });

  it('a failed corrupt/mismatched resolution leaves the persisted files untouched (no silent regeneration)', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();
    await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();

    const keyPath = path.join(dir, 'transport-key.pem');
    fs.writeFileSync(keyPath, 'not a real key', 'utf8');
    const corruptedContent = fs.readFileSync(keyPath, 'utf8');

    const broken = new ConnectorTransportIdentityService(dir, logger);
    await expect(broken.getServerCredentials()).rejects.toBeTruthy();

    expect(fs.readFileSync(keyPath, 'utf8')).toBe(corruptedContent);
  });

  it('SPKI fingerprint is stable across a restart (new instance, same directory)', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();

    const before = await new ConnectorTransportIdentityService(dir, logger).getIdentity();
    const after = await new ConnectorTransportIdentityService(dir, logger).getIdentity();

    expect(after.fingerprint).toBe(before.fingerprint);
  });

  it('SPKI fingerprint is stable across certificate renewal using the same key, and the key file is unchanged', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();

    const original = await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();
    const originalIdentity = await new ConnectorTransportIdentityService(dir, logger).getIdentity();

    // Seed an already-expired certificate signed with the SAME persisted key, simulating a
    // Connector that has been running past its certificate's validity window.
    const certPath = path.join(dir, 'transport-cert.pem');
    const expiredCert = await selfSignExpired(original.key);
    fs.writeFileSync(certPath, expiredCert, 'utf8');
    const beforeRenewalCertContent = fs.readFileSync(certPath, 'utf8');

    const renewed = await new ConnectorTransportIdentityService(dir, logger).getIdentity();
    const renewedCredentials = await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();

    expect(renewed.fingerprint).toBe(originalIdentity.fingerprint);
    expect(renewedCredentials.key.trim()).toBe(original.key.trim());
    expect(fs.readFileSync(certPath, 'utf8')).not.toBe(beforeRenewalCertContent);
    expect(new Date(renewed.notAfter).getTime()).toBeGreaterThan(Date.now());
  });

  it('fingerprint changes with a different key (independent Connectors are distinguishable)', async () => {
    const dirA = makeTempDir();
    const dirB = makeTempDir();
    const { logger } = createCapturingLogger();

    const identityA = await new ConnectorTransportIdentityService(dirA, logger).getIdentity();
    const identityB = await new ConnectorTransportIdentityService(dirB, logger).getIdentity();

    expect(identityA.fingerprint).not.toBe(identityB.fingerprint);
    expect(identityA.fingerprint).toMatch(/^sha256\//);
    expect(identityB.fingerprint).toMatch(/^sha256\//);
  });

  it('the generated certificate contains no customer, company, or accounting information', async () => {
    const dir = makeTempDir();
    const { logger } = createCapturingLogger();
    const credentials = await new ConnectorTransportIdentityService(dir, logger).getServerCredentials();

    const { X509Certificate } = await import('node:crypto');
    const certificate = new X509Certificate(credentials.cert);

    expect(certificate.subject).not.toMatch(/company|customer|voucher|ledger|tally/i);
    expect(certificate.subject).toContain('venture-connector.local');
  });
});

async function selfSignExpired(privateKeyPem: string): Promise<string> {
  const crypto = await import('node:crypto');
  const publicKeyPem = crypto
    .createPublicKey(privateKeyPem)
    .export({ type: 'spki', format: 'pem' }) as string;

  const pems = await selfsigned.generate(
    [{ name: 'commonName', value: 'venture-connector.local' }],
    {
      keyType: 'ec',
      curve: 'P-256',
      algorithm: 'sha256',
      notBeforeDate: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
      notAfterDate: new Date(Date.now() - 24 * 60 * 60 * 1000),
      keyPair: { publicKey: publicKeyPem, privateKey: privateKeyPem },
    },
  );
  return pems.cert;
}
