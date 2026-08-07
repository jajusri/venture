/**
 * @vitest-environment jsdom
 */
import fs from 'node:fs';
import path from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { bindSettingsActions } from '../../src/renderer/scripts/app.js';

function loadRendererMarkup(): void {
  const html = fs.readFileSync(
    path.resolve(process.cwd(), 'src/renderer/index.html'),
    'utf8',
  );
  const parsed = new DOMParser().parseFromString(html, 'text/html');
  document.body.innerHTML = parsed.body.innerHTML;
}

function mobileAccessStatus(overrides: Partial<Awaited<ReturnType<typeof window.budcomDesktop.getMobileAccessStatus>>> = {}) {
  return {
    connectorId: 'connector-abc',
    connectorName: 'Front Desk PC',
    connectorBindMode: 'trusted-lan' as const,
    activeNetwork: { adapterName: 'Wi-Fi', ipv4: '192.168.1.42', profileCategory: 'Private' },
    reachableEndpoint: '192.168.1.42',
    trustedLanEligible: true,
    trustedLanBlockedReason: null,
    discoveryAdvertising: false,
    pairedDeviceCount: 0,
    rebind: { status: 'idle' as const, at: null, message: null },
    connectorReachable: true,
    userMessage: null,
    ...overrides,
  };
}

/**
 * TD-012 (docs/technical-debt/registry.md): a normal user switching Connector bind mode to
 * Trusted LAN in Settings must never have to look up or type their own machine's private IPv4
 * address — the (read-only, Advanced-Connector-Settings-only) host field auto-fills from the
 * same live network detection the Mobile Access / pairing panel already uses.
 */
describe('Settings — Trusted-LAN host auto-detection', () => {
  beforeEach(() => {
    loadRendererMarkup();
    bindSettingsActions();
  });

  it('auto-fills the host field from the detected private LAN address when switching to Trusted LAN', async () => {
    window.budcomDesktop = {
      getMobileAccessStatus: vi.fn(async () => mobileAccessStatus()),
    } as unknown as typeof window.budcomDesktop;

    const select = document.getElementById('input-connector-bind-mode') as HTMLSelectElement;
    select.value = 'trusted-lan';
    select.dispatchEvent(new Event('change'));
    await Promise.resolve();
    await Promise.resolve();

    const hostInput = document.getElementById('input-connector-host') as HTMLInputElement;
    expect(hostInput.value).toBe('192.168.1.42');
    expect(hostInput.readOnly).toBe(true);
    expect(document.getElementById('settings-status-message')?.textContent).toContain('automatically');
  });

  it('leaves the host field blank with a plain-language reason when no eligible network is detected yet', async () => {
    window.budcomDesktop = {
      getMobileAccessStatus: vi.fn(async () =>
        mobileAccessStatus({ activeNetwork: null, trustedLanEligible: false, trustedLanBlockedReason: null })),
    } as unknown as typeof window.budcomDesktop;

    const select = document.getElementById('input-connector-bind-mode') as HTMLSelectElement;
    select.value = 'trusted-lan';
    select.dispatchEvent(new Event('change'));
    await Promise.resolve();
    await Promise.resolve();

    const hostInput = document.getElementById('input-connector-host') as HTMLInputElement;
    expect(hostInput.value).toBe('');
    const status = document.getElementById('settings-status-message');
    expect(status?.className).toContain('form-error');
    expect(status?.textContent).not.toMatch(/\d+\.\d+\.\d+\.\d+/);
  });

  it('never shows a raw connectorHost IPv4-validation error as the auto-detect failure message', async () => {
    window.budcomDesktop = {
      getMobileAccessStatus: vi.fn(async () =>
        mobileAccessStatus({
          activeNetwork: null,
          trustedLanEligible: false,
          trustedLanBlockedReason: 'Trusted-LAN mode is blocked: "Wi-Fi" is on a Public Windows network profile. Switch the network to Private in Windows Settings, or use Local-only mode.',
        })),
    } as unknown as typeof window.budcomDesktop;

    const select = document.getElementById('input-connector-bind-mode') as HTMLSelectElement;
    select.value = 'trusted-lan';
    select.dispatchEvent(new Event('change'));
    await Promise.resolve();
    await Promise.resolve();

    const status = document.getElementById('settings-status-message');
    expect(status?.textContent).not.toMatch(/connectorHost must be/i);
    expect(status?.textContent).toContain('Trusted-LAN mode is blocked');
  });

  it('still sets the host field to 127.0.0.1 (read-only) for local-only mode', () => {
    const select = document.getElementById('input-connector-bind-mode') as HTMLSelectElement;
    select.value = 'local-only';
    select.dispatchEvent(new Event('change'));

    const hostInput = document.getElementById('input-connector-host') as HTMLInputElement;
    expect(hostInput.value).toBe('127.0.0.1');
    expect(hostInput.readOnly).toBe(true);
  });
});
