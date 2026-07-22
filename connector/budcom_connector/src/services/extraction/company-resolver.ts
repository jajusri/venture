import type { CompanyDiscoveryService } from '../interfaces/company-discovery.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { slugify } from '../../extraction/normalization/strings.js';

export class CompanyResolver {
  private cache: { expiresAt: number; map: Map<string, string> } | null = null;
  private readonly cacheTtlMs = 60_000;

  constructor(private readonly companyDiscovery: CompanyDiscoveryService) {}

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

  private async getCompanyMap(): Promise<Map<string, string>> {
    const now = Date.now();
    if (this.cache && this.cache.expiresAt > now) {
      return this.cache.map;
    }

    const result = await this.companyDiscovery.discoverCompanies();
    const map = new Map<string, string>();
    for (const company of result.items) {
      map.set(company.id, company.name);
      map.set(slugify(company.name), company.name);
    }

    this.cache = { expiresAt: now + this.cacheTtlMs, map };
    return map;
  }
}
