import type { VoucherDiscoveryManifest } from './voucher-fixture-manifest.js';
import { hasCompleteVoucherFixtureApprovals } from './voucher-fixture-manifest.js';
import {
  VOUCHER_DISCOVERY_OPERATION,
  VOUCHER_DISCOVERY_OPERATION_ID,
  validateVoucherDiscoveryRequestShape,
} from '../tally/registry/voucher-discovery-operation.js';

export interface VoucherDiscoveryHarnessInput {
  readonly fixtureManifestPath: string;
  readonly manifest: VoucherDiscoveryManifest;
  readonly approvedFixtureCompany: string;
  readonly productionCompanyNames: readonly string[];
  readonly dateFrom: string;
  readonly dateTo: string;
  readonly operationId: typeof VOUCHER_DISCOVERY_OPERATION_ID;
  readonly outputDirectory: string;
  readonly approvedOutputRoot: string;
  readonly sanitizationMode: string;
  readonly maximumResponseBytes: number;
  readonly timeoutMs: number;
  readonly reviewerAcknowledgment: string;
  readonly identityMutationExperimentRequested: boolean;
}

export interface VoucherDiscoveryHarness {
  validate(input: VoucherDiscoveryHarnessInput): readonly string[];
}

function pathInside(root: string, candidate: string): boolean {
  const normalizedRoot = root.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();
  const normalizedCandidate = candidate.replace(/\\/g, '/').toLowerCase();
  return normalizedRoot.length > 0 && normalizedCandidate.startsWith(`${normalizedRoot}/`);
}

export const nonOperationalVoucherDiscoveryHarness: VoucherDiscoveryHarness = {
  validate(input) {
    const errors = [
      ...validateVoucherDiscoveryRequestShape({
        operationId: input.operationId,
        companyName: input.approvedFixtureCompany,
        dateFrom: input.dateFrom,
        dateTo: input.dateTo,
      }),
    ];
    if (!input.fixtureManifestPath.trim()) errors.push('Fixture manifest path is required.');
    if (!hasCompleteVoucherFixtureApprovals(input.manifest)) errors.push('Manifest approvals are incomplete.');
    if (!input.manifest.nonProductionConfirmed) errors.push('Fixture is not confirmed non-production.');
    if (input.manifest.fixtureCompanyAlias !== input.approvedFixtureCompany) {
      errors.push('Fixture company does not match the approved manifest.');
    }
    if (
      input.productionCompanyNames.some(
        (name) => name.toLowerCase() === input.approvedFixtureCompany.toLowerCase(),
      )
    ) {
      errors.push('Production companies are prohibited.');
    }
    if (
      input.identityMutationExperimentRequested &&
      !(['create', 'edit', 'cancel', 'delete'] as const).some((experiment) =>
        input.manifest.approvedExperiments.includes(experiment),
      )
    ) {
      errors.push('Mutation authorization is absent for identity experiments.');
    }
    if (!pathInside(input.approvedOutputRoot, input.outputDirectory)) {
      errors.push('Output directory is outside the approved root.');
    }
    if (input.sanitizationMode !== 'strict') errors.push('Strict sanitization is required.');
    if (!Number.isInteger(input.maximumResponseBytes) || input.maximumResponseBytes <= 0) {
      errors.push('A positive maximum response size is required.');
    }
    if (!Number.isInteger(input.timeoutMs) || input.timeoutMs <= 0) {
      errors.push('A positive timeout is required.');
    }
    if (!input.reviewerAcknowledgment.trim()) errors.push('Reviewer acknowledgment is required.');
    if (
      VOUCHER_DISCOVERY_OPERATION.classification !== 'EXPERIMENTAL_DISABLED' ||
      VOUCHER_DISCOVERY_OPERATION.rolloutStatus !== 'disabled'
    ) {
      errors.push('Discovery operation is not disabled.');
    }
    return errors;
  },
};
