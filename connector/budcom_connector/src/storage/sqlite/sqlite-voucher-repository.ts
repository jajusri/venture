import type {
  VoucherDateRange,
  VoucherDetails,
  VoucherInventoryEntry,
  VoucherLedgerEntry,
  VoucherNestedAllocation,
  VoucherSearchCriteria,
  VoucherSearchResult,
  VoucherStatistics,
  VoucherStructuredValue,
} from '../../erp/voucher/voucher-domain.js';
import type { VoucherLedgerMovement } from '../../erp/voucher/voucher-ledger-movement.js';
import type {
  VoucherAllocationOwner,
  VoucherRepositoryPort,
  VoucherSnapshotMetadata,
  VoucherSnapshotMetrics,
  VoucherSnapshotStatus,
} from '../../services/voucher/voucher-repository.interface.js';
import type { SqliteDatabase } from './sqlite-database.js';

export type VoucherRepositoryErrorCode =
  | 'SNAPSHOT_NOT_FOUND'
  | 'CROSS_COMPANY_WRITE'
  | 'DUPLICATE_IDENTITY'
  | 'ORPHAN_LEDGER_ENTRY'
  | 'ORPHAN_INVENTORY_ENTRY'
  | 'BROKEN_ALLOCATION_TREE'
  | 'INVALID_SNAPSHOT_TRANSITION'
  | 'SNAPSHOT_INCOMPLETE';

export class VoucherRepositoryError extends Error {
  constructor(
    readonly code: VoucherRepositoryErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'VoucherRepositoryError';
  }
}

export class SqliteVoucherRepository implements VoucherRepositoryPort {
  constructor(private readonly database: SqliteDatabase) {}

  createSnapshot(
    companyId: string,
    snapshotId: string,
    period: VoucherDateRange,
  ): Promise<void> {
    return this.beginSnapshot(companyId, snapshotId, period);
  }

  writeVoucherBatch(
    companyId: string,
    snapshotId: string,
    vouchers: readonly VoucherDetails[],
  ): Promise<void> {
    return this.stageMany(companyId, snapshotId, vouchers);
  }

  finalizeSnapshot(
    companyId: string,
    snapshotId: string,
    validatedAt: string,
  ): Promise<void> {
    return this.completeSnapshot(companyId, snapshotId, validatedAt);
  }

  rollbackSnapshot(companyId: string, snapshotId: string): Promise<void> {
    return this.database.runInTransaction(() => {
      const snapshot = this.requireSnapshot(companyId, snapshotId);
      if (!['PENDING', 'WRITING', 'VALIDATED'].includes(snapshot.status)) {
        throw repositoryError(
          'INVALID_SNAPSHOT_TRANSITION',
          `Snapshot in ${snapshot.status} state cannot be rolled back.`,
        );
      }
      this.deleteSnapshotRows(companyId, snapshotId);
    });
  }

  deleteSnapshot(companyId: string, snapshotId: string): Promise<void> {
    return this.database.runInTransaction(() => {
      this.requireSnapshot(companyId, snapshotId);
      if (this.activeSnapshotId(companyId) === snapshotId) {
        throw repositoryError(
          'INVALID_SNAPSHOT_TRANSITION',
          'The promoted snapshot currently visible to readers cannot be deleted.',
        );
      }
      this.deleteSnapshotRows(companyId, snapshotId);
    });
  }

  acquireSyncReservation(
    companyId: string,
    ownerId: string,
    acquiredAt: string,
    expiresAt: string,
  ): Promise<boolean> {
    return this.database.runInTransaction(() => {
      requireText(companyId, 'companyId');
      requireText(ownerId, 'ownerId');
      this.db().prepare(`
        DELETE FROM voucher_sync_reservations
        WHERE company_id = ? AND expires_at <= ?
      `).run(companyId, acquiredAt);
      const result = this.db().prepare(`
        INSERT OR IGNORE INTO voucher_sync_reservations (
          company_id, owner_id, acquired_at, expires_at
        ) VALUES (?, ?, ?, ?)
      `).run(companyId, ownerId, acquiredAt, expiresAt);
      return Number(result.changes) === 1;
    });
  }

  releaseSyncReservation(companyId: string, ownerId: string): Promise<void> {
    return this.database.runInTransaction(() => {
      this.db().prepare(`
        DELETE FROM voucher_sync_reservations
        WHERE company_id = ? AND owner_id = ?
      `).run(companyId, ownerId);
    });
  }

  beginSnapshot(
    companyId: string,
    snapshotId: string,
    period: VoucherDateRange,
  ): Promise<void> {
    return this.database.runInTransaction(() => {
      requireText(companyId, 'companyId');
      requireText(snapshotId, 'snapshotId');
      const now = new Date().toISOString();
      try {
        this.db().prepare(`
          INSERT INTO voucher_snapshots (
            company_id, snapshot_id, date_from, date_to, status, created_at
          ) VALUES (?, ?, ?, ?, 'PENDING', ?)
        `).run(companyId, snapshotId, period.dateFrom, period.dateTo, now);
      } catch (error) {
        throw this.translateWriteError(error, companyId, snapshotId);
      }
    });
  }

