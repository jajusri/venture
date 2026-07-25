import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import {
  getNetworkExposureWarning,
  isLoopbackConnectorHost,
  isLanModePolicySatisfied,
  parseConnectorBindHost,
} from '../../../src/config/network-binding.js';

describe('parseConnectorBindHost', () => {
  it('defaults to loopback-only binding', () => {
    const parsed = parseConnectorBindHost(undefined, '127.0.0.1');
    expect(parsed.host).toBe('127.0.0.1');
    expect(parsed.exposure).toBe('loopback');
    expect(parsed.isLoopback).toBe(true);
  });

  it('treats localhost as loopback', () => {
    expect(isLoopbackConnectorHost('localhost')).toBe(true);
    expect(parseConnectorBindHost('localhost', '127.0.0.1').exposure).toBe('loopback');
  });

  it('rejects 0.0.0.0 bind host', () => {
    expect(() => parseConnectorBindHost('0.0.0.0', '127.0.0.1')).toThrow(/0\.0\.0\.0/);
  });

  it('classifies explicit non-loopback host as LAN mode', () => {
    const parsed = parseConnectorBindHost('192.168.1.50', '127.0.0.1');
    expect(parsed.exposure).toBe('lan');
    expect(getNetworkExposureWarning(parsed)).toMatch(/network-exposed/);
  });

  it('requires LAN acknowledgement for production policy', () => {
    const lan = parseConnectorBindHost('192.168.1.50', '127.0.0.1');
    expect(isLanModePolicySatisfied(lan, false)).toBe(false);
    expect(isLanModePolicySatisfied(lan, true)).toBe(true);
  });
});

describe('loadConfig network binding', () => {
  it('loads secure loopback default host', () => {
    const config = loadConfig({
      env: 'test',
      port: 8080,
      tallyMinRequestIntervalMs: 0,
      tallySafeMode: false,
      tallyPoolMaxConnections: 1,
      tallyRetryMaxAttempts: 1,
      tallyCircuitBreakerEnabled: false,
    });
    expect(config.host).toBe('127.0.0.1');
    expect(config.networkExposure).toBe('loopback');
    expect(config.networkExposureWarning).toBeNull();
  });

  it('rejects 0.0.0.0 via overrides', () => {
    expect(() =>
      loadConfig({
        env: 'test',
        host: '0.0.0.0',
        port: 8080,
        tallyMinRequestIntervalMs: 0,
        tallySafeMode: false,
        tallyPoolMaxConnections: 1,
        tallyRetryMaxAttempts: 1,
        tallyCircuitBreakerEnabled: false,
      }),
    ).toThrow(/0\.0\.0\.0/);
  });

  it('rejects production LAN bind without explicit acknowledgement', () => {
    expect(() =>
      loadConfig({
        env: 'production',
        host: '192.168.1.50',
        lanModeAcknowledged: false,
        port: 8080,
        tallyMinRequestIntervalMs: 0,
        tallySafeMode: false,
        tallyPoolMaxConnections: 1,
        tallyRetryMaxAttempts: 1,
        tallyCircuitBreakerEnabled: false,
      }),
    ).toThrow(/LAN_MODE_ACKNOWLEDGED/);
  });
});
