import fs from 'node:fs';
import path from 'node:path';

export class AppDataLayoutError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AppDataLayoutError';
  }
}

export interface AppDataLayout {
  readonly installRoot: string | null;
  readonly userDataRoot: string;
  readonly configDir: string;
  readonly logsDir: string;
  readonly diagnosticsExportDir: string;
  readonly connectorDataDir: string;
  readonly connectorDatabaseDir: string;
  readonly connectorDiagnosticsDir: string;
  readonly connectorTallyAuditPath: string;
  /**
   * Persistent home for the Connector's transport identity (private key + self-signed
   * certificate used for pinned-HTTPS secure pairing). Deliberately a sibling of
   * connectorDataDir, under the same reinstall-surviving userDataRoot — see
   * docs/technical-debt/registry.md TD-018 and transport-identity-migration.ts. Never the
   * install/resources tree, which a Desktop reinstall replaces.
   */
  readonly connectorTransportIdentityDir: string;
  readonly importStagingDir: string;
  readonly tempDir: string;
  readonly releaseMetadataDir: string;
}

export interface ResolveAppDataLayoutInput {
  readonly userDataDir: string;
  readonly isPackaged: boolean;
  readonly installRoot?: string | null;
}

export function resolveAppDataLayout(input: ResolveAppDataLayoutInput): AppDataLayout {
  const userDataRoot = assertAbsolutePath(input.userDataDir, 'userDataDir');
  if (userDataRoot === process.cwd()) {
    throw new AppDataLayoutError('Mutable application data must not resolve to the current working directory.');
  }
  const connectorDataDir = path.join(userDataRoot, 'connector-data');
  const connectorDiagnosticsDir = path.join(userDataRoot, 'connector-diagnostics');
  return {
    installRoot: input.isPackaged ? (input.installRoot ?? null) : null,
    userDataRoot,
    configDir: userDataRoot,
    logsDir: path.join(userDataRoot, 'logs'),
    diagnosticsExportDir: path.join(userDataRoot, 'diagnostics-exports'),
    connectorDataDir,
    connectorDatabaseDir: connectorDataDir,
    connectorDiagnosticsDir,
    connectorTallyAuditPath: path.join(connectorDiagnosticsDir, 'tally-request-audit.jsonl'),
    connectorTransportIdentityDir: path.join(userDataRoot, 'connector-transport-identity'),
    importStagingDir: path.join(userDataRoot, 'imports', 'staging'),
    tempDir: path.join(userDataRoot, 'temp'),
    releaseMetadataDir: path.join(userDataRoot, 'release-metadata'),
  };
}

export function ensureAppDataDirectories(
  layout: AppDataLayout,
  fsImpl: Pick<typeof fs, 'mkdirSync'> = fs,
): void {
  for (const dir of [
    layout.configDir,
    layout.logsDir,
    layout.diagnosticsExportDir,
    layout.connectorDataDir,
    layout.connectorDiagnosticsDir,
    layout.connectorTransportIdentityDir,
    layout.importStagingDir,
    layout.tempDir,
    layout.releaseMetadataDir,
  ]) {
    fsImpl.mkdirSync(dir, { recursive: true });
  }
}

export function assertPackagedMutablePathsOutsideInstallRoot(layout: AppDataLayout): void {
  if (!layout.installRoot) return;
  const mutablePaths = [
    layout.configDir,
    layout.logsDir,
    layout.diagnosticsExportDir,
    layout.connectorDataDir,
    layout.connectorDatabaseDir,
    layout.connectorDiagnosticsDir,
    layout.connectorTallyAuditPath,
    layout.connectorTransportIdentityDir,
    layout.importStagingDir,
    layout.tempDir,
    layout.releaseMetadataDir,
  ];
  for (const mutablePath of mutablePaths) {
    if (isPathWithinRoot(mutablePath, layout.installRoot)) {
      throw new AppDataLayoutError(`Mutable application path must remain outside the install root: ${mutablePath}`);
    }
  }
}

export function isPathWithinRoot(candidatePath: string, rootPath: string): boolean {
  const resolvedCandidate = path.resolve(candidatePath);
  const resolvedRoot = path.resolve(rootPath);
  const relative = path.relative(resolvedRoot, resolvedCandidate);
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

export function assertDeletionWithinAppDataRoot(candidatePath: string, appDataRoot: string): void {
  if (!isPathWithinRoot(candidatePath, appDataRoot)) {
    throw new AppDataLayoutError('Deletion path must remain within the application data root.');
  }
}

function assertAbsolutePath(value: string, label: string): string {
  if (!value || value.trim().length === 0) {
    throw new AppDataLayoutError(`${label} must not be empty.`);
  }
  const resolved = path.resolve(value);
  if (!path.isAbsolute(resolved)) {
    throw new AppDataLayoutError(`${label} must be an absolute path.`);
  }
  return resolved;
}
