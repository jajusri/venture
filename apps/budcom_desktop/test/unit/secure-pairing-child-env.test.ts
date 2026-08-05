import { describe, expect, it } from 'vitest';

import {
  buildSecurePairingChildEnvOverrides,
  DEFAULT_SECURE_TRANSPORT_PORT,
} from '../../src/application/secure-pairing-child-env.js';
import { buildConnectorChildEnvironment } from '../../src/application/release/connector-packaged-paths.js';

describe('buildSecurePairingChildEnvOverrides', () => {
  it('returns an empty object when disabled — merging it changes nothing', () => {
    const overrides = buildSecurePairingChildEnvOverrides({ enabled: false, controlToken: 'unused' });
    expect(overrides).toEqual({});
  });

  it('sets exactly the four expected keys when enabled', () => {
    const overrides = buildSecurePairingChildEnvOverrides({ enabled: true, controlToken: 'my-token' });
    expect(overrides).toEqual({
      BUDCOM_SECURE_PAIRING_ENABLED: 'true',
      BUDCOM_SECURE_TRANSPORT_ENABLED: 'true',
      BUDCOM_SECURE_TRANSPORT_PORT: String(DEFAULT_SECURE_TRANSPORT_PORT),
      BUDCOM_DESKTOP_CONTROL_TOKEN: 'my-token',
    });
  });

  it('honors an explicit securePort override', () => {
    const overrides = buildSecurePairingChildEnvOverrides({ enabled: true, controlToken: 'x', securePort: 9443 });
    expect(overrides.BUDCOM_SECURE_TRANSPORT_PORT).toBe('9443');
  });

  it('disabled mode does not alter an existing childEnv when merged through buildConnectorChildEnvironment', () => {
    const baseChildEnv = { BUDCOM_CONNECTOR_HOST: '127.0.0.1', BUDCOM_CONNECTOR_PORT: '8080' };
    const overrides = buildSecurePairingChildEnvOverrides({ enabled: false, controlToken: 'unused' });
    const merged = buildConnectorChildEnvironment(baseChildEnv, overrides);

    expect(merged.BUDCOM_SECURE_PAIRING_ENABLED).toBeUndefined();
    expect(merged.BUDCOM_DESKTOP_CONTROL_TOKEN).toBeUndefined();
    expect(merged.BUDCOM_CONNECTOR_HOST).toBe('127.0.0.1');
    expect(merged.BUDCOM_CONNECTOR_PORT).toBe('8080');
  });

  it('enabled mode adds only the four approved keys — no other existing key is disturbed', () => {
    const baseChildEnv = { BUDCOM_CONNECTOR_HOST: '127.0.0.1', BUDCOM_CONNECTOR_PORT: '8080', PATH: '/usr/bin' };
    const overrides = buildSecurePairingChildEnvOverrides({ enabled: true, controlToken: 'my-token' });
    const merged = buildConnectorChildEnvironment(baseChildEnv, overrides);

    expect(merged.BUDCOM_SECURE_PAIRING_ENABLED).toBe('true');
    expect(merged.BUDCOM_SECURE_TRANSPORT_ENABLED).toBe('true');
    expect(merged.BUDCOM_SECURE_TRANSPORT_PORT).toBe(String(DEFAULT_SECURE_TRANSPORT_PORT));
    expect(merged.BUDCOM_DESKTOP_CONTROL_TOKEN).toBe('my-token');
    expect(merged.BUDCOM_CONNECTOR_HOST).toBe('127.0.0.1');
    expect(merged.BUDCOM_CONNECTOR_PORT).toBe('8080');
  });
});
