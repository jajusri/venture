import { describe, expect, it } from 'vitest';

import { getLiveIpv4Addresses } from '../../../src/application/network/route-querier.js';

/** Minimal stand-in for node:os's NetworkInterfaceInfo, only the fields getLiveIpv4Addresses reads. */
function iface(overrides: {
  readonly address: string;
  readonly family?: 'IPv4' | 'IPv6';
  readonly internal?: boolean;
}) {
  return {
    address: overrides.address,
    netmask: '255.255.255.0',
    family: overrides.family ?? 'IPv4',
    mac: '00:00:00:00:00:00',
    internal: overrides.internal ?? false,
    cidr: `${overrides.address}/24`,
  };
}

describe('getLiveIpv4Addresses', () => {
  // TD-017: this is the last-line-of-defense cross-check against PowerShell route-table
  // staleness — a pure, synchronous function over an injected node:os.networkInterfaces()
  // result, so it never touches the real NIC in tests.

  it('collects IPv4 addresses from every non-internal interface', () => {
    const fake = () => ({
      'Wi-Fi': [iface({ address: '10.142.207.231' })],
      Ethernet: [iface({ address: '192.168.1.20' })],
    });

    const result = getLiveIpv4Addresses(fake as never);

    expect(result.has('10.142.207.231')).toBe(true);
    expect(result.has('192.168.1.20')).toBe(true);
    expect(result.size).toBe(2);
  });

  it('excludes the loopback/internal interface', () => {
    const fake = () => ({
      'Loopback Pseudo-Interface 1': [iface({ address: '127.0.0.1', internal: true })],
      'Wi-Fi': [iface({ address: '10.0.0.5' })],
    });

    const result = getLiveIpv4Addresses(fake as never);

    expect(result.has('127.0.0.1')).toBe(false);
    expect(result.has('10.0.0.5')).toBe(true);
  });

  it('excludes IPv6 entries', () => {
    const fake = () => ({
      'Wi-Fi': [iface({ address: 'fe80::1', family: 'IPv6' }), iface({ address: '10.0.0.5' })],
    });

    const result = getLiveIpv4Addresses(fake as never);

    expect(result.has('fe80::1')).toBe(false);
    expect(result.has('10.0.0.5')).toBe(true);
  });

  it('handles an interface entry list of undefined (Node can report this) without throwing', () => {
    const fake = () => ({ 'Ghost Adapter': undefined });

    expect(() => getLiveIpv4Addresses(fake as never)).not.toThrow();
    expect(getLiveIpv4Addresses(fake as never).size).toBe(0);
  });

  it('returns an empty set when no interfaces are reported', () => {
    const fake = () => ({});

    expect(getLiveIpv4Addresses(fake as never).size).toBe(0);
  });
});
