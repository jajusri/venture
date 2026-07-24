import { describe, expect, it, vi } from 'vitest';

import { AppError, ErrorCodes } from '../../../src/infrastructure/errors/app-error.js';
import { CompanyResolver, COMPANY_DISCOVERY_CACHE_TTL_MS } from '../../../src/services/extraction/company-resolver.js';
import type { CompanyDiscoveryService } from '../../../src/services/interfaces/company-discovery.js';

function discoveryService(
  impl: CompanyDiscoveryService['discoverCompanies'],
): CompanyDiscoveryService {
  return {
    start: vi.fn(),
    stop: vi.fn(),
    isRunning: () => true,
    discoverCompanies: impl,
    getStatus: () => ({ name: 'CompanyDiscovery', running: true, ready: true }),
  };
}

describe('company resolver discovery cache (4A-4G)', () => {
  it('4A serves cache hit without a second discovery call inside TTL', async () => {
    const discoverCompanies = vi.fn(async () => ({
      items: [{ id: 'estimation', name: 'ESTIMATION' }],
      schemaVersion: '1',
      dataFreshnessAt: new Date().toISOString(),
      contractVersion: '1' as const,
      status: 'SUCCESS' as const,
      tallyReachable: true,
    }));
    const resolver = new CompanyResolver(discoveryService(discoverCompanies), {
      nowMs: () => 1_000,
    });

    await resolver.resolveName('estimation');
    await resolver.resolveName('estimation');
    expect(discoverCompanies).toHaveBeenCalledTimes(1);
  });

  it('4B refreshes after TTL expiry using injectable clock', async () => {
    let now = 1_000;
    const discoverCompanies = vi.fn(async () => ({
      items: [{ id: 'estimation', name: 'ESTIMATION' }],
      schemaVersion: '1',
      dataFreshnessAt: new Date().toISOString(),
      contractVersion: '1' as const,
      status: 'SUCCESS' as const,
      tallyReachable: true,
    }));
    const resolver = new CompanyResolver(discoveryService(discoverCompanies), {
      nowMs: () => now,
      cacheTtlMs: COMPANY_DISCOVERY_CACHE_TTL_MS,
    });

    await resolver.resolveName('estimation');
    now += COMPANY_DISCOVERY_CACHE_TTL_MS + 1;
    await resolver.resolveName('estimation');
    expect(discoverCompanies).toHaveBeenCalledTimes(2);
  });

  it('4C invalidates explicitly and refetches on next resolution', async () => {
    const discoverCompanies = vi.fn(async () => ({
      items: [{ id: 'estimation', name: 'ESTIMATION' }],
      schemaVersion: '1',
      dataFreshnessAt: new Date().toISOString(),
      contractVersion: '1' as const,
      status: 'SUCCESS' as const,
      tallyReachable: true,
    }));
    const resolver = new CompanyResolver(discoveryService(discoverCompanies), {
      nowMs: () => 1_000,
    });

    await resolver.resolveName('estimation');
    resolver.invalidateCache();
    await resolver.resolveName('estimation');
    expect(discoverCompanies).toHaveBeenCalledTimes(2);
  });

  it('4E clears cache when discovery fails so stale entries are not retained', async () => {
    let failNext = false;
    const discoverCompanies = vi.fn(async () => {
      if (failNext) {
        throw new AppError(ErrorCodes.SERVICE_UNAVAILABLE, 'Tally unavailable', 503);
      }
      return {
        items: [{ id: 'estimation', name: 'ESTIMATION' }],
        schemaVersion: '1',
        dataFreshnessAt: new Date().toISOString(),
        contractVersion: '1' as const,
        status: 'SUCCESS' as const,
        tallyReachable: true,
      };
    });
    const resolver = new CompanyResolver(discoveryService(discoverCompanies), {
      nowMs: () => 1_000,
      cacheTtlMs: 10_000,
    });

    await resolver.resolveName('estimation');
    resolver.invalidateCache();
    failNext = true;
    await expect(resolver.resolveName('estimation')).rejects.toMatchObject({ statusCode: 503 });
    failNext = false;
    await resolver.resolveName('estimation');
    expect(discoverCompanies).toHaveBeenCalledTimes(3);
  });

  it('4G coalesces concurrent cache misses into one discovery call', async () => {
    let resolveDiscovery: (() => void) | undefined;
    const discoverCompanies = vi.fn(
      () =>
        new Promise<Awaited<ReturnType<CompanyDiscoveryService['discoverCompanies']>>>((resolve) => {
          resolveDiscovery = () =>
            resolve({
              items: [{ id: 'estimation', name: 'ESTIMATION' }],
              schemaVersion: '1',
              dataFreshnessAt: new Date().toISOString(),
              contractVersion: '1' as const,
              status: 'SUCCESS' as const,
              tallyReachable: true,
            });
        }),
    );
    const resolver = new CompanyResolver(discoveryService(discoverCompanies), {
      nowMs: () => 1_000,
    });

    const first = resolver.resolveName('estimation');
    const second = resolver.resolveName('estimation');
    resolveDiscovery?.();
    const [a, b] = await Promise.all([first, second]);
    expect(a).toBe('ESTIMATION');
    expect(b).toBe('ESTIMATION');
    expect(discoverCompanies).toHaveBeenCalledTimes(1);
  });
});