  storeVoucher(
    companyId: string,
    snapshotId: string,
    voucher: VoucherDetails,
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.storeVoucherSync(companyId, snapshotId, voucher)
    );
  }

  storeLedgerEntries(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherLedgerEntry[],
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.storeLedgerEntriesSync(companyId, snapshotId, voucherId, entries)
    );
  }

  storeInventoryEntries(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherInventoryEntry[],
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.storeInventoryEntriesSync(companyId, snapshotId, voucherId, entries)
    );
  }

  storeAllocations(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    owner: VoucherAllocationOwner,
    allocations: readonly VoucherNestedAllocation[],
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.storeAllocationsSync(companyId, snapshotId, voucherId, owner, allocations)
    );
  }

  completeSnapshot(
    companyId: string,
    snapshotId: string,
    completedAt: string,
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.completeSnapshotSync(companyId, snapshotId, completedAt)
    );
  }

  promoteSnapshot(
    companyId: string,
    snapshotId: string,
    promotedAt: string,
  ): Promise<void> {
    return this.database.runInTransaction(() =>
      this.promoteSnapshotSync(companyId, snapshotId, promotedAt)
    );
  }

  discardSnapshot(
    companyId: string,
    snapshotId: string,
    reason = 'discarded',
  ): Promise<void> {
    return this.database.runInTransaction(() => {
      const snapshot = this.requireSnapshot(companyId, snapshotId);
      if (!['PENDING', 'WRITING', 'VALIDATED'].includes(snapshot.status)) {
        throw repositoryError(
          'INVALID_SNAPSHOT_TRANSITION',
          `Snapshot in ${snapshot.status} state cannot be discarded.`,
        );
      }
      this.db().prepare(`
        UPDATE voucher_snapshots
        SET status = 'FAILED', failed_at = ?, failure_reason = ?
        WHERE company_id = ? AND snapshot_id = ?
      `).run(new Date().toISOString(), reason, companyId, snapshotId);
    });
  }

  querySnapshot(
    companyId: string,
    snapshotId?: string,
  ): Promise<readonly VoucherDetails[]> {
    return this.database.runInTransaction(() => {
      const resolved = snapshotId ?? this.activeSnapshotId(companyId);
      if (!resolved) return [];
      const snapshot = this.requireSnapshot(companyId, resolved);
      if (!['PROMOTED', 'ARCHIVED'].includes(snapshot.status)) {
        throw repositoryError(
          'INVALID_SNAPSHOT_TRANSITION',
          'Readers may query only promoted Voucher snapshots.',
        );
      }
      const rows = this.db().prepare(`
        SELECT voucher_json FROM voucher_headers
        WHERE company_id = ? AND snapshot_id = ?
        ORDER BY voucher_date, voucher_id
      `).all(companyId, resolved) as Array<{ voucher_json: string }>;
      return rows.map((row) => JSON.parse(row.voucher_json) as VoucherDetails);
    });
  }

  getActiveSnapshotMetadata(companyId: string) {
    return this.database.runInTransaction(() => {
      const row = this.db().prepare(`
        SELECT s.snapshot_id, s.date_from, s.date_to, s.status
        FROM voucher_active_snapshots a
        JOIN voucher_snapshots s
          ON s.company_id = a.company_id AND s.snapshot_id = a.snapshot_id
        WHERE a.company_id = ?
      `).get(companyId) as {
        snapshot_id: string;
        date_from: string;
        date_to: string;
        status: 'PROMOTED';
      } | undefined;
      return row ? {
        snapshotId: row.snapshot_id,
        period: { dateFrom: row.date_from, dateTo: row.date_to },
        status: toPublicSnapshotStatus(row.status),
      } : null;
    });
  }

  getSnapshot(
    companyId: string,
    snapshotId: string,
  ): Promise<VoucherSnapshotMetadata | null> {
    return this.database.runInTransaction(() => {
      const row = this.db().prepare(`
        SELECT snapshot_id, date_from, date_to, status, created_at,
          validated_at, promoted_at, voucher_count
        FROM voucher_snapshots
        WHERE company_id = ? AND snapshot_id = ?
      `).get(companyId, snapshotId) as SnapshotMetadataRow | undefined;
      return row ? mapSnapshotMetadata(row) : null;
    });
  }

  getVoucher(companyId: string, voucherId: string): Promise<VoucherDetails | null> {
    return this.findById(companyId, voucherId);
  }

  searchVouchers(
    companyId: string,
    criteria: VoucherSearchCriteria,
  ): Promise<VoucherSearchResult> {
    return this.search(companyId, criteria);
  }

  listSnapshots(companyId: string): Promise<readonly VoucherSnapshotMetadata[]> {
    return this.database.runInTransaction(() => {
      const rows = this.db().prepare(`
        SELECT snapshot_id, date_from, date_to, status, created_at,
          validated_at, promoted_at, voucher_count
        FROM voucher_snapshots
        WHERE company_id = ?
        ORDER BY created_at DESC, snapshot_id DESC
      `).all(companyId) as unknown as SnapshotMetadataRow[];
      return rows.map(mapSnapshotMetadata);
    });
  }

  getSnapshotMetrics(companyId: string, snapshotId: string) {
    return this.database.runInTransaction(() => {
      this.requireSnapshot(companyId, snapshotId);
      const count = (table: string): number => Number((this.db().prepare(`
        SELECT COUNT(*) AS count FROM ${table}
        WHERE company_id = ? AND snapshot_id = ?
      `).get(companyId, snapshotId) as { count: number }).count);
      return {
        voucherCount: count('voucher_headers'),
        ledgerEntryCount: count('voucher_ledger_entries'),
        inventoryEntryCount: count('voucher_inventory_entries'),
        allocationCount: count('voucher_allocations'),
      };
    });
  }

  failStaleSnapshots(companyId: string, reason: string): Promise<number> {
    return this.database.runInTransaction(() => {
      const result = this.db().prepare(`
        UPDATE voucher_snapshots
        SET status = 'FAILED', failed_at = ?, failure_reason = ?
        WHERE company_id = ? AND status IN ('PENDING', 'WRITING', 'VALIDATED')
      `).run(new Date().toISOString(), reason, companyId);
      return Number(result.changes);
    });
  }

  beginStaging(
    companyId: string,
    syncRunId: string,
    period: VoucherDateRange,
  ): Promise<void> {
    return this.beginSnapshot(companyId, syncRunId, period);
  }

  stageMany(
    companyId: string,
    syncRunId: string,
    vouchers: readonly VoucherDetails[],
  ): Promise<void> {
    return this.database.runInTransaction(() => {
      this.requireWritableSnapshot(companyId, syncRunId);
      this.markSnapshotWriting(companyId, syncRunId);
      for (const voucher of vouchers) {
        this.storeVoucherSync(companyId, syncRunId, voucher);
        this.storeLedgerEntriesSync(companyId, syncRunId, voucher.voucherId, voucher.ledgerEntries);
        this.storeInventoryEntriesSync(
          companyId,
          syncRunId,
          voucher.voucherId,
          voucher.inventoryEntries,
        );
        this.storeAllocationsSync(
          companyId,
          syncRunId,
          voucher.voucherId,
          { type: 'VOUCHER' },
          voucher.allocations,
        );
        for (const entry of voucher.ledgerEntries) {
          this.storeAllocationsSync(
            companyId,
            syncRunId,
            voucher.voucherId,
            { type: 'LEDGER', lineNumber: entry.lineNumber },
            entry.allocations,
          );
        }
        for (const entry of voucher.inventoryEntries) {
          this.storeAllocationsSync(
            companyId,
            syncRunId,
            voucher.voucherId,
            { type: 'INVENTORY', lineNumber: entry.lineNumber },
            entry.allocations,
          );
        }
      }
    });
  }

  discardStaging(
    companyId: string,
    syncRunId: string,
  ): Promise<void> {
    return this.discardSnapshot(companyId, syncRunId);
  }

  carryForwardVouchersOutsideWindow(
    companyId: string,
    fromSnapshotId: string,
    toSnapshotId: string,
    excludeDateFrom: string,
    excludeDateTo: string,
  ): Promise<VoucherSnapshotMetrics> {
    return this.database.runInTransaction(() => {
      this.requireWritableSnapshot(companyId, toSnapshotId);
      this.markSnapshotWriting(companyId, toSnapshotId);
      const params = {
        companyId,
        fromSnapshotId,
        toSnapshotId,
        dateFrom: excludeDateFrom,
        dateTo: excludeDateTo,
      };
      // Eligibility (outside the refreshed window, not already staged fresh this sync) is
      // computed ONCE into a temp table and reused by all four inserts below. Re-running the
      // "not already in toSnapshotId" check live against each statement would be wrong: the
      // header insert below mutates toSnapshotId's voucher_headers, which would make every
      // just-carried-forward id look "already present" to the next statement's subquery and
      // silently exclude its own child rows from being copied at all.
      this.db().prepare('DROP TABLE IF EXISTS temp.voucher_carry_forward_ids').run();
      this.db().prepare(`
        CREATE TEMP TABLE voucher_carry_forward_ids AS
        SELECT voucher_id FROM voucher_headers
        WHERE company_id = @companyId AND snapshot_id = @fromSnapshotId
          AND (voucher_date < @dateFrom OR voucher_date > @dateTo)
          AND voucher_id NOT IN (
            SELECT voucher_id FROM voucher_headers
            WHERE company_id = @companyId AND snapshot_id = @toSnapshotId
          )
      `).run(params);
      const eligibleVoucherIds = `SELECT voucher_id FROM voucher_carry_forward_ids`;
      // better-sqlite3 rejects any named parameter bound to a statement that doesn't reference
      // it, so the four copy statements below get their own minimal param set (no dateFrom/
      // dateTo — that filtering already happened when the temp table was built above).
      const copyParams = { companyId, fromSnapshotId, toSnapshotId, toCompanyId: companyId };
      const headerResult = this.db().prepare(`
        INSERT INTO voucher_headers (
          company_id, snapshot_id, voucher_id, identity_version, guid, master_id,
          alter_id, voucher_key, voucher_retain_key, voucher_date, effective_date,
          voucher_type, voucher_number, reference_number, narration, narration_preview,
          party_name, amount, amount_side, amount_comparable, voucher_status, data_quality,
          ledger_entry_count, inventory_entry_count, allocation_count, voucher_json
        )
        SELECT
          @toCompanyId, @toSnapshotId, voucher_id, identity_version, guid, master_id,
          alter_id, voucher_key, voucher_retain_key, voucher_date, effective_date,
          voucher_type, voucher_number, reference_number, narration, narration_preview,
          party_name, amount, amount_side, amount_comparable, voucher_status, data_quality,
          ledger_entry_count, inventory_entry_count, allocation_count, voucher_json
        FROM voucher_headers
        WHERE company_id = @companyId AND snapshot_id = @fromSnapshotId
          AND voucher_id IN (${eligibleVoucherIds})
      `).run(copyParams);
      // Every count below reflects actual rows inserted by THIS operation (RunResult.changes),
      // not a re-derived query — a voucher excluded from the header insert above because a
      // fresh-extraction row already claimed its voucherId (date moved into the refreshed
      // window) must not be double-counted as carried forward, since it was never inserted here.
      const ledgerResult = this.db().prepare(`
        INSERT INTO voucher_ledger_entries (
          company_id, snapshot_id, voucher_id, line_number, ledger_name, amount,
          amount_side, is_deemed_positive, reference_type, reference_name, allocation_count
        )
        SELECT
          @toCompanyId, @toSnapshotId, voucher_id, line_number, ledger_name, amount,
          amount_side, is_deemed_positive, reference_type, reference_name, allocation_count
        FROM voucher_ledger_entries
        WHERE company_id = @companyId AND snapshot_id = @fromSnapshotId
          AND voucher_id IN (${eligibleVoucherIds})
      `).run(copyParams);
      const inventoryResult = this.db().prepare(`
        INSERT INTO voucher_inventory_entries (
          company_id, snapshot_id, voucher_id, line_number, item_name, quantity,
          actual_quantity, billed_quantity, unit, rate, amount, amount_side, allocation_count
        )
        SELECT
          @toCompanyId, @toSnapshotId, voucher_id, line_number, item_name, quantity,
          actual_quantity, billed_quantity, unit, rate, amount, amount_side, allocation_count
        FROM voucher_inventory_entries
        WHERE company_id = @companyId AND snapshot_id = @fromSnapshotId
          AND voucher_id IN (${eligibleVoucherIds})
      `).run(copyParams);
      const allocationResult = this.db().prepare(`
        INSERT INTO voucher_allocations (
          company_id, snapshot_id, voucher_id, owner_type, owner_line_number,
          allocation_index, allocation_type, source_name, values_json
        )
        SELECT
          @toCompanyId, @toSnapshotId, voucher_id, owner_type, owner_line_number,
          allocation_index, allocation_type, source_name, values_json
        FROM voucher_allocations
        WHERE company_id = @companyId AND snapshot_id = @fromSnapshotId
          AND voucher_id IN (${eligibleVoucherIds})
      `).run(copyParams);
      this.db().prepare('DROP TABLE IF EXISTS temp.voucher_carry_forward_ids').run();
      return {
        voucherCount: Number(headerResult.changes),
        ledgerEntryCount: Number(ledgerResult.changes),
        inventoryEntryCount: Number(inventoryResult.changes),
        allocationCount: Number(allocationResult.changes),
      };
    });
  }

  promoteCompleteSnapshot(
    companyId: string,
    syncRunId: string,
    completedAt: string,
  ): Promise<void> {
    return this.database.runInTransaction(() => {
      this.completeSnapshotSync(companyId, syncRunId, completedAt);
      this.promoteSnapshotSync(companyId, syncRunId, completedAt);
    });
  }

  findById(companyId: string, voucherId: string): Promise<VoucherDetails | null> {
    return this.database.runInTransaction(() => {
      const snapshotId = this.activeSnapshotId(companyId);
      if (!snapshotId) return null;
      const row = this.db().prepare(`
        SELECT voucher_json FROM voucher_headers
        WHERE company_id = ? AND snapshot_id = ? AND voucher_id = ?
      `).get(companyId, snapshotId, voucherId) as { voucher_json: string } | undefined;
      return row ? JSON.parse(row.voucher_json) as VoucherDetails : null;
    });
  }

  search(
    companyId: string,
    criteria: VoucherSearchCriteria,
  ): Promise<VoucherSearchResult> {
    return this.database.runInTransaction(() => {
      const snapshotId = this.activeSnapshotId(companyId);
      if (!snapshotId) return emptySearch(criteria);
      const filters = ['company_id = @companyId', 'snapshot_id = @snapshotId'];
      const values: Record<string, string | number> = {
        companyId,
        snapshotId,
        dateFrom: criteria.dateFrom,
        dateTo: criteria.dateTo,
        limit: criteria.pageSize,
        offset: (criteria.page - 1) * criteria.pageSize,
      };
      filters.push('voucher_date BETWEEN @dateFrom AND @dateTo');
      if (criteria.voucherType) {
        filters.push('voucher_type = @voucherType');
        values.voucherType = criteria.voucherType;
      }
      if (criteria.status) {
        filters.push('voucher_status = @status');
        values.status = criteria.status;
      }
      // TD-023: exact-match filters, distinct from the fuzzy `query` field below — added so the
      // list endpoint (which needs these two) can move off the unbounded querySnapshot() path
      // onto this already-paginated query without losing any filtering behavior.
      if (criteria.voucherNumber) {
        filters.push('voucher_number = @voucherNumber');
        values.voucherNumber = criteria.voucherNumber;
      }
      if (criteria.partyName) {
        filters.push('party_name = @partyName');
        values.partyName = criteria.partyName;
      }
      if (criteria.query?.trim()) {
        filters.push(`(
          lower(voucher_number) LIKE @query OR
          lower(reference_number) LIKE @query OR
          lower(party_name) LIKE @query OR
          lower(voucher_type) LIKE @query
        )`);
        values.query = `%${criteria.query.trim().toLowerCase()}%`;
      }
      const where = filters.join(' AND ');
      const countValues = { ...values };
      delete countValues.limit;
      delete countValues.offset;
      const total = Number((this.db().prepare(
        `SELECT COUNT(*) AS total FROM voucher_headers WHERE ${where}`,
      ).get(countValues) as { total: number }).total);
      const sortColumn = criteria.sortBy === 'voucherNumber'
        ? 'voucher_number'
        : criteria.sortBy === 'amount' ? 'CAST(amount AS REAL)' : 'voucher_date';
      const direction = criteria.sortDirection === 'desc' ? 'DESC' : 'ASC';
      const rows = this.db().prepare(`
        SELECT voucher_json FROM voucher_headers WHERE ${where}
        ORDER BY ${sortColumn} ${direction}, voucher_id ASC
        LIMIT @limit OFFSET @offset
      `).all(values) as Array<{ voucher_json: string }>;
      return {
        items: rows.map((row) => JSON.parse(row.voucher_json) as VoucherDetails),
        pagination: {
          page: criteria.page,
          pageSize: criteria.pageSize,
          totalItems: total,
          totalPages: Math.max(1, Math.ceil(total / criteria.pageSize)),
        },
      };
    });
  }

  getStatistics(
    companyId: string,
    period: VoucherDateRange,
  ): Promise<VoucherStatistics> {
    return this.database.runInTransaction(() => {
      const snapshotId = this.activeSnapshotId(companyId);
      if (!snapshotId) {
        return {
          ...period,
          totalVouchers: 0,
          countsByType: [],
          lastSynchronizedAt: null,
          incompleteVouchers: 0,
          cancelledVouchers: 0,
        };
      }
      const rows = this.db().prepare(`
        SELECT voucher_type, COUNT(*) AS count
        FROM voucher_headers
        WHERE company_id = ? AND snapshot_id = ?
          AND voucher_date BETWEEN ? AND ?
        GROUP BY voucher_type ORDER BY voucher_type
      `).all(companyId, snapshotId, period.dateFrom, period.dateTo) as Array<{
        voucher_type: string;
        count: number;
      }>;
      const totals = this.db().prepare(`
        SELECT COUNT(*) AS total,
          SUM(CASE WHEN data_quality = 'incomplete' THEN 1 ELSE 0 END) AS incomplete,
          SUM(CASE WHEN voucher_status = 'cancelled' THEN 1 ELSE 0 END) AS cancelled
        FROM voucher_headers
        WHERE company_id = ? AND snapshot_id = ?
          AND voucher_date BETWEEN ? AND ?
      `).get(companyId, snapshotId, period.dateFrom, period.dateTo) as {
        total: number;
        incomplete: number | null;
        cancelled: number | null;
      };
      const active = this.db().prepare(`
        SELECT promoted_at FROM voucher_active_snapshots WHERE company_id = ?
      `).get(companyId) as { promoted_at: string } | undefined;
      return {
        ...period,
        totalVouchers: totals.total,
        countsByType: rows.map((row) => ({
          voucherType: row.voucher_type,
          count: row.count,
        })),
        lastSynchronizedAt: active?.promoted_at ?? null,
        incompleteVouchers: totals.incomplete ?? 0,
        cancelledVouchers: totals.cancelled ?? 0,
      };
    });
  }

  findLedgerMovements(
    companyId: string,
    ledgerName: string,
    dateFrom: string,
    dateTo: string,
  ): Promise<readonly VoucherLedgerMovement[]> {
    return this.database.runInTransaction(() => {
      const snapshotId = this.activeSnapshotId(companyId);
      if (!snapshotId) return [];
      const exact = this.queryLedgerMovements(companyId, snapshotId, dateFrom, dateTo, {
        exact: ledgerName,
      });
      if (exact.length > 0) return exact;
      return this.queryLedgerMovements(companyId, snapshotId, dateFrom, dateTo, {
        normalized: ledgerName,
      });
    });
  }

  private queryLedgerMovements(
    companyId: string,
    snapshotId: string,
    dateFrom: string,
    dateTo: string,
    match: { exact: string } | { normalized: string },
  ): readonly VoucherLedgerMovement[] {
    const nameFilter = 'exact' in match
      ? 'e.ledger_name = @ledgerName'
      : 'lower(trim(e.ledger_name)) = lower(trim(@ledgerName))';
    const ledgerName = 'exact' in match ? match.exact : match.normalized;
    const rows = this.db().prepare(`
      SELECT
        h.voucher_id AS voucherId,
        h.voucher_date AS date,
        h.voucher_type AS voucherType,
        h.voucher_number AS voucherNumber,
        h.reference_number AS referenceNumber,
        h.narration AS narration,
        e.line_number AS lineNumber,
        e.amount AS amount,
        e.amount_side AS amountSide
      FROM voucher_ledger_entries e
      JOIN voucher_headers h
        ON h.company_id = e.company_id AND h.snapshot_id = e.snapshot_id AND h.voucher_id = e.voucher_id
      WHERE e.company_id = @companyId AND e.snapshot_id = @snapshotId
        AND ${nameFilter}
        AND h.voucher_date BETWEEN @dateFrom AND @dateTo
        AND h.voucher_status = 'active'
      ORDER BY h.voucher_date, h.rowid, e.line_number
    `).all({ companyId, snapshotId, ledgerName, dateFrom, dateTo }) as Array<{
      voucherId: string;
      date: string;
      voucherType: string;
      voucherNumber: string | null;
      referenceNumber: string | null;
      narration: string | null;
      lineNumber: number;
      amount: string;
      amountSide: 'debit' | 'credit' | null;
    }>;
    return rows.map((row) => ({ ...row }));
  }

  private storeVoucherSync(
    companyId: string,
    snapshotId: string,
    voucher: VoucherDetails,
  ): void {
    this.requireWritableSnapshot(companyId, snapshotId);
    this.markSnapshotWriting(companyId, snapshotId);
    validateAllocationTree(voucher.allocations);
    for (const entry of voucher.ledgerEntries) validateAllocationTree(entry.allocations);
    for (const entry of voucher.inventoryEntries) validateAllocationTree(entry.allocations);
    try {
      this.db().prepare(`
        INSERT INTO voucher_headers (
          company_id, snapshot_id, voucher_id, identity_version, guid, master_id,
          alter_id, voucher_key, voucher_retain_key, voucher_date, effective_date,
          voucher_type, voucher_number, reference_number, narration, narration_preview,
          party_name, amount, amount_side, amount_comparable, voucher_status, data_quality,
          ledger_entry_count, inventory_entry_count, allocation_count, voucher_json
        ) VALUES (
          @companyId, @snapshotId, @voucherId, @identityVersion, @guid, @masterId,
          @alterId, @voucherKey, @voucherRetainKey, @date, @effectiveDate,
          @voucherType, @voucherNumber, @referenceNumber, @narration, @narrationPreview,
          @partyName, @amount, @amountSide, @amountComparable, @status, @dataQuality,
          @ledgerCount, @inventoryCount, @allocationCount, @voucherJson
        )
      `).run({
        companyId,
        snapshotId,
        voucherId: voucher.voucherId,
        identityVersion: voucher.identityVersion,
        guid: voucher.guid,
        masterId: voucher.masterId,
        alterId: voucher.alterId,
        voucherKey: voucher.voucherKey,
        voucherRetainKey: voucher.voucherRetainKey,
        date: voucher.date,
        effectiveDate: voucher.effectiveDate ?? null,
        voucherType: voucher.voucherType,
        voucherNumber: voucher.voucherNumber,
        referenceNumber: voucher.referenceNumber ?? null,
        narration: voucher.narration ?? null,
        narrationPreview: voucher.narrationPreview ?? null,
        partyName: voucher.partyName,
        amount: voucher.amount?.amount ?? null,
        amountSide: voucher.amount?.side ?? null,
        amountComparable: voucher.amountComparable ? 1 : 0,
        status: voucher.status,
        dataQuality: voucher.dataQuality,
        ledgerCount: voucher.ledgerEntries.length,
        inventoryCount: voucher.inventoryEntries.length,
        allocationCount: voucher.allocations.length,
        voucherJson: JSON.stringify(voucher),
      });
    } catch (error) {
      throw this.translateWriteError(error, companyId, snapshotId);
    }
  }

  private storeLedgerEntriesSync(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherLedgerEntry[],
  ): void {
    this.requireWritableSnapshot(companyId, snapshotId);
    if (!this.voucherExists(companyId, snapshotId, voucherId)) {
      throw repositoryError('ORPHAN_LEDGER_ENTRY', 'Parent Voucher does not exist.');
    }
    const stmt = this.db().prepare(`
      INSERT INTO voucher_ledger_entries (
        company_id, snapshot_id, voucher_id, line_number, ledger_name, amount,
        amount_side, is_deemed_positive, reference_type, reference_name, allocation_count
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    try {
      for (const entry of entries) {
        stmt.run(
          companyId,
          snapshotId,
          voucherId,
          entry.lineNumber,
          entry.ledgerName,
          entry.amount.amount,
          entry.amount.side,
          entry.isDeemedPositive === null ? null : entry.isDeemedPositive ? 1 : 0,
          entry.referenceType ?? null,
          entry.referenceName ?? null,
          entry.allocations.length,
        );
      }
    } catch (error) {
      throw repositoryError(
        'ORPHAN_LEDGER_ENTRY',
        `Ledger entries could not be stored: ${safeSqlMessage(error)}`,
      );
    }
  }

  private storeInventoryEntriesSync(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    entries: readonly VoucherInventoryEntry[],
  ): void {
    this.requireWritableSnapshot(companyId, snapshotId);
    if (!this.voucherExists(companyId, snapshotId, voucherId)) {
      throw repositoryError('ORPHAN_INVENTORY_ENTRY', 'Parent Voucher does not exist.');
    }
    const stmt = this.db().prepare(`
      INSERT INTO voucher_inventory_entries (
        company_id, snapshot_id, voucher_id, line_number, item_name, quantity,
        actual_quantity, billed_quantity, unit, rate, amount, amount_side, allocation_count
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    try {
      for (const entry of entries) {
        stmt.run(
          companyId,
          snapshotId,
          voucherId,
          entry.lineNumber,
          entry.itemName,
          entry.quantity ?? null,
          entry.actualQuantity ?? null,
          entry.billedQuantity ?? null,
          entry.unit ?? null,
          entry.rate ?? null,
          entry.amount?.amount ?? null,
          entry.amount?.side ?? null,
          entry.allocations.length,
        );
      }
    } catch (error) {
      throw repositoryError(
        'ORPHAN_INVENTORY_ENTRY',
        `Inventory entries could not be stored: ${safeSqlMessage(error)}`,
      );
    }
  }

  private storeAllocationsSync(
    companyId: string,
    snapshotId: string,
    voucherId: string,
    owner: VoucherAllocationOwner,
    allocations: readonly VoucherNestedAllocation[],
  ): void {
    this.requireWritableVoucher(companyId, snapshotId, voucherId);
    validateAllocationTree(allocations);
    const line = owner.type === 'VOUCHER' ? 0 : owner.lineNumber ?? 0;
    if (owner.type === 'LEDGER' && !this.entryExists(
      'voucher_ledger_entries',
      companyId,
      snapshotId,
      voucherId,
      line,
    )) {
      throw repositoryError('ORPHAN_LEDGER_ENTRY', 'Allocation owner ledger entry does not exist.');
    }
    if (owner.type === 'INVENTORY' && !this.entryExists(
      'voucher_inventory_entries',
      companyId,
      snapshotId,
      voucherId,
      line,
    )) {
      throw repositoryError(
        'ORPHAN_INVENTORY_ENTRY',
        'Allocation owner inventory entry does not exist.',
      );
    }
    const stmt = this.db().prepare(`
      INSERT INTO voucher_allocations (
        company_id, snapshot_id, voucher_id, owner_type, owner_line_number,
        allocation_index, allocation_type, source_name, values_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    try {
      allocations.forEach((allocation, index) => {
        stmt.run(
          companyId,
          snapshotId,
          voucherId,
          owner.type,
          line,
          index,
          allocation.type,
          allocation.sourceName,
          JSON.stringify(allocation.values),
        );
      });
    } catch (error) {
      throw repositoryError(
        'BROKEN_ALLOCATION_TREE',
        `Allocations could not be stored: ${safeSqlMessage(error)}`,
      );
    }
  }

  private completeSnapshotSync(
    companyId: string,
    snapshotId: string,
    completedAt: string,
  ): void {
    const snapshot = this.requireSnapshot(companyId, snapshotId);
    if (!['PENDING', 'WRITING'].includes(snapshot.status) || snapshot.validated_at) {
      throw repositoryError(
        'INVALID_SNAPSHOT_TRANSITION',
        'Only a PENDING or WRITING snapshot can be finalized.',
      );
    }
    const mismatched = this.db().prepare(`
      SELECT v.voucher_id
      FROM voucher_headers v
      WHERE v.company_id = ? AND v.snapshot_id = ?
        AND (
          v.ledger_entry_count != (
            SELECT COUNT(*) FROM voucher_ledger_entries l
            WHERE l.company_id = v.company_id AND l.snapshot_id = v.snapshot_id
              AND l.voucher_id = v.voucher_id
          )
          OR v.inventory_entry_count != (
            SELECT COUNT(*) FROM voucher_inventory_entries i
            WHERE i.company_id = v.company_id AND i.snapshot_id = v.snapshot_id
              AND i.voucher_id = v.voucher_id
          )
          OR (
            v.allocation_count
            + COALESCE((SELECT SUM(l.allocation_count) FROM voucher_ledger_entries l
              WHERE l.company_id = v.company_id AND l.snapshot_id = v.snapshot_id
                AND l.voucher_id = v.voucher_id), 0)
            + COALESCE((SELECT SUM(i.allocation_count) FROM voucher_inventory_entries i
              WHERE i.company_id = v.company_id AND i.snapshot_id = v.snapshot_id
                AND i.voucher_id = v.voucher_id), 0)
          ) != (
            SELECT COUNT(*) FROM voucher_allocations a
            WHERE a.company_id = v.company_id AND a.snapshot_id = v.snapshot_id
              AND a.voucher_id = v.voucher_id
          )
        )
      LIMIT 1
    `).get(companyId, snapshotId) as { voucher_id: string } | undefined;
    if (mismatched) {
      throw repositoryError(
        'SNAPSHOT_INCOMPLETE',
        `Snapshot has incomplete child records for Voucher ${mismatched.voucher_id}.`,
      );
    }
    const total = (this.db().prepare(`
      SELECT COUNT(*) AS total FROM voucher_headers WHERE company_id = ? AND snapshot_id = ?
    `).get(companyId, snapshotId) as { total: number }).total;
    this.db().prepare(`
      UPDATE voucher_snapshots
      SET status = 'VALIDATED', validated_at = ?, voucher_count = ?
      WHERE company_id = ? AND snapshot_id = ?
    `).run(completedAt, total, companyId, snapshotId);
  }

  private promoteSnapshotSync(
    companyId: string,
    snapshotId: string,
    promotedAt: string,
  ): void {
    const snapshot = this.requireSnapshot(companyId, snapshotId);
    if (snapshot.status !== 'VALIDATED' || !snapshot.validated_at) {
      throw repositoryError(
        'INVALID_SNAPSHOT_TRANSITION',
        'Only a VALIDATED snapshot can be promoted.',
      );
    }
    this.db().prepare(`
      UPDATE voucher_snapshots SET status = 'ARCHIVED'
      WHERE company_id = ? AND status = 'PROMOTED'
    `).run(companyId);
    this.db().prepare(`
      UPDATE voucher_snapshots SET status = 'PROMOTED', promoted_at = ?
      WHERE company_id = ? AND snapshot_id = ?
    `).run(promotedAt, companyId, snapshotId);
    this.db().prepare(`
      INSERT INTO voucher_active_snapshots (company_id, snapshot_id, promoted_at)
      VALUES (?, ?, ?)
      ON CONFLICT(company_id) DO UPDATE SET
        snapshot_id = excluded.snapshot_id,
        promoted_at = excluded.promoted_at
    `).run(companyId, snapshotId, promotedAt);
  }

  private requireWritableSnapshot(companyId: string, snapshotId: string): SnapshotRow {
    const snapshot = this.requireSnapshot(companyId, snapshotId);
    if (!['PENDING', 'WRITING'].includes(snapshot.status) || snapshot.validated_at) {
      throw repositoryError(
        'INVALID_SNAPSHOT_TRANSITION',
        'Snapshot is not writable.',
      );
    }
    return snapshot;
  }

  private markSnapshotWriting(companyId: string, snapshotId: string): void {
    this.db().prepare(`
      UPDATE voucher_snapshots
      SET status = 'WRITING'
      WHERE company_id = ? AND snapshot_id = ? AND status = 'PENDING'
    `).run(companyId, snapshotId);
  }

  private deleteSnapshotRows(companyId: string, snapshotId: string): void {
    this.db().prepare(`
      DELETE FROM voucher_allocations WHERE company_id = ? AND snapshot_id = ?
    `).run(companyId, snapshotId);
    this.db().prepare(`
      DELETE FROM voucher_ledger_entries WHERE company_id = ? AND snapshot_id = ?
    `).run(companyId, snapshotId);
    this.db().prepare(`
      DELETE FROM voucher_inventory_entries WHERE company_id = ? AND snapshot_id = ?
    `).run(companyId, snapshotId);
    this.db().prepare(`
      DELETE FROM voucher_headers WHERE company_id = ? AND snapshot_id = ?
    `).run(companyId, snapshotId);
    this.db().prepare(`
      DELETE FROM voucher_snapshots WHERE company_id = ? AND snapshot_id = ?
    `).run(companyId, snapshotId);
  }

  private requireWritableVoucher(
    companyId: string,
    snapshotId: string,
    voucherId: string,
  ): void {
    this.requireWritableSnapshot(companyId, snapshotId);
    if (!this.voucherExists(companyId, snapshotId, voucherId)) {
      throw repositoryError('SNAPSHOT_NOT_FOUND', 'Parent Voucher does not exist.');
    }
  }

  private voucherExists(
    companyId: string,
    snapshotId: string,
    voucherId: string,
  ): boolean {
    return Boolean(this.db().prepare(`
      SELECT 1 AS present FROM voucher_headers
      WHERE company_id = ? AND snapshot_id = ? AND voucher_id = ?
    `).get(companyId, snapshotId, voucherId));
  }

  private requireSnapshot(companyId: string, snapshotId: string): SnapshotRow {
    const row = this.db().prepare(`
      SELECT * FROM voucher_snapshots WHERE company_id = ? AND snapshot_id = ?
    `).get(companyId, snapshotId) as SnapshotRow | undefined;
    if (row) return row;
    const other = this.db().prepare(`
      SELECT company_id FROM voucher_snapshots WHERE snapshot_id = ?
    `).get(snapshotId) as { company_id: string } | undefined;
    if (other && other.company_id !== companyId) {
      throw repositoryError(
        'CROSS_COMPANY_WRITE',
        'Snapshot belongs to a different company.',
      );
    }
    throw repositoryError('SNAPSHOT_NOT_FOUND', 'Voucher snapshot does not exist.');
  }

  private activeSnapshotId(companyId: string): string | null {
    const row = this.db().prepare(`
      SELECT snapshot_id FROM voucher_active_snapshots WHERE company_id = ?
    `).get(companyId) as { snapshot_id: string } | undefined;
    return row?.snapshot_id ?? null;
  }

  private entryExists(
    table: 'voucher_ledger_entries' | 'voucher_inventory_entries',
    companyId: string,
    snapshotId: string,
    voucherId: string,
    line: number,
  ): boolean {
    return Boolean(this.db().prepare(`
      SELECT 1 AS present FROM ${table}
      WHERE company_id = ? AND snapshot_id = ? AND voucher_id = ? AND line_number = ?
    `).get(companyId, snapshotId, voucherId, line));
  }

  private translateWriteError(
    error: unknown,
    companyId: string,
    snapshotId: string,
  ): VoucherRepositoryError {
    const message = safeSqlMessage(error);
    if (message.includes('UNIQUE constraint failed')) {
      const existing = this.db().prepare(`
        SELECT company_id FROM voucher_snapshots WHERE snapshot_id = ?
      `).get(snapshotId) as { company_id: string } | undefined;
      if (existing && existing.company_id !== companyId) {
        return repositoryError(
          'CROSS_COMPANY_WRITE',
          'Snapshot identifier belongs to a different company.',
        );
      }
      return repositoryError(
        'DUPLICATE_IDENTITY',
        'Duplicate Voucher snapshot or identity.',
      );
    }
    if (message.includes('voucher snapshot is not writable')) {
      return repositoryError('INVALID_SNAPSHOT_TRANSITION', 'Snapshot is not writable.');
    }
    return repositoryError('SNAPSHOT_INCOMPLETE', `Voucher write failed: ${message}`);
  }

  private db() {
    return this.database.getDatabase();
  }
}

interface SnapshotRow {
  readonly company_id: string;
  readonly snapshot_id: string;
  readonly status:
    | 'PENDING'
    | 'WRITING'
    | 'VALIDATED'
    | 'PROMOTED'
    | 'ARCHIVED'
    | 'FAILED';
  readonly validated_at: string | null;
}

interface SnapshotMetadataRow extends SnapshotRow {
  readonly date_from: string;
  readonly date_to: string;
  readonly created_at: string;
  readonly promoted_at: string | null;
  readonly voucher_count: number;
}

function mapSnapshotMetadata(row: SnapshotMetadataRow): VoucherSnapshotMetadata {
  return {
    snapshotId: row.snapshot_id,
    period: { dateFrom: row.date_from, dateTo: row.date_to },
    status: toPublicSnapshotStatus(row.status),
    createdAt: row.created_at,
    validatedAt: row.validated_at,
    promotedAt: row.promoted_at,
    voucherCount: row.voucher_count,
  };
}

function toPublicSnapshotStatus(status: SnapshotRow['status']): VoucherSnapshotStatus {
  const statuses: Record<SnapshotRow['status'], VoucherSnapshotStatus> = {
    PENDING: 'Pending',
    WRITING: 'Writing',
    VALIDATED: 'Validated',
    PROMOTED: 'Promoted',
    ARCHIVED: 'Archived',
    FAILED: 'Failed',
  };
  return statuses[status];
}

function validateAllocationTree(allocations: readonly VoucherNestedAllocation[]): void {
  const seen = new WeakSet<object>();
  for (const allocation of allocations) {
    if (!allocation.sourceName.trim()) {
      throw repositoryError('BROKEN_ALLOCATION_TREE', 'Allocation source name is required.');
    }
    for (const value of allocation.values) validateStructuredValue(value, seen, 1);
  }
}

function validateStructuredValue(
  value: VoucherStructuredValue,
  seen: WeakSet<object>,
  depth: number,
): void {
  if (depth > 64 || seen.has(value as object) || !value.name.trim()) {
    throw repositoryError('BROKEN_ALLOCATION_TREE', 'Allocation tree is invalid or cyclic.');
  }
  seen.add(value as object);
  if (
    !value.attributes ||
    typeof value.attributes !== 'object' ||
    Array.isArray(value.attributes) ||
    !Array.isArray(value.children)
  ) {
    throw repositoryError('BROKEN_ALLOCATION_TREE', 'Allocation tree shape is invalid.');
  }
  for (const child of value.children) validateStructuredValue(child, seen, depth + 1);
  seen.delete(value as object);
}

function emptySearch(criteria: VoucherSearchCriteria): VoucherSearchResult {
  return {
    items: [],
    pagination: {
      page: criteria.page,
      pageSize: criteria.pageSize,
      totalItems: 0,
      totalPages: 1,
    },
  };
}

function requireText(value: string, field: string): void {
  if (!value.trim()) {
    throw repositoryError('SNAPSHOT_INCOMPLETE', `${field} is required.`);
  }
}

function safeSqlMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function repositoryError(
  code: VoucherRepositoryErrorCode,
  message: string,
): VoucherRepositoryError {
  return new VoucherRepositoryError(code, message);
}

export { SqliteVoucherRepository as VoucherRepository };
