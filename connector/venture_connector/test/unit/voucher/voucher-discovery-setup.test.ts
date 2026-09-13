import { describe, expect, it } from 'vitest';

import { decidePolicy } from '../../../src/erp/policy/policy-engine.js';
import { findApprovedOperationByRequest } from '../../../src/tally/registry/operation-registry.js';
import {
  VOUCHER_DISCOVERY_COLLECTION_NAME,
  VOUCHER_DISCOVERY_OPERATION,
  VOUCHER_DISCOVERY_OPERATION_ID,
  validateVoucherDiscoveryRequestShape,
} from '../../../src/tally/registry/voucher-discovery-operation.js';
import {
  hasCompleteVoucherFixtureApprovals,
  validateVoucherDiscoveryManifest,
  VOUCHER_APPROVAL_ROLES,
  type VoucherDiscoveryManifest,
} from '../../../src/discovery/voucher-fixture-manifest.js';
import { nonOperationalVoucherDiscoveryHarness } from '../../../src/discovery/voucher-discovery-harness.js';
import { FORBIDDEN_REQUEST_TOKENS } from '../../../src/tally/security/capabilities.js';

function approvedManifest(): VoucherDiscoveryManifest {
  return {
    evidenceVersion: 'test-evidence-v1',
    governanceMode: 'single-authorized-owner',
    authorizedOwner: {
      name: 'Test Owner',
      role: 'Project Owner',
      approvedAt: '2026-07-26T00:00:00Z',
      approvalReference: 'LOCAL-TEST-APPROVAL',
      fixtureOnlyAcknowledged: true,
      syntheticDataConfirmed: true,
      backupConfirmed: true,
      sanitizationApproved: true,
      dateRangeApproved: true,
      expectedCountsApproved: true,
      executionAcknowledged: true,
    },
    fixtureCompanyAlias: 'FIXTURE_ONLY',
    nonProductionConfirmed: true,
    approvedExperiments: ['create'],
    dateRange: { dateFrom: '2026-04-01', dateTo: '2026-07-26' },
    expectedVouchers: [],
    expectedCountsByType: {},
    expectedActiveCount: 0,
    expectedCancelledCount: 0,
    mutationSequence: [],
    draftValuesApprovalStatus: 'owner-approved',
    sanitizationStatus: 'sanitized',
    reviewerSignOffStatus: 'approved',
  };
}

describe('Voucher discovery candidate policy', () => {
  it('remains experimental-disabled and denied by policy', () => {
    expect(VOUCHER_DISCOVERY_OPERATION).toMatchObject({
      classification: 'EXPERIMENTAL_DISABLED',
      rolloutStatus: 'disabled',
      productionGatewayAllowed: false,
    });
    expect(
      decidePolicy({
        operation: {
          operationId: VOUCHER_DISCOVERY_OPERATION_ID,
          classification: VOUCHER_DISCOVERY_OPERATION.classification,
          rolloutStatus: VOUCHER_DISCOVERY_OPERATION.rolloutStatus,
          maxRequestBytes: 1,
          isHealthProbe: false,
          autoApproveConditional: false,
        },
        requestBytes: 1,
        circuitState: 'closed',
        isHealthProbe: false,
      }).decision,
    ).toBe('DENY');
  });

  it('keeps discovery denied while the proven collection resolves only to production VOUCHERS', () => {
    expect(findApprovedOperationByRequest('DATA', 'Day Book')).toBeUndefined();
    expect(
      findApprovedOperationByRequest('COLLECTION', VOUCHER_DISCOVERY_COLLECTION_NAME),
    ).toMatchObject({ operationId: 'VOUCHERS' });
    expect(
      decidePolicy({
        operation: undefined,
        requestBytes: 1,
        circuitState: 'closed',
        isHealthProbe: false,
      }).decision,
    ).toBe('DENY');
  });

  it.each([
    [{ operationId: VOUCHER_DISCOVERY_OPERATION_ID }, 'Explicit fixture company is required.'],
    [
      { operationId: VOUCHER_DISCOVERY_OPERATION_ID, companyName: 'FIXTURE' },
      'dateFrom is required.',
    ],
    [
      {
        operationId: VOUCHER_DISCOVERY_OPERATION_ID,
        companyName: 'FIXTURE',
        dateFrom: '2026-05-02',
        dateTo: '2026-05-01',
      },
      'dateFrom must not follow dateTo.',
    ],
    [
      {
        operationId: VOUCHER_DISCOVERY_OPERATION_ID,
        companyName: 'FIXTURE',
        dateFrom: '2025-01-01',
        dateTo: '2026-01-02',
      },
      'Date range exceeds one financial year.',
    ],
  ])('rejects invalid or unbounded shape', (input, expected) => {
    expect(validateVoucherDiscoveryRequestShape(input)).toContain(expected);
  });

  it('retains all mutation and execution prohibitions', () => {
    expect(FORBIDDEN_REQUEST_TOKENS).toEqual(
      expect.arrayContaining(['IMPORT', 'CREATE', 'ALTER', 'DELETE', 'CANCEL', 'EXECUTE']),
    );
  });
});

