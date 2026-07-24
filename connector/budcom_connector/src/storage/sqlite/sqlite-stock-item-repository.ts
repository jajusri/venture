import type { DatabaseSync } from './node-sqlite.js';

import type {
  StockItemDetails,
  StockItemSearchParams,
  StockItemSearchResult,
  StockItemStatistics,
  StockItemSummary,
} from '../../erp/stock-item/stock-item-domain.js';
import type { StockItemRepositoryPort } from '../../services/stock-item/stock-item-repository.interface.js';
import { computeStockItemFingerprint } from '../../services/stock-item/stock-item-fingerprint.js';
import type { SqliteDatabase } from './sqlite-database.js';

const ALLOWED_SORT_FIELDS = new Set(['name', 'parentGroup', 'category', 'baseUnit', 'syncedAt']);

export class SqliteStockItemRepository implements StockItemRepositoryPort {
  constructor(private readonly database: SqliteDatabase) {}

  async upsertMany(companyId: string, items: readonly StockItemDetails[]): Promise<void> {
    const db = this.database.getDatabase();
    const now = new Date().toISOString();
    const stmt = db.prepare(`
      INSERT INTO stock_items (
        company_id, stock_item_id, name, normalized_name, parent_group, category, base_unit,
        data_quality, opening_balance_json, closing_balance_json, hsn_code, gst_rate,
        guid, alter_id, alias, part_number, status, source_system,
        metadata_json, content_fingerprint, is_deleted, synced_at, updated_at
      ) VALUES (
        @companyId, @stockItemId, @name, @normalizedName, @parentGroup, @category, @baseUnit,
        @dataQuality, @openingBalanceJson, @closingBalanceJson, @hsnCode, @gstRate,
        @guid, @alterId, @alias, @partNumber, @status, @sourceSystem,
        @metadataJson, @fingerprint, @isDeleted, @syncedAt, @updatedAt
      )
      ON CONFLICT(company_id, stock_item_id) DO UPDATE SET
        name = excluded.name,
        normalized_name = excluded.normalized_name,
        parent_group = excluded.parent_group,
        category = excluded.category,
        base_unit = excluded.base_unit,
        data_quality = excluded.data_quality,
        opening_balance_json = excluded.opening_balance_json,
        closing_balance_json = excluded.closing_balance_json,
        hsn_code = excluded.hsn_code,
        gst_rate = excluded.gst_rate,
        guid = excluded.guid,
        alter_id = excluded.alter_id,
        alias = excluded.alias,
        part_number = excluded.part_number,
        status = excluded.status,
        source_system = excluded.source_system,
        metadata_json = excluded.metadata_json,
        content_fingerprint = excluded.content_fingerprint,
        is_deleted = excluded.is_deleted,
        synced_at = excluded.synced_at,
        updated_at = excluded.updated_at
    `);

    const writeAll = (): void => {
      for (const item of items) {
        stmt.run(toRow(companyId, item, now));
      }
    };

    // Join ambient sync-batch transaction when present; otherwise own a short sync TX.
    // Standalone callers (tests) require synchronous completion.
    if (this.database.isInTransaction()) {
      writeAll();
      return;
    }
    this.database.runInTransactionSync(writeAll);
  }

  insert(companyId: string, item: StockItemDetails): Promise<void> {
    return this.upsertMany(companyId, [item]);
  }

  update(companyId: string, item: StockItemDetails): Promise<void> {
    return this.upsertMany(companyId, [item]);
  }

  softDelete(companyId: string, stockItemId: string): Promise<boolean> {
    const db = this.database.getDatabase();
    const result = db
      .prepare(
        `UPDATE stock_items SET is_deleted = 1, updated_at = @updatedAt
         WHERE company_id = @companyId AND stock_item_id = @stockItemId`,
      )
      .run({ companyId, stockItemId, updatedAt: new Date().toISOString() });
    return Promise.resolve(result.changes > 0);
  }

  delete(companyId: string, stockItemId: string): Promise<boolean> {
    const db = this.database.getDatabase();
    const result = db
      .prepare('DELETE FROM stock_items WHERE company_id = ? AND stock_item_id = ?')
      .run(companyId, stockItemId);
    return Promise.resolve(result.changes > 0);
  }

