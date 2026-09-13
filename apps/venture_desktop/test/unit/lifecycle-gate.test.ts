import { describe, expect, it } from 'vitest';

import {
  assertBuildIdentity,
  assertDowngradeBlocked,
  assertFutureSchemaBlocked,
  assertPrivacySafeLifecycleLog,
  assertRetentionMarkersAbsent,
  assertRetentionMarkersPresent,
  assertUpgradePermitted,
  CONTROLLED_PILOT_CANDIDATE,
  detectUnexpectedStartupEntries,
  LifecycleGateError,
  rejectNonDistributableClassification,
  sanitizeLifecycleReportForExport,
  validateCleanupTarget,
  validateInstallScope,
  validateLifecycleReport,
  validateMutableDataOutsideInstall,
  validateProcessOwnership,
  validateUninstallRetention,
  verifyPilotCandidateIntegrity,
  rejectSymlinkOrJunctionCleanup,
  type PilotCandidateVerificationInput,
} from '../../src/application/lifecycle/lifecycle-gate.js';
import { ArtifactClassification } from '../../src/application/release/artifact-classification.js';

const baseCandidate = (): PilotCandidateVerificationInput => ({
  installerPath: CONTROLLED_PILOT_CANDIDATE.installerFilename,
  sha256: CONTROLLED_PILOT_CANDIDATE.sha256,
  sizeBytes: CONTROLLED_PILOT_CANDIDATE.sizeBytes,
  manifestEntry: {
    filename: CONTROLLED_PILOT_CANDIDATE.installerFilename,
    sizeBytes: CONTROLLED_PILOT_CANDIDATE.sizeBytes,
    checksum: CONTROLLED_PILOT_CANDIDATE.sha256,
    classification: ArtifactClassification.NsisInstaller,
    distributable: true,
  },
  checksumLine: `${CONTROLLED_PILOT_CANDIDATE.sha256}  ${CONTROLLED_PILOT_CANDIDATE.installerFilename}`,
  buildInfo: {
    gitCommit: CONTROLLED_PILOT_CANDIDATE.gitCommit,
    releaseMode: CONTROLLED_PILOT_CANDIDATE.releaseMode,
    dirtyTree: false,
    sourceTreeCleanAtStart: true,
    desktopVersion: CONTROLLED_PILOT_CANDIDATE.desktopVersion,
    connectorVersion: CONTROLLED_PILOT_CANDIDATE.connectorVersion,
    storageSchemaVersion: CONTROLLED_PILOT_CANDIDATE.storageSchemaVersion,
  },
});

