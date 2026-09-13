import path from 'node:path';

import { resolveAppDataLayout, isPathWithinRoot } from '../release/app-data-layout.js';
import {
  ArtifactClassification,
  isDistributableClassification,
} from '../release/artifact-classification.js';

/**
 * Identity of the last approved controlled-pilot installer.
 *
 * Every field describes one specific, checksummed installer artifact and
 * must be refreshed together as a single unit whenever a new candidate is
 * packaged and approved — including connectorVersion and
 * storageSchemaVersion. Updating any field in isolation would describe an
 * installer that was never actually built, hashed, or verified.
 */
export const CONTROLLED_PILOT_CANDIDATE = {
  gitCommit: 'a9595af857546de3c65f1457775f3f65eb78ae77',
  shortCommit: 'a9595af',
  sha256: '911422191ea977558f73fc756b6eb32dd701e01b16fb6626fc030da6e6480bdd',
  installerFilename: 'VentureDesktop-0.4.3-x64-setup.exe',
  sizeBytes: 81970565,
  releaseMode: 'controlled_pilot',
  desktopVersion: '0.4.3',
  connectorVersion: '0.3.1',
  storageSchemaVersion: 8,
  architecture: 'x64',
} as const;

export class LifecycleGateError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LifecycleGateError';
  }
}

export interface PilotCandidateBuildInfo {
  readonly gitCommit?: string;
  readonly releaseMode?: string;
  readonly dirtyTree?: boolean;
  readonly sourceTreeCleanAtStart?: boolean;
  readonly desktopVersion?: string;
  readonly connectorVersion?: string;
  readonly storageSchemaVersion?: number;
}

export interface PilotCandidateManifestEntry {
  readonly filename: string;
  readonly sizeBytes: number;
  readonly checksum: string;
  readonly classification?: string;
  readonly distributable?: boolean;
}

export interface PilotCandidateVerificationInput {
  readonly installerPath: string;
  readonly sha256: string;
  readonly sizeBytes: number;
  readonly manifestEntry: PilotCandidateManifestEntry;
  readonly checksumLine: string;
  readonly buildInfo: PilotCandidateBuildInfo;
}

export interface RetentionMarkers {
  readonly configMarker?: string;
  readonly databaseMarker?: string;
  readonly diagnosticMarker?: string;
}

export interface ProcessSnapshot {
  readonly desktopProcessCount: number;
  readonly connectorProcessCount: number;
  readonly orphanConnectorDetected: boolean;
}

export interface InstallScopeObservation {
  readonly installRoot: string;
  readonly perMachine: boolean;
  readonly uacPromptObserved: boolean;
}

const ALLOWED_INSTALL_ROOT_PREFIXES = [
  path.join(process.env.LOCALAPPDATA ?? '', 'Programs'),
] as const;

export function verifyPilotCandidateIntegrity(input: PilotCandidateVerificationInput): void {
  if (input.sha256 !== CONTROLLED_PILOT_CANDIDATE.sha256) {
    throw new LifecycleGateError('Installer SHA-256 mismatch');
  }
  if (input.sizeBytes !== CONTROLLED_PILOT_CANDIDATE.sizeBytes) {
    throw new LifecycleGateError('Installer size mismatch');
  }
  if (input.manifestEntry.checksum !== CONTROLLED_PILOT_CANDIDATE.sha256) {
    throw new LifecycleGateError('Manifest checksum mismatch');
  }
  if (input.manifestEntry.sizeBytes !== CONTROLLED_PILOT_CANDIDATE.sizeBytes) {
    throw new LifecycleGateError('Manifest size mismatch');
  }
  if (input.checksumLine.trim() !== `${CONTROLLED_PILOT_CANDIDATE.sha256}  ${CONTROLLED_PILOT_CANDIDATE.installerFilename}`) {
    throw new LifecycleGateError('Checksum file mismatch');
  }
  if (input.manifestEntry.classification !== ArtifactClassification.NsisInstaller) {
    throw new LifecycleGateError('Installer classification is not nsis_installer');
  }
  if (input.manifestEntry.distributable !== true) {
    throw new LifecycleGateError('Installer is not marked distributable');
  }
  assertBuildIdentity(input.buildInfo);
}

