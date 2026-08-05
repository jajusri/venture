import crypto from 'node:crypto';

import type { SqliteDatabase } from '../../storage/sqlite/sqlite-database.js';

/**
 * Device-bootstrap credentials issued on successful pairing-session redemption.
 *
 * This is deliberately a separate, narrower credential from `trusted_devices`
 * (services/device/trusted-device-repository.ts): that table binds a device to a specific
 * companyId, which does not exist yet at first-scan time (see pairing-session-repository.ts
 * doc comment). A pairing_device_credentials row only proves "this device completed the
 * physically-trusted bootstrap challenge for this Connector" — company-scoped trust, if wanted,
 * is layered on separately via the existing /device/pair flow once a company is chosen.
 *
 * This phase deliberately does not add credential expiry, Android device-key/certificate
 * binding, or rotation — the credential is a long-lived, revocable foundation credential only;
 * those hardening steps belong to a later phase once Android Keystore storage and transport
 * security exist to make them meaningful.
 */

export interface PairingDeviceCredentialRecord {
  readonly credentialId: string;
  readonly pairingSessionId: string;
  readonly connectorId: string;
  /** Android-supplied logical device identifier, when provided. Never IMEI/serial/Android ID. */
  readonly deviceId: string | null;
  readonly tokenHash: string;
  readonly deviceLabel: string | null;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
}

interface PairingDeviceCredentialRow {
  credential_id: string;
  pairing_session_id: string;
  connector_id: string;
  device_id: string | null;
  token_hash: string;
  device_label: string | null;
  created_at: string;
  last_used_at: string | null;
  revoked_at: string | null;
}

function mapRow(row: PairingDeviceCredentialRow): PairingDeviceCredentialRecord {
  return {
    credentialId: row.credential_id,
    pairingSessionId: row.pairing_session_id,
    connectorId: row.connector_id,
    deviceId: row.device_id,
    tokenHash: row.token_hash,
    deviceLabel: row.device_label,
    createdAt: row.created_at,
    lastUsedAt: row.last_used_at,
    revokedAt: row.revoked_at,
  };
}

export interface IssueCredentialParams {
  readonly pairingSessionId: string;
  readonly connectorId: string;
  readonly deviceId?: string | null;
  readonly deviceLabel?: string | null;
}

export interface IssueCredentialResult {
  readonly credentialId: string;
  /** Raw bearer token — returned once; never stored, never logged. */
  readonly rawToken: string;
}

function hashToken(rawToken: string): string {
  return crypto.createHash('sha256').update(rawToken, 'utf8').digest('hex');
}

export class PairingDeviceCredentialRepository {
  constructor(private readonly db: SqliteDatabase) {}

  /** Issued only by the pairing-session redeem route, only after a successful redemption. */
  issue(params: IssueCredentialParams): IssueCredentialResult {
    const credentialId = crypto.randomUUID();
    const rawToken = crypto.randomBytes(32).toString('base64url');
    const now = new Date().toISOString();

    this.db
      .getDatabase()
      .prepare(
        `INSERT INTO pairing_device_credentials
           (credential_id, pairing_session_id, connector_id, device_id, token_hash, device_label, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        credentialId,
        params.pairingSessionId,
        params.connectorId,
        params.deviceId ?? null,
        hashToken(rawToken),
        params.deviceLabel ?? null,
        now,
      );

    return { credentialId, rawToken };
  }

  /**
   * Validates a raw bearer token. Returns the record if valid and not revoked; null otherwise.
   * Updates last_used_at on success.
   */
  validateToken(rawToken: string): PairingDeviceCredentialRecord | null {
    const tokenHash = hashToken(rawToken);
    const row = this.db
      .getDatabase()
      .prepare('SELECT * FROM pairing_device_credentials WHERE token_hash = ? AND revoked_at IS NULL')
      .get(tokenHash) as PairingDeviceCredentialRow | undefined;

    if (!row) return null;

    const now = new Date().toISOString();
    this.db
      .getDatabase()
      .prepare('UPDATE pairing_device_credentials SET last_used_at = ? WHERE credential_id = ?')
      .run(now, row.credential_id);

    return mapRow({ ...row, last_used_at: now });
  }

  revoke(credentialId: string): boolean {
    const result = this.db
      .getDatabase()
      .prepare('UPDATE pairing_device_credentials SET revoked_at = ? WHERE credential_id = ? AND revoked_at IS NULL')
      .run(new Date().toISOString(), credentialId);
    return (result.changes as number) > 0;
  }

  /**
   * `rowid DESC` as a secondary key breaks ties deterministically when two credentials are
   * issued within the same millisecond (created_at has only millisecond resolution) — without
   * it, same-millisecond rows have no defined relative order at all.
   */
  listByConnector(connectorId: string): readonly PairingDeviceCredentialRecord[] {
    const rows = this.db
      .getDatabase()
      .prepare('SELECT *, rowid FROM pairing_device_credentials WHERE connector_id = ? ORDER BY created_at DESC, rowid DESC')
      .all(connectorId) as unknown as PairingDeviceCredentialRow[];
    return rows.map(mapRow);
  }
}