describe('controlled-pilot lifecycle gate', () => {
  it('1. accepts candidate checksum and manifest integrity', () => {
    expect(() => verifyPilotCandidateIntegrity(baseCandidate())).not.toThrow();
  });

  it('2. rejects checksum mismatch', () => {
    expect(() => verifyPilotCandidateIntegrity({
      ...baseCandidate(),
      sha256: '0'.repeat(64),
    })).toThrow(/SHA-256 mismatch/);
  });

  it('3. rejects manifest mismatch', () => {
    expect(() => verifyPilotCandidateIntegrity({
      ...baseCandidate(),
      manifestEntry: {
        ...baseCandidate().manifestEntry,
        checksum: '0'.repeat(64),
      },
    })).toThrow(/Manifest checksum mismatch/);
  });

  it('4. rejects wrong commit', () => {
    expect(() => assertBuildIdentity({ ...baseCandidate().buildInfo, gitCommit: 'deadbeef'.repeat(5) }))
      .toThrow(/Git commit mismatch/);
  });

  it('5. rejects dirty-tree candidate', () => {
    expect(() => assertBuildIdentity({ ...baseCandidate().buildInfo, dirtyTree: true }))
      .toThrow(/dirtyTree=true/);
  });

  it('6. rejects wrong release mode', () => {
    expect(() => assertBuildIdentity({ ...baseCandidate().buildInfo, releaseMode: 'production' }))
      .toThrow(/Release mode mismatch/);
  });

  it('7. rejects non-distributable classification', () => {
    expect(() => rejectNonDistributableClassification(ArtifactClassification.ValidationOnly, false))
      .toThrow(/Non-distributable classification/);
  });

  it('8. rejects installation path outside expected scope', () => {
    expect(() => validateInstallScope({
      installRoot: 'C:\\Program Files\\Venture Desktop',
      perMachine: false,
      uacPromptObserved: false,
    })).toThrow(/outside expected per-user scope/);
  });

  it('9. rejects mutable data inside install directory', () => {
    expect(() => validateMutableDataOutsideInstall({
      userDataRoot: 'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
      installRoot: 'C:\\Users\\Tester\\AppData\\Roaming\\venture-desktop',
    })).toThrow(/must not be inside install root/);
  });

  it('10. detects unexpected startup entries', () => {
    expect(() => detectUnexpectedStartupEntries(['HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\Venture']))
      .toThrow(/Unexpected startup/);
  });

  it('11. detects duplicate desktop process', () => {
    expect(() => validateProcessOwnership({
      desktopProcessCount: 2,
      connectorProcessCount: 1,
      orphanConnectorDetected: false,
    })).toThrow(/Duplicate desktop process/);
  });

  it('12. detects duplicate connector process', () => {
    expect(() => validateProcessOwnership({
      desktopProcessCount: 1,
      connectorProcessCount: 2,
      orphanConnectorDetected: false,
    })).toThrow(/Duplicate connector process/);
  });

  it('13. detects orphan connector process', () => {
    expect(() => validateProcessOwnership({
      desktopProcessCount: 0,
      connectorProcessCount: 1,
      orphanConnectorDetected: true,
    })).toThrow(/Orphan connector process/);
  });

  it('14. requires retained configuration marker', () => {
    expect(() => assertRetentionMarkersPresent({ databaseMarker: 'db-marker' }))
      .toThrow(/configuration marker missing/);
  });

  it('15. requires retained database marker', () => {
    expect(() => assertRetentionMarkersPresent({ configMarker: 'cfg-marker' }))
      .toThrow(/database marker missing/);
  });

  it('16. rejects missing retained data after uninstall', () => {
    expect(() => validateUninstallRetention({
      installRootExists: false,
      appDataRootExists: false,
      markers: {},
    })).toThrow(/AppData root deleted/);
  });

  it('17. rejects unexpected uninstall deletion', () => {
    expect(() => assertRetentionMarkersAbsent({ configMarker: 'still-here' }))
      .toThrow(/Unexpected data deletion/);
  });

  it('18. rejects cleanup path escape', () => {
    expect(() => validateCleanupTarget('C:\\outside\\file.txt', 'C:\\isolated-root'))
      .toThrow(/Cleanup path escape/);
  });

  it('19. rejects symlink/junction cleanup escape', () => {
    expect(() => rejectSymlinkOrJunctionCleanup('C:\\isolated-root\\link', true, false))
      .toThrow(/Symlink\/junction cleanup escape/);
  });

  it('20. rejects malformed lifecycle report', () => {
    expect(() => validateLifecycleReport({
      gateVersion: 99,
      platform: 'win32',
      isolationMethod: 'isolated',
      candidateVerified: true,
      installationCompleted: false,
      uninstallRetentionVerified: false,
    })).toThrow(/Unsupported lifecycle report version/);
  });

  it('21. blocks future schema versions', () => {
    expect(() => assertFutureSchemaBlocked(8, 8)).toThrow(/Future schema/);
    expect(() => assertFutureSchemaBlocked(9, 8)).not.toThrow();
  });

  it('22. rejects downgrade attempts', () => {
    expect(() => assertDowngradeBlocked(8, 8)).toThrow(/Downgrade attempt/);
    expect(() => assertDowngradeBlocked(9, 8)).not.toThrow();
  });

  it('23. accepts reinstall marker preservation', () => {
    expect(() => assertRetentionMarkersPresent({
      configMarker: 'cfg-marker',
      databaseMarker: 'db-marker',
    })).not.toThrow();
  });

  it('24. exports privacy-safe lifecycle report', () => {
    const exported = sanitizeLifecycleReportForExport({
      gateVersion: 1,
      platform: 'win32',
      isolationMethod: 'isolated-root',
      candidateVerified: true,
      installationCompleted: true,
      uninstallRetentionVerified: true,
      releaseMode: 'controlled_pilot',
      gitCommit: CONTROLLED_PILOT_CANDIDATE.gitCommit,
      notes: ['no secrets'],
    });
    expect(JSON.stringify(exported)).not.toMatch(/password/i);
    expect(exported.gitCommit).toBe('a9595af85754');
    assertPrivacySafeLifecycleLog(['releaseMode=controlled_pilot', 'startup complete']);
  });
});

describe('upgrade path helpers', () => {
  it('permits supported upgrade path', () => {
    expect(() => assertUpgradePermitted(7, 8)).not.toThrow();
  });
});