export function assertBuildIdentity(buildInfo: PilotCandidateBuildInfo): void {
  if (!buildInfo.gitCommit?.startsWith(CONTROLLED_PILOT_CANDIDATE.shortCommit)) {
    throw new LifecycleGateError('Embedded Git commit mismatch');
  }
  if (buildInfo.releaseMode !== CONTROLLED_PILOT_CANDIDATE.releaseMode) {
    throw new LifecycleGateError('Release mode mismatch');
  }
  if (buildInfo.dirtyTree !== false) {
    throw new LifecycleGateError('Candidate reports dirtyTree=true');
  }
  if (buildInfo.sourceTreeCleanAtStart !== true) {
    throw new LifecycleGateError('Candidate sourceTreeCleanAtStart is not true');
  }
  if (buildInfo.desktopVersion !== CONTROLLED_PILOT_CANDIDATE.desktopVersion) {
    throw new LifecycleGateError('Desktop version mismatch');
  }
  if (buildInfo.connectorVersion !== CONTROLLED_PILOT_CANDIDATE.connectorVersion) {
    throw new LifecycleGateError('Connector version mismatch');
  }
  if (buildInfo.storageSchemaVersion !== CONTROLLED_PILOT_CANDIDATE.storageSchemaVersion) {
    throw new LifecycleGateError('Storage schema version mismatch');
  }
}

export function rejectNonDistributableClassification(classification: string, distributable: boolean): void {
  if (!isDistributableClassification(classification as typeof ArtifactClassification[keyof typeof ArtifactClassification])) {
    throw new LifecycleGateError(`Non-distributable classification rejected: ${classification}`);
  }
  if (!distributable) {
    throw new LifecycleGateError('Artifact distributable flag is false');
  }
}

export function validateInstallScope(observation: InstallScopeObservation): void {
  if (observation.perMachine) {
    throw new LifecycleGateError('Per-machine installation scope is not permitted for controlled pilot');
  }
  if (observation.uacPromptObserved) {
    throw new LifecycleGateError('Unexpected UAC elevation during per-user install');
  }
  const normalized = path.resolve(observation.installRoot);
  const allowed = ALLOWED_INSTALL_ROOT_PREFIXES.some((prefix) => {
    if (!prefix) {
      return false;
    }
    const relative = path.relative(path.resolve(prefix), normalized);
    return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
  });
  if (!allowed) {
    throw new LifecycleGateError('Installation path outside expected per-user scope');
  }
}

export function validateMutableDataOutsideInstall(input: {
  readonly userDataRoot: string;
  readonly installRoot: string;
}): void {
  const layout = resolveAppDataLayout({
    userDataDir: input.userDataRoot,
    isPackaged: true,
    installRoot: input.installRoot,
  });
  if (isPathWithinRoot(layout.connectorDatabaseDir, input.installRoot)) {
    throw new LifecycleGateError('Connector database directory must not be inside install root');
  }
  if (isPathWithinRoot(layout.configDir, input.installRoot)) {
    throw new LifecycleGateError('Configuration directory must not be inside install root');
  }
  if (layout.userDataRoot === process.cwd()) {
    throw new LifecycleGateError('Mutable data must not resolve to current working directory');
  }
}

export function validateProcessOwnership(snapshot: ProcessSnapshot): void {
  if (snapshot.desktopProcessCount > 1) {
    throw new LifecycleGateError('Duplicate desktop process detected');
  }
  if (snapshot.connectorProcessCount > 1) {
    throw new LifecycleGateError('Duplicate connector process detected');
  }
  if (snapshot.orphanConnectorDetected) {
    throw new LifecycleGateError('Orphan connector process detected');
  }
}

export function assertRetentionMarkersPresent(markers: RetentionMarkers): void {
  if (!markers.configMarker) {
    throw new LifecycleGateError('Retained configuration marker missing');
  }
  if (!markers.databaseMarker) {
    throw new LifecycleGateError('Retained database marker missing');
  }
}

