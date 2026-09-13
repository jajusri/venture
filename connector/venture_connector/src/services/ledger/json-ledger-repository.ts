import fs from 'node:fs';
import path from 'node:path';

import type {
  LedgerContactDetailsPatch,
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
  LedgerSummary,
} from '../../erp/ledger/ledger-domain.js';

import type { LedgerRepositoryPort } from './ledger-repository.interface.js';

export interface JsonLedgerRepositoryOptions {
  readonly basePath: string;
  readonly fsImpl?: Pick<typeof fs, 'existsSync' | 'mkdirSync' | 'readFileSync' | 'writeFileSync' | 'renameSync'>;
}

interface CompanyLedgerStore {
  companyId: string;
  ledgers: Record<string, LedgerDetails>;
  updatedAt: string;
}

export class JsonLedgerRepository implements LedgerRepositoryPort {
  private readonly basePath: string;
  private readonly fsImpl: NonNullable<JsonLedgerRepositoryOptions['fsImpl']>;
  private readonly cache = new Map<string, CompanyLedgerStore>();

  constructor(options: JsonLedgerRepositoryOptions) {
    this.basePath = options.basePath;
    this.fsImpl = options.fsImpl ?? fs;
    this.fsImpl.mkdirSync(this.basePath, { recursive: true });
  }

  async upsertMany(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void> {
    const store = await this.loadCompany(companyId);
    for (const ledger of ledgers) {
      store.ledgers[ledger.id] = ledger;
    }
    store.updatedAt = new Date().toISOString();
    await this.persistCompany(store);
  }

  async insert(companyId: string, ledger: LedgerDetails): Promise<void> {
    await this.upsertMany(companyId, [ledger]);
  }

  async update(companyId: string, ledger: LedgerDetails): Promise<void> {
    await this.upsertMany(companyId, [ledger]);
  }

  /** Narrow partial update -- see `SqliteLedgerRepository.updateContactDetailsMany`'s own doc comment. */
  async updateContactDetailsMany(
    companyId: string,
    patches: readonly LedgerContactDetailsPatch[],
  ): Promise<{ updated: number; skipped: number }> {
    const store = await this.loadCompany(companyId);
    let updated = 0;
    for (const patch of patches) {
      const existing = store.ledgers[patch.ledgerId];
      if (!existing) continue;
      store.ledgers[patch.ledgerId] = {
        ...existing,
        mailing: patch.mailing,
        contact: patch.contact,
        gst: patch.gst,
      };
      updated += 1;
    }
    if (updated > 0) {
      store.updatedAt = new Date().toISOString();
      await this.persistCompany(store);
    }
    return { updated, skipped: patches.length - updated };
  }

  async softDelete(companyId: string, ledgerId: string): Promise<boolean> {
    const store = await this.loadCompany(companyId);
    const existing = store.ledgers[ledgerId];
    if (!existing) {
      return false;
    }
    store.ledgers[ledgerId] = { ...existing, isDeleted: true, status: 'inactive' };
    store.updatedAt = new Date().toISOString();
    await this.persistCompany(store);
    return true;
  }

  async delete(companyId: string, ledgerId: string): Promise<boolean> {
    const store = await this.loadCompany(companyId);
    if (!store.ledgers[ledgerId]) {
      return false;
    }
    delete store.ledgers[ledgerId];
    store.updatedAt = new Date().toISOString();
    await this.persistCompany(store);
    return true;
  }

  async findById(companyId: string, ledgerId: string): Promise<LedgerDetails | null> {
    const store = await this.loadCompany(companyId);
    return store.ledgers[ledgerId] ?? null;
  }

  async findByGuid(companyId: string, guid: string): Promise<LedgerDetails | null> {
    const store = await this.loadCompany(companyId);
    return Object.values(store.ledgers).find((ledger) => ledger.guid === guid) ?? null;
  }

  async findByName(companyId: string, name: string): Promise<LedgerDetails | null> {
    const normalized = name.trim().toLowerCase();
    const store = await this.loadCompany(companyId);
    return Object.values(store.ledgers).find((ledger) => ledger.name.toLowerCase() === normalized) ?? null;
  }

  async findByAlias(companyId: string, alias: string): Promise<LedgerDetails | null> {
    const normalized = alias.trim().toLowerCase();
    const store = await this.loadCompany(companyId);
    return Object.values(store.ledgers).find((ledger) => ledger.alias?.toLowerCase() === normalized) ?? null;
  }

  async search(companyId: string, params: LedgerSearchParams): Promise<LedgerSearchResult> {
    const store = await this.loadCompany(companyId);
    let items = Object.values(store.ledgers).filter((ledger) => !ledger.isDeleted);

    if (params.query?.trim()) {
      const query = params.query.trim().toLowerCase();
      items = items.filter(
        (ledger) =>
          ledger.name.toLowerCase().includes(query)
          || ledger.normalizedName.toLowerCase().includes(query)
          || ledger.alias?.toLowerCase().includes(query)
          || ledger.parentGroup?.toLowerCase().includes(query),
      );
    }

    if (params.status) {
      items = items.filter((ledger) => ledger.status === params.status);
    }

    if (params.parentGroup) {
      const parent = params.parentGroup.toLowerCase();
      items = items.filter((ledger) => ledger.parentGroup?.toLowerCase() === parent);
    }

    const sortBy = params.sortBy ?? 'name';
    const direction = params.sortDirection === 'desc' ? -1 : 1;
    items.sort((left, right) => {
      let leftValue = left.name;
      let rightValue = right.name;
      if (sortBy === 'parentGroup') {
        leftValue = left.parentGroup ?? '';
        rightValue = right.parentGroup ?? '';
      } else if (sortBy === 'closingBalance') {
        leftValue = left.closingBalance?.amount ?? '';
        rightValue = right.closingBalance?.amount ?? '';
      } else if (sortBy === 'syncedAt') {
        leftValue = left.syncedAt;
        rightValue = right.syncedAt;
      }
      return leftValue.localeCompare(rightValue) * direction;
    });

    const totalItems = items.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / params.pageSize));
    const start = (params.page - 1) * params.pageSize;
    const pageItems = items.slice(start, start + params.pageSize).map(toSummary);

