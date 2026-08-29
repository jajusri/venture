import { describe, expect, it } from 'vitest';
import { validateSigningKeyId } from '../services/trust/src/domain/signing-key-id.js';

describe('validateSigningKeyId (round 7, Codex finding 4: key-id filesystem containment)', () => {
  it('accepts ordinary filename-safe key ids', () => {
    for (const ok of ['key-1', 'key_1', 'key.1', 'KEY123', 'a', '2026-08-29-rotation-1']) {
      expect(validateSigningKeyId(ok)).toBe(ok);
    }
  });

  it('rejects path traversal segments', () => {
    for (const unsafe of ['..', '.', '../escape', 'a/../../etc/passwd', '..\\escape']) {
      expect(() => validateSigningKeyId(unsafe)).toThrow(/Invalid signing key id/);
    }
  });

  it('rejects path separators of either kind', () => {
    for (const unsafe of ['a/b', 'a\\b', '/etc/passwd', 'C:\\Windows\\System32', '\\\\server\\share']) {
      expect(() => validateSigningKeyId(unsafe)).toThrow(/Invalid signing key id/);
    }
  });

  it('rejects absolute-path-like and drive-prefixed ids', () => {
    for (const unsafe of ['/root', 'C:evil', 'C:\\evil']) {
      expect(() => validateSigningKeyId(unsafe)).toThrow(/Invalid signing key id/);
    }
  });

  it('rejects empty and overlong ids', () => {
    expect(() => validateSigningKeyId('')).toThrow(/Invalid signing key id/);
    expect(() => validateSigningKeyId('a'.repeat(129))).toThrow(/Invalid signing key id/);
    expect(validateSigningKeyId('a'.repeat(128))).toHaveLength(128);
  });

  it('rejects control characters and other non-whitelisted symbols', () => {
    for (const unsafe of ['key\u0000id', 'key\nid', 'key id', 'key;rm -rf', 'key$(whoami)', 'key`whoami`']) {
      expect(() => validateSigningKeyId(unsafe)).toThrow(/Invalid signing key id/);
    }
  });
});
