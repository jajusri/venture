import { describe, expect, it } from 'vitest';

import { generateDesktopControlToken } from '../../src/application/desktop-control-token.js';

describe('generateDesktopControlToken', () => {
  it('generates a token with at least 256 bits of entropy (32+ raw bytes, base64url-encoded)', () => {
    const token = generateDesktopControlToken();
    // base64url encodes 3 bytes per 4 characters with no padding; 32 bytes -> 43 characters.
    expect(token.length).toBeGreaterThanOrEqual(43);
    expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('generates a different token on every call (fresh per Connector launch)', () => {
    const seen = new Set<string>();
    for (let i = 0; i < 20; i += 1) {
      seen.add(generateDesktopControlToken());
    }
    expect(seen.size).toBe(20);
  });
});