    return {
      items: pageItems,
      pagination: {
        page: params.page,
        pageSize: params.pageSize,
        totalItems,
        totalPages,
      },
    };
  }

  async getStatistics(companyId: string): Promise<LedgerStatistics> {
    const store = await this.loadCompany(companyId);
    const ledgers = Object.values(store.ledgers);
    return {
      totalLedgers: ledgers.length,
      activeLedgers: ledgers.filter((ledger) => ledger.status === 'active' && !ledger.isDeleted).length,
      inactiveLedgers: ledgers.filter((ledger) => ledger.status === 'inactive').length,
      reservedLedgers: ledgers.filter((ledger) => ledger.status === 'reserved').length,
      deletedLedgers: ledgers.filter((ledger) => ledger.isDeleted).length,
      withGst: ledgers.filter((ledger) => Boolean(ledger.gst?.gstin)).length,
      withOpeningBalance: ledgers.filter((ledger) => ledger.openingBalance !== undefined).length,
      lastSyncedAt: store.updatedAt,
    };
  }

  async clearCompany(companyId: string): Promise<void> {
    this.cache.delete(companyId);
    const filePath = this.companyFilePath(companyId);
    if (this.fsImpl.existsSync(filePath)) {
      this.fsImpl.writeFileSync(filePath, JSON.stringify({ companyId, ledgers: {}, updatedAt: new Date().toISOString() }, null, 2));
    }
  }

  async countByCompany(companyId: string): Promise<number> {
    const store = await this.loadCompany(companyId);
    return Object.keys(store.ledgers).length;
  }

  async hasLegacyLedgerIds(companyId: string): Promise<boolean> {
    const store = await this.loadCompany(companyId);
    return Object.values(store.ledgers).some(
      (ledger) => !ledger.id.startsWith('guid:') && !ledger.id.startsWith('name:'),
    );
  }

  async getLedgerIdentityVersion(_companyId: string): Promise<number> {
    return 2;
  }

  async replaceCompanyLedgersAtomically(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void> {
    const store: CompanyLedgerStore = {
      companyId,
      ledgers: Object.fromEntries(ledgers.map((ledger) => [ledger.id, ledger])),
      updatedAt: new Date().toISOString(),
    };
    await this.persistCompany(store);
  }

  async markLedgerIdentityCurrent(_companyId: string): Promise<void> {
    return Promise.resolve();
  }

  private async loadCompany(companyId: string): Promise<CompanyLedgerStore> {
    const cached = this.cache.get(companyId);
    if (cached) {
      return cached;
    }

    const filePath = this.companyFilePath(companyId);
    if (!this.fsImpl.existsSync(filePath)) {
      const empty: CompanyLedgerStore = { companyId, ledgers: {}, updatedAt: new Date().toISOString() };
      this.cache.set(companyId, empty);
      return empty;
    }

    const parsed = JSON.parse(this.fsImpl.readFileSync(filePath, 'utf8')) as CompanyLedgerStore;
    this.cache.set(companyId, parsed);
    return parsed;
  }

  private async persistCompany(store: CompanyLedgerStore): Promise<void> {
    const filePath = this.companyFilePath(store.companyId);
    const tempPath = `${filePath}.tmp`;
    this.fsImpl.writeFileSync(tempPath, `${JSON.stringify(store, null, 2)}\n`, 'utf8');
    this.fsImpl.renameSync(tempPath, filePath);
    this.cache.set(store.companyId, store);
  }

  private companyFilePath(companyId: string): string {
    const safeId = companyId.replace(/[^a-zA-Z0-9._-]/g, '_');
    return path.join(this.basePath, `${safeId}.json`);
  }
}

function toSummary(ledger: LedgerDetails): LedgerSummary {
  return {
    id: ledger.id,
    name: ledger.name,
    normalizedName: ledger.normalizedName,
    alias: ledger.alias,
    parentGroup: ledger.parentGroup,
    status: ledger.status,
    openingBalance: ledger.openingBalance,
    closingBalance: ledger.closingBalance,
    balanceNature: ledger.balanceNature,
    guid: ledger.guid,
    alterId: ledger.alterId,
    masterId: ledger.masterId,
    identitySource: ledger.identitySource,
    dataQuality: ledger.dataQuality,
    isBillWiseOn: ledger.isBillWiseOn,
    isDeleted: ledger.isDeleted,
    syncedAt: ledger.syncedAt,
  };
}
