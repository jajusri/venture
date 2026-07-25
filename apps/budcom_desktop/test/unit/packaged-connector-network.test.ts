import { describe, expect, it } from 'vitest';

import {
  assertPackagedConnectorHostIsLoopback,
  PACKAGED_CONNECTOR_LOOPBACK_HOST,
  resolvePackagedConnectorLoopbackHost,
} from '../../src/application/release/packaged-connector-network.js';

describe('packaged connector network', () => {
  it('forces loopback host for packaged startup', () => {
    expect(resolvePackagedConnectorLoopbackHost('0.0.0.0', true)).toBe(PACKAGED_CONNECTOR_LOOPBACK_HOST);
    expect(resolvePackagedConnectorLoopbackHost('192.168.1.10', true)).toBe(PACKAGED_CONNECTOR_LOOPBACK_HOST);
  });

  it('preserves configured host for unpackaged startup', () => {
    expect(resolvePackagedConnectorLoopbackHost('192.168.1.10', false)).toBe('192.168.1.10');
  });

  it('asserts loopback host when packaged', () => {
    expect(() => assertPackagedConnectorHostIsLoopback('127.0.0.1', true)).not.toThrow();
    expect(() => assertPackagedConnectorHostIsLoopback('10.0.0.5', true)).toThrow(/loopback/i);
  });
});
