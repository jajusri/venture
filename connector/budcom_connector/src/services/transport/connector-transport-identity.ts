import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

import selfsigned from 'selfsigned';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { Logger } from '../../infrastructure/logging/logger.js';

/**
 * Stable Connector transport identity (separate from the logical Connector ID —
 * see connector-identity-repository.ts) used to terminate the optional pinned-HTTPS
 * listener. See architectural principles in Phase 3K: the Connector ID identifies the
 * installation to the pairing/business protocol; this keypair exists solely so a client can
 * cryptographically pin *which host* it is actually talking to on the LAN, independent of that
 * logical ID (which, by itself, is just a self-reported string any host could claim).
 *
 * TRUST MODEL: the fingerprint (SHA-256 of the DER SubjectPublicKeyInfo, "sha256/<base64>") is
 * PUBLIC, not secret — it is meant to be carried in the QR/pairing-session payload and pinned
 * by the client at first trust. It is never treated as trusted merely because it appeared in an
 * mDNS TXT record; the pairing-session contract (api/routes/pairing.ts) is the sole authoritative
 * first-trust channel for it.
 *
 * KEY GENERATION: EC P-256, generated once via Node's own crypto.generateKeyPairSync — never
 * derived from the Connector ID, machine name, password, or any other predictable material.
 *
 * PERSISTENCE: two PEM files under transportIdentityDir (transport-key.pem,
 * transport-cert.pem), written via write-temp-then-rename for atomicity, with owner-only
 * permissions where the platform supports them (best-effort on Windows). The private key is
 * NEVER stored in SQLite and NEVER logged — only the public fingerprint appears in logs/status.
 *
 * CERTIFICATE RENEWAL POLICY: the self-signed certificate is valid for CERT_VALIDITY_DAYS from
 * generation. If, at resolution time, the persisted certificate has expired but the persisted
 * private key is intact and matches it, a fresh certificate is generated reusing the SAME key —
 * so the pinned fingerprint is unchanged across a normal renewal. This is the only case in which
 * this service writes new certificate material without being asked; it never regenerates the
 * key itself, and never treats a corrupt or mismatched key/certificate pair as a renewal — those
 * states fail explicitly (TRANSPORT_IDENTITY_ERROR) and require manual operator recovery
 * (deleting the identity directory to force a fresh identity, which any already-paired client
 * will then correctly detect as a fingerprint change on reconnect).
 */

const KEY_FILE_NAME = 'transport-key.pem';
const CERT_FILE_NAME = 'transport-cert.pem';
const CERT_COMMON_NAME = 'budcom-connector.local';
export const TRANSPORT_CERT_VALIDITY_DAYS = 730;
export const TRANSPORT_FINGERPRINT_ALGORITHM = 'sha256';

export interface TransportServerCredentials {
  readonly key: string;
  readonly cert: string;
}

export interface TransportIdentitySummary {
  readonly fingerprint: string;
  readonly fingerprintAlgorithm: 'sha256';
  readonly notBefore: string;
  readonly notAfter: string;
}

interface ResolvedIdentity {
  readonly credentials: TransportServerCredentials;
  readonly summary: TransportIdentitySummary;
}

function fingerprintFromSpkiDer(der: Buffer | Uint8Array): string {
  const digest = crypto.createHash('sha256').update(der).digest('base64');
  return `${TRANSPORT_FINGERPRINT_ALGORITHM}/${digest}`;
}

function corruptError(message: string, details?: Record<string, unknown>): AppError {
  return new AppError(ErrorCodes.TRANSPORT_IDENTITY_ERROR, message, 503, details);
}

export class ConnectorTransportIdentityService {
  private cached: ResolvedIdentity | null = null;
  private pending: Promise<ResolvedIdentity> | null = null;

  constructor(
    private readonly identityDir: string,
    private readonly logger: Logger,
  ) {}

  async getServerCredentials(): Promise<TransportServerCredentials> {
    return (await this.resolve()).credentials;
  }

  async getIdentity(): Promise<TransportIdentitySummary> {
    return (await this.resolve()).summary;
  }

  private async resolve(): Promise<ResolvedIdentity> {
    if (this.cached) return this.cached;
    if (this.pending) return this.pending;

    this.pending = this.resolveUncached().then(
      (result) => {
        this.cached = result;
        this.pending = null;
        return result;
      },
      (error) => {
        this.pending = null;
        throw error;
      },
    );
    return this.pending;
  }

  private async resolveUncached(): Promise<ResolvedIdentity> {
    ensureDirectory(this.identityDir);
    const keyPath = path.join(this.identityDir, KEY_FILE_NAME);
    const certPath = path.join(this.identityDir, CERT_FILE_NAME);

    const keyExists = fs.existsSync(keyPath);
    const certExists = fs.existsSync(certPath);

    if (!keyExists && !certExists) {
      return this.generateFreshIdentity(keyPath, certPath);
    }

    if (keyExists !== certExists) {
      throw corruptError(
        'Connector transport identity is incomplete (key/certificate pair partially missing). ' +
          'This Connector will not silently regenerate its transport identity — manual recovery ' +
          'is required before secure transport can start.',
        { keyPresent: keyExists, certPresent: certExists, identityDir: this.identityDir },
      );
    }

    const privateKeyPem = readTextFile(keyPath, 'transport private key');
    const certPem = readTextFile(certPath, 'transport certificate');

    const privateKey = parsePrivateKey(privateKeyPem);
    const certificate = parseCertificate(certPem);
    assertKeyMatchesCertificate(privateKey, certificate);

    const notAfterMs = new Date(certificate.validTo).getTime();
    if (Number.isNaN(notAfterMs) || notAfterMs <= Date.now()) {
      this.logger.info('transport_certificate_renewing', {
        event: 'transport_certificate_renewing',
        reason: 'expired',
        previousNotAfter: certificate.validTo,
      });
      return this.renewCertificate(privateKeyPem, certPath);
    }

    return {
      credentials: { key: privateKeyPem, cert: certPem },
      summary: summaryFromCertificate(certificate),
    };
  }