  findById(companyId: string, stockItemId: string): Promise<StockItemDetails | null> {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM stock_items WHERE company_id = ? AND stock_item_id = ?')
      .get(companyId, stockItemId) as unknown as StockItemRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  findByName(companyId: string, name: string): Promise<StockItemDetails | null> {
    const normalized = name.trim().toLowerCase();
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM stock_items WHERE company_id = ? AND normalized_name = ?')
      .get(companyId, normalized) as unknown as StockItemRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  search(companyId: string, params: StockItemSearchParams): Promise<StockItemSearchResult> {
    const db = this.database.getDatabase();
    const filters: string[] = ['company_id = @companyId', 'is_deleted = 0'];
    const values: Record<string, string | number> = { companyId };

    if (params.query?.trim()) {
      filters.push(
        '(lower(name) LIKE @query OR lower(normalized_name) LIKE @query OR lower(parent_group) LIKE @query OR lower(category) LIKE @query OR lower(base_unit) LIKE @query)',
      );
      values.query = `%${params.query.trim().toLowerCase()}%`;
    }
    if (params.parentGroup) {
      filters.push('lower(parent_group) = @parentGroup');
      values.parentGroup = params.parentGroup.toLowerCase();
    }
    if (params.category) {
      filters.push('lower(category) = @category');
      values.category = params.category.toLowerCase();
    }
    if (params.dataQuality) {
      filters.push('data_quality = @dataQuality');
      values.dataQuality = params.dataQuality;
    }

    const where = filters.join(' AND ');
    const countRow = db
      .prepare(`SELECT COUNT(*) AS total FROM stock_items WHERE ${where}`)
      .get(values) as { total: number };
    const totalItems = countRow.total;
    const totalPages = Math.max(1, Math.ceil(totalItems / params.pageSize));
    const sortField = ALLOWED_SORT_FIELDS.has(params.sortBy ?? 'name') ? params.sortBy ?? 'name' : 'name';
    const sortColumn = mapSortColumn(sortField);
    const direction = params.sortDirection === 'desc' ? 'DESC' : 'ASC';
    const offset = (params.page - 1) * params.pageSize;

    const rows = db
      .prepare(
        `SELECT * FROM stock_items WHERE ${where}
         ORDER BY ${sortColumn} ${direction}, stock_item_id ASC
         LIMIT @limit OFFSET @offset`,
      )
      .all({ ...values, limit: params.pageSize, offset }) as unknown as StockItemRow[];

    return Promise.resolve({
      items: rows.map(toSummary),
      pagination: {
        page: params.page,
        pageSize: params.pageSize,
        totalItems,
        totalPages,
      },
    });
  }

  getStatistics(companyId: string): Promise<StockItemStatistics> {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT
          COUNT(*) AS totalStockItems,
          SUM(CASE WHEN base_unit IS NOT NULL AND base_unit != '' AND is_deleted = 0 THEN 1 ELSE 0 END) AS withBaseUnit,
          SUM(CASE WHEN data_quality = 'incomplete' AND is_deleted = 0 THEN 1 ELSE 0 END) AS incompleteData,
          SUM(CASE WHEN hsn_code IS NOT NULL AND hsn_code != '' THEN 1 ELSE 0 END) AS withHsn,
          SUM(CASE WHEN gst_rate IS NOT NULL AND gst_rate != '' THEN 1 ELSE 0 END) AS withGst,
          SUM(CASE WHEN opening_balance_json IS NOT NULL AND opening_balance_json != '' THEN 1 ELSE 0 END) AS withOpeningBalance,
          SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deletedStockItems,
          MAX(updated_at) AS lastSyncedAt
        FROM stock_items WHERE company_id = ?`,
      )
      .get(companyId) as Record<string, number | string | null>;

    return Promise.resolve({
      totalStockItems: Number(row.totalStockItems ?? 0),
      withBaseUnit: Number(row.withBaseUnit ?? 0),
      incompleteData: Number(row.incompleteData ?? 0),
      withHsn: Number(row.withHsn ?? 0),
      withGst: Number(row.withGst ?? 0),
      withOpeningBalance: Number(row.withOpeningBalance ?? 0),
      deletedStockItems: Number(row.deletedStockItems ?? 0),
      lastSyncedAt: row.lastSyncedAt ? String(row.lastSyncedAt) : null,
    });
  }

  clearCompany(companyId: string): Promise<void> {
    const db = this.database.getDatabase();
    db.prepare('DELETE FROM stock_items WHERE company_id = ?').run(companyId);
    return Promise.resolve();
  }

  countByCompany(companyId: string): Promise<number> {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT COUNT(*) AS total FROM stock_items WHERE company_id = ?')
      .get(companyId) as { total: number };
    return Promise.resolve(row.total);
  }
}

interface StockItemRow {
  company_id: string;
  stock_item_id: string;
  name: string;
  normalized_name: string;
  parent_group: string | null;
  category: string | null;
  base_unit: string | null;
  data_quality: string;
  opening_balance_json: string | null;
  closing_balance_json: string | null;
  hsn_code: string | null;
  gst_rate: string | null;
  guid: string | null;
  alter_id: string | null;
  alias: string | null;
  part_number: string | null;
  status: string;
  source_system: string;
  metadata_json: string | null;
  content_fingerprint: string;
  is_deleted: number;
  synced_at: string;
  updated_at: string;
}

function toRow(companyId: string, item: StockItemDetails, updatedAt: string) {
  return {
    companyId,
    stockItemId: item.id,
    name: item.name,
    normalizedName: item.normalizedName,
    parentGroup: item.parentGroup ?? null,
    category: item.category ?? null,
    baseUnit: item.baseUnit ?? null,
    dataQuality: item.dataQuality,
    openingBalanceJson: item.openingBalance ? JSON.stringify(item.openingBalance) : null,
    closingBalanceJson: item.closingBalance ? JSON.stringify(item.closingBalance) : null,
    hsnCode: item.hsnCode ?? null,
    gstRate: item.gstRate ?? null,
    guid: item.guid ?? null,
    alterId: item.alterId ?? null,
    alias: item.alias ?? null,
    partNumber: item.partNumber ?? null,
    status: item.status,
    sourceSystem: item.sourceSystem,
    metadataJson: item.metadata ? JSON.stringify(item.metadata) : null,
    fingerprint: computeStockItemFingerprint(item),
    isDeleted: item.isDeleted ? 1 : 0,
    syncedAt: item.syncedAt,
    updatedAt,
  };
}

function fromRow(row: StockItemRow): StockItemDetails {
  return {
    id: row.stock_item_id,
    name: row.name,
    normalizedName: row.normalized_name,
    parentGroup: row.parent_group ?? undefined,
    category: row.category ?? undefined,
    baseUnit: row.base_unit ?? undefined,
    dataQuality: row.data_quality as StockItemDetails['dataQuality'],
    openingBalance: parseJson(row.opening_balance_json),
    closingBalance: parseJson(row.closing_balance_json),
    hsnCode: row.hsn_code ?? undefined,
    gstRate: row.gst_rate ?? undefined,
    guid: row.guid ?? undefined,
    alterId: row.alter_id ?? undefined,
    alias: row.alias ?? undefined,
    partNumber: row.part_number ?? undefined,
    status: row.status as StockItemDetails['status'],
    sourceSystem: row.source_system,
    metadata: parseJson(row.metadata_json),
    isDeleted: row.is_deleted === 1,
    syncedAt: row.synced_at,
  };
}

function toSummary(row: StockItemRow): StockItemSummary {
  return fromRow(row);
}

function parseJson<T>(value: string | null): T | undefined {
  if (!value) return undefined;
  return JSON.parse(value) as T;
}

function mapSortColumn(sortBy: string): string {
  switch (sortBy) {
    case 'parentGroup':
      return 'parent_group';
    case 'category':
      return 'category';
    case 'baseUnit':
      return 'base_unit';
    case 'syncedAt':
      return 'synced_at';
    default:
      return 'name';
  }
}

export type { DatabaseSync };
