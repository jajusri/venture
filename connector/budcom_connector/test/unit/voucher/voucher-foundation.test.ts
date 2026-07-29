import { describe, expect, expectTypeOf, it } from 'vitest';

import {
  VOUCHER_CONTRACT_VERSION,
  type VoucherDetails,
  type VoucherSearchCriteria,
} from '../../../src/erp/voucher/voucher-domain.js';
import { VOUCHER_VALIDATION_LIMITS } from '../../../src/erp/voucher/voucher-validation.js';
import { registerServices } from '../../../src/bootstrap/register-services.js';
import { ServiceTokens } from '../../../src/core/tokens.js';
import type { VoucherFoundation } from '../../../src/services/voucher/voucher-foundation.js';
import type { VoucherRepositoryPort } from '../../../src/services/voucher/voucher-repository.interface.js';
import type { VoucherQueryService } from '../../../src/services/voucher/voucher-application.interface.js';
import {
  MIGRATION_009,
  MIGRATION_010,
  STORAGE_SCHEMA_VERSION,
} from '../../../src/storage/sqlite/schema.js';

describe('Voucher Phase 1 foundation', () => {
  it('registers a non-operational, read-only contract descriptor', () => {
    const context = registerServices({ env: 'test', logLevel: 'error' });
    const foundation = context.container.resolve<VoucherFoundation>(
      ServiceTokens.VoucherFoundation,
    );

    expect(foundation).toEqual({
      contractVersion: VOUCHER_CONTRACT_VERSION,
      sourceNeutral: true,
      readOnly: true,
      internallyComposed: true,
      customerOperational: false,
      persistenceEnabled: true,
      synchronizationInternallyComposed: true,
      validationLimits: VOUCHER_VALIDATION_LIMITS,
    });
  });

  it('keeps repository operations explicitly company scoped', () => {
    expectTypeOf<VoucherRepositoryPort['findById']>().parameters.toEqualTypeOf<
      [companyId: string, voucherId: string]
    >();
    expectTypeOf<VoucherRepositoryPort['stageMany']>().parameters.toEqualTypeOf<
      [companyId: string, syncRunId: string, vouchers: readonly VoucherDetails[]]
    >();
  });

  it('keeps application query inputs company scoped', () => {
    expectTypeOf<Parameters<VoucherQueryService['list']>[0]>().toMatchTypeOf<{
      companyId: string;
      criteria: VoucherSearchCriteria;
    }>();
  });

  it('defines contract limits without inventing a full narration limit', () => {
    expect(VOUCHER_VALIDATION_LIMITS).toEqual({
      searchQuery: 128,
      voucherNumber: 128,
      voucherType: 128,
      partyName: 256,
      reference: 256,
      narrationPreview: 160,
      defaultPageSize: 25,
      maximumPageSize: 100,
    });
    expect(VOUCHER_VALIDATION_LIMITS).not.toHaveProperty('narration');
  });

  it('registers the immutable Voucher snapshot migration', () => {
    expect(STORAGE_SCHEMA_VERSION).toBe(11);
    expect(MIGRATION_009).toContain('CREATE TABLE voucher_snapshots');
    expect(MIGRATION_009).toContain('CREATE TABLE voucher_active_snapshots');
    expect(MIGRATION_010).toContain('CREATE TABLE voucher_sync_reservations');
  });
});