  private async generateFreshIdentity(keyPath: string, certPath: string): Promise<ResolvedIdentity> {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', {
      namedCurve: 'P-256',
      publicKeyEncoding: { type: 'spki', format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    });

    const certPem = await selfSign(publicKey, privateKey);

    writeAtomically(keyPath, privateKey, 0o600);
    writeAtomically(certPath, certPem, 0o644);

    this.logger.info('transport_identity_generated', {
      event: 'transport_identity_generated',
      fingerprint: fingerprintFromSpkiDer(
        crypto.createPublicKey(publicKey).export({ type: 'spki', format: 'der' }),
      ),
    });

    const certificate = parseCertificate(certPem);
    return {
      credentials: { key: privateKey, cert: certPem },
      summary: summaryFromCertificate(certificate),
    };
  }

  private async renewCertificate(privateKeyPem: string, certPath: string): Promise<ResolvedIdentity> {
    const privateKey = crypto.createPrivateKey(privateKeyPem);
    const publicKeyPem = crypto.createPublicKey(privateKey).export({ type: 'spki', format: 'pem' }) as string;

    const certPem = await selfSign(publicKeyPem, privateKeyPem);
    writeAtomically(certPath, certPem, 0o644);

    const certificate = parseCertificate(certPem);
    const summary = summaryFromCertificate(certificate);
    this.logger.info('transport_certificate_renewed', {
      event: 'transport_certificate_renewed',
      fingerprint: summary.fingerprint,
      notAfter: summary.notAfter,
    });

    return { credentials: { key: privateKeyPem, cert: certPem }, summary };
  }
}

async function selfSign(publicKeyPem: string, privateKeyPem: string): Promise<string> {
  const now = new Date();
  const notAfter = new Date(now.getTime() + TRANSPORT_CERT_VALIDITY_DAYS * 24 * 60 * 60 * 1000);
  const pems = await selfsigned.generate(
    [{ name: 'commonName', value: CERT_COMMON_NAME }],
    {
      keyType: 'ec',
      curve: 'P-256',
      algorithm: 'sha256',
      notBeforeDate: now,
      notAfterDate: notAfter,
      keyPair: { publicKey: publicKeyPem, privateKey: privateKeyPem },
      extensions: [
        { name: 'basicConstraints', cA: false, critical: true },
        { name: 'keyUsage', digitalSignature: true, keyEncipherment: true, critical: true },
        { name: 'extKeyUsage', serverAuth: true },
      ],
    },
  );
  return pems.cert;
}

function summaryFromCertificate(certificate: crypto.X509Certificate): TransportIdentitySummary {
  const fingerprint = fingerprintFromSpkiDer(certificate.publicKey.export({ type: 'spki', format: 'der' }));
  return {
    fingerprint,
    fingerprintAlgorithm: TRANSPORT_FINGERPRINT_ALGORITHM,
    notBefore: certificate.validFrom,
    notAfter: certificate.validTo,
  };
}

function parsePrivateKey(pem: string): crypto.KeyObject {
  try {
    return crypto.createPrivateKey(pem);
  } catch (error) {
    throw corruptError('Connector transport private key file is corrupt or unreadable.', {
      cause: error instanceof Error ? error.message : String(error),
    });
  }
}

function parseCertificate(pem: string): crypto.X509Certificate {
  try {
    return new crypto.X509Certificate(pem);
  } catch (error) {
    throw corruptError('Connector transport certificate file is corrupt or unreadable.', {
      cause: error instanceof Error ? error.message : String(error),
    });
  }
}

function assertKeyMatchesCertificate(privateKey: crypto.KeyObject, certificate: crypto.X509Certificate): void {
  const keySpkiDer = crypto.createPublicKey(privateKey).export({ type: 'spki', format: 'der' });
  const certSpkiDer = certificate.publicKey.export({ type: 'spki', format: 'der' });
  if (!Buffer.from(keySpkiDer).equals(Buffer.from(certSpkiDer))) {
    throw corruptError(
      'Connector transport certificate does not match the persisted private key. This Connector ' +
        'will not silently replace either file — manual recovery is required before secure ' +
        'transport can start.',
    );
  }
}

function readTextFile(filePath: string, label: string): string {
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch (error) {
    throw corruptError(`Unable to read ${label} at ${filePath}.`, {
      cause: error instanceof Error ? error.message : String(error),
    });
  }
}

function ensureDirectory(dir: string): void {
  fs.mkdirSync(dir, { recursive: true });
  try {
    fs.chmodSync(dir, 0o700);
  } catch {
    // Best-effort — not all platforms (notably Windows) honor POSIX mode bits.
  }
}

function writeAtomically(filePath: string, content: string, mode: number): void {
  const tempPath = `${filePath}.tmp`;
  fs.writeFileSync(tempPath, content, { encoding: 'utf8', mode });
  fs.renameSync(tempPath, filePath);
  try {
    fs.chmodSync(filePath, mode);
  } catch {
    // Best-effort — not all platforms (notably Windows) honor POSIX mode bits.
  }
}
