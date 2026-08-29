import { describe, expect, it } from 'vitest';
import { isDevProvisionRuntimeAllowed } from '../services/trust/scripts/dev-provision.js';

describe('dev-provision production guard (positive allowlist, fail closed)', () => {
  it('allows development and test, on either signal', () => {
    expect(isDevProvisionRuntimeAllowed('development', undefined)).toBe(true);
    expect(isDevProvisionRuntimeAllowed(undefined, 'development')).toBe(true);
    expect(isDevProvisionRuntimeAllowed('test', 'test')).toBe(true);
  });

  it('refuses when unset entirely -- no default-allow', () => {
    expect(isDevProvisionRuntimeAllowed(undefined, undefined)).toBe(false);
  });

  it('refuses production regardless of case', () => {
    expect(isDevProvisionRuntimeAllowed('production', undefined)).toBe(false);
    expect(isDevProvisionRuntimeAllowed('Production', undefined)).toBe(false);
    expect(isDevProvisionRuntimeAllowed('PRODUCTION', undefined)).toBe(false);
    expect(isDevProvisionRuntimeAllowed(undefined, ' Production ')).toBe(false);
  });

  it('refuses when BUDCOM_RUNTIME_ENV is looser than a production NODE_ENV -- neither signal can override the other into allowing', () => {
    expect(isDevProvisionRuntimeAllowed('production', 'development')).toBe(false);
    expect(isDevProvisionRuntimeAllowed('development', 'production')).toBe(false);
  });

  it('refuses unknown or ambiguous values instead of defaulting to allowed', () => {
    expect(isDevProvisionRuntimeAllowed('staging', undefined)).toBe(false);
    expect(isDevProvisionRuntimeAllowed('', undefined)).toBe(false);
  });
});