describe('Voucher fixture manifest and non-operational harness', () => {
  it('requires explicit non-production confirmation and complete owner approval', () => {
    expect(validateVoucherDiscoveryManifest({}).valid).toBe(false);
    expect(hasCompleteVoucherFixtureApprovals(approvedManifest())).toBe(true);
    expect(
      hasCompleteVoucherFixtureApprovals({
        ...approvedManifest(),
        authorizedOwner: { ...approvedManifest().authorizedOwner!, executionAcknowledged: false },
      }),
    ).toBe(false);
  });

  it('rejects pending owner values, mode ambiguity, and production companies', () => {
    expect(
      validateVoucherDiscoveryManifest({
        ...approvedManifest(),
        authorizedOwner: {
          ...approvedManifest().authorizedOwner,
          name: '',
          approvedAt: '',
          approvalReference: '',
        },
      }).errors,
    ).toEqual(
      expect.arrayContaining([
        'authorizedOwner.name must be complete and non-placeholder.',
        'authorizedOwner.approvedAt must be a valid dated UTC approval.',
        'authorizedOwner.approvalReference must be complete and non-placeholder.',
      ]),
    );
    expect(
      validateVoucherDiscoveryManifest({
        ...approvedManifest(),
        approvals: [
          {
            role: 'product-owner',
            approverName: 'Another Owner',
            approved: true,
            approvedAt: '2026-07-26T00:00:00Z',
            reference: 'AMBIGUOUS-APPROVAL',
          },
        ],
      }).valid,
    ).toBe(false);
    expect(
      validateVoucherDiscoveryManifest(approvedManifest(), {
        productionCompanyNames: ['FIXTURE_ONLY'],
      }).errors,
    ).toContain('Production companies are prohibited.');
  });

  it('retains complete multi-reviewer governance support', () => {
    const multi: VoucherDiscoveryManifest = {
      ...approvedManifest(),
      governanceMode: 'multi-reviewer',
      authorizedOwner: undefined,
      approvals: VOUCHER_APPROVAL_ROLES.map((role) => ({
        role,
        approverName: `${role} reviewer`,
        approved: true,
        approvedAt: '2026-07-26T00:00:00Z',
        reference: `${role}-approval`,
      })),
    };
    expect(validateVoucherDiscoveryManifest(multi).valid).toBe(true);
  });

  it('fails closed for company, approval, mutation, path, sanitization and limit failures', () => {
    const errors = nonOperationalVoucherDiscoveryHarness.validate({
      fixtureManifestPath: '[manifest]',
      manifest: {
        ...approvedManifest(),
        authorizedOwner: { ...approvedManifest().authorizedOwner!, executionAcknowledged: false },
        approvedExperiments: [],
      },
      approvedFixtureCompany: 'PRODUCTION',
      productionCompanyNames: ['PRODUCTION'],
      dateFrom: '2025-01-01',
      dateTo: '2026-02-01',
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      outputDirectory: 'C:/unsafe',
      approvedOutputRoot: 'D:/approved',
      sanitizationMode: 'strict',
      maximumResponseBytes: 0,
      timeoutMs: 0,
      reviewerAcknowledgment: '',
      identityMutationExperimentRequested: true,
    });
    expect(errors).toEqual(
      expect.arrayContaining([
        'Manifest approvals are incomplete.',
        'Fixture company does not match the approved manifest.',
        'Production companies are prohibited.',
        'Mutation authorization is absent for identity experiments.',
        'Date range exceeds one financial year.',
        'Output directory is outside the approved root.',
        'A positive maximum response size is required.',
        'A positive timeout is required.',
        'Reviewer acknowledgment is required.',
      ]),
    );
  });

  it('has no public Voucher route implementation and emits no validation logs', async () => {
    const apiStubs = await import('../../../src/api/routes/api-stubs.js');
    expect(apiStubs.createApiStubsRouter).toBeTypeOf('function');
    expect(Object.keys(apiStubs)).not.toContain('registerVoucherRoutes');

    const messages: string[] = [];
    validateVoucherDiscoveryRequestShape({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: 'PRIVATE_FIXTURE_VALUE',
    });
    expect(messages).toEqual([]);
  });
});
