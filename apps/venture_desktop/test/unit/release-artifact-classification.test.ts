import { describe, expect, it } from 'vitest';

import {
  ArtifactClassification,
  assertNotPlaceholderInstaller,
  assertRealInstallerArtifact,
  classifyArtifactFilename,
  evaluateControlledPilotAcceptance,
  isControlledPilotInstallerFilename,
  minimumInstallerByteSize,
} from '../../src/application/release/artifact-classification.js';

describe('artifact classification safety', () => {
  it('classifies NSIS installer filenames', () => {
    expect(classifyArtifactFilename('VentureDesktop-0.4.3-x64-setup.exe'))
      .toBe(ArtifactClassification.NsisInstaller);
  });

  it('classifies portable zip separately from installer', () => {
    expect(classifyArtifactFilename('VentureDesktop-0.4.3-x64-portable.zip'))
      .toBe(ArtifactClassification.PortableZip);
  });

  it('classifies validation-only artifacts as non-distributable', () => {
    expect(classifyArtifactFilename('pipeline.validation-only'))
      .toBe(ArtifactClassification.ValidationOnly);
  });

  it('rejects placeholder impersonating installer filename', () => {
    expect(() => assertNotPlaceholderInstaller({
      filename: 'VentureDesktop-0.4.3-x64-setup.exe',
      classification: ArtifactClassification.ValidationOnly,
      distributable: false,
    })).toThrow(/cannot use installer filename/);
  });

  it('requires real installer size for NSIS artifacts', () => {
    expect(() => assertRealInstallerArtifact('VentureDesktop-0.4.3-x64-setup.exe', 1024))
      .toThrow(/too small/);
    expect(() => assertRealInstallerArtifact('VentureDesktop-0.4.3-x64-setup.exe', minimumInstallerByteSize()))
      .not.toThrow();
  });

  it('fails Windows acceptance without NSIS installer', () => {
    const result = evaluateControlledPilotAcceptance({
      platform: 'win32',
      artifacts: [{
        filename: 'pipeline.validation-only',
        classification: ArtifactClassification.ValidationOnly,
        distributable: false,
      }],
      releaseVerdict: 'PASS',
    });
    expect(result.accepted).toBe(false);
    expect(result.reasons.join(' ')).toMatch(/requires a real NSIS installer/);
  });

  it('accepts Windows release with real installer artifact', () => {
    const result = evaluateControlledPilotAcceptance({
      platform: 'win32',
      artifacts: [{
        filename: 'VentureDesktop-0.4.3-x64-setup.exe',
        classification: ArtifactClassification.NsisInstaller,
        distributable: true,
      }],
      releaseVerdict: 'PASS',
    });
    expect(result.accepted).toBe(true);
  });

  it('never accepts placeholder as distributable installer', () => {
    expect(isControlledPilotInstallerFilename('VentureDesktop-0.4.3-x64-setup.exe')).toBe(true);
    const result = evaluateControlledPilotAcceptance({
      platform: 'win32',
      artifacts: [{
        filename: 'VentureDesktop-0.4.3-x64-setup.exe',
        classification: ArtifactClassification.NonDistributable,
        distributable: false,
      }],
      releaseVerdict: 'PASS',
    });
    expect(result.accepted).toBe(false);
  });

  it('fails non-Windows distributable acceptance', () => {
    const result = evaluateControlledPilotAcceptance({
      platform: 'linux',
      artifacts: [{
        filename: 'VentureDesktop-0.4.3-x64-setup.exe',
        classification: ArtifactClassification.NsisInstaller,
        distributable: true,
      }],
      releaseVerdict: 'PASS',
    });
    expect(result.accepted).toBe(false);
    expect(result.reasons.join(' ')).toMatch(/requires Windows/);
  });
});
