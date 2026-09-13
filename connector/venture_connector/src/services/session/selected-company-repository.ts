import type { SelectedCompany } from '../../erp/session/connector-session.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import type { SqliteDatabase } from '../../storage/sqlite/sqlite-database.js';

const STORAGE_KEY_SELECTED_COMPANY = 'selected_company';
const RECORD_VERSION = 1;

export interface PersistedSelectedCompany {
  readonly company: SelectedCompany;
  readonly selectedAt: string;
}

interface SelectedCompanyRecordV1 {
  readonly version: number;
  readonly id: string;
  readonly name: string;
  readonly selectedAt: string;
}

/**
 * Durable continuity (TD-013) for the Connector's selected company, using the same
 * `storage_meta` key/value table `ConnectorIdentityRepository` already uses for identity
 * continuity — no second ad-hoc storage system. This is continuity state only: it lets a
 * restarted Connector resume with the same selection, it is never proof the company still
 * exists in Tally. `session-validator.ts` remains the sole authority for that.
 *
 * Every method fails safe: a missing table/row, corrupt JSON, or a database that has not
 * started yet all resolve to "no persisted selection" (load) or a silently logged no-op
 * (save/clear) rather than throwing, matching `ConnectorIdentityRepository`'s fallback design.
 */
export class SelectedCompanyRepository {
  constructor(
    private readonly getDatabase: () => SqliteDatabase,
    private readonly logger: Logger,
  ) {}

  load(): PersistedSelectedCompany | null {
    try {
      const db = this.getDatabase().getDatabase();
      const row = db
        .prepare('SELECT value FROM storage_meta WHERE key = ?')
        .get(STORAGE_KEY_SELECTED_COMPANY) as { value: string } | undefined;
      if (!row?.value) {
        return null;
      }

      const parsed = JSON.parse(row.value) as Partial<SelectedCompanyRecordV1>;
      if (
        parsed.version !== RECORD_VERSION ||
        typeof parsed.id !== 'string' ||
        !parsed.id ||
        typeof parsed.name !== 'string' ||
        !parsed.name ||
        typeof parsed.selectedAt !== 'string' ||
        !parsed.selectedAt
      ) {
        this.logger.warn('selected_company_state_corrupt', {
          component: 'selected-company-repository',
          reason: 'schema-mismatch',
        });
        return null;
      }

      return {
        company: { id: parsed.id, name: parsed.name },
        selectedAt: parsed.selectedAt,
      };
    } catch (error) {
      this.logger.warn('selected_company_state_unavailable', {
        component: 'selected-company-repository',
        reason: error instanceof Error ? error.message : 'unknown',
      });
      return null;
    }
  }

  save(company: SelectedCompany, selectedAt: string): void {
    try {
      const db = this.getDatabase().getDatabase();
      const record: SelectedCompanyRecordV1 = {
        version: RECORD_VERSION,
        id: company.id,
        name: company.name,
        selectedAt,
      };
      db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(
        STORAGE_KEY_SELECTED_COMPANY,
        JSON.stringify(record),
      );
    } catch (error) {
      this.logger.warn('selected_company_state_persist_failed', {
        component: 'selected-company-repository',
        reason: error instanceof Error ? error.message : 'unknown',
      });
    }
  }

  clear(): void {
    try {
      const db = this.getDatabase().getDatabase();
      db.prepare('DELETE FROM storage_meta WHERE key = ?').run(STORAGE_KEY_SELECTED_COMPANY);
    } catch (error) {
      this.logger.warn('selected_company_state_clear_failed', {
        component: 'selected-company-repository',
        reason: error instanceof Error ? error.message : 'unknown',
      });
    }
  }
}
