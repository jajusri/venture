import type { CompanyDiscoveryService } from '../interfaces/company-discovery.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { slugify } from '../../extraction/normalization/strings.js';

export const COMPANY_DISCOVERY_CACHE_TTL_MS = 60_000;

export interface DiscoveredCompanyRef {
  readonly id: string;
  readonly name: string;
}

export interface CompanyDiscoverySnapshot {
  readonly companies: readonly DiscoveredCompanyRef[];
  readonly tallyReachable: boolean;
}

export interface CompanyResolverOptions {
  readonly nowMs?: () => number;
  readonly cacheTtlMs?: number;
}

export class CompanyResolver {
  private cache: {
    expiresAt: number;
    map: Map<string, string>;
    snapshot: CompanyDiscoverySnapshot;
  } | null = null;
  private inFlightRefresh: Promise<Map<string, string>> | null = null;
  private readonly nowMs: () => number;
  private readonly cacheTtlMs: number;

  constructor(
    private readonly companyDiscovery: CompanyDiscoveryService,
    options: CompanyResolverOptions = {},
  ) {
    this.nowMs = options.nowMs ?? (() => Date.now());
    this.cacheTtlMs = options.cacheTtlMs ?? COMPANY_DISCOVERY_CACHE_TTL_MS;
  }

  async resolveName(companyId: string): Promise<string> {
    const map = await this.getCompanyMap();
    const name = map.get(companyId);
    if (!name) {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        `Unknown company id: ${companyId}`,
        404,
        { companyId },
      );
    }
    return name;
  }

  async getDiscoverySnapshot(): Promise<CompanyDiscoverySnapshot> {
    await this.getCompanyMap();
    return this.cache?.snapshot ?? { companies: [], tallyReachable: false };
  }

  invalidateCache(): void {
    this.cache = null;
  }

  /**
   * Synchronous, zero-I/O peek at the current cached discovery snapshot, or null if nothing has
   * been discovered yet (or the cache was invalidated). Never triggers a refresh — safe to call
   * from hot/synchronous paths like ConnectorSessionServiceImpl.getStatus(), which must stay fast
   * and cannot await a live Tally round-trip. A null result must be read as "unknown", not "the
   * selection is invalid" -- there's no evidence either way yet.
   */
  peekCachedSnapshot(): CompanyDiscoverySnapshot | null {
    return this.cache?.snapshot ?? null;
  }

  getCacheAgeMs(): number | null {
    if (!this.cache) {
      return null;
    }
    return Math.max(0, this.cacheTtlMs - (this.cache.expiresAt - this.nowMs()));
  }

  private async getCompanyMap(): Promise<Map<string, string>> {
    const now = this.nowMs();
    if (this.cache && this.cache.expiresAt > now) {
      return this.cache.map;
    }

    if (!this.inFlightRefresh) {
      this.inFlightRefresh = this.refreshCompanyMap(now).finally(() => {
        this.inFlightRefresh = null;
      });
    }

    return this.inFlightRefresh;
  }

  private async refreshCompanyMap(now: number): Promise<Map<string, string>> {
    try {
      const result = await this.companyDiscovery.discoverCompanies();
      const map = new Map<string, string>();
      const companies: DiscoveredCompanyRef[] = [];
      for (const company of result.items) {
        map.set(company.id, company.name);
        map.set(slugify(company.name), company.name);
        companies.push({ id: company.id, name: company.name });
      }

      this.cache = {
        expiresAt: now + this.cacheTtlMs,
        map,
        snapshot: {
          companies,
          tallyReachable: result.tallyReachable,
        },
      };
      return map;
    } catch (error) {
      this.invalidateCache();
      throw error;
    }
  }
}
