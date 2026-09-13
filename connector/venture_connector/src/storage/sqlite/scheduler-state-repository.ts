import type { SyncResourceKind } from '../../erp/ledger/ledger-domain.js';
import type { SchedulerState } from '../../services/scheduler/adaptive-scheduler-domain.js';
import type { SqliteDatabase } from './sqlite-database.js';

interface SchedulerStateRow {
  readonly company_id: string;
  readonly resource_kind: string;
  readonly stage: string;
  readonly active_window_expires_at: string | null;
  readonly next_check_due_at: string;
  readonly updated_at: string;
}

function toDomain(row: SchedulerStateRow): SchedulerState {
  return {
    companyId: row.company_id,
    resourceKind: row.resource_kind as SyncResourceKind,
    stage: row.stage as SchedulerState['stage'],
    activeWindowExpiresAt: row.active_window_expires_at,
    nextCheckDueAt: row.next_check_due_at,
    updatedAt: row.updated_at,
  };
}

export class SchedulerStateRepository {
  /**
   * Takes a lazy getter, not a `SqliteDatabase` instance directly — mirrors
   * `ConnectorIdentityRepository`'s pattern so constructing this repository (and, transitively,
   * the `Scheduler` and `HealthService` that resolve it during application wiring) never itself
   * requires `LocalDatabase` to have already started. Storage access only happens inside each
   * method call, at the moment it's actually needed.
   */
  constructor(private readonly getDatabase: () => SqliteDatabase) {}

  /**
   * Returns `null` only when no row exists yet — never throws on a corrupt row (defensively
   * treats an unparseable stage as absent, per the architecture doc §15's fail-toward-freshness
   * requirement; the caller falls back to [initialSchedulerState]).
   */
  find(companyId: string, resourceKind: SyncResourceKind): SchedulerState | null {
    const db = this.getDatabase().getDatabase();
    const row = db
      .prepare(
        `SELECT company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at
         FROM scheduler_state WHERE company_id = ? AND resource_kind = ?`,
      )
      .get(companyId, resourceKind) as SchedulerStateRow | undefined;
    if (!row) return null;
    if (!isKnownStage(row.stage)) return null;
    return toDomain(row);
  }

  upsert(state: SchedulerState): void {
    const db = this.getDatabase().getDatabase();
    db.prepare(
      `INSERT INTO scheduler_state (company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(company_id, resource_kind) DO UPDATE SET
         stage = excluded.stage,
         active_window_expires_at = excluded.active_window_expires_at,
         next_check_due_at = excluded.next_check_due_at,
         updated_at = excluded.updated_at`,
    ).run(
      state.companyId,
      state.resourceKind,
      state.stage,
      state.activeWindowExpiresAt,
      state.nextCheckDueAt,
      state.updatedAt,
    );
  }

  /** All rows whose next check is due at or before `nowIso` — used to resume correctly after a restart. */
  listDue(nowIso: string): SchedulerState[] {
    const db = this.getDatabase().getDatabase();
    const rows = db
      .prepare(
        `SELECT company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at
         FROM scheduler_state WHERE next_check_due_at <= ?`,
      )
      .all(nowIso) as unknown as SchedulerStateRow[];
    return rows.filter((row) => isKnownStage(row.stage)).map(toDomain);
  }

  listForCompany(companyId: string): SchedulerState[] {
    const db = this.getDatabase().getDatabase();
    const rows = db
      .prepare(
        `SELECT company_id, resource_kind, stage, active_window_expires_at, next_check_due_at, updated_at
         FROM scheduler_state WHERE company_id = ?`,
      )
      .all(companyId) as unknown as SchedulerStateRow[];
    return rows.filter((row) => isKnownStage(row.stage)).map(toDomain);
  }
}

function isKnownStage(stage: string): stage is SchedulerState['stage'] {
  return stage === 'active_window' || stage === 'backoff_15' || stage === 'backoff_30' || stage === 'backoff_60';
}
