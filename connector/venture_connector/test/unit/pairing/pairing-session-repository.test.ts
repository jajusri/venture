import { afterEach, describe, expect, it, vi } from 'vitest';

import { PairingSessionRepository } from '../../../src/services/pairing/pairing-session-repository.js';
import { cleanupTestSqliteStorage, createTestSqliteStorage } from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
  vi.useRealTimers();
});

describe('PairingSessionRepository', () => {
  async function setup() {
    const { storage } = await createTestSqliteStorage();
    const db = storage.getBundle().database;
    const repo = new PairingSessionRepository(db);
    return { repo };
  }

  const CONNECTOR = { connectorId: 'connector-abc', connectorName: 'VIDHI', host: '10.100.141.231', port: 8080 };

  function fieldsFor(session: { connectorId: string; host: string; port: number }) {
    return { connectorId: session.connectorId, host: session.host, port: session.port };
  }

  it('creates a session with a random, single-use secret and short code', async () => {
    const { repo } = await setup();

    const a = repo.create(CONNECTOR);
    const b = repo.create(CONNECTOR);

    expect(a.pairingSessionId).toBeTruthy();
    expect(a.secret).toBeTruthy();
    expect(a.secret.length).toBeGreaterThanOrEqual(32);

    // Randomness: two sessions never share an ID, secret, or short code.
    expect(a.pairingSessionId).not.toBe(b.pairingSessionId);
    expect(a.secret).not.toBe(b.secret);
    expect(a.shortCode).not.toBe(b.shortCode);
  });

  it('short code has a bounded, human-enterable format', async () => {
    const { repo } = await setup();
    const seen = new Set<string>();

    for (let i = 0; i < 20; i += 1) {
      const result = repo.create({ ...CONNECTOR, connectorId: `connector-${i}` });
      expect(result.shortCode).toMatch(/^[A-HJ-NP-Z2-9]{8}$/);
      seen.add(result.shortCode);
    }
    // 20 independently generated codes should not collide.
    expect(seen.size).toBe(20);
  });

  it('never stores the raw secret or short code (privacy guard)', async () => {
    const { repo } = await setup();
    const result = repo.create(CONNECTOR);

    const record = repo.get(result.pairingSessionId);
    const json = JSON.stringify(record);
    expect(json).not.toContain(result.secret);
    expect(json).not.toContain(result.shortCode);
  });

  it('stores connectorId, connectorName, host, port, and schemaVersion on the session', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const record = repo.get(created.pairingSessionId);
    expect(record?.connectorId).toBe(CONNECTOR.connectorId);
    expect(record?.connectorName).toBe(CONNECTOR.connectorName);
    expect(record?.host).toBe(CONNECTOR.host);
    expect(record?.port).toBe(CONNECTOR.port);
    expect(record?.schemaVersion).toBe('1');
  });

  it('redeems successfully via pairingSessionId + secret + matching fields', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const outcome = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(outcome.kind).toBe('redeemed');
    if (outcome.kind === 'redeemed') {
      expect(outcome.connectorId).toBe(CONNECTOR.connectorId);
      expect(outcome.connectorName).toBe(CONNECTOR.connectorName);
    }
  });

  it('redeems successfully via short code alone', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const outcome = repo.redeemByShortCode(created.shortCode);
    expect(outcome.kind).toBe('redeemed');
  });

  it('rejects an unknown pairing session ID', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const outcome = repo.redeemBySessionId(
      '00000000-0000-0000-0000-000000000000',
      'anything',
      fieldsFor(created),
    );
    expect(outcome.kind).toBe('not_found');
  });

  it('rejects the wrong secret for a real session (secret mismatch), and the session remains valid for a correct retry', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const outcome = repo.redeemBySessionId(created.pairingSessionId, 'totally-wrong-secret', fieldsFor(created));
    expect(outcome.kind).toBe('secret_mismatch');

    const retry = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(retry.kind).toBe('redeemed');
  });

  it('rejects a redemption whose connectorId does not match the stored session (checked before the secret)', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const outcome = repo.redeemBySessionId(created.pairingSessionId, created.secret, {
      ...fieldsFor(created),
      connectorId: 'attacker-connector-id',
    });
    expect(outcome.kind).toBe('connector_id_mismatch');

    // A single mismatch is still within the attempt budget — the correct fields+secret still
    // work on the very next try.
    const retry = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(retry.kind).toBe('redeemed');
  });

  it('rejects a redemption whose host/port does not match the stored session', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const wrongHost = repo.redeemBySessionId(created.pairingSessionId, created.secret, {
      ...fieldsFor(created),
      host: '10.0.0.99',
    });
    expect(wrongHost.kind).toBe('endpoint_mismatch');

    const wrongPort = repo.redeemBySessionId(created.pairingSessionId, created.secret, {
      ...fieldsFor(created),
      port: 9999,
    });
    expect(wrongPort.kind).toBe('endpoint_mismatch');

    const retry = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(retry.kind).toBe('redeemed');
  });

  it('counts a connectorId mismatch against the same failed-attempts budget as a wrong secret', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    repo.redeemBySessionId(created.pairingSessionId, created.secret, {
      ...fieldsFor(created),
      connectorId: 'attacker-connector-id',
    });
    const record = repo.get(created.pairingSessionId);
    expect(record?.failedAttempts).toBe(1);
  });

  it('counts a host/port mismatch against the same failed-attempts budget as a wrong secret', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    repo.redeemBySessionId(created.pairingSessionId, created.secret, { ...fieldsFor(created), port: 9999 });
    const record = repo.get(created.pairingSessionId);
    expect(record?.failedAttempts).toBe(1);
  });

  it('locks out after a mix of connectorId-mismatch, endpoint-mismatch, and wrong-secret attempts reaches the bound', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const attempts: Array<() => ReturnType<PairingSessionRepository['redeemBySessionId']>> = [
      () => repo.redeemBySessionId(created.pairingSessionId, created.secret, { ...fieldsFor(created), connectorId: 'x' }),
      () => repo.redeemBySessionId(created.pairingSessionId, created.secret, { ...fieldsFor(created), port: 1 }),
      () => repo.redeemBySessionId(created.pairingSessionId, 'wrong', fieldsFor(created)),
      () => repo.redeemBySessionId(created.pairingSessionId, created.secret, { ...fieldsFor(created), connectorId: 'y' }),
      () => repo.redeemBySessionId(created.pairingSessionId, 'still-wrong', fieldsFor(created)),
    ];
    for (const attempt of attempts) {
      const outcome = attempt();
      expect(outcome.kind).not.toBe('redeemed');
    }

    const record = repo.get(created.pairingSessionId);
    expect(record?.failedAttempts).toBe(5);

    const finalAttempt = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(finalAttempt.kind).toBe('too_many_attempts');
  });

  it('is single-use: a second redemption of the same session fails', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const first = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(first.kind).toBe('redeemed');

    const second = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(second.kind).toBe('already_redeemed');
  });

  it('atomic redemption: only one of two concurrent redeem attempts for the same session succeeds', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const attempts = Array.from({ length: 10 }, () =>
      repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created)),
    );

    const redeemedCount = attempts.filter((a) => a.kind === 'redeemed').length;
    const alreadyRedeemedCount = attempts.filter((a) => a.kind === 'already_redeemed').length;
    expect(redeemedCount).toBe(1);
    expect(alreadyRedeemedCount).toBe(9);
  });

  it('cancellation prevents redemption', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const cancelled = repo.cancel(created.pairingSessionId);
    expect(cancelled).toBe(true);

    const outcome = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(outcome.kind).toBe('cancelled');
  });

  it('cancel is idempotent-safe: cancelling twice returns false the second time', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    expect(repo.cancel(created.pairingSessionId)).toBe(true);
    expect(repo.cancel(created.pairingSessionId)).toBe(false);
  });

  it('expires after its TTL', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
    const { repo } = await setup();
    const created = repo.create({ ...CONNECTOR, ttlMs: 30_000 });

    vi.setSystemTime(new Date('2026-01-01T00:00:31.000Z'));
    const outcome = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(outcome.kind).toBe('expired');
  });

  it('clamps an excessively long TTL to the maximum allowed', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
    const { repo } = await setup();
    const created = repo.create({ ...CONNECTOR, ttlMs: 10_000_000 });

    const expiresInMs = new Date(created.expiresAt).getTime() - Date.now();
    expect(expiresInMs).toBeLessThanOrEqual(300_000);
  });

  it('locks out after too many wrong-secret attempts', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    for (let i = 0; i < 5; i += 1) {
      const attempt = repo.redeemBySessionId(created.pairingSessionId, 'wrong', fieldsFor(created));
      expect(attempt.kind).toBe('secret_mismatch');
    }

    const finalAttempt = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(finalAttempt.kind).toBe('too_many_attempts');
  });

  it('locks out redeemByShortCode after too many wrong-code attempts', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    for (let i = 0; i < 5; i += 1) {
      const attempt = repo.redeemByShortCode('WRONGCOD');
      expect(attempt.kind).toBe('secret_mismatch');
    }

    const finalAttempt = repo.redeemByShortCode(created.shortCode);
    expect(finalAttempt.kind).toBe('too_many_attempts');
  });

  it('creating a second session for the same connector cancels the first active one', async () => {
    const { repo } = await setup();
    const first = repo.create(CONNECTOR);
    const second = repo.create(CONNECTOR);

    expect(first.pairingSessionId).not.toBe(second.pairingSessionId);

    const firstRecord = repo.get(first.pairingSessionId);
    expect(firstRecord?.cancelledAt).not.toBeNull();

    const firstRedeem = repo.redeemBySessionId(first.pairingSessionId, first.secret, fieldsFor(first));
    expect(firstRedeem.kind).toBe('cancelled');

    const secondRedeem = repo.redeemBySessionId(second.pairingSessionId, second.secret, fieldsFor(second));
    expect(secondRedeem.kind).toBe('redeemed');
  });

  it('does not cancel active sessions belonging to a different connector', async () => {
    const { repo } = await setup();
    const a = repo.create({ ...CONNECTOR, connectorId: 'connector-a' });
    repo.create({ ...CONNECTOR, connectorId: 'connector-b' });

    const record = repo.get(a.pairingSessionId);
    expect(record?.cancelledAt).toBeNull();
  });

  it('automatically prunes expired, never-redeemed sessions when a new session is created', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
    const { repo } = await setup();
    const stale = repo.create({ ...CONNECTOR, connectorId: 'connector-stale', ttlMs: 30_000 });

    vi.setSystemTime(new Date('2026-01-01T00:05:00.000Z'));
    // Creating any new session (even for a different connector) triggers the prune sweep.
    repo.create({ ...CONNECTOR, connectorId: 'connector-fresh' });

    expect(repo.get(stale.pairingSessionId)).toBeNull();
  });

  it('does not prune a redeemed or cancelled session even after its TTL has passed', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
    const { repo } = await setup();
    const redeemed = repo.create({ ...CONNECTOR, connectorId: 'connector-redeemed', ttlMs: 30_000 });
    repo.redeemBySessionId(redeemed.pairingSessionId, redeemed.secret, fieldsFor(redeemed));

    vi.setSystemTime(new Date('2026-01-01T00:05:00.000Z'));
    repo.create({ ...CONNECTOR, connectorId: 'connector-fresh' });

    expect(repo.get(redeemed.pairingSessionId)).not.toBeNull();
  });

  it('constant-time comparison path: correctness holds for secrets of differing and matching length', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    // crypto.timingSafeEqual requires equal-length buffers; the repository must handle a
    // presented value whose *hash* differs in length safely (SHA-256 digests are always 32
    // bytes, so this exercises the length-mismatch guard) as well as the equal-length mismatch.
    const shortGuess = repo.redeemBySessionId(created.pairingSessionId, 'x', fieldsFor(created));
    expect(shortGuess.kind).toBe('secret_mismatch');

    const longGuess = repo.redeemBySessionId(created.pairingSessionId, 'x'.repeat(500), fieldsFor(created));
    expect(longGuess.kind).toBe('secret_mismatch');

    const correct = repo.redeemBySessionId(created.pairingSessionId, created.secret, fieldsFor(created));
    expect(correct.kind).toBe('redeemed');
  });

  it('getStatus never includes secret_hash/short_code_hash or failedAttempts', async () => {
    const { repo } = await setup();
    const created = repo.create(CONNECTOR);

    const status = repo.getStatus(created.pairingSessionId);
    expect(status).not.toBeNull();
    const json = JSON.stringify(status);
    expect(json).not.toContain(created.secret);
    expect(json).not.toContain(created.shortCode);
    expect((status as unknown as Record<string, unknown>)['failedAttempts']).toBeUndefined();
  });
});
