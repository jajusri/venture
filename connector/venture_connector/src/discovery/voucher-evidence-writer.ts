import fs from 'node:fs';
import path from 'node:path';

import { assertWriteTargetContained, isPathContainedInRoot } from '../infrastructure/security/path-containment.js';
import type {
  VoucherFieldInventoryEntry,
  VoucherIdentityEvidence,
} from './voucher-evidence-sanitizer.js';

export interface VoucherDiscoveryEvidence {
  readonly requestShape: Readonly<Record<string, unknown>>;
  readonly sanitizedResponseXml: string;
  readonly fieldInventory: readonly VoucherFieldInventoryEntry[];
  readonly structuralCounts: Readonly<Record<string, unknown>>;
  readonly performance: Readonly<Record<string, unknown>>;
  readonly completenessMarkdown: string;
  readonly privacyReview: Readonly<Record<string, unknown>>;
  readonly runMetadata: Readonly<Record<string, unknown>>;
  readonly identityEvidence: readonly VoucherIdentityEvidence[];
}

export interface VoucherEvidenceLocations {
  readonly outputDirectory: string;
  readonly approvedOutputRoot: string;
  readonly restrictedOutputDirectory: string;
  readonly restrictedOutputRoot: string;
  readonly repositoryRoot: string;
}

export function validateVoucherEvidenceLocations(
  locations: VoucherEvidenceLocations,
): { readonly outputDirectory: string; readonly restrictedOutputDirectory: string } {
  const outputDirectory = assertWriteTargetContained({
    candidatePath: locations.outputDirectory,
    rootPath: locations.approvedOutputRoot,
  });
  const restrictedOutputDirectory = assertWriteTargetContained({
    candidatePath: locations.restrictedOutputDirectory,
    rootPath: locations.restrictedOutputRoot,
  });
  if (isPathContainedInRoot(restrictedOutputDirectory, locations.repositoryRoot)) {
    throw new Error('Restricted identity evidence must remain outside the repository.');
  }
  return { outputDirectory, restrictedOutputDirectory };
}

function writeJson(filePath: string, value: unknown): void {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
}

export function writeVoucherDiscoveryEvidence(
  locations: VoucherEvidenceLocations,
  evidence: VoucherDiscoveryEvidence,
): readonly string[] {
  const { outputDirectory, restrictedOutputDirectory } =
    validateVoucherEvidenceLocations(locations);

  fs.mkdirSync(outputDirectory, { recursive: true });
  fs.mkdirSync(restrictedOutputDirectory, { recursive: true });

  const files = [
    'request-shape.json',
    'response-structure.xml',
    'field-inventory.json',
    'structural-counts.json',
    'performance.json',
    'completeness-observations.md',
    'privacy-review.json',
    'run-metadata.json',
  ];
  writeJson(path.join(outputDirectory, files[0]!), evidence.requestShape);
  fs.writeFileSync(path.join(outputDirectory, files[1]!), evidence.sanitizedResponseXml, {
    encoding: 'utf8',
    flag: 'wx',
  });
  writeJson(path.join(outputDirectory, files[2]!), evidence.fieldInventory);
  writeJson(path.join(outputDirectory, files[3]!), evidence.structuralCounts);
  writeJson(path.join(outputDirectory, files[4]!), evidence.performance);
  fs.writeFileSync(path.join(outputDirectory, files[5]!), evidence.completenessMarkdown, {
    encoding: 'utf8',
    flag: 'wx',
  });
  writeJson(path.join(outputDirectory, files[6]!), evidence.privacyReview);
  writeJson(path.join(outputDirectory, files[7]!), evidence.runMetadata);
  writeJson(path.join(restrictedOutputDirectory, 'identity-fingerprints.local.json'), {
    nonCommittable: true,
    identityEvidence: evidence.identityEvidence,
  });
  return files;
}
