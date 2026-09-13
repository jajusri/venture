import crypto from 'node:crypto';
import type { SqliteDatabase } from '../../storage/sqlite/sqlite-database.js';

export interface TrustedDeviceRecord {
  readonly deviceRecordId: string;
  readonly tokenHash: string;
  readonly companyId: string;
  readonly companyName: string;
  readonly installationId: string;
  readonly friendlyName: string | null;
  readonly autoConnectEnabled: boolean;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
}

interface TrustedDeviceRow {
  device_record_id: string;
  token_hash: string;
  company_id: string;
  company_name: string;
  installation_id: string;
  friendly_name: string | null;
  auto_connect_enabled: number;
  created_at: string;
  last_used_at: string | null;
  revoked_at: string | null;
}

function mapRow(row: TrustedDeviceRow): TrustedDeviceRecord {
  return {
    deviceRecordId: row.device_record_id,
    tokenHash: row.token_hash,
    companyId: row.company_id,
    companyName: row.company_name,
    installationId: row.installation_id,
    friendlyName: row.friendly_name,
    autoConnectEnabled: row.auto_connect_enabled === 1,
    createdAt: row.created_at,
    lastUsedAt: row.last_used_at,
    revokedAt: row.revoked_at,
  };
}

export interface PairDeviceParams {
  readonly companyId: string;
  readonly companyName: string;
  readonly installationId: string;
  readonly friendlyName?: string | null;
  readonly autoConnectEnabled?: boolean;
}

export interface PairDeviceResult {
  readonly deviceRecordId: string;
  /** Raw bearer token — returned once to the caller; never stored. */
  readonly rawToken: string;
  readonly companyId: string;
  readonly companyName: string;
  readonly autoConnectEnabled: boolean;
}

/** SHA-256 hex of raw token — the only form stored on the Connector side. */
function hashToken(rawToken: string): string {
  return crypto.createHash('sha256').update(rawToken, 'utf8').digest('hex');
}

export class TrustedDeviceRepository {
  constructor(private readonly db: SqliteDatabase) {}

  pair(params: PairDeviceParams): PairDeviceResult {
    const deviceRecordId = crypto.randomUUID();
    const rawToken = crypto.randomBytes(32).toString('base64url');
    const tokenHash = hashToken(rawToken);
    const now = new Date().toISOString();

    this.db.getDatabase().prepare(`
      INSERT INTO trusted_devices
        (device_record_id, token_hash, company_id, company_name, installation_id,
         friendly_name, auto_connect_enabled, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      deviceRecordId,
      tokenHash,
      params.companyId,
      params.companyName,
      params.installationId,
      params.friendlyName ?? null,
      params.autoConnectEnabled ? 1 : 0,
      now,
    );

    return {
      deviceRecordId,
      rawToken,
      companyId: params.companyId,
      companyName: params.companyName,
      autoConnectEnabled: params.autoConnectEnabled ?? false,
    };
  }

  /**
   * Validates a raw bearer token. Returns the record if the token is valid and
   * not revoked; null otherwise. Updates last_used_at on success.
   */
  validateToken(rawToken: string): TrustedDeviceRecord | null {
    const tokenHash = hashToken(rawToken);
    const row = this.db.getDatabase().prepare(`
      SELECT * FROM trusted_devices
      WHERE token_hash = ? AND revoked_at IS NULL
    `).get(tokenHash) as TrustedDeviceRow | undefined;

    if (!row) return null;

    const now = new Date().toISOString();
    this.db.getDatabase().prepare(
      'UPDATE trusted_devices SET last_used_at = ? WHERE device_record_id = ?',
    ).run(now, row.device_record_id);

    return mapRow({ ...row, last_used_at: now });
  }

  listByInstallation(installationId: string): readonly TrustedDeviceRecord[] {
    const rows = this.db.getDatabase().prepare(`
      SELECT * FROM trusted_devices
      WHERE installation_id = ? AND revoked_at IS NULL
      ORDER BY last_used_at DESC, created_at DESC
    `).all(installationId) as unknown as TrustedDeviceRow[];
    return rows.map(mapRow);
  }

  listAll(): readonly TrustedDeviceRecord[] {
    const rows = this.db.getDatabase().prepare(`
      SELECT * FROM trusted_devices ORDER BY created_at DESC
    `).all() as unknown as TrustedDeviceRow[];
    return rows.map(mapRow);
  }

  revoke(deviceRecordId: string): boolean {
    const result = this.db.getDatabase().prepare(`
      UPDATE trusted_devices SET revoked_at = ?
      WHERE device_record_id = ? AND revoked_at IS NULL
    `).run(new Date().toISOString(), deviceRecordId);
    return (result.changes as number) > 0;
  }

  setAutoConnect(deviceRecordId: string, enabled: boolean): boolean {
    const result = this.db.getDatabase().prepare(`
      UPDATE trusted_devices SET auto_connect_enabled = ?
      WHERE device_record_id = ? AND revoked_at IS NULL
    `).run(enabled ? 1 : 0, deviceRecordId);
    return (result.changes as number) > 0;
  }
}
