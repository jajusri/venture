export const ArtifactClassification = {
  NsisInstaller: 'nsis_installer',
  PortableZip: 'portable_zip',
  ValidationOnly: 'validation_only',
  NonDistributable: 'non_distributable',
  InstallerNotBuilt: 'installer_not_built',
};

export const CONTROLLED_PILOT_INSTALLER_SUFFIX = '-x64-setup.exe';

export function isControlledPilotInstallerFilename(filename) {
  const normalized = filename.replace(/\\/g, '/').split('/').pop() ?? filename;
  return normalized.endsWith(CONTROLLED_PILOT_INSTALLER_SUFFIX) && normalized.endsWith('.exe');
}

export function classifyArtifactFilename(filename) {
  const base = filename.replace(/\\/g, '/').split('/').pop() ?? filename;
  if (base.includes('validation-only') || base.endsWith('.validation-only')) {
    return ArtifactClassification.ValidationOnly;
  }
  if (base.endsWith(CONTROLLED_PILOT_INSTALLER_SUFFIX)) {
    return ArtifactClassification.NsisInstaller;
  }
  if (base.endsWith('-x64-portable.zip')) {
    return ArtifactClassification.PortableZip;
  }
  if (base.startsWith('portable-directory:') || base.includes('placeholder')) {
    return ArtifactClassification.NonDistributable;
  }
  return ArtifactClassification.NonDistributable;
}

export function isDistributableClassification(classification) {
  return classification === ArtifactClassification.NsisInstaller
    || classification === ArtifactClassification.PortableZip;
}

export function assertNotPlaceholderInstaller(entry) {
  if (entry.classification === ArtifactClassification.ValidationOnly
    || entry.classification === ArtifactClassification.NonDistributable
    || entry.classification === ArtifactClassification.InstallerNotBuilt) {
    if (isControlledPilotInstallerFilename(entry.filename)) {
      throw new Error(`Placeholder or non-distributable artifact cannot use installer filename: ${entry.filename}`);
    }
    return;
  }
  if (entry.classification === ArtifactClassification.NsisInstaller && !entry.distributable) {
    throw new Error(`NSIS installer artifact must be marked distributable: ${entry.filename}`);
  }
}

export function minimumInstallerByteSize() {
  return 256 * 1024;
}

export function assertRealInstallerArtifact(filename, sizeBytes) {
  if (!isControlledPilotInstallerFilename(filename)) {
    throw new Error(`Expected NSIS installer filename ending with ${CONTROLLED_PILOT_INSTALLER_SUFFIX}`);
  }
  if (sizeBytes < minimumInstallerByteSize()) {
    throw new Error(`Installer artifact too small to be a real NSIS build (${sizeBytes} bytes)`);
  }
}

export function evaluateControlledPilotAcceptance(input) {
  const reasons = [];
  if (input.platform !== 'win32') {
    reasons.push('controlled_pilot distributable release requires Windows (win32)');
  }
  const installers = input.artifacts.filter(
    (entry) => entry.classification === ArtifactClassification.NsisInstaller,
  );
  const placeholders = input.artifacts.filter(
    (entry) => entry.classification === ArtifactClassification.ValidationOnly
      || entry.classification === ArtifactClassification.NonDistributable
      || entry.classification === ArtifactClassification.InstallerNotBuilt,
  );
  if (input.platform === 'win32' && installers.length === 0) {
    reasons.push('Windows controlled_pilot release requires a real NSIS installer artifact');
  }
  for (const entry of placeholders) {
    if (isControlledPilotInstallerFilename(entry.filename)) {
      reasons.push(`Placeholder/non-distributable artifact impersonates installer filename: ${entry.filename}`);
    }
  }
  if (input.releaseVerdict !== 'PASS') {
    reasons.push(`Release pipeline verdict is ${input.releaseVerdict}, expected PASS`);
  }
  for (const entry of input.artifacts) {
    try {
      assertNotPlaceholderInstaller(entry);
    } catch (error) {
      reasons.push(error instanceof Error ? error.message : String(error));
    }
  }
  return { accepted: reasons.length === 0, reasons };
}
