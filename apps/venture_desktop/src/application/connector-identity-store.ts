import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export interface ConnectorIdentity {
  readonly connectorId: string;
  readonly createdAt: string;
}

export interface ConnectorIdentityStoreOptions {
  readonly userDataDir: string;
  readonly fsImpl?: Pick<typeof fs, 'readFileSync' | 'writeFileSync' | 'renameSync' | 'existsSync' | 'mkdirSync'>;
  readonly generateId?: () => string;
}

export const CONNECTOR_IDENTITY_FILE_NAME = 'connector-identity.json';

/**
 * Owns the Desktop application's private, stable Connector identity — generated once with
 * crypto.randomUUID(), persisted alongside desktop-config.json, and never derived from IP,
 * MAC, Windows username, or machine name. Stable across Desktop/Connector/Windows restarts,
 * DHCP/Wi-Fi changes, and application upgrades (the file simply isn't touched by any of those).
 *
 * Deliberately a standalone file rather than a new field on DesktopConfigV1/desktop-config.json —
 * this keeps identity persistence independent from the (separately evolving) settings schema.
 */
export class ConnectorIdentityStore {
  private readonly filePath: string;
  private readonly tempPath: string;
  private readonly fsImpl: Pick<typeof fs, 'readFileSync' | 'writeFileSync' | 'renameSync' | 'existsSync' | 'mkdirSync'>;
  private readonly generateId: () => string;
  private cached: ConnectorIdentity | null = null;

  constructor(options: ConnectorIdentityStoreOptions) {
    this.filePath = path.join(options.userDataDir, CONNECTOR_IDENTITY_FILE_NAME);
    this.tempPath = `${this.filePath}.tmp`;
    this.fsImpl = options.fsImpl ?? fs;
    this.generateId = options.generateId ?? (() => crypto.randomUUID());
    this.fsImpl.mkdirSync(options.userDataDir, { recursive: true });
  }

  getOrCreateIdentity(): ConnectorIdentity {
    if (this.cached) {
      return this.cached;
    }

    const existing = this.readExisting();
    if (existing) {
      this.cached = existing;
      return existing;
    }

    const identity: ConnectorIdentity = {
      connectorId: this.generateId(),
      createdAt: new Date().toISOString(),
    };
    this.persist(identity);
    this.cached = identity;
    return identity;
  }

  private readExisting(): ConnectorIdentity | null {
    if (!this.fsImpl.existsSync(this.filePath)) {
      return null;
    }
    try {
      const raw = this.fsImpl.readFileSync(this.filePath, 'utf8');
      const parsed = JSON.parse(raw) as Partial<ConnectorIdentity>;
      if (typeof parsed.connectorId === 'string' && parsed.connectorId.trim().length > 0) {
        return {
          connectorId: parsed.connectorId,
          createdAt: typeof parsed.createdAt === 'string' ? parsed.createdAt : new Date().toISOString(),
        };
      }
      return null;
    } catch {
      // Corrupt identity file — there is no safe recovery for a scalar id, so this falls through
      // to generating a fresh one. Any already-paired Android device will need to re-pair, same
      // as if this install were freshly provisioned.
      return null;
    }
  }

  private persist(identity: ConnectorIdentity): void {
    const payload = `${JSON.stringify(identity, null, 2)}\n`;
    this.fsImpl.writeFileSync(this.tempPath, payload, 'utf8');
    this.fsImpl.renameSync(this.tempPath, this.filePath);
  }
}
