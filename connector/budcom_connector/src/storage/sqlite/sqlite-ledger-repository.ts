import type { DatabaseSync } from './node-sqlite.js';

import type {
  LedgerDetails,
  LedgerSearchParams,
  LedgerSearchResult,
  LedgerStatistics,
  LedgerSummary,
} from '../../erp/ledger/ledger-domain.js';
import type { LedgerRepositoryPort } from '../../services/ledger/ledger-repository.interface.js';
import { computeLedgerFingerprint } from '../../services/ledger/ledger-fingerprint.js';
import type { SqliteDatabase } from './sqlite-database.js';

const ALLOWED_SORT_FIELDS = new Set(['name', 'parentGroup', 'closingBalance', 'syncedAt']);

export class SqliteLedgerRepository implements LedgerRepositoryPort {
  constructor(private readonly database: SqliteDatabase) {}

  async upsertMany(companyId: string, ledgers: readonly LedgerDetails[]): Promise<void> {
    const db = this.database.getDatabase();
    const now = new Date().toISOString();
    const stmt = db.prepare(`
      INSERT INTO ledgers (
        company_id, ledger_id, name, normalized_name, alias, parent_group, status, balance_nature,
        opening_balance_json, closing_balance_json, guid, alter_id, reserved_name,
        mailing_json, contact_json, gst_json, metadata_json, content_fingerprint,
        is_deleted, synced_at, updated_at
      ) VALUES (
        @companyId, @ledgerId, @name, @normalizedName, @alias, @parentGroup, @status, @balanceNature,
        @openingBalanceJson, @closingBalanceJson, @guid, @alterId, @reservedName,
        @mailingJson, @contactJson, @gstJson, @metadataJson, @fingerprint,
        @isDeleted, @syncedAt, @updatedAt
      )
      ON CONFLICT(company_id, ledger_id) DO UPDATE SET
        name = excluded.name,
        normalized_name = excluded.normalized_name,
        alias = excluded.alias,
        parent_group = excluded.parent_group,
        status = excluded.status,
        balance_nature = excluded.balance_nature,
        opening_balance_json = excluded.opening_balance_json,
        closing_balance_json = excluded.closing_balance_json,
        guid = excluded.guid,
        alter_id = excluded.alter_id,
        reserved_name = excluded.reserved_name,
        mailing_json = excluded.mailing_json,
        contact_json = excluded.contact_json,
        gst_json = excluded.gst_json,
        metadata_json = excluded.metadata_json,
        content_fingerprint = excluded.content_fingerprint,
        is_deleted = excluded.is_deleted,
        synced_at = excluded.synced_at,
        updated_at = excluded.updated_at
    `);

    const writeAll = (): void => {
      for (const ledger of ledgers) {
        stmt.run(toRow(companyId, ledger, now));
      }
    };

    // Join ambient sync-batch transaction when present; otherwise own a short sync TX.
    // Standalone callers (migration/tests) require synchronous completion.
    if (this.database.isInTransaction()) {
      writeAll();
      return;
    }
    this.database.runInTransactionSync(writeAll);
  }

  insert(companyId: string, ledger: LedgerDetails): Promise<void> {
    return this.upsertMany(companyId, [ledger]);
  }

  update(companyId: string, ledger: LedgerDetails): Promise<void> {
    return this.upsertMany(companyId, [ledger]);
  }

  softDelete(companyId: string, ledgerId: string): Promise<boolean> {
    const db = this.database.getDatabase();
    const result = db
      .prepare(
        `UPDATE ledgers SET is_deleted = 1, status = 'inactive', updated_at = @updatedAt
         WHERE company_id = @companyId AND ledger_id = @ledgerId`,
      )
      .run({ companyId, ledgerId, updatedAt: new Date().toISOString() });
    return Promise.resolve(result.changes > 0);
  }

  delete(companyId: string, ledgerId: string): Promise<boolean> {
    const db = this.database.getDatabase();
    const result = db
      .prepare('DELETE FROM ledgers WHERE company_id = ? AND ledger_id = ?')
      .run(companyId, ledgerId);
    return Promise.resolve(result.changes > 0);
  }

