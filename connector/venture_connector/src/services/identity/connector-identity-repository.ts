import crypto from 'node:crypto';
import os from 'node:os';

import type { SqliteDatabase } from '../../storage/sqlite/sqlite-database.js';

export interface ConnectorIdentity {
  readonly connectorId: string;
  readonly connectorName: string;
  /**
   * 'desktop-supplied': the Desktop shell generated and persisted the ID in its own private
   * configuration (connector-identity.json) and passed it via VENTURE_CONNECTOR_ID. This is the
   * normal production path.
   * 'connector-generated': no Desktop supervisor is present (standalone/dev run); the Connector
   * generated its own ID once and persisted it in storage_meta so it survives restarts.
   */
  readonly source: 'desktop-supplied' | 'connector-generated';
}

const STORAGE_KEY_CONNECTOR_ID = 'connector_id';

/**
 * Resolves and persists the stable Connector identity described in the discovery/reconnection
 * architecture. Never derived from IP, MAC, Windows username, or machine name alone — generated
 * once with crypto.randomUUID() and cached for the lifetime of the process.
 */
export class ConnectorIdentityRepository {
  private cached: ConnectorIdentity | null = null;

  constructor(
    private readonly getDatabase: () => SqliteDatabase,
    private readonly env: NodeJS.ProcessEnv = process.env,
  ) {}

  getOrCreateIdentity(): ConnectorIdentity {
    if (this.cached) {
      return this.cached;
    }

    const connectorName = this.env.VENTURE_CONNECTOR_NAME?.trim() || safeHostname();
    const suppliedId = this.env.VENTURE_CONNECTOR_ID?.trim();
    if (suppliedId) {
      this.cached = { connectorId: suppliedId, connectorName, source: 'desktop-supplied' };
      return this.cached;
    }

    try {
      const db = this.getDatabase().getDatabase();
      const existing = db
        .prepare('SELECT value FROM storage_meta WHERE key = ?')
        .get(STORAGE_KEY_CONNECTOR_ID) as { value: string } | undefined;
      if (existing?.value) {
        this.cached = { connectorId: existing.value, connectorName, source: 'connector-generated' };
        return this.cached;
      }

      const generated = crypto.randomUUID();
      db.prepare('INSERT OR REPLACE INTO storage_meta (key, value) VALUES (?, ?)').run(
        STORAGE_KEY_CONNECTOR_ID,
        generated,
      );
      this.cached = { connectorId: generated, connectorName, source: 'connector-generated' };
      return this.cached;
    } catch {
      // Local storage isn't available yet (e.g. health probed before the database service has
      // started). Return an ephemeral, uncached ID rather than failing health reporting — the
      // storage/database-health checks elsewhere in the report already surface this condition.
      return { connectorId: crypto.randomUUID(), connectorName, source: 'connector-generated' };
    }
  }
}

function safeHostname(): string {
  try {
    return os.hostname() || 'VENTURE Connector';
  } catch {
    return 'VENTURE Connector';
  }
}
