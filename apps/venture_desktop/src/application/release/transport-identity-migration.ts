import fs from 'node:fs';
import path from 'node:path';

const KEY_FILE_NAME = 'transport-key.pem';
const CERT_FILE_NAME = 'transport-cert.pem';

export type TransportIdentityMigrationOutcome =
  | { readonly migrated: true }
  | {
      readonly migrated: false;
      readonly reason:
        | 'persistent-identity-already-present'
        | 'persistent-identity-partial'
        | 'no-legacy-identity-found';
      readonly requiresRePair?: boolean;
    };

export interface MigrateLegacyTransportIdentityInput {
  /** The new, reinstall-surviving directory (AppDataLayout.connectorTransportIdentityDir). */
  readonly persistentDir: string;
  /**
   * Where the Connector's own default (`./data/transport`, relative to its packaged working
   * directory) previously left the identity — inside the install/resources tree a Desktop
   * reinstall replaces. Never written to by this function, only read from.
   */
  readonly legacyDir: string;
  readonly existingInstallation?: boolean;
  readonly fsImpl?: Pick<typeof fs, 'existsSync' | 'mkdirSync' | 'copyFileSync' | 'renameSync' | 'rmSync'>;
  readonly onOutcome?: (outcome: TransportIdentityMigrationOutcome) => void;
}

export function resolvePackagedLegacyTransportIdentityDir(connectorEntryScript: string): string {
  return path.join(path.dirname(connectorEntryScript), 'data', 'transport');
}

/**
 * One-time, idempotent, fail-safe copy of a pre-existing Connector transport identity from its
 * old install-relative location into the new persistent one — see TD-018
 * (docs/technical-debt/registry.md) and app-data-layout.ts's connectorTransportIdentityDir.
 *
 * Deliberately narrow:
 *  - NEVER overwrites an already-present persistent identity (both files present there is always
 *    a no-op, regardless of what legacyDir contains) — the persistent location, once it has a
 *    real identity, is permanently authoritative.
 *  - NEVER attempts to repair a PARTIAL persistent identity (exactly one of the two files
 *    present) by guessing from legacy — that ambiguous state is left for
 *    ConnectorTransportIdentityService's own existing, documented corrupt-detection
 *    (TRANSPORT_IDENTITY_ERROR, manual recovery) to surface clearly instead of silently masking
 *    it with an unrelated identity.
 *  - Only copies (never moves/deletes) from legacyDir, so an interrupted migration always has an
 *    intact, unmodified source to retry from on the next launch — copies happen via a temp
 *    filename then an atomic rename per file, matching ConnectorTransportIdentityService's own
 *    write convention, so a crash between copying the key and the cert never leaves a
 *    half-renamed pair with the real filenames.
 *  - Never logs key material — callers only ever observe the outcome variant above.
 */
export function migrateLegacyTransportIdentity(input: MigrateLegacyTransportIdentityInput): TransportIdentityMigrationOutcome {
  const fsImpl = input.fsImpl ?? fs;
  const persistentKeyPath = path.join(input.persistentDir, KEY_FILE_NAME);
  const persistentCertPath = path.join(input.persistentDir, CERT_FILE_NAME);
  const persistentKeyExists = fsImpl.existsSync(persistentKeyPath);
  const persistentCertExists = fsImpl.existsSync(persistentCertPath);

  const outcome = resolveOutcome({
    fsImpl,
    legacyDir: input.legacyDir,
    persistentDir: input.persistentDir,
    persistentKeyExists,
    persistentCertExists,
    persistentKeyPath,
    persistentCertPath,
    existingInstallation: input.existingInstallation ?? false,
  });
  input.onOutcome?.(outcome);
  return outcome;
}

function resolveOutcome(args: {
  fsImpl: Required<MigrateLegacyTransportIdentityInput>['fsImpl'];
  legacyDir: string;
  persistentDir: string;
  persistentKeyExists: boolean;
  persistentCertExists: boolean;
  persistentKeyPath: string;
  persistentCertPath: string;
  existingInstallation: boolean;
}): TransportIdentityMigrationOutcome {
  const { fsImpl, legacyDir, persistentDir, persistentKeyExists, persistentCertExists, persistentKeyPath, persistentCertPath, existingInstallation } = args;

  if (persistentKeyExists && persistentCertExists) {
    return { migrated: false, reason: 'persistent-identity-already-present' };
  }
  if (persistentKeyExists !== persistentCertExists) {
    // Partial persistent state — never guess-repair; leave it for the Connector's own
    // corrupt-detection to surface explicitly.
    return { migrated: false, reason: 'persistent-identity-partial' };
  }

  const legacyKeyPath = path.join(legacyDir, KEY_FILE_NAME);
  const legacyCertPath = path.join(legacyDir, CERT_FILE_NAME);
  const legacyKeyExists = fsImpl.existsSync(legacyKeyPath);
  const legacyCertExists = fsImpl.existsSync(legacyCertPath);
  if (!legacyKeyExists || !legacyCertExists) {
    return {
      migrated: false,
      reason: 'no-legacy-identity-found',
      ...(existingInstallation ? { requiresRePair: true } : {}),
    };
  }

  fsImpl.mkdirSync(persistentDir, { recursive: true });
  copyAtomically(fsImpl, legacyKeyPath, persistentKeyPath);
  copyAtomically(fsImpl, legacyCertPath, persistentCertPath);
  return { migrated: true };
}

function copyAtomically(
  fsImpl: Required<MigrateLegacyTransportIdentityInput>['fsImpl'],
  sourcePath: string,
  destinationPath: string,
): void {
  const tempPath = `${destinationPath}.migrating`;
  fsImpl.copyFileSync(sourcePath, tempPath);
  fsImpl.renameSync(tempPath, destinationPath);
}
