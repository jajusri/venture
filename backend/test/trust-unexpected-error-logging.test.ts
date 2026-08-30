import { Writable } from 'node:stream';
import { createHash } from 'node:crypto';
import { afterEach, describe, expect, it } from 'vitest';
import { buildTrustService } from '../services/trust/src/app.js';
import type { DeviceRegistrationStore } from '../services/trust/src/application/register-device.js';
import type { EnrollmentAuthorityLookup, EnrollmentGrantConsumptionStore } from '../services/trust/src/application/consume-enrollment-grant.js';
import { BusinessDeviceCredentialIssuer, type TrustCredentialSigner } from '../services/trust/src/application/issue-credential.js';
import { InMemoryCredentialIssuanceStore } from '../services/trust/src/persistence/in-memory-credential-store.js';

// Representative secret-bearing strings a driver/library/future code path could plausibly embed
// in an error message or stack -- none of these match mapUnknownEnrollmentError's recognized
// (safe, hand-authored) message patterns, so each reaches the unexpected-error (500) branch.
const SECRET_CASES: Record<string, string> = {
  'a credential-bearing database connection URL': 'connect failed: postgresql://dbuser:supersecret@localhost:5432/trust',
  'a standalone password assignment': 'auth failure, password=supersecret123 rejected by upstream',
  'a bearer token': 'request failed: Authorization: Bearer abcdef-secret-token-value',
  'Trust enrollment grant/credential material': 'grant consumption failed for grantSecret=one-time-secret-abcxyz token=trust-cred-99887766',
  'PEM private key material': '-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEA\n-----END PRIVATE KEY-----',
  'structured/serialized secret-bearing text': JSON.stringify({ ok: false, secret: 'nested-secret-value', apiKey: 'sk_live_abcdef123456' }),
};

const apps: ReturnType<typeof buildTrustService>[] = [];
afterEach(async () => Promise.all(apps.splice(0).map((app) => app.close())));

/** Fastify/pino accept a custom `stream` in the logger config; collecting into an array lets a
 * test assert on the exact structured log lines emitted for one request, instead of relying on
 * console output or reaching into implementation internals. */
function collectingLogger(): { logger: { level: 'error'; stream: Writable }; lines: () => Record<string, unknown>[] } {
  const raw: string[] = [];
  const stream = new Writable({ write(chunk, _enc, cb) { raw.push(chunk.toString()); cb(); } });
  return {
    logger: { level: 'error', stream },
    lines: () => raw.filter((l) => l.trim().length > 0).map((l) => JSON.parse(l) as Record<string, unknown>),
  };
}

const NOW = new Date(10_000);
const SECRET = 'a-genuinely-long-enough-secret-value';

class ThrowingGrantStore implements EnrollmentGrantConsumptionStore {
  constructor(private readonly valueToThrow: unknown) {}
  async consumeAndRegister<T>(): Promise<T> {
    throw this.valueToThrow;
  }
}

function buildAppThatThrows(valueToThrow: unknown, logger: { level: 'error'; stream: Writable }) {
  const store = new ThrowingGrantStore(valueToThrow);
  const signer: TrustCredentialSigner = { sign: (build) => { const identity = { issuerId: 'issuer-1', issuerKeyId: 'key-1', profile: 'P256-SHA256-v1' }; build(identity); return Promise.resolve({ ...identity, signature: new Uint8Array([1, 2, 3]) }); } };
  const credentialIssuer = new BusinessDeviceCredentialIssuer(new InMemoryCredentialIssuanceStore(), signer, 3_600_000, () => NOW);
  const app = buildTrustService({ enrollment: { store, credentialIssuer }, now: () => NOW, logger });
  apps.push(app);
  return app;
}