export function assertRetentionMarkersAbsent(markers: RetentionMarkers): void {
  if (markers.configMarker || markers.databaseMarker || markers.diagnosticMarker) {
    throw new LifecycleGateError('Unexpected data deletion detected');
  }
}

export function validateUninstallRetention(input: {
  readonly installRootExists: boolean;
  readonly appDataRootExists: boolean;
  readonly markers: RetentionMarkers;
}): void {
  if (input.installRootExists) {
    throw new LifecycleGateError('Install root still present after uninstall');
  }
  if (!input.appDataRootExists) {
    throw new LifecycleGateError('AppData root deleted during uninstall');
  }
  assertRetentionMarkersPresent(input.markers);
}

export function validateCleanupTarget(candidatePath: string, allowedRoot: string): void {
  const resolvedCandidate = path.resolve(candidatePath);
  const resolvedRoot = path.resolve(allowedRoot);
  if (!isPathWithinRoot(resolvedCandidate, resolvedRoot)) {
    throw new LifecycleGateError('Cleanup path escape rejected');
  }
  if (resolvedCandidate.includes('..')) {
    throw new LifecycleGateError('Cleanup path traversal rejected');
  }
}

export function rejectSymlinkOrJunctionCleanup(targetPath: string, isSymlink: boolean, isJunction: boolean): void {
  if (isSymlink || isJunction) {
    throw new LifecycleGateError('Symlink/junction cleanup escape rejected');
  }
}

export function assertFutureSchemaBlocked(appliedVersion: number, expectedVersion: number): void {
  if (appliedVersion <= expectedVersion) {
    throw new LifecycleGateError('Future schema version was not blocked');
  }
}

export function assertDowngradeBlocked(appliedVersion: number, expectedVersion: number): void {
  if (appliedVersion <= expectedVersion) {
    throw new LifecycleGateError('Downgrade attempt was not blocked');
  }
}

export function assertUpgradePermitted(fromVersion: number, toVersion: number): void {
  if (fromVersion > toVersion) {
    throw new LifecycleGateError('Upgrade path was rejected unexpectedly');
  }
}

export interface LifecycleGateReport {
  readonly gateVersion: number;
  readonly platform: string;
  readonly isolationMethod: string;
  readonly candidateVerified: boolean;
  readonly installationCompleted: boolean;
  readonly uninstallRetentionVerified: boolean;
  readonly releaseMode?: string;
  readonly gitCommit?: string;
  readonly notes?: readonly string[];
}

export function validateLifecycleReport(report: LifecycleGateReport): void {
  if (report.gateVersion !== 1) {
    throw new LifecycleGateError('Unsupported lifecycle report version');
  }
  if (!report.platform) {
    throw new LifecycleGateError('Lifecycle report missing platform');
  }
  if (!report.isolationMethod) {
    throw new LifecycleGateError('Lifecycle report missing isolation method');
  }
}

export function sanitizeLifecycleReportForExport(report: LifecycleGateReport): Record<string, unknown> {
  return {
    gateVersion: report.gateVersion,
    platform: report.platform,
    isolationMethod: report.isolationMethod,
    candidateVerified: report.candidateVerified,
    installationCompleted: report.installationCompleted,
    uninstallRetentionVerified: report.uninstallRetentionVerified,
    releaseMode: report.releaseMode,
    gitCommit: report.gitCommit?.slice(0, 12),
    notes: report.notes,
  };
}

export function detectUnexpectedStartupEntries(entries: readonly string[]): void {
  const forbidden = entries.filter((entry) => /(HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run|Scheduled Tasks|Windows Service)/i.test(entry));
  if (forbidden.length > 0) {
    throw new LifecycleGateError('Unexpected startup or service entry detected');
  }
}

export function assertPrivacySafeLifecycleLog(lines: readonly string[]): void {
  const forbiddenPatterns = [
    /BEGIN (RSA )?PRIVATE KEY/i,
    /password\s*[:=]/i,
    /gstin/i,
    /<\?xml/i,
    /voucher/i,
  ];
  for (const line of lines) {
    for (const pattern of forbiddenPatterns) {
      if (pattern.test(line)) {
        throw new LifecycleGateError('Lifecycle log contains prohibited content');
      }
    }
  }
}