  findById(companyId: string, ledgerId: string): Promise<LedgerDetails | null> {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM ledgers WHERE company_id = ? AND ledger_id = ?')
      .get(companyId, ledgerId) as unknown as LedgerRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  findByGuid(companyId: string, guid: string): Promise<LedgerDetails | null> {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM ledgers WHERE company_id = ? AND guid = ?')
      .get(companyId, guid) as unknown as LedgerRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  findByName(companyId: string, name: string): Promise<LedgerDetails | null> {
    const normalized = name.trim().toLowerCase();
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM ledgers WHERE company_id = ? AND normalized_name = ?')
      .get(companyId, normalized) as unknown as LedgerRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  findByAlias(companyId: string, alias: string): Promise<LedgerDetails | null> {
    const normalized = alias.trim().toLowerCase();
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT * FROM ledgers WHERE company_id = ? AND lower(alias) = ?')
      .get(companyId, normalized) as unknown as LedgerRow | undefined;
    return Promise.resolve(row ? fromRow(row) : null);
  }

  search(companyId: string, params: LedgerSearchParams): Promise<LedgerSearchResult> {
    const db = this.database.getDatabase();
    const filters: string[] = ['company_id = @companyId', 'is_deleted = 0'];
    const values: Record<string, string | number> = { companyId };

    if (params.query?.trim()) {
      filters.push(
        '(lower(name) LIKE @query OR lower(normalized_name) LIKE @query OR lower(alias) LIKE @query OR lower(parent_group) LIKE @query)',
      );
      values.query = `%${params.query.trim().toLowerCase()}%`;
    }
    if (params.status) {
      filters.push('status = @status');
      values.status = params.status;
    }
    if (params.parentGroup) {
      filters.push('lower(parent_group) = @parentGroup');
      values.parentGroup = params.parentGroup.toLowerCase();
    }

    const where = filters.join(' AND ');
    const countRow = db
      .prepare(`SELECT COUNT(*) AS total FROM ledgers WHERE ${where}`)
      .get(values) as { total: number };
    const totalItems = countRow.total;
    const totalPages = Math.max(1, Math.ceil(totalItems / params.pageSize));
    const sortField = ALLOWED_SORT_FIELDS.has(params.sortBy ?? 'name') ? params.sortBy ?? 'name' : 'name';
    const sortColumn = mapSortColumn(sortField);
    const direction = params.sortDirection === 'desc' ? 'DESC' : 'ASC';
    const offset = (params.page - 1) * params.pageSize;

    const rows = db
      .prepare(
        `SELECT * FROM ledgers WHERE ${where}
         ORDER BY ${sortColumn} ${direction}, ledger_id ASC
         LIMIT @limit OFFSET @offset`,
      )
      .all({ ...values, limit: params.pageSize, offset }) as unknown as LedgerRow[];

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

  getStatistics(companyId: string): Promise<LedgerStatistics> {
    const db = this.database.getDatabase();
    const row = db
      .prepare(
        `SELECT
          COUNT(*) AS totalLedgers,
          SUM(CASE WHEN status = 'active' AND is_deleted = 0 THEN 1 ELSE 0 END) AS activeLedgers,
          SUM(CASE WHEN status = 'inactive' THEN 1 ELSE 0 END) AS inactiveLedgers,
          SUM(CASE WHEN status = 'reserved' THEN 1 ELSE 0 END) AS reservedLedgers,
          SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deletedLedgers,
          SUM(CASE WHEN gst_json IS NOT NULL AND gst_json != '' THEN 1 ELSE 0 END) AS withGst,
          SUM(CASE WHEN opening_balance_json IS NOT NULL AND opening_balance_json != '' THEN 1 ELSE 0 END) AS withOpeningBalance,
          MAX(updated_at) AS lastSyncedAt
        FROM ledgers WHERE company_id = ?`,
      )
      .get(companyId) as Record<string, number | string | null>;

    return Promise.resolve({
      totalLedgers: Number(row.totalLedgers ?? 0),
      activeLedgers: Number(row.activeLedgers ?? 0),
      inactiveLedgers: Number(row.inactiveLedgers ?? 0),
      reservedLedgers: Number(row.reservedLedgers ?? 0),
      deletedLedgers: Number(row.deletedLedgers ?? 0),
      withGst: Number(row.withGst ?? 0),
      withOpeningBalance: Number(row.withOpeningBalance ?? 0),
      lastSyncedAt: row.lastSyncedAt ? String(row.lastSyncedAt) : null,
    });
  }

  clearCompany(companyId: string): Promise<void> {
    const db = this.database.getDatabase();
    db.prepare('DELETE FROM ledgers WHERE company_id = ?').run(companyId);
    return Promise.resolve();
  }

  countByCompany(companyId: string): Promise<number> {
    const db = this.database.getDatabase();
    const row = db
      .prepare('SELECT COUNT(*) AS total FROM ledgers WHERE company_id = ?')
      .get(companyId) as { total: number };
    return Promise.resolve(row.total);
  }
}

interface LedgerRow {
  company_id: string;
  ledger_id: string;
  name: string;
  normalized_name: string;
  alias: string | null;
  parent_group: string | null;
  status: string;
  balance_nature: string;
  opening_balance_json: string | null;
  closing_balance_json: string | null;
  guid: string | null;
  alter_id: string | null;
  reserved_name: string | null;
  mailing_json: string | null;
  contact_json: string | null;
  gst_json: string | null;
  metadata_json: string | null;
  content_fingerprint: string;
  is_deleted: number;
  synced_at: string;
  updated_at: string;
}

function toRow(companyId: string, ledger: LedgerDetails, updatedAt: string) {
  return {
    companyId,
    ledgerId: ledger.id,
    name: ledger.name,
    normalizedName: ledger.normalizedName,
    alias: ledger.alias ?? null,
    parentGroup: ledger.parentGroup ?? null,
    status: ledger.status,
    balanceNature: ledger.balanceNature,
    openingBalanceJson: ledger.openingBalance ? JSON.stringify(ledger.openingBalance) : null,
    closingBalanceJson: ledger.closingBalance ? JSON.stringify(ledger.closingBalance) : null,
    guid: ledger.guid ?? null,
    alterId: ledger.alterId ?? null,
    reservedName: ledger.reservedName ?? null,
    mailingJson: ledger.mailing ? JSON.stringify(ledger.mailing) : null,
    contactJson: ledger.contact ? JSON.stringify(ledger.contact) : null,
    gstJson: ledger.gst ? JSON.stringify(ledger.gst) : null,
    metadataJson: ledger.metadata ? JSON.stringify(ledger.metadata) : null,
    fingerprint: computeLedgerFingerprint(ledger),
    isDeleted: ledger.isDeleted ? 1 : 0,
    syncedAt: ledger.syncedAt,
    updatedAt,
  };
}

function fromRow(row: LedgerRow): LedgerDetails {
  return {
    id: row.ledger_id,
    name: row.name,
    normalizedName: row.normalized_name,
    alias: row.alias ?? undefined,
    parentGroup: row.parent_group ?? undefined,
    status: row.status as LedgerDetails['status'],
    balanceNature: row.balance_nature as LedgerDetails['balanceNature'],
    openingBalance: parseJson(row.opening_balance_json),
    closingBalance: parseJson(row.closing_balance_json),
    guid: row.guid ?? undefined,
    alterId: row.alter_id ?? undefined,
    reservedName: row.reserved_name ?? undefined,
    mailing: parseJson(row.mailing_json),
    contact: parseJson(row.contact_json),
    gst: parseJson(row.gst_json),
    metadata: parseJson(row.metadata_json),
    isDeleted: row.is_deleted === 1,
    syncedAt: row.synced_at,
  };
}

function toSummary(row: LedgerRow): LedgerSummary {
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
    case 'closingBalance':
      return 'closing_balance_json';
    case 'syncedAt':
      return 'synced_at';
    default:
      return 'name';
  }
}

export type { DatabaseSync };