const publicKey = new Uint8Array(65).fill(9);
const publicKeyB64 = Buffer.from(publicKey).toString('base64');
const fingerprint = createHash('sha256').update(publicKey).digest('base64');
function validBody(overrides: Record<string, unknown> = {}) {
  return { grantId: 'grant-1', grantSecret: SECRET, deviceId: 'device-1', deviceKeyId: 'device-1-key-1', deviceKeyVersion: 1, publicKey: publicKeyB64, publicKeyFingerprint: fingerprint, ...overrides };
}

describe('Trust unexpected-error logging never leaks secret-bearing content', () => {
  for (const [label, secretMessage] of Object.entries(SECRET_CASES)) {
    it(`does not expose ${label} anywhere in the emitted log`, async () => {
      const { logger, lines } = collectingLogger();
      const app = buildAppThatThrows(new Error(secretMessage), logger);

      const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });

      // Client-facing response stays generic -- unchanged by this task, re-asserted here because
      // an unexpected-error test is exactly where a regression would first surface.
      expect(response.statusCode).toBe(500);
      expect(response.body).not.toContain(secretMessage);
      const clientBody = response.json<{ error: { message: string } }>();
      expect(clientBody.error.message).toBe('The Trust Service could not process the request.');

      const emitted = lines();
      expect(emitted.length).toBeGreaterThan(0);
      const emittedText = JSON.stringify(emitted);
      // The literal secret string, and the raw secret error message as a whole, must not appear
      // anywhere in the log output.
      expect(emittedText).not.toContain(secretMessage);
      for (const secretFragment of extractSecretFragments(secretMessage)) {
        expect(emittedText).not.toContain(secretFragment);
      }
    });
  }

  it('retains safe diagnostic context: event, fixed errorKind, route, method, and a correlation id matching the client response', async () => {
    const { logger, lines } = collectingLogger();
    const app = buildAppThatThrows(new TypeError('connect failed: postgresql://dbuser:supersecret@localhost/trust'), logger);

    const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });
    const clientRequestId = response.json<{ error: { requestId: string } }>().error.requestId;

    const errorLine = lines().find((l) => l['event'] === 'unexpected_trust_error');
    expect(errorLine).toBeDefined();
    expect(errorLine?.['errorKind']).toBe('unexpected_error');
    expect(errorLine?.['route']).toBe('/v1/trust/enrollment/consume');
    expect(errorLine?.['method']).toBe('POST');
    expect(errorLine?.['requestId']).toBe(clientRequestId);
    // And, restated explicitly: the fields that must NOT be present -- including the two fields
    // this exact suite existed to remove (errorName/errorConstructor read error.name/
    // error.constructor.name, which are attacker-controllable on a hostile thrown value).
    expect(errorLine).not.toHaveProperty('errorName');
    expect(errorLine).not.toHaveProperty('errorConstructor');
    expect(errorLine).not.toHaveProperty('message');
    expect(errorLine).not.toHaveProperty('stack');
    expect(errorLine).not.toHaveProperty('err');
    expect(errorLine).not.toHaveProperty('name');
    expect(errorLine).not.toHaveProperty('constructor');
  });

  describe('adversarial: the thrown value is treated as unknown, not Error', () => {
    it('does not leak a secret placed in a hostile Error.name override', async () => {
      const { logger, lines } = collectingLogger();
      const secret = 'Authorization: Bearer secret-token-should-never-log';
      const hostile = new Error('boom');
      Object.defineProperty(hostile, 'name', { get: () => secret });

      const app = buildAppThatThrows(hostile, logger);
      const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });

      expect(response.statusCode).toBe(500);
      expect(response.body).not.toContain(secret);
      const emittedText = JSON.stringify(lines());
      expect(emittedText).not.toContain(secret);
      expect(emittedText).not.toContain('secret-token-should-never-log');
    });

    it('does not leak arbitrary text from a custom Error subclass / spoofed constructor identity', async () => {
      const { logger, lines } = collectingLogger();
      const secretClassName = 'CredentialLeak_apiKey_sk_live_abcdef123456';
      class CustomTrustDriverError extends Error {}
      Object.defineProperty(CustomTrustDriverError, 'name', { value: secretClassName });
      const hostile = new CustomTrustDriverError('boom');
      // Spoof the instance's own constructor identity too, independent of the class declaration.
      Object.defineProperty(hostile, 'constructor', { get: () => ({ name: secretClassName }) });

      const app = buildAppThatThrows(hostile, logger);
      const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });

      expect(response.statusCode).toBe(500);
      const emittedText = JSON.stringify(lines());
      expect(emittedText).not.toContain(secretClassName);
      expect(emittedText).not.toContain('sk_live_abcdef123456');
      const errorLine = lines().find((l) => l['event'] === 'unexpected_trust_error');
      expect(errorLine?.['errorKind']).toBe('unexpected_error');
    });

    it('does not leak secret-bearing fields from a plain object throw (no Error at all)', async () => {
      const { logger, lines } = collectingLogger();
      const plainThrow = {
        name: 'PlainObjectError',
        message: 'plain object thrown instead of an Error',
        stack: 'fake-stack-frame',
        password: 'plain-object-password-secret',
        token: 'plain-object-token-secret',
        credential: 'plain-object-credential-secret',
      };

      const app = buildAppThatThrows(plainThrow, logger);
      const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });

      expect(response.statusCode).toBe(500);
      const clientBody = response.json<{ error: { message: string } }>();
      expect(clientBody.error.message).toBe('The Trust Service could not process the request.');
      const emittedText = JSON.stringify(lines());
      for (const secretValue of ['plain-object-password-secret', 'plain-object-token-secret', 'plain-object-credential-secret', 'fake-stack-frame']) {
        expect(emittedText).not.toContain(secretValue);
      }
    });

    it('never reads a hostile getter on name/constructor/stack -- proves the logging path needs none of them', async () => {
      const { logger, lines } = collectingLogger();
      const accessedProperties: string[] = [];
      const secret = 'hostile-getter-access-secret-value';
      // A real Error instance (so `error instanceof Error` is true and this exact object -- not a
      // fresh synthetic one -- is what the error handler and logger see), with hostile getters on
      // every property the OLD logging code used to read. `message` stays a plain, static, safe
      // string on purpose: it IS read by the pre-existing, out-of-scope status-classification
      // logic (mapUnknownEnrollmentError, unchanged by this task), so tracking it here would
      // conflate that unrelated, already-accepted behavior with what this test is actually
      // proving -- that the LOGGING path specifically never touches name/constructor/stack.
      const hostile = new Error('a plain, safe, non-recognized message');
      for (const prop of ['name', 'stack'] as const) {
        Object.defineProperty(hostile, prop, {
          configurable: true,
          get() {
            accessedProperties.push(prop);
            return secret;
          },
        });
      }
      Object.defineProperty(hostile, 'constructor', {
        get() {
          accessedProperties.push('constructor');
          return { name: secret };
        },
      });
      expect(hostile).toBeInstanceOf(Error);

      const app = buildAppThatThrows(hostile, logger);
      const response = await app.inject({ method: 'POST', url: '/v1/trust/enrollment/consume', payload: validBody() });

      expect(response.statusCode).toBe(500);
      const emittedText = JSON.stringify(lines());
      expect(emittedText).not.toContain(secret);
      // The real proof: the logging path never triggers any of these getters at all, on the exact
      // same object instance the old errorName/errorConstructor code used to read them from.
      expect(accessedProperties).toEqual([]);
    });
  });
});

/** Splits a secret message into individual "obviously sensitive" tokens so a test can assert none
 * of them leaked even in some transformed/partial form, not just the exact original string. */
function extractSecretFragments(message: string): string[] {
  const matches = message.match(/[A-Za-z0-9_-]{10,}/g) ?? [];
  return [...new Set(matches)];
}
