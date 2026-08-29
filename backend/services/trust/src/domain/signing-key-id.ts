const SAFE_KEY_ID_PATTERN = /^[A-Za-z0-9._-]+$/;
const MAX_KEY_ID_LENGTH = 128;

/**
 * WHO CAN CONSTRUCT OR OBTAIN THIS AUTHORITY? A `key_id` becomes a filesystem path segment
 * (`{keyDir}/{keyId}.pem`) everywhere a signing key's local private-key file is addressed. This is
 * the ONE production validation path every such use must go through (round 7, Codex finding 4) --
 * centralizing it here means there is exactly one place to audit for path-traversal/filesystem-
 * escape safety, not several scattered checks that could drift out of sync.
 *
 * Whitelist-only by design: the allowed character class itself excludes `/`, `\`, drive-letter
 * colons, and every control character, so no key_id that passes this check can ever contain a path
 * separator at all -- `.`/`..` are rejected explicitly too, both for defense in depth and so the
 * intent is visible to a reader/reviewer, not merely an emergent property of the regex.
 */
export function validateSigningKeyId(rawKeyId: string): string {
  if (typeof rawKeyId !== 'string' || rawKeyId.length === 0 || rawKeyId.length > MAX_KEY_ID_LENGTH) {
    throw new Error(`Invalid signing key id: must be 1-${MAX_KEY_ID_LENGTH} characters`);
  }
  if (rawKeyId === '.' || rawKeyId === '..') throw new Error('Invalid signing key id: must not be a path traversal segment');
  if (rawKeyId.includes('..')) throw new Error('Invalid signing key id: must not contain path traversal segments');
  if (!SAFE_KEY_ID_PATTERN.test(rawKeyId)) throw new Error('Invalid signing key id: only letters, digits, ".", "_", and "-" are allowed');
  return rawKeyId;
}
